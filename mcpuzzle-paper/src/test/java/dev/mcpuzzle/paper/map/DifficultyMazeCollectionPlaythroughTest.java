package dev.mcpuzzle.paper.map;

import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.domain.OperationResult;
import dev.mcpuzzle.core.domain.Party;
import dev.mcpuzzle.core.domain.PuzzleSession;
import dev.mcpuzzle.core.domain.SessionFailure;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.domain.SessionState;
import dev.mcpuzzle.core.mechanic.ChoiceInputMechanic;
import dev.mcpuzzle.core.mechanic.ClueRegionsMechanic;
import dev.mcpuzzle.core.mechanic.LogicAnswerMechanic;
import dev.mcpuzzle.core.mechanic.MechanicId;
import dev.mcpuzzle.core.mechanic.OrderedInputMechanic;
import dev.mcpuzzle.core.mechanic.RoomAttemptId;
import dev.mcpuzzle.core.mechanic.RoomCompletionPolicy;
import dev.mcpuzzle.core.mechanic.RoomMechanic;
import dev.mcpuzzle.core.mechanic.RoomRuntimeCoordinator;
import dev.mcpuzzle.core.mechanic.RoomRuntimeOutcome;
import dev.mcpuzzle.core.mechanic.RoomRuntimeOutcomeType;
import dev.mcpuzzle.core.mechanic.ToggleInputMechanic;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifficultyMazeCollectionPlaythroughTest {
    private static final Instant START = Instant.parse("2026-08-06T00:00:00Z");

    @Test
    void everySupportedPartySizeCanCompleteEveryDifficultyMaze() throws Exception {
        for (MapPack pack : loadPacks()) {
            for (int partySize = 1; partySize <= 4; partySize++) {
                PuzzleSession session = activeSession(pack, party(partySize), START);
                solveRooms(pack, session, 1, pack.rooms().size(), START);
                assertEquals(SessionState.COMPLETED, session.state());
                assertEquals(pack.rooms().size() + 1, session.currentRoom());
                assertEquals(pack.rooms().size(), session.roomAttemptRevision());
                assertEquals(Duration.ofSeconds(pack.rooms().size() * 30L),
                        session.metricsAt(START.plusSeconds(pack.rooms().size() * 30L + 60)).activePlayTime());
            }
        }
    }

    @Test
    void everyDifficultyMazeCanSaveRehydrateAndFinishWithTheExactRoster() throws Exception {
        for (MapPack pack : loadPacks()) {
            Party party = party(4);
            PuzzleSession original = activeSession(pack, party, START);
            int split = Math.max(1, pack.rooms().size() / 2);
            solveRooms(pack, original, 1, split, START);
            Instant suspendedAt = START.plusSeconds(split * 30L + 1);
            assertSuccess(original.requestSuspend(party.leaderId(), suspendedAt));
            PuzzleSession restored = PuzzleSession.rehydrate(original.snapshot(suspendedAt.plusSeconds(1)));
            assertEquals(split + 1, restored.currentRoom());
            assertSuccess(restored.queueForResume(party.leaderId(), party.toRoster()));
            assertSuccess(restored.beginProvisioning());
            assertSuccess(restored.activate(suspendedAt.plusSeconds(30)));
            solveRooms(pack, restored, split + 1, pack.rooms().size(), suspendedAt);
            assertEquals(SessionState.COMPLETED, restored.state());
        }
    }

    private void solveRooms(MapPack pack, PuzzleSession session, int first, int last, Instant origin) {
        for (int sequence = first; sequence <= last; sequence++) {
            assertEquals(sequence, session.currentRoom());
            MapPack.RoomDefinition room = pack.room(sequence);
            Instant answeredAt = origin.plusSeconds(sequence * 30L);
            RoomRuntimeOutcome answer = solveActualMechanics(room, session.id(), session.roomAttemptRevision());
            assertEquals(RoomRuntimeOutcomeType.ROOM_COMPLETED, answer.type(), room.id());
            assertSuccess(session.completeCurrentRoom(answeredAt));
        }
    }

    private RoomRuntimeOutcome solveActualMechanics(MapPack.RoomDefinition room, SessionId sessionId, long revision) {
        RoomAttemptId attempt = new RoomAttemptId(sessionId, room.sequence(), revision);
        List<RoomMechanic> states = new ArrayList<>();
        for (MapPack.MechanicDefinition definition : room.mechanics()) {
            if (definition instanceof MapPack.LogicAnswer logic) {
                if (logic.chatSubmissionEnabled()) {
                    states.add(new LogicAnswerMechanic(new MechanicId(logic.id()), attempt, logic.answers()));
                }
            } else if (definition instanceof MapPack.ClueRegions clues) {
                states.add(new ClueRegionsMechanic(new MechanicId(clues.id()), attempt,
                        clues.regions().stream().map(MapPack.ClueRegion::id).toList()));
            } else if (definition instanceof MapPack.OrderedInput ordered) {
                states.add(new OrderedInputMechanic(new MechanicId(ordered.id()), attempt,
                        ordered.expected().stream().map(MapPack.ExpectedStep::controlId).toList()));
            } else if (definition instanceof MapPack.ChoiceInput choice) {
                states.add(new ChoiceInputMechanic(new MechanicId(choice.id()), attempt, choice.correctControl()));
            } else if (definition instanceof MapPack.ToggleInput toggle) {
                states.add(new ToggleInputMechanic(new MechanicId(toggle.id()), attempt,
                        toggle.controls().stream().map(MapPack.Control::id).toList(),
                        toggle.expectedActive(), toggle.maxSelections()));
            } else {
                throw new AssertionError("playthrough simulator does not support " + definition.type());
            }
        }
        RoomRuntimeCoordinator coordinator = new RoomRuntimeCoordinator(
                attempt, RoomCompletionPolicy.ALL_MECHANICS, states);
        RoomRuntimeOutcome latest = null;
        for (MapPack.MechanicDefinition definition : room.mechanics()) {
            MechanicId id = new MechanicId(definition.id());
            if (definition instanceof MapPack.ClueRegions clues) {
                for (MapPack.ClueRegion region : clues.regions()) {
                    latest = coordinator.handle(id, attempt, new ClueRegionsMechanic.RegionEntered(region.id()));
                }
            } else if (definition instanceof MapPack.OrderedInput ordered) {
                for (MapPack.ExpectedStep step : ordered.expected()) {
                    latest = coordinator.handle(id, attempt, new OrderedInputMechanic.ControlEntered(step.controlId()));
                }
            } else if (definition instanceof MapPack.ChoiceInput choice) {
                latest = coordinator.handle(id, attempt, new ChoiceInputMechanic.ChoiceSelected(choice.correctControl()));
            } else if (definition instanceof MapPack.ToggleInput toggle) {
                for (String selected : toggle.expectedActive()) {
                    latest = coordinator.handle(id, attempt, new ToggleInputMechanic.TogglePressed(selected));
                }
                latest = coordinator.handle(id, attempt, new ToggleInputMechanic.SubmitPressed());
            }
        }
        MapPack.LogicAnswer logic = (MapPack.LogicAnswer) room.mechanics().get(0);
        if (logic.chatSubmissionEnabled()) {
            latest = coordinator.handle(new MechanicId(logic.id()), attempt,
                    new LogicAnswerMechanic.AnswerSubmitted(logic.answers().get(0)));
        }
        if (latest == null) throw new AssertionError("room produced no simulated input: " + room.id());
        return latest;
    }

    private PuzzleSession activeSession(MapPack pack, Party party, Instant at) {
        PuzzleSession session = PuzzleSession.create(
                SessionId.random(), pack.mazeId(), new MapVersion(pack.mapVersion().value()), party, pack.rooms().size());
        assertSuccess(session.queue(party.leaderId()));
        assertSuccess(session.beginProvisioning());
        assertSuccess(session.activate(at));
        return session;
    }

    private Party party(int size) {
        List<UUID> members = new ArrayList<>();
        for (int index = 0; index < size; index++) members.add(UUID.randomUUID());
        return Party.of(members.get(0), members);
    }

    private List<MapPack> loadPacks() throws Exception {
        List<MapPack> packs = new ArrayList<>();
        for (String level : List.of("easy", "normal", "hard")) {
            Path source = Path.of("map-packs", "difficulty-mazes-30", level + ".jsonc");
            if (!Files.exists(source)) source = Path.of("..", "map-packs", "difficulty-mazes-30", level + ".jsonc");
            packs.add(new JsoncMapPackLoader().load(source));
        }
        return packs;
    }

    private static void assertSuccess(OperationResult<SessionFailure> result) {
        assertTrue(result.succeeded(), () -> "Expected success but got " + result.failure());
    }
}
