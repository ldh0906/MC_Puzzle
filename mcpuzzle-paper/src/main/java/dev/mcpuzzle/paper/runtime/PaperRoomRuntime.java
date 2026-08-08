package dev.mcpuzzle.paper.runtime;

import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.mechanic.ChoiceInputMechanic;
import dev.mcpuzzle.core.mechanic.ClueRegionsMechanic;
import dev.mcpuzzle.core.mechanic.CornerObjectivesMechanic;
import dev.mcpuzzle.core.mechanic.DestructibleTargetMechanic;
import dev.mcpuzzle.core.mechanic.DynamicPartyPadsAndTargetMechanic;
import dev.mcpuzzle.core.mechanic.InputOperatorLock;
import dev.mcpuzzle.core.mechanic.LatchingPressurePadsMechanic;
import dev.mcpuzzle.core.mechanic.LogicAnswerMechanic;
import dev.mcpuzzle.core.mechanic.LogicAnswerNormalizer;
import dev.mcpuzzle.core.mechanic.MechanicEvent;
import dev.mcpuzzle.core.mechanic.MechanicId;
import dev.mcpuzzle.core.mechanic.MechanicStatus;
import dev.mcpuzzle.core.mechanic.NumericKeypadMechanic;
import dev.mcpuzzle.core.mechanic.OrderedInputMechanic;
import dev.mcpuzzle.core.mechanic.ProximityEscortMechanic;
import dev.mcpuzzle.core.mechanic.RoomAttemptId;
import dev.mcpuzzle.core.mechanic.RoomCompletionPolicy;
import dev.mcpuzzle.core.mechanic.RoomMechanic;
import dev.mcpuzzle.core.mechanic.RoomRuntimeCoordinator;
import dev.mcpuzzle.core.mechanic.RoomRuntimeOutcome;
import dev.mcpuzzle.core.mechanic.RoomRuntimeOutcomeType;
import dev.mcpuzzle.core.mechanic.ToggleInputMechanic;
import dev.mcpuzzle.paper.map.MapPack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Allay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Main-thread Bukkit bridge over the pure core room state machines. */
public final class PaperRoomRuntime {
    public enum Signal { NONE, PROGRESSED, ROOM_COMPLETED, ROOM_FAILED }
    public enum AnswerStatus { CORRECT, INCORRECT, COOLDOWN, INVALID_FORMAT, PREREQUISITE, NOT_SUPPORTED }
    public record AnswerSubmission(Signal signal, AnswerStatus status, long retryAfterSeconds) { }

    private final MapPack.RoomDefinition room;
    private final PartyRoster roster;
    private final RoomAttemptId attempt;
    private final RoomRuntimeCoordinator coordinator;
    private final Map<String, MapPack.MechanicDefinition> definitions = new HashMap<>();
    private final Map<String, RoomMechanic> mechanicStates = new HashMap<>();
    private final Map<UUID, String> occupiedMoveTriggers = new HashMap<>();
    private final InputOperatorLock operatorLock = new InputOperatorLock();
    private Instant nextAnswerAt = Instant.EPOCH;
    private Allay escort;
    private int escortTarget;

    public PaperRoomRuntime(SessionId sessionId, long revision, MapPack.RoomDefinition room, PartyRoster roster) {
        this.room = Objects.requireNonNull(room, "room");
        this.roster = Objects.requireNonNull(roster, "roster");
        this.attempt = new RoomAttemptId(sessionId, room.sequence(), revision);
        List<RoomMechanic> mechanics = new ArrayList<>();
        for (MapPack.MechanicDefinition definition : room.mechanics()) {
            definitions.put(definition.id(), definition);
            if (definition instanceof MapPack.LogicAnswer answer && !answer.chatSubmissionEnabled()) continue;
            RoomMechanic mechanic = create(definition);
            mechanics.add(mechanic);
            mechanicStates.put(definition.id(), mechanic);
        }
        this.coordinator = new RoomRuntimeCoordinator(attempt, RoomCompletionPolicy.ALL_MECHANICS, mechanics);
    }

    public Signal onMove(Player player) {
        Instant now = Instant.now();
        MoveTrigger trigger = findMoveTrigger(player.getLocation());
        String previous = occupiedMoveTriggers.get(player.getUniqueId());
        if (trigger == null) {
            occupiedMoveTriggers.remove(player.getUniqueId());
        } else {
            if (trigger.key().equals(previous)) return Signal.NONE;
            occupiedMoveTriggers.put(player.getUniqueId(), trigger.key());
            return dispatchMoveTrigger(player, trigger, now);
        }

        for (MapPack.MechanicDefinition definition : room.mechanics()) {
            if (definition instanceof MapPack.PadMechanic pads) {
                for (int index = 0; index < pads.pads().size(); index++) {
                    if (matches(player.getLocation(), pads.pads().get(index), 0.8)) {
                        MechanicEvent event = pads.type().equals("CORNER_OBJECTIVES")
                                ? new CornerObjectivesMechanic.ObjectiveActivated("pad-" + index)
                                : new LatchingPressurePadsMechanic.PadPressed("pad-" + index);
                        return translate(coordinator.handle(new MechanicId(pads.id()), attempt, event));
                    }
                }
            } else if (definition instanceof MapPack.DynamicPartyPads dynamic) {
                for (MapPack.IndexedPad pad : dynamic.playerPads()) {
                    int rosterIndex = pad.partyIndex() - 1;
                    if (rosterIndex < roster.size() && roster.members().get(rosterIndex).equals(player.getUniqueId())
                            && matches(player.getLocation(), pad.position(), 0.8)) {
                        return translate(coordinator.handle(new MechanicId(dynamic.id()), attempt,
                                new DynamicPartyPadsAndTargetMechanic.RosterPadPressed(rosterIndex)));
                    }
                }
            }
        }
        return Signal.NONE;
    }

    public Signal onBreak(Location location) {
        for (MapPack.MechanicDefinition definition : room.mechanics()) {
            if (definition instanceof MapPack.DestructibleTarget target && matches(location, target.position(), 0.1)) {
                return translate(coordinator.handle(new MechanicId(target.id()), attempt,
                        new DestructibleTargetMechanic.TargetDestroyed(target.id())));
            }
            if (definition instanceof MapPack.DynamicPartyPads dynamic && matches(location, dynamic.target().position(), 0.1)) {
                return translate(coordinator.handle(new MechanicId(dynamic.id()), attempt,
                        new DynamicPartyPadsAndTargetMechanic.TargetDestroyed()));
            }
        }
        return Signal.NONE;
    }

    public Signal onInteract(Player player, Location location) {
        Instant now = Instant.now();
        for (MapPack.MechanicDefinition definition : room.mechanics()) {
            if (definition instanceof MapPack.NumericKeypad keypad) {
                for (MapPack.DigitButton button : keypad.digitButtons()) {
                    if (matches(location, button.position(), 0.1)) {
                        return translate(coordinator.handle(new MechanicId(keypad.id()), attempt,
                                new NumericKeypadMechanic.DigitPressed(button.digit())));
                    }
                }
                if (matches(location, keypad.clearButton(), 0.1)) {
                    return translate(coordinator.handle(new MechanicId(keypad.id()), attempt,
                            new NumericKeypadMechanic.ClearPressed()));
                }
                if (matches(location, keypad.submitButton(), 0.1)) {
                    return translate(coordinator.handle(new MechanicId(keypad.id()), attempt,
                            new NumericKeypadMechanic.SubmitPressed()));
                }
            } else if (definition instanceof MapPack.OrderedInput ordered) {
                for (MapPack.Control control : ordered.controls()) {
                    if (control.activation().equals("CLICK") && matches(location, control.position(), 0.1)) {
                        return handleOrdered(player, ordered, control.id(), now);
                    }
                }
                if (ordered.clearButton().isPresent() && matches(location, ordered.clearButton().orElseThrow(), 0.1)) {
                    if (!acquire(player, now, ordered.operatorLockSeconds())) return Signal.NONE;
                    RoomRuntimeOutcome outcome = coordinator.handle(new MechanicId(ordered.id()), attempt,
                            new OrderedInputMechanic.ClearPressed());
                    actionBar(player, "입력을 지웠습니다.", NamedTextColor.YELLOW);
                    return translate(outcome);
                }
            } else if (definition instanceof MapPack.ChoiceInput choice) {
                for (MapPack.Control control : choice.controls()) {
                    if (control.activation().equals("CLICK") && matches(location, control.position(), 0.1)) {
                        return handleChoice(player, choice, control.id(), now);
                    }
                }
            } else if (definition instanceof MapPack.ToggleInput toggle) {
                for (MapPack.Control control : toggle.controls()) {
                    if (control.activation().equals("CLICK") && matches(location, control.position(), 0.1)) {
                        return handleToggle(player, toggle, new ToggleInputMechanic.TogglePressed(control.id()), now);
                    }
                }
                if (matches(location, toggle.submitButton(), 0.1)) {
                    return handleToggle(player, toggle, new ToggleInputMechanic.SubmitPressed(), now);
                }
                if (matches(location, toggle.clearButton(), 0.1)) {
                    return handleToggle(player, toggle, new ToggleInputMechanic.ClearPressed(), now);
                }
            }
        }
        return Signal.NONE;
    }

    public Signal submitNumericAnswer(String answer) {
        MapPack.NumericKeypad keypad = room.mechanics().stream()
                .filter(MapPack.NumericKeypad.class::isInstance).map(MapPack.NumericKeypad.class::cast)
                .findFirst().orElse(null);
        if (keypad == null || answer == null || !answer.matches("[0-9]+")
                || answer.length() < keypad.minDigits() || answer.length() > keypad.maxDigits()) return Signal.NONE;
        coordinator.handle(new MechanicId(keypad.id()), attempt, new NumericKeypadMechanic.ClearPressed());
        for (int index = 0; index < answer.length(); index++) {
            Signal signal = translate(coordinator.handle(new MechanicId(keypad.id()), attempt,
                    new NumericKeypadMechanic.DigitPressed(answer.charAt(index) - '0')));
            if (signal == Signal.ROOM_FAILED || signal == Signal.ROOM_COMPLETED) return signal;
        }
        return translate(coordinator.handle(new MechanicId(keypad.id()), attempt,
                new NumericKeypadMechanic.SubmitPressed()));
    }

    public AnswerSubmission submitAnswer(String answer, Instant now) {
        Objects.requireNonNull(now, "now");
        MapPack.LogicAnswer terminal = room.mechanics().stream()
                .filter(MapPack.LogicAnswer.class::isInstance).map(MapPack.LogicAnswer.class::cast)
                .findFirst().orElse(null);
        if (terminal == null) {
            Signal legacy = submitNumericAnswer(answer);
            return new AnswerSubmission(legacy, legacy == Signal.NONE ? AnswerStatus.NOT_SUPPORTED : AnswerStatus.CORRECT, 0);
        }
        if (!terminal.chatSubmissionEnabled()) return new AnswerSubmission(Signal.NONE, AnswerStatus.NOT_SUPPORTED, 0);
        boolean prerequisitesMet = terminal.requires().stream().allMatch(required -> coordinator
                .mechanicStatus(new MechanicId(required)).orElse(MechanicStatus.ACTIVE) == MechanicStatus.COMPLETED);
        if (!prerequisitesMet) return new AnswerSubmission(Signal.NONE, AnswerStatus.PREREQUISITE, 0);
        String normalized = answer == null ? "" : LogicAnswerNormalizer.normalize(answer);
        if (normalized.isEmpty() || normalized.length() > 64) {
            return new AnswerSubmission(Signal.NONE, AnswerStatus.INVALID_FORMAT, 0);
        }
        if (now.isBefore(nextAnswerAt)) {
            long seconds = Math.max(1, Duration.between(now, nextAnswerAt).toSeconds() + 1);
            return new AnswerSubmission(Signal.NONE, AnswerStatus.COOLDOWN, seconds);
        }

        RoomRuntimeOutcome outcome = coordinator.handle(new MechanicId(terminal.id()), attempt,
                new LogicAnswerMechanic.AnswerSubmitted(answer));
        Signal signal = translate(outcome);
        if (signal == Signal.ROOM_COMPLETED || outcome.detailKey().equals("logic_answer.correct")) {
            return new AnswerSubmission(signal, AnswerStatus.CORRECT, 0);
        }
        nextAnswerAt = now.plusSeconds(terminal.cooldownSeconds());
        return new AnswerSubmission(Signal.NONE, AnswerStatus.INCORRECT, terminal.cooldownSeconds());
    }

    public Signal tickEscort(World world, List<Player> party) {
        MapPack.Escort config = room.mechanics().stream().filter(MapPack.Escort.class::isInstance)
                .map(MapPack.Escort.class::cast).findFirst().orElse(null);
        if (config == null) return Signal.NONE;
        if (escort == null || !escort.isValid()) spawnEscort(world, config);
        if (escort == null) return Signal.NONE;
        List<MapPack.Position> route = new ArrayList<>(config.checkpoints());
        route.add(config.destination());
        if (escortTarget >= route.size()) return Signal.NONE;
        Location entityLocation = escort.getLocation();
        Location target = location(world, route.get(escortTarget));
        Vector delta = target.toVector().subtract(entityLocation.toVector());
        if (delta.lengthSquared() <= 0.36) {
            boolean gateOpen = party.stream().anyMatch(player -> player.isOnline() && player.getWorld().equals(world)
                    && player.getLocation().distanceSquared(target) <= config.proximityRadius() * config.proximityRadius());
            return escortReached(config, gateOpen);
        }
        double step = Math.min(config.movementSpeed(), delta.length());
        escort.teleport(entityLocation.add(delta.normalize().multiply(step)));
        return Signal.PROGRESSED;
    }

    Signal escortReached(MapPack.Escort config, boolean gateOpen) {
        String id = escortTarget < config.checkpoints().size() ? "checkpoint-" + escortTarget : "destination";
        Signal signal = translate(coordinator.handle(new MechanicId(config.id()), attempt,
                new ProximityEscortMechanic.EntityReached(id, gateOpen)));
        if (signal != Signal.ROOM_FAILED) escortTarget++;
        return signal;
    }

    public void releaseOperator(UUID playerId) {
        occupiedMoveTriggers.remove(playerId);
        operatorLock.release(playerId);
    }

    public void cleanup() {
        if (escort != null) { escort.remove(); escort = null; }
        occupiedMoveTriggers.clear();
        operatorLock.clear();
    }

    public void restoreBlocks(World world) {
        for (MapPack.MechanicDefinition definition : room.mechanics()) {
            if (definition instanceof MapPack.DestructibleTarget target) {
                world.getBlockAt(location(world, target.position())).setType(material(target.material()), false);
            } else if (definition instanceof MapPack.DynamicPartyPads dynamic) {
                world.getBlockAt(location(world, dynamic.target().position())).setType(material(dynamic.target().material()), false);
            } else if (definition instanceof MapPack.ToggleInput toggle) {
                toggle.controls().forEach(control -> control.indicator().ifPresent(position ->
                        world.getBlockAt(location(world, position)).setType(Material.RED_STAINED_GLASS, false)));
            }
        }
    }

    public MapPack.RoomDefinition room() { return room; }
    public long revision() { return attempt.revision(); }

    private MoveTrigger findMoveTrigger(Location location) {
        for (MapPack.MechanicDefinition definition : room.mechanics()) {
            if (definition instanceof MapPack.ClueRegions clues) {
                for (MapPack.ClueRegion region : clues.regions()) {
                    if (contains(region.bounds(), location)) return new MoveTrigger(clues, region.id(), region);
                }
            } else if (definition instanceof MapPack.OrderedInput ordered) {
                for (MapPack.Control control : ordered.controls()) {
                    if (control.activation().equals("STEP") && matches(location, control.position(), 0.8)) {
                        return new MoveTrigger(ordered, control.id(), null);
                    }
                }
            } else if (definition instanceof MapPack.ChoiceInput choice) {
                for (MapPack.Control control : choice.controls()) {
                    if (control.activation().equals("STEP") && matches(location, control.position(), 0.8)) {
                        return new MoveTrigger(choice, control.id(), null);
                    }
                }
            } else if (definition instanceof MapPack.ToggleInput toggle) {
                for (MapPack.Control control : toggle.controls()) {
                    if (control.activation().equals("STEP") && matches(location, control.position(), 0.8)) {
                        return new MoveTrigger(toggle, control.id(), null);
                    }
                }
            }
        }
        return null;
    }

    private Signal dispatchMoveTrigger(Player player, MoveTrigger trigger, Instant now) {
        if (trigger.definition() instanceof MapPack.ClueRegions clues) {
            MapPack.ClueRegion region = Objects.requireNonNull(trigger.clueRegion());
            player.sendMessage("§b[환경 단서] §f" + region.message());
            region.sound().ifPresent(sound -> playConfiguredSound(player, sound, region.pitch()));
            return translate(coordinator.handle(new MechanicId(clues.id()), attempt,
                    new ClueRegionsMechanic.RegionEntered(region.id())));
        }
        if (trigger.definition() instanceof MapPack.OrderedInput ordered) {
            return handleOrdered(player, ordered, trigger.controlId(), now);
        }
        if (trigger.definition() instanceof MapPack.ChoiceInput choice) {
            return handleChoice(player, choice, trigger.controlId(), now);
        }
        if (trigger.definition() instanceof MapPack.ToggleInput toggle) {
            return handleToggle(player, toggle, new ToggleInputMechanic.TogglePressed(trigger.controlId()), now);
        }
        return Signal.NONE;
    }

    private Signal handleOrdered(Player player, MapPack.OrderedInput definition, String controlId, Instant now) {
        if (!acquire(player, now, definition.operatorLockSeconds())) return Signal.NONE;
        OrderedInputMechanic mechanic = (OrderedInputMechanic) mechanicStates.get(definition.id());
        RoomRuntimeOutcome outcome = coordinator.handle(new MechanicId(definition.id()), attempt,
                new OrderedInputMechanic.ControlEntered(controlId));
        if (outcome.detailKey().equals("ordered_input.wrong_reset")) {
            failureFeedback(player, "순서가 달라 입력을 지웠습니다.");
        } else if (outcome.detailKey().equals("ordered_input.completed")) {
            successFeedback(player, definition.resultText());
            operatorLock.clear();
        } else {
            actionBar(player, formatOrdered(definition, mechanic.cursor()), NamedTextColor.AQUA);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.4f);
        }
        return translate(outcome);
    }

    private Signal handleChoice(Player player, MapPack.ChoiceInput definition, String controlId, Instant now) {
        if (!acquire(player, now, definition.operatorLockSeconds())) return Signal.NONE;
        RoomRuntimeOutcome outcome = coordinator.handle(new MechanicId(definition.id()), attempt,
                new ChoiceInputMechanic.ChoiceSelected(controlId));
        if (outcome.detailKey().equals("choice_input.completed")) {
            successFeedback(player, definition.resultText());
            operatorLock.clear();
        }
        else if (outcome.detailKey().equals("choice_input.wrong_reset")) failureFeedback(player, "선택이 맞지 않습니다.");
        return translate(outcome);
    }

    private Signal handleToggle(Player player, MapPack.ToggleInput definition, MechanicEvent event, Instant now) {
        if (!acquire(player, now, definition.operatorLockSeconds())) return Signal.NONE;
        RoomRuntimeOutcome outcome = coordinator.handle(new MechanicId(definition.id()), attempt, event);
        ToggleInputMechanic mechanic = (ToggleInputMechanic) mechanicStates.get(definition.id());
        updateToggleIndicators(player.getWorld(), definition, mechanic.selected());
        if (outcome.detailKey().equals("toggle_input.completed")) {
            successFeedback(player, definition.resultText());
            operatorLock.clear();
        } else if (outcome.detailKey().equals("toggle_input.wrong_reset")
                || outcome.detailKey().equals("toggle_input.selection_limit_reset")) {
            failureFeedback(player, "조합이 맞지 않아 선택을 지웠습니다.");
        } else {
            String display = definition.controls().size() == 13
                    ? definition.controls().stream().map(control -> mechanic.selected().contains(control.id()) ? "1" : "0")
                        .reduce("", String::concat)
                    : mechanic.selected().isEmpty() ? "선택 없음" : definition.controls().stream()
                        .filter(control -> mechanic.selected().contains(control.id())).map(MapPack.Control::label)
                        .reduce((left, right) -> left + " + " + right).orElse("선택 없음");
            actionBar(player, display, NamedTextColor.AQUA);
        }
        return translate(outcome);
    }

    private boolean acquire(Player player, Instant now, int seconds) {
        if (operatorLock.acquire(player.getUniqueId(), now, Duration.ofSeconds(seconds))) return true;
        UUID ownerId = operatorLock.owner(now).orElse(null);
        Player owner = ownerId == null ? null : player.getServer().getPlayer(ownerId);
        actionBar(player, (owner == null ? "다른 파티원" : owner.getName()) + "님이 조작 중입니다.", NamedTextColor.YELLOW);
        return false;
    }

    private String formatOrdered(MapPack.OrderedInput definition, int cursor) {
        List<String> visible = definition.expected().subList(0, Math.min(cursor, definition.expected().size())).stream()
                .map(MapPack.ExpectedStep::display).filter(value -> !value.isEmpty()).toList();
        if (visible.isEmpty()) return "입력 없음";
        if (definition.groups().isEmpty()) return String.join(" → ", visible);
        List<String> groups = new ArrayList<>();
        int offset = 0;
        for (int size : definition.groups()) {
            if (offset >= visible.size()) break;
            int end = Math.min(offset + size, visible.size());
            groups.add(String.join("", visible.subList(offset, end)));
            offset = end;
        }
        return String.join(" / ", groups);
    }

    private void updateToggleIndicators(World world, MapPack.ToggleInput definition, Set<String> selected) {
        for (MapPack.Control control : definition.controls()) {
            control.indicator().ifPresent(position -> world.getBlockAt(location(world, position)).setType(
                    selected.contains(control.id()) ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS, false));
        }
    }

    private void successFeedback(Player player, String text) {
        actionBar(player, text, NamedTextColor.GREEN);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.6f);
    }

    private void failureFeedback(Player player, String text) {
        actionBar(player, text, NamedTextColor.RED);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
    }

    private void actionBar(Player player, String text, NamedTextColor color) {
        player.sendActionBar(Component.text(text, color));
    }

    private void playConfiguredSound(Player player, String sound, float pitch) {
        try { player.playSound(player.getLocation(), Sound.valueOf(sound), 0.8f, pitch); }
        catch (IllegalArgumentException ignored) { /* validated map data should prevent this */ }
    }

    private RoomMechanic create(MapPack.MechanicDefinition definition) {
        MechanicId id = new MechanicId(definition.id());
        if (definition instanceof MapPack.PadMechanic pads) {
            List<String> names = java.util.stream.IntStream.range(0, pads.requiredCount()).mapToObj(i -> "pad-" + i).toList();
            return pads.type().equals("CORNER_OBJECTIVES")
                    ? new CornerObjectivesMechanic(id, attempt, names)
                    : new LatchingPressurePadsMechanic(id, attempt, names);
        }
        if (definition instanceof MapPack.DestructibleTarget target) return new DestructibleTargetMechanic(id, attempt, target.id());
        if (definition instanceof MapPack.NumericKeypad keypad) {
            return new NumericKeypadMechanic(id, attempt, Integer.toString(keypad.answer()), keypad.maxDigits());
        }
        if (definition instanceof MapPack.LogicAnswer terminal) return new LogicAnswerMechanic(id, attempt, terminal.answers());
        if (definition instanceof MapPack.ClueRegions clues) {
            return new ClueRegionsMechanic(id, attempt, clues.regions().stream().map(MapPack.ClueRegion::id).toList());
        }
        if (definition instanceof MapPack.OrderedInput ordered) {
            return new OrderedInputMechanic(id, attempt, ordered.expected().stream().map(MapPack.ExpectedStep::controlId).toList());
        }
        if (definition instanceof MapPack.ChoiceInput choice) return new ChoiceInputMechanic(id, attempt, choice.correctControl());
        if (definition instanceof MapPack.ToggleInput toggle) {
            return new ToggleInputMechanic(id, attempt, toggle.controls().stream().map(MapPack.Control::id).toList(),
                    toggle.expectedActive(), toggle.maxSelections());
        }
        if (definition instanceof MapPack.Escort escort) {
            List<String> checkpoints = java.util.stream.IntStream.range(0, escort.checkpoints().size())
                    .mapToObj(i -> "checkpoint-" + i).toList();
            return new ProximityEscortMechanic(id, attempt, checkpoints, "destination");
        }
        if (definition instanceof MapPack.DynamicPartyPads) {
            return new DynamicPartyPadsAndTargetMechanic(id, attempt, roster.size());
        }
        throw new IllegalArgumentException("Unsupported mechanic definition " + definition.type());
    }

    private void spawnEscort(World world, MapPack.Escort config) {
        cleanup();
        escortTarget = 0;
        if (!(world.spawnEntity(location(world, config.entity().spawn()), EntityType.ALLAY) instanceof Allay allay)) return;
        allay.setCustomName(config.entity().customName());
        allay.setCustomNameVisible(true);
        allay.setInvulnerable(config.entity().invulnerable());
        allay.setAI(false);
        allay.setSilent(true);
        allay.setRemoveWhenFarAway(false);
        this.escort = allay;
    }

    private static Signal translate(RoomRuntimeOutcome outcome) {
        return switch (outcome.type()) {
            case ROOM_COMPLETED -> Signal.ROOM_COMPLETED;
            case ROOM_FAILED -> Signal.ROOM_FAILED;
            case MECHANIC_PROGRESSED -> Signal.PROGRESSED;
            default -> Signal.NONE;
        };
    }

    private static boolean matches(Location location, MapPack.Position position, double tolerance) {
        return Math.abs(location.getX() - position.x()) <= tolerance
                && Math.abs(location.getY() - position.y()) <= Math.max(1.1, tolerance)
                && Math.abs(location.getZ() - position.z()) <= tolerance;
    }

    private static boolean contains(MapPack.Bounds bounds, Location location) {
        return location.getX() >= bounds.min().x() && location.getX() <= bounds.max().x()
                && location.getY() >= bounds.min().y() && location.getY() <= bounds.max().y()
                && location.getZ() >= bounds.min().z() && location.getZ() <= bounds.max().z();
    }

    private static Location location(World world, MapPack.Position position) {
        return new Location(world, position.x(), position.y(), position.z(), position.yaw(), position.pitch());
    }

    private static Material material(String value) {
        return Objects.requireNonNull(Material.matchMaterial(value), "Validated material disappeared");
    }

    private record MoveTrigger(MapPack.MechanicDefinition definition, String controlId,
                               MapPack.ClueRegion clueRegion) {
        private String key() { return definition.id() + ":" + controlId; }
    }
}
