package dev.mcpuzzle.paper.map;

import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.paper.runtime.PaperRoomRuntime;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifficultyMazeCollectionContentTest {
    private static final Map<String, Integer> COUNTS = Map.of("easy", 12, "normal", 12, "hard", 5);
    private static final Map<String, String> VERSIONS = Map.of(
            "easy", "5.4.0-easy12", "normal", "5.1.0-normal12", "hard", "5.0.1-hard5");
    private static final Map<String, Set<Integer>> DIFFICULTIES = Map.of(
            "easy", Set.of(1, 2), "normal", Set.of(3, 4), "hard", Set.of(5));
    private static final List<Class<? extends MapPack.MechanicDefinition>> EASY_TYPES = List.of(
            MapPack.ToggleInput.class, MapPack.ClueRegions.class,
            MapPack.ChoiceInput.class, MapPack.ChoiceInput.class, MapPack.ToggleInput.class,
            MapPack.OrderedInput.class, MapPack.ChoiceInput.class, MapPack.OrderedInput.class,
            MapPack.OrderedInput.class, MapPack.OrderedInput.class, MapPack.ChoiceInput.class,
            MapPack.OrderedInput.class);
    private static final List<Class<? extends MapPack.MechanicDefinition>> NORMAL_TYPES = List.of(
            MapPack.OrderedInput.class, MapPack.OrderedInput.class, MapPack.OrderedInput.class,
            MapPack.OrderedInput.class, MapPack.OrderedInput.class, MapPack.ClueRegions.class,
            MapPack.OrderedInput.class, MapPack.OrderedInput.class, MapPack.OrderedInput.class,
            MapPack.OrderedInput.class, MapPack.OrderedInput.class, MapPack.OrderedInput.class);

    @Test
    void threeMazesContainTwentyNineUniqueRoomsAndTheApprovedInteractionKinds() throws Exception {
        Set<String> ids = new HashSet<>();
        Set<String> titles = new HashSet<>();
        int total = 0;
        for (String level : List.of("easy", "normal", "hard")) {
            MapPack pack = loadPack(level);
            assertEquals(COUNTS.get(level), pack.rooms().size());
            assertEquals(VERSIONS.get(level), pack.mapVersion().value());
            assertEquals(1, pack.minPlayers());
            assertEquals(4, pack.maxPlayers());
            Set<String> visuals = new HashSet<>();
            for (int sequence = 1; sequence <= pack.rooms().size(); sequence++) {
                MapPack.RoomDefinition room = pack.room(sequence);
                assertTrue(ids.add(room.id()), "room duplicated across mazes: " + room.id());
                assertTrue(titles.add(room.title()), "room name must be globally unique: " + room.title());
                assertFalse(room.title().matches("^[A-Z]{1,2} · .*"), "alphabet label leaked into title");
                MapPack.LogicAnswer terminal = assertInstanceOf(MapPack.LogicAnswer.class, room.mechanics().get(0));
                assertTrue(DIFFICULTIES.get(level).contains(terminal.difficulty()));
                assertFalse(terminal.pages().isEmpty());
                assertEquals(3, room.hints().size());
                assertTrue(terminal.solutionExplanation().length() >= 20);
                assertTrue(visuals.add(room.visual().orElseThrow().cells().toString()));

                if (level.equals("hard")) {
                    assertEquals(1, room.mechanics().size());
                    assertTrue(room.structure().isEmpty());
                    assertTrue(terminal.chatSubmissionEnabled());
                } else {
                    assertEquals(2, room.mechanics().size());
                    assertTrue(room.structure().isPresent());
                    for (MapPack.StructureSign sign : room.structure().orElseThrow().signs()) {
                        assertTrue(sign.position().y() >= pack.world().floorY() + 1
                                        && sign.position().y() <= pack.world().floorY() + 3,
                                room.id() + " sign must be readable from survival eye height: " + sign.position());
                        assertTrue(Set.of("NORTH", "SOUTH", "EAST", "WEST").contains(sign.facing()), room.id());
                        assertTrue(sign.lines().stream().allMatch(line -> signVisualWidth(line) <= 18),
                                room.id() + " contains an over-wide sign line: " + sign.lines());
                    }
                    Class<? extends MapPack.MechanicDefinition> expected =
                            (level.equals("easy") ? EASY_TYPES : NORMAL_TYPES).get(sequence - 1);
                    assertInstanceOf(expected, room.mechanics().get(1));
                }
            }
            total += pack.rooms().size();
        }
        assertEquals(29, total);
        assertEquals(29, ids.size());
        assertEquals(29, titles.size());
        assertFalse(titles.contains("여섯 점의 눈"));
        assertTrue(loadPack("normal").room(4).structure().orElseThrow().signs().stream()
                .map(MapPack.StructureSign::facing)
                .anyMatch(facing -> facing.equals("EAST") || facing.equals("WEST")),
                "원형 궤도 표지판은 중앙을 향하도록 회전해야 한다");
    }

    @Test
    void chatSubmissionModesAndEnvironmentalPrerequisitesMatchTheRoomPlan() throws Exception {
        MapPack easy = loadPack("easy");
        MapPack normal = loadPack("normal");
        for (MapPack.RoomDefinition room : easy.rooms()) {
            MapPack.LogicAnswer terminal = (MapPack.LogicAnswer) room.mechanics().get(0);
            assertEquals(room.sequence() == 2, terminal.chatSubmissionEnabled(), room.id());
            assertEquals(room.sequence() == 2 ? List.of("seven-records") : List.of(), terminal.requires());
        }
        for (MapPack.RoomDefinition room : normal.rooms()) {
            MapPack.LogicAnswer terminal = (MapPack.LogicAnswer) room.mechanics().get(0);
            boolean hybrid = room.sequence() == 2 || room.sequence() == 6;
            assertEquals(hybrid, terminal.chatSubmissionEnabled(), room.id());
            assertEquals(room.sequence() == 2 ? List.of("love-signal")
                    : room.sequence() == 6 ? List.of("food-sounds") : List.of(), terminal.requires());
        }
    }

    @Test
    void deviceRoomsRejectChatAndHybridRoomsRejectItUntilTheirDeviceCompletes() throws Exception {
        Instant now = Instant.parse("2026-08-06T00:00:00Z");
        for (String level : List.of("easy", "normal")) {
            for (MapPack.RoomDefinition room : loadPack(level).rooms()) {
                MapPack.LogicAnswer terminal = (MapPack.LogicAnswer) room.mechanics().get(0);
                PaperRoomRuntime.AnswerStatus expected = terminal.chatSubmissionEnabled()
                        ? PaperRoomRuntime.AnswerStatus.PREREQUISITE
                        : PaperRoomRuntime.AnswerStatus.NOT_SUPPORTED;
                assertEquals(expected, runtime(room, 1).submitAnswer(terminal.answers().get(0), now).status(), room.id());
            }
        }
        for (MapPack.RoomDefinition room : loadPack("hard").rooms()) {
            MapPack.LogicAnswer terminal = (MapPack.LogicAnswer) room.mechanics().get(0);
            assertEquals(PaperRoomRuntime.AnswerStatus.CORRECT,
                    runtime(room, 4).submitAnswer(terminal.answers().get(0), now).status(), room.id());
        }
    }

    @Test
    void easyThreeStatesItsQuestionAndKeepsCandidateEvidenceInTheRoom() throws Exception {
        MapPack.RoomDefinition easyThree = loadPack("easy").room(3);
        MapPack.LogicAnswer easyTerminal = (MapPack.LogicAnswer) easyThree.mechanics().get(0);
        String book = easyTerminal.pages().stream().map(MapPack.BookPage::text)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertFalse(book.contains("영역:"));
        assertTrue(book.contains("하늘을 다스리고 번개를 들며"));
        assertTrue(easyThree.intro().startsWith("문제: "));
        assertTrue(easyThree.structure().orElseThrow().signs().stream()
                .flatMap(value -> value.lines().stream()).anyMatch("영역: 하늘"::equals));
        assertTrue(easyThree.structure().orElseThrow().signs().stream()
                .flatMap(value -> value.lines().stream()).anyMatch("[관찰]"::equals));
    }

    @Test
    void everyEasyRoomShowsGoalObservationAndInputBeforeTheDevice() throws Exception {
        for (MapPack.RoomDefinition room : loadPack("easy").rooms()) {
            MapPack.LogicAnswer terminal = (MapPack.LogicAnswer) room.mechanics().get(0);
            assertTrue(terminal.question().contains("목표\n"), room.id());
            assertTrue(terminal.question().contains("관찰\n"), room.id());
            assertTrue(terminal.question().contains("입력\n"), room.id());
            assertTrue(room.intro().equals("문제: " + terminal.question()), room.id());
            List<String> signText = room.structure().orElseThrow().signs().stream()
                    .flatMap(sign -> sign.lines().stream()).toList();
            assertTrue(signText.contains("[문제]"), room.id());
            assertTrue(signText.contains("[관찰]"), room.id());
            assertTrue(signText.contains("[입력]"), room.id());
        }
    }

    @Test
    void easyRoomsKeepAnswersOutOfBriefingsAndPutRequiredEvidenceInTheRoom() throws Exception {
        MapPack easy = loadPack("easy");

        assertFalse(logic(easy.room(1)).question().contains("2¹²와 2²만"));
        assertTrue(signText(easy.room(1)).contains("출력 4096"));
        assertTrue(signText(easy.room(1)).contains("출력 4"));

        MapPack.ClueRegions colors = assertInstanceOf(MapPack.ClueRegions.class, easy.room(2).mechanics().get(1));
        Map<String, String> colorRecords = colors.regions().stream()
                .collect(java.util.stream.Collectors.toMap(MapPack.ClueRegion::id, MapPack.ClueRegion::message));
        assertEquals("[빨강] 이야기주", colorRecords.get("red"));
        assertEquals("[주황] 문명경", colorRecords.get("orange"));
        assertEquals("[노랑] 제목야", colorRecords.get("yellow"));
        assertEquals("[초록] 의문독", colorRecords.get("green"));
        assertEquals("[파랑] 정원입", colorRecords.get("blue"));
        assertEquals("[남색] 답변니", colorRecords.get("indigo"));
        assertEquals("[보라] 은하다", colorRecords.get("purple"));

        MapPack.ToggleInput cards = assertInstanceOf(MapPack.ToggleInput.class, easy.room(5).mechanics().get(1));
        assertEquals(5, cards.controls().size());
        assertFalse(logic(easy.room(5)).question().contains("원페어"));
        assertFalse(signText(easy.room(5)).contains("원페어 확정"));

        MapPack.ChoiceInput flag = assertInstanceOf(MapPack.ChoiceInput.class, easy.room(7).mechanics().get(1));
        assertEquals(List.of("후보 I", "후보 II", "후보 III", "후보 IV"),
                flag.controls().stream().map(MapPack.Control::label).toList());
        assertFalse(signText(easy.room(7)).stream().anyMatch(
                text -> Set.of("핀란드", "자메이카", "스코틀랜드", "스웨덴").contains(text)));

        MapPack.OrderedInput clocks = assertInstanceOf(MapPack.OrderedInput.class, easy.room(8).mechanics().get(1));
        assertEquals(20, clocks.controls().size());
        assertTrue(signText(easy.room(8)).contains("A B C D E"));
        assertTrue(signText(easy.room(8)).contains("U V W X Y"));

        assertFalse(logic(easy.room(11)).question().contains("정지와 공격 버튼만"));
        assertTrue(logic(easy.room(11)).question().contains("스스로 이동하지 못하며"));
        assertTrue(signText(easy.room(12)).contains("1 = ㅁ"));
        assertTrue(signText(easy.room(12)).contains("2 = ㅇ"));
    }

    @Test
    void easySixRequiresReadingTheKnightRuleInsteadOfPublishingTheRoute() throws Exception {
        MapPack.RoomDefinition room = loadPack("easy").room(6);
        MapPack.LogicAnswer terminal = (MapPack.LogicAnswer) room.mechanics().get(0);
        MapPack.OrderedInput route = assertInstanceOf(MapPack.OrderedInput.class, room.mechanics().get(1));
        String briefing = terminal.pages().stream().map(MapPack.BookPage::text)
                .collect(java.util.stream.Collectors.joining("\n"));

        assertEquals("달빛 기사의 봉인", room.title());
        assertTrue(briefing.contains("직선으로 두 칸"));
        assertFalse(briefing.contains("B1→D2"));
        assertEquals(List.of("b1", "d2", "e4", "c5", "a4"),
                route.expected().stream().map(MapPack.ExpectedStep::controlId).toList());
        assertEquals(5, route.controls().stream().filter(control -> control.material().equals("GOLD_BLOCK")).count());
        assertTrue(room.structure().orElseThrow().signs().stream()
                .flatMap(value -> value.lines().stream()).anyMatch("[관찰]"::equals));
    }

    @Test
    void hardOneUsesTheGridLayout() throws Exception {

        MapPack.RoomDefinition hardOne = loadPack("hard").room(1);
        MapPack.LogicAnswer hardTerminal = (MapPack.LogicAnswer) hardOne.mechanics().get(0);
        assertEquals("아홉 칸의 역회전", hardOne.title());
        assertEquals(List.of("equinox"), hardTerminal.answers());
        assertTrue(hardTerminal.pages().stream().anyMatch(page -> page.layout() == MapPack.PageLayout.GRID));
        assertTrue(hardTerminal.pages().stream().map(MapPack.BookPage::text).anyMatch(text -> text.contains("A L H")));
    }

    private PaperRoomRuntime runtime(MapPack.RoomDefinition room, int partySize) {
        List<UUID> members = new ArrayList<>();
        for (int index = 0; index < partySize; index++) members.add(UUID.randomUUID());
        return new PaperRoomRuntime(SessionId.random(), 0, room, new PartyRoster(members.get(0), members));
    }

    private MapPack.LogicAnswer logic(MapPack.RoomDefinition room) {
        return (MapPack.LogicAnswer) room.mechanics().get(0);
    }

    private List<String> signText(MapPack.RoomDefinition room) {
        return room.structure().orElseThrow().signs().stream()
                .flatMap(sign -> sign.lines().stream()).toList();
    }

    private int signVisualWidth(String line) {
        return line.codePoints().map(codePoint -> codePoint > 127 ? 2 : 1).sum();
    }

    private MapPack loadPack(String level) throws Exception {
        return new JsoncMapPackLoader().load(packSource(level));
    }

    private Path packSource(String level) {
        Path source = Path.of("map-packs", "difficulty-mazes-30", level + ".jsonc");
        return Files.exists(source) ? source : Path.of("..", "map-packs", "difficulty-mazes-30", level + ".jsonc");
    }
}
