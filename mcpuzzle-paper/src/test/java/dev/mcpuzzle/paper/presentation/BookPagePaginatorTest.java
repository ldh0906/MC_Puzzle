package dev.mcpuzzle.paper.presentation;

import dev.mcpuzzle.paper.map.JsoncMapPackLoader;
import dev.mcpuzzle.paper.map.MapPack;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookPagePaginatorTest {
    private final BookPagePaginator paginator = new BookPagePaginator();

    @Test
    void wrapsLongKoreanTextWithinTheBookWidth() {
        List<String> pages = paginator.paginate("앞 신호를 영어 모스로 읽고 한글로 번역한 뒤, 뒤 자모와 자연스럽게 연결하세요.");

        assertEquals(1, pages.size());
        for (String line : pages.get(0).split("\n")) {
            assertTrue(BookPagePaginator.visibleWidth(line) <= BookPagePaginator.MAX_LINE_WIDTH, line);
        }
    }

    @Test
    void movesOverflowingLinesOntoAdditionalPages() {
        String text = String.join("\n", java.util.Collections.nCopies(16, "한 줄"));
        List<String> pages = paginator.paginate(text);

        assertEquals(2, pages.size());
        assertEquals(BookPagePaginator.MAX_LINES_PER_PAGE, pages.get(0).split("\n", -1).length);
        assertEquals(4, pages.get(1).split("\n", -1).length);
    }

    @Test
    void formattingCodesDoNotConsumeVisibleWidth() {
        assertEquals(BookPagePaginator.visibleWidth("제목"), BookPagePaginator.visibleWidth("§0§l제목§r"));
    }

    @Test
    void preservesIntentionalIndentationInDiagrams() {
        assertTrue(paginator.paginate("   A B C").get(0).startsWith("§0   A B C"));
    }

    @Test
    void everyCommittedEvidencePageFitsThePaginatorLimits() throws Exception {
        for (String level : List.of("easy", "normal", "hard")) {
            Path source = Path.of("map-packs", "difficulty-mazes-30", level + ".jsonc");
            if (!Files.exists(source)) source = Path.of("..", "map-packs", "difficulty-mazes-30", level + ".jsonc");
            MapPack pack = new JsoncMapPackLoader().load(source);
            for (MapPack.RoomDefinition room : pack.rooms()) {
                MapPack.LogicAnswer answer = (MapPack.LogicAnswer) room.mechanics().get(0);
                List<MapPack.BookPage> logicalPages = new ArrayList<>();
                logicalPages.add(new MapPack.BookPage(MapPack.PageLayout.PROSE, answer.question()));
                logicalPages.addAll(answer.pages());
                for (MapPack.BookPage logicalPage : logicalPages) {
                    boolean uniform = logicalPage.layout() == MapPack.PageLayout.GRID;
                    for (String page : paginator.paginate(logicalPage.text(), uniform)) {
                        String[] lines = page.split("\n", -1);
                        assertTrue(lines.length <= BookPagePaginator.MAX_LINES_PER_PAGE, room.id());
                        for (String line : lines) {
                            assertTrue(BookPagePaginator.visibleWidth(line, uniform) <= BookPagePaginator.MAX_LINE_WIDTH,
                                    room.id() + ": " + line);
                        }
                    }
                }
            }
        }
    }
}
