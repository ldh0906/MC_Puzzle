package dev.mcpuzzle.paper.presentation;

import dev.mcpuzzle.paper.map.MapPack;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

/** Creates the immutable in-game evidence book for the active room. */
public final class RoomBriefingBookFactory {
    private static final Key READABLE_FONT = Key.key("minecraft", "uniform");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final BookPagePaginator paginator = new BookPagePaginator();

    public ItemStack create(MapPack.RoomDefinition room) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("방 " + room.sequence() + " · " + room.title());
        meta.setAuthor("MCPuzzle 연구소");
        meta.setGeneration(BookMeta.Generation.ORIGINAL);

        MapPack.LogicAnswer terminal = room.mechanics().stream()
                .filter(MapPack.LogicAnswer.class::isInstance)
                .map(MapPack.LogicAnswer.class::cast)
                .findFirst().orElse(null);
        List<RenderedPage> pages = new ArrayList<>();
        if (terminal == null) {
            pages.add(new RenderedPage(MapPack.PageLayout.PROSE,
                    "§0§l[방 " + room.sequence() + "]\n" + room.title() + "§r\n\n§0" + room.intro()));
        } else {
            String lead = "§0§l[방 " + room.sequence() + "]\n" + room.title()
                    + "§r\n\n§0§l문제§r\n§0" + terminal.question();
            if (!terminal.chatSubmissionEnabled()) {
                lead += "\n\n§0§l입력 방법§r\n§0방 안의 발판이나 버튼을 사용하세요."
                        + "\n§8채팅 제출은 사용할 수 없습니다.";
            }
            pages.add(new RenderedPage(MapPack.PageLayout.PROSE, lead));
            terminal.pages().stream().filter(page -> !page.text().equals(terminal.question()))
                    .forEach(page -> pages.add(new RenderedPage(page.layout(), "§0" + page.text())));
            if (terminal.chatSubmissionEnabled()) {
                pages.add(new RenderedPage(MapPack.PageLayout.PROSE,
                        "§0§l정답 형식§r\n" + terminal.answerFormat()
                                + "\n\n§0§l제출§r\n§1/maze answer <정답>"
                                + "\n\n§8오답 후 " + terminal.cooldownSeconds() + "초 동안 파티 입력이 잠깁니다."));
            }
        }
        List<Component> readablePages = pages.stream()
                .flatMap(page -> paginator.paginate(page.text(), page.layout() == MapPack.PageLayout.GRID).stream()
                        .map(text -> new RenderedPage(page.layout(), text)))
                .map(page -> {
                    Component component = LEGACY.deserialize(page.text());
                    return page.layout() == MapPack.PageLayout.GRID ? component.font(READABLE_FONT) : component;
                })
                .toList();
        meta.addPages(readablePages.toArray(Component[]::new));
        book.setItemMeta(meta);
        return book;
    }

    private record RenderedPage(MapPack.PageLayout layout, String text) { }
}
