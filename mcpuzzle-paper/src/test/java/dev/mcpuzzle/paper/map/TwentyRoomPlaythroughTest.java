package dev.mcpuzzle.paper.map;

import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.domain.OperationResult;
import dev.mcpuzzle.core.domain.Party;
import dev.mcpuzzle.core.domain.PuzzleSession;
import dev.mcpuzzle.core.domain.PuzzleSessionSnapshot;
import dev.mcpuzzle.core.domain.SessionFailure;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.domain.SessionState;
import dev.mcpuzzle.paper.runtime.PaperRoomRuntime;
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

class TwentyRoomPlaythroughTest {
    private static final Instant START = Instant.parse("2026-08-06T00:00:00Z");

    @Test
    void everySupportedPartySizeCanCompleteAllTwentyRoomsInOrder() throws Exception {
        MapPack pack = loadPack();
        for (int partySize = 1; partySize <= 4; partySize++) {
            PuzzleSession session = activeSession(pack, party(partySize), START);

            solveRooms(pack, session, 1, 20, START);

            assertEquals(SessionState.COMPLETED, session.state());
            assertEquals(21, session.currentRoom());
            assertEquals(20, session.roomAttemptRevision());
            assertTrue(session.checkpoint().isEmpty());
            assertEquals(Duration.ofSeconds(600), session.metricsAt(START.plusSeconds(900)).activePlayTime());
            PuzzleSessionSnapshot snapshot = session.snapshot(START.plusSeconds(601));
            assertEquals(SessionState.COMPLETED, snapshot.state());
            assertEquals(21, snapshot.currentRoom());
        }
    }

    @Test
    void aFourPlayerRunCanSaveAfterRoomTenRehydrateAndFinishWithTheExactRoster() throws Exception {
        MapPack pack = loadPack();
        Party party = party(4);
        PuzzleSession original = activeSession(pack, party, START);
        solveRooms(pack, original, 1, 10, START);

        Instant suspendedAt = START.plusSeconds(301);
        assertSuccess(original.requestSuspend(party.leaderId(), suspendedAt));
        PuzzleSession restored = PuzzleSession.rehydrate(original.snapshot(suspendedAt.plusSeconds(1)));
        assertEquals(SessionState.SUSPENDED, restored.state());
        assertEquals(11, restored.currentRoom());
        assertEquals(10, restored.checkpoint().orElseThrow().completedRoom());

        assertSuccess(restored.queueForResume(party.leaderId(), party.toRoster()));
        assertSuccess(restored.beginProvisioning());
        Instant resumedAt = START.plusSeconds(600);
        assertSuccess(restored.activate(resumedAt));
        solveRooms(pack, restored, 11, 20, resumedAt.minusSeconds(300));

        assertEquals(SessionState.COMPLETED, restored.state());
        assertEquals(21, restored.currentRoom());
        assertEquals(20, restored.roomAttemptRevision());
        assertTrue(restored.checkpoint().isEmpty());
    }

    private void solveRooms(MapPack pack, PuzzleSession session, int first, int last, Instant origin) {
        for (int sequence = first; sequence <= last; sequence++) {
            assertEquals(sequence, session.currentRoom());
            MapPack.RoomDefinition room = pack.room(sequence);
            MapPack.LogicAnswer terminal = (MapPack.LogicAnswer) room.mechanics().get(0);
            PaperRoomRuntime runtime = new PaperRoomRuntime(
                    session.id(), session.roomAttemptRevision(), room, session.party().toRoster());
            Instant answeredAt = origin.plusSeconds(sequence * 30L);
            PaperRoomRuntime.AnswerSubmission answer = runtime.submitAnswer(terminal.answers().get(0), answeredAt);
            assertEquals(PaperRoomRuntime.AnswerStatus.CORRECT, answer.status(), room.id());
            assertEquals(PaperRoomRuntime.Signal.ROOM_COMPLETED, answer.signal(), room.id());
            assertSuccess(session.completeCurrentRoom(answeredAt));
        }
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

    private MapPack loadPack() throws Exception {
        Path source = Path.of("map-packs", "a-to-z-archive-20", "map.jsonc");
        if (!Files.exists(source)) source = Path.of("..", "map-packs", "a-to-z-archive-20", "map.jsonc");
        return new JsoncMapPackLoader().load(source);
    }

    private static void assertSuccess(OperationResult<SessionFailure> result) {
        assertTrue(result.succeeded(), () -> "Expected success but got " + result.failure());
    }
}
