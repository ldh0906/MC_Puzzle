package dev.mcpuzzle.paper.runtime;

import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.paper.map.JsoncMapPackLoader;
import dev.mcpuzzle.paper.map.MapPack;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaperRoomRuntimeLogicAnswerTest {
    @Test
    void wrongGuessStartsSharedCooldownWithoutResetAndCorrectAnswerCompletesAfterIt() throws Exception {
        MapPack.RoomDefinition room = loadPack().room(1);
        UUID leader = UUID.randomUUID();
        PaperRoomRuntime runtime = new PaperRoomRuntime(SessionId.random(), 0, room,
                new PartyRoster(leader, List.of(leader)));
        Instant now = Instant.parse("2026-08-06T00:00:00Z");

        PaperRoomRuntime.AnswerSubmission wrong = runtime.submitAnswer("speaker", now);
        assertEquals(PaperRoomRuntime.AnswerStatus.INCORRECT, wrong.status());
        assertEquals(PaperRoomRuntime.Signal.NONE, wrong.signal());

        PaperRoomRuntime.AnswerSubmission throttled = runtime.submitAnswer("speller", now.plusSeconds(1));
        assertEquals(PaperRoomRuntime.AnswerStatus.COOLDOWN, throttled.status());

        PaperRoomRuntime.AnswerSubmission correct = runtime.submitAnswer("S P E L L E R", now.plusSeconds(11));
        assertEquals(PaperRoomRuntime.AnswerStatus.CORRECT, correct.status());
        assertEquals(PaperRoomRuntime.Signal.ROOM_COMPLETED, correct.signal());
    }

    private MapPack loadPack() throws Exception {
        Path source = Path.of("map-packs", "a-to-z-archive-20", "map.jsonc");
        if (!Files.exists(source)) source = Path.of("..", "map-packs", "a-to-z-archive-20", "map.jsonc");
        return new JsoncMapPackLoader().load(source);
    }
}
