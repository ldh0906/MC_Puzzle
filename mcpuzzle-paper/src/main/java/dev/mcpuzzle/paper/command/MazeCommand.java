package dev.mcpuzzle.paper.command;

import dev.mcpuzzle.paper.authoring.AuthoringWandService;
import dev.mcpuzzle.paper.gui.MazeMenu;
import dev.mcpuzzle.paper.runtime.MazeRuntimeService;
import dev.mcpuzzle.paper.runtime.PluginReadiness;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class MazeCommand implements CommandExecutor, TabCompleter {
    static final List<String> ROOT = List.of("help", "status", "party", "accept", "deny", "decline",
            "start", "queue", "saves", "resume", "leave", "delete", "hint", "leaderboard", "answer", "admin");
    static final List<String> PARTY = List.of("create", "invite", "accept", "deny", "decline", "kick",
            "status", "leave", "disband");
    private final MazeRuntimeService runtime;
    private final MazeMenu menu;
    private final PluginReadiness readiness;
    private final AuthoringWandService authoring;
    private final Consumer<CommandSender> reloadAction;
    private final Consumer<CommandSender> verifyWorldAction;

    public MazeCommand(MazeRuntimeService runtime, MazeMenu menu, PluginReadiness readiness,
                       AuthoringWandService authoring, Consumer<CommandSender> reloadAction,
                       Consumer<CommandSender> verifyWorldAction) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.menu = Objects.requireNonNull(menu, "menu");
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.authoring = Objects.requireNonNull(authoring, "authoring");
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
        this.verifyWorldAction = Objects.requireNonNull(verifyWorldAction, "verifyWorldAction");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) menu.openMain(player); else help(sender, label);
            return true;
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (root.equals("help")) { help(sender, label); return true; }
        if (root.equals("status")) { status(sender); return true; }
        if (!(sender instanceof Player player) && !root.equals("admin")) {
            sender.sendMessage("§c플레이어만 사용할 수 있는 명령입니다."); return true;
        }
        try {
            switch (root) {
                case "party" -> party((Player) sender, args);
                case "accept" -> runtime.accept((Player) sender, online(args, 1));
                case "deny", "decline" -> runtime.decline((Player) sender, online(args, 1));
                case "start" -> {
                    boolean namedMaze = args.length >= 2 && !args[1].matches("[1-3]");
                    String mazeId = namedMaze ? mazeId(args[1]) : "midnight-easy";
                    runtime.requestStart((Player) sender, mazeId, slot(args, namedMaze ? 2 : 1, 1));
                }
                case "queue" -> {
                    if (args.length >= 2 && args[1].equalsIgnoreCase("cancel")) runtime.cancelQueue((Player) sender);
                    else sender.sendMessage("§e/" + label + " queue cancel");
                }
                case "saves" -> menu.openSaves((Player) sender);
                case "resume" -> {
                    boolean namedMaze = args.length >= 2 && !args[1].matches("[1-3]");
                    int slotIndex = namedMaze ? 2 : 1;
                    int ownerIndex = namedMaze ? 3 : 2;
                    runtime.requestResume((Player) sender, namedMaze ? mazeId(args[1]) : "midnight-easy",
                            slot(args, slotIndex, -1), args.length > ownerIndex
                                    ? identity(args[ownerIndex]) : ((Player) sender).getUniqueId());
                }
                case "leave" -> runtime.leave((Player) sender);
                case "delete" -> runtime.deleteSave((Player) sender,
                        args.length >= 3 ? identity(args[2]) : ((Player) sender).getUniqueId(), slot(args, 1, -1));
                case "hint" -> hint((Player) sender, args);
                case "leaderboard" -> {
                    boolean namedMaze = args.length >= 2 && !args[1].matches("[1-4]");
                    runtime.leaderboard((Player) sender, namedMaze ? mazeId(args[1]) : "midnight-easy",
                            args.length >= (namedMaze ? 3 : 2) ? integer(args[namedMaze ? 2 : 1], 1, 4, "파티 인원") : 1);
                }
                case "answer" -> {
                    if (args.length < 2) sender.sendMessage("§e/" + label + " answer <정답>");
                    else runtime.submitAnswer((Player) sender, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                }
                case "admin" -> admin(sender, args);
                default -> help(sender, label);
            }
        } catch (IllegalArgumentException failure) {
            sender.sendMessage("§c" + failure.getMessage());
        }
        if (sender instanceof Player active) runtime.recordActivity(active);
        return true;
    }

    private void party(Player player, String[] args) {
        if (args.length < 2) { showParty(player); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> runtime.createParty(player);
            case "status" -> showParty(player);
            case "disband" -> runtime.disbandParty(player);
            case "leave" -> runtime.leaveOpenParty(player);
            case "invite" -> runtime.invite(player, online(args, 2));
            case "accept" -> runtime.accept(player, online(args, 2));
            case "deny", "decline" -> runtime.decline(player, online(args, 2));
            case "kick" -> runtime.kick(player, online(args, 2));
            default -> player.sendMessage("§e/maze party <create|invite|accept|deny|kick|status|leave|disband>");
        }
    }

    private void hint(Player player, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("request")) runtime.requestHint(player);
        else if (args[1].equalsIgnoreCase("confirm")) runtime.confirmHint(player, true);
        else if (args[1].equalsIgnoreCase("decline")) runtime.confirmHint(player, false);
        else if (args[1].equalsIgnoreCase("view") && args.length == 3) runtime.viewHint(player, integer(args[2], 1, 3, "힌트 단계"));
        else menu.openHints(player);
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcpuzzle.admin")) { sender.sendMessage("§c권한이 없습니다."); return; }
        if (args.length < 2) { sender.sendMessage("§e/maze admin <reload|verify-world|wand|selection|saves|delete|transfer>"); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "reload" -> reloadAction.accept(sender);
            case "verify-world" -> verifyWorldAction.accept(sender);
            case "wand" -> {
                if (sender instanceof Player player) authoring.give(player); else sender.sendMessage("§c플레이어만 받을 수 있습니다.");
            }
            case "selection" -> {
                if (sender instanceof Player player) authoring.printSelection(player); else sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            }
            case "saves" -> {
                if (!(sender instanceof Player operator) || args.length != 3) {
                    sender.sendMessage("§e/maze admin saves <소유자 UUID/이름>"); return;
                }
                runtime.listSaves(operator, identity(args[2]));
            }
            case "delete" -> {
                if (!(sender instanceof Player operator) || args.length != 4) {
                    sender.sendMessage("§e/maze admin delete <소유자 UUID/이름> <슬롯>"); return;
                }
                runtime.deleteSave(operator, identity(args[2]), integer(args[3], 1, 3, "슬롯"));
            }
            case "transfer" -> {
                if (!(sender instanceof Player operator) || args.length != 5) {
                    sender.sendMessage("§e/maze admin transfer <현재소유자 UUID/이름> <슬롯> <새소유자 UUID/이름>"); return;
                }
                runtime.transferOwnership(operator, identity(args[2]), integer(args[3], 1, 3, "슬롯"), identity(args[4]));
            }
            default -> sender.sendMessage("§e/maze admin <reload|verify-world|wand|selection|saves|delete|transfer>");
        }
    }

    private void status(CommandSender sender) {
        sender.sendMessage("§6[MCPuzzle] §f" + readiness.state() + " §7- " + readiness.detail());
        if (sender instanceof Player player) {
            runtime.run(player.getUniqueId()).ifPresentOrElse(run -> sender.sendMessage("§f미궁: " + run.mazeName() + " / " + run.state()
                    + " / 방 " + run.room() + " / 슬롯 " + run.slot()), () -> sender.sendMessage("§7활성 미궁 없음"));
        }
    }

    private void showParty(Player player) {
        runtime.party(player.getUniqueId()).ifPresentOrElse(party -> {
            player.sendMessage("§6[파티] §f" + party.lifecycle() + " / " + party.members().size() + "명");
            party.members().forEach(id -> player.sendMessage((id.equals(party.leaderId()) ? "§e★ " : "§7- ")
                    + Objects.toString(Bukkit.getOfflinePlayer(id).getName(), id.toString())));
        }, () -> player.sendMessage("§c파티가 없습니다. /maze party create"));
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("§6[MCPuzzle 명령어]");
        sender.sendMessage("§e/" + label + " §7- 미궁 GUI");
        sender.sendMessage("§e/" + label + " accept <파티장> | deny <파티장> §7- 초대 응답");
        sender.sendMessage("§e/" + label + " party <create|invite|kick|status|leave|disband>");
        sender.sendMessage("§e/" + label + " start <easy|normal|hard> [1-3] §7- 새 시작");
        sender.sendMessage("§e/" + label + " resume <easy|normal|hard> <1-3> [현재소유자 UUID/이름]");
        sender.sendMessage("§e/" + label + " saves | leave | queue cancel | delete <1-3> [현재소유자 UUID/이름]");
        sender.sendMessage("§e/" + label + " hint <request|confirm|decline|view 1-3>");
        sender.sendMessage("§e/" + label + " answer <정답> | leaderboard [easy|normal|hard] [1-4] | status");
    }

    private Player online(String[] args, int index) {
        if (args.length <= index) throw new IllegalArgumentException("플레이어 이름이 필요합니다.");
        Player target = Bukkit.getPlayerExact(args[index]);
        if (target == null) throw new IllegalArgumentException("온라인 플레이어를 찾을 수 없습니다.");
        return target;
    }

    private int slot(String[] args, int index, int fallback) {
        if (args.length <= index) {
            if (fallback > 0) return fallback;
            throw new IllegalArgumentException("슬롯 번호가 필요합니다.");
        }
        return integer(args[index], 1, 3, "슬롯");
    }

    static int integer(String value, int min, int max, String name) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException failure) { throw new IllegalArgumentException(name + "은 " + min + "~" + max + "여야 합니다."); }
    }

    private UUID identity(String value) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return Bukkit.getOfflinePlayer(value).getUniqueId(); }
    }

    private String mazeId(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "easy", "쉬움" -> "midnight-easy";
            case "normal", "보통" -> "midnight-normal";
            case "hard", "어려움" -> "midnight-hard";
            default -> throw new IllegalArgumentException("미궁은 easy, normal, hard 중 하나여야 합니다.");
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return prefix(ROOT, args[0]);
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "party" -> prefix(PARTY, args[1]);
                case "accept", "deny", "decline" -> prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
                case "queue" -> prefix(List.of("cancel"), args[1]);
                case "start", "resume" -> prefix(List.of("easy", "normal", "hard", "1", "2", "3"), args[1]);
                case "delete" -> prefix(List.of("1", "2", "3"), args[1]);
                case "hint" -> prefix(List.of("request", "confirm", "decline", "view"), args[1]);
                case "leaderboard" -> prefix(List.of("1", "2", "3", "4"), args[1]);
                case "admin" -> sender.hasPermission("mcpuzzle.admin")
                        ? prefix(List.of("reload", "verify-world", "wand", "selection", "saves", "delete", "transfer"), args[1]) : List.of();
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("hint") && args[1].equalsIgnoreCase("view"))
            return prefix(List.of("1", "2", "3"), args[2]);
        if (args.length == 3 && List.of("start", "resume").contains(args[0].toLowerCase(Locale.ROOT))
                && List.of("easy", "normal", "hard").contains(args[1].toLowerCase(Locale.ROOT)))
            return prefix(List.of("1", "2", "3"), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("party")
                && List.of("invite", "accept", "deny", "decline", "kick").contains(args[1].toLowerCase(Locale.ROOT)))
            return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        if (args.length == 3 && List.of("resume", "delete").contains(args[0].toLowerCase(Locale.ROOT)))
            return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        return List.of();
    }

    private List<String> prefix(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
