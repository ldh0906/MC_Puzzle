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
        MapPack.RoomDefinition room = loadPack("hard").room(1);
        MapPack.LogicAnswer terminal = room.mechanics().stream()
                .filter(MapPack.LogicAnswer.class::isInstance)
                .map(MapPack.LogicAnswer.class::cast)
                .findFirst().orElseThrow();
        UUID leader = UUID.randomUUID();
        PaperRoomRuntime runtime = new PaperRoomRuntime(SessionId.random(), 0, room,
                new PartyRoster(leader, List.of(leader)));
        Instant now = Instant.parse("2026-08-06T00:00:00Z");

        PaperRoomRuntime.AnswerSubmission wrong = runtime.submitAnswer("equinoxe", now);
        assertEquals(PaperRoomRuntime.AnswerStatus.INCORRECT, wrong.status());
        assertEquals(PaperRoomRuntime.Signal.NONE, wrong.signal());

        PaperRoomRuntime.AnswerSubmission throttled = runtime.submitAnswer("equinox", now.plusSeconds(1));
        assertEquals(PaperRoomRuntime.AnswerStatus.COOLDOWN, throttled.status());

        PaperRoomRuntime.AnswerSubmission correct = runtime.submitAnswer(
                "E Q U I N O X", now.plusSeconds(terminal.cooldownSeconds() + 1L));
        assertEquals(PaperRoomRuntime.AnswerStatus.CORRECT, correct.status());
        assertEquals(PaperRoomRuntime.Signal.ROOM_COMPLETED, correct.signal());
    }

    @Test
    void deviceOnlyAndUnfinishedHybridRoomsRejectChatSubmission() throws Exception {
        UUID leader = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-06T00:00:00Z");
        MapPack normal = loadPack("normal");

        PaperRoomRuntime device = new PaperRoomRuntime(SessionId.random(), 0, normal.room(1),
                new PartyRoster(leader, List.of(leader)));
        assertEquals(PaperRoomRuntime.AnswerStatus.NOT_SUPPORTED,
                device.submitAnswer("speller", now).status());

        PaperRoomRuntime hybrid = new PaperRoomRuntime(SessionId.random(), 0, normal.room(2),
                new PartyRoster(leader, List.of(leader)));
        assertEquals(PaperRoomRuntime.AnswerStatus.PREREQUISITE,
                hybrid.submitAnswer("사랑과전쟁", now).status());
    }

    private MapPack loadPack(String level) throws Exception {
        Path source = Path.of("map-packs", "difficulty-mazes-30", level + ".jsonc");
        if (!Files.exists(source)) source = Path.of("..", "map-packs", "difficulty-mazes-30", level + ".jsonc");
        return new JsoncMapPackLoader().load(source);
    }
}
