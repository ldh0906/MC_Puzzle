package dev.mcpuzzle.paper.map;

import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.mechanic.LogicAnswerNormalizer;
import dev.mcpuzzle.paper.runtime.PaperRoomRuntime;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwentyRoomContentValidationTest {
    private static final String EXPECTED_LETTERS = "ABCDEGJKLMNOPQRSTWYZ";

    @Test
    void activePackHasTwentyDistinctEvidenceDrivenRoomsAndNoForbiddenInputMechanics() throws Exception {
        MapPack pack = loadPack();
        assertEquals(20, pack.rooms().size());
        assertEquals(1, pack.minPlayers());
        assertEquals(4, pack.maxPlayers());

        Set<String> normalizedAnswers = new HashSet<>();
        Set<String> visualFingerprints = new HashSet<>();
        int previousDifficulty = 0;
        for (int index = 0; index < pack.rooms().size(); index++) {
            MapPack.RoomDefinition room = pack.room(index + 1);
            assertTrue(room.title().startsWith(EXPECTED_LETTERS.charAt(index) + " · "));
            assertEquals(1, room.mechanics().size());
            MapPack.LogicAnswer terminal = (MapPack.LogicAnswer) room.mechanics().get(0);
            assertTrue(terminal.difficulty() >= previousDifficulty, "difficulty must not decrease at room " + room.sequence());
            previousDifficulty = terminal.difficulty();
            assertTrue(terminal.pages().size() >= 2);
            assertEquals(3, room.hints().size());
            assertTrue(terminal.question().length() >= 20);
            assertTrue(terminal.solutionExplanation().length() >= 20);
            assertTrue(terminal.cooldownSeconds() >= 10);
            assertTrue(visualFingerprints.add(room.visual().orElseThrow().cells().toString()),
                    "every room needs a distinct physical floor diagram: " + room.id());
            for (String answer : terminal.answers()) {
                assertTrue(normalizedAnswers.add(room.id() + ":" + LogicAnswerNormalizer.normalize(answer)));
            }
            assertFalse(terminal.type().contains("PAD"));
            assertFalse(terminal.type().contains("BUTTON"));
            assertFalse(terminal.type().contains("LEVER"));
        }
        assertEquals(20, visualFingerprints.size());
    }

    @Test
    void lateRoomEvidenceRequiresDerivationInsteadOfPrintingAnAcceptedAnswer() throws Exception {
        MapPack pack = loadPack();
        for (MapPack.RoomDefinition room : pack.rooms()) {
            MapPack.LogicAnswer terminal = (MapPack.LogicAnswer) room.mechanics().get(0);
            if (terminal.difficulty() < 4) continue;

            String evidence = LogicAnswerNormalizer.normalize(
                    terminal.question() + " " + String.join(" ", terminal.pages()));
            String earlyHints = LogicAnswerNormalizer.normalize(
                    room.hints().get(0).text() + " " + room.hints().get(1).text());
            for (String answer : terminal.answers()) {
                String normalized = LogicAnswerNormalizer.normalize(answer);
                assertFalse(evidence.contains(normalized),
                        () -> room.id() + " prints accepted answer in mandatory evidence: " + answer);
                assertFalse(earlyHints.contains(normalized),
                        () -> room.id() + " reveals accepted answer before tier 3: " + answer);
            }
        }
    }

    @Test
    void serializedActivePackContainsNoForbiddenInputBlocks() throws Exception {
        Path source = packSource();
        String serialized = Files.readString(source);
        for (String forbidden : List.of("PRESSURE_PLATE", "BUTTON", "LEVER")) {
            assertFalse(serialized.contains(forbidden), "active pack contains forbidden input block " + forbidden);
        }
    }

    @Test
    void everyRoomCanBeSolvedByEverySupportedPartySizeAndRejectsAuthoredWrongSamples() throws Exception {
        MapPack pack = loadPack();
        Instant now = Instant.parse("2026-08-06T00:00:00Z");
        for (MapPack.RoomDefinition room : pack.rooms()) {
            MapPack.LogicAnswer terminal = (MapPack.LogicAnswer) room.mechanics().get(0);
            for (int partySize = 1; partySize <= 4; partySize++) {
                PaperRoomRuntime runtime = runtime(room, partySize);
                PaperRoomRuntime.AnswerSubmission solved = runtime.submitAnswer(terminal.answers().get(0), now);
                assertEquals(PaperRoomRuntime.AnswerStatus.CORRECT, solved.status(), room.id());
                assertEquals(PaperRoomRuntime.Signal.ROOM_COMPLETED, solved.signal(), room.id());
            }
            for (String wrong : terminal.wrongAnswerSamples()) {
                PaperRoomRuntime.AnswerSubmission rejected = runtime(room, 1).submitAnswer(wrong, now);
                assertEquals(PaperRoomRuntime.AnswerStatus.INCORRECT, rejected.status(), room.id() + ":" + wrong);
                assertEquals(PaperRoomRuntime.Signal.NONE, rejected.signal(), room.id() + ":" + wrong);
            }
        }
    }

    @Test
    void aWrongGuessLocksTheSharedRoomTerminalBeforeAnotherPartyMemberCanBruteForce() throws Exception {
        MapPack pack = loadPack();
        Instant now = Instant.parse("2026-08-06T00:00:00Z");
        for (MapPack.RoomDefinition room : pack.rooms()) {
            MapPack.LogicAnswer terminal = (MapPack.LogicAnswer) room.mechanics().get(0);
            PaperRoomRuntime runtime = runtime(room, 4);
            assertEquals(PaperRoomRuntime.AnswerStatus.INCORRECT,
                    runtime.submitAnswer(terminal.wrongAnswerSamples().get(0), now).status());
            assertEquals(PaperRoomRuntime.AnswerStatus.COOLDOWN,
                    runtime.submitAnswer(terminal.answers().get(0), now.plusSeconds(1)).status());
            assertEquals(PaperRoomRuntime.AnswerStatus.CORRECT,
                    runtime.submitAnswer(terminal.answers().get(0), now.plusSeconds(terminal.cooldownSeconds() + 1L)).status());
        }
    }

    private PaperRoomRuntime runtime(MapPack.RoomDefinition room, int partySize) {
        List<UUID> members = new ArrayList<>();
        for (int index = 0; index < partySize; index++) members.add(UUID.randomUUID());
        return new PaperRoomRuntime(SessionId.random(), 0, room, new PartyRoster(members.get(0), members));
    }

    private MapPack loadPack() throws Exception {
        return new JsoncMapPackLoader().load(packSource());
    }

    private Path packSource() {
        Path source = Path.of("map-packs", "a-to-z-archive-20", "map.jsonc");
        return Files.exists(source) ? source : Path.of("..", "map-packs", "a-to-z-archive-20", "map.jsonc");
    }
}
