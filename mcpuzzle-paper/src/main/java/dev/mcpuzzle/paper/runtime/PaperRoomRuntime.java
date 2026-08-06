package dev.mcpuzzle.paper.runtime;

import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.mechanic.CornerObjectivesMechanic;
import dev.mcpuzzle.core.mechanic.DestructibleTargetMechanic;
import dev.mcpuzzle.core.mechanic.DynamicPartyPadsAndTargetMechanic;
import dev.mcpuzzle.core.mechanic.LatchingPressurePadsMechanic;
import dev.mcpuzzle.core.mechanic.LogicAnswerMechanic;
import dev.mcpuzzle.core.mechanic.LogicAnswerNormalizer;
import dev.mcpuzzle.core.mechanic.MechanicEvent;
import dev.mcpuzzle.core.mechanic.MechanicId;
import dev.mcpuzzle.core.mechanic.NumericKeypadMechanic;
import dev.mcpuzzle.core.mechanic.ProximityEscortMechanic;
import dev.mcpuzzle.core.mechanic.RoomAttemptId;
import dev.mcpuzzle.core.mechanic.RoomCompletionPolicy;
import dev.mcpuzzle.core.mechanic.RoomMechanic;
import dev.mcpuzzle.core.mechanic.RoomRuntimeCoordinator;
import dev.mcpuzzle.core.mechanic.RoomRuntimeOutcome;
import dev.mcpuzzle.core.mechanic.RoomRuntimeOutcomeType;
import dev.mcpuzzle.paper.map.MapPack;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Allay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

/** Main-thread Bukkit bridge over the pure core room state machines. */
public final class PaperRoomRuntime {
    public enum Signal { NONE, PROGRESSED, ROOM_COMPLETED, ROOM_FAILED }
    public enum AnswerStatus { CORRECT, INCORRECT, COOLDOWN, INVALID_FORMAT, NOT_SUPPORTED }
    public record AnswerSubmission(Signal signal, AnswerStatus status, long retryAfterSeconds) { }

    private final MapPack.RoomDefinition room;
    private final PartyRoster roster;
    private final RoomAttemptId attempt;
    private final RoomRuntimeCoordinator coordinator;
    private final Map<String, MapPack.MechanicDefinition> definitions = new HashMap<>();
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
            mechanics.add(create(definition));
        }
        this.coordinator = new RoomRuntimeCoordinator(attempt, RoomCompletionPolicy.ALL_MECHANICS, mechanics);
    }

    public Signal onMove(Player player) {
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

    public Signal onInteract(Location location) {
        for (MapPack.MechanicDefinition definition : room.mechanics()) {
            if (!(definition instanceof MapPack.NumericKeypad keypad)) continue;
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
        }
        return Signal.NONE;
    }

    public Signal submitNumericAnswer(String answer) {
        MapPack.NumericKeypad keypad = room.mechanics().stream()
                .filter(MapPack.NumericKeypad.class::isInstance).map(MapPack.NumericKeypad.class::cast)
                .findFirst().orElse(null);
        if (keypad == null || answer == null || !answer.matches("[0-9]+")
                || answer.length() < keypad.minDigits() || answer.length() > keypad.maxDigits()) {
            return Signal.NONE;
        }
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
            return new AnswerSubmission(legacy,
                    legacy == Signal.NONE ? AnswerStatus.NOT_SUPPORTED : AnswerStatus.CORRECT, 0);
        }
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
            Signal signal = escortReached(config, gateOpen);
            return signal;
        }
        double step = Math.min(config.movementSpeed(), delta.length());
        Location moved = entityLocation.add(delta.normalize().multiply(step));
        escort.teleport(moved);
        return Signal.PROGRESSED;
    }

    Signal escortReached(MapPack.Escort config, boolean gateOpen) {
        String id = escortTarget < config.checkpoints().size() ? "checkpoint-" + escortTarget : "destination";
        Signal signal = translate(coordinator.handle(new MechanicId(config.id()), attempt,
                new ProximityEscortMechanic.EntityReached(id, gateOpen)));
        if (signal != Signal.ROOM_FAILED) escortTarget++;
        return signal;
    }

    public void cleanup() {
        if (escort != null) { escort.remove(); escort = null; }
    }

    public void restoreBlocks(World world) {
        for (MapPack.MechanicDefinition definition : room.mechanics()) {
            if (definition instanceof MapPack.DestructibleTarget target) {
                world.getBlockAt(location(world, target.position())).setType(material(target.material()), false);
            } else if (definition instanceof MapPack.DynamicPartyPads dynamic) {
                world.getBlockAt(location(world, dynamic.target().position())).setType(material(dynamic.target().material()), false);
            }
        }
    }

    public MapPack.RoomDefinition room() { return room; }
    public long revision() { return attempt.revision(); }

    private RoomMechanic create(MapPack.MechanicDefinition definition) {
        MechanicId id = new MechanicId(definition.id());
        if (definition instanceof MapPack.PadMechanic pads) {
            List<String> names = java.util.stream.IntStream.range(0, pads.requiredCount()).mapToObj(i -> "pad-" + i).toList();
            return pads.type().equals("CORNER_OBJECTIVES")
                    ? new CornerObjectivesMechanic(id, attempt, names)
                    : new LatchingPressurePadsMechanic(id, attempt, names);
        }
        if (definition instanceof MapPack.DestructibleTarget target) {
            return new DestructibleTargetMechanic(id, attempt, target.id());
        }
        if (definition instanceof MapPack.NumericKeypad keypad) {
            return new NumericKeypadMechanic(id, attempt, Integer.toString(keypad.answer()), keypad.maxDigits());
        }
        if (definition instanceof MapPack.LogicAnswer terminal) {
            return new LogicAnswerMechanic(id, attempt, terminal.answers());
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

    private static Location location(World world, MapPack.Position position) {
        return new Location(world, position.x(), position.y(), position.z(), position.yaw(), position.pitch());
    }

    private static Material material(String value) {
        return Objects.requireNonNull(Material.matchMaterial(value), "Validated material disappeared");
    }
}
