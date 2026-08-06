package dev.mcpuzzle.paper.presentation;

import dev.mcpuzzle.paper.map.MapPack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

/** Creates the immutable in-game evidence book for the active room. */
public final class RoomBriefingBookFactory {
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
        List<String> pages = new ArrayList<>();
        if (terminal == null) {
            pages.add("§0§l[방 " + room.sequence() + "]\n" + room.title() + "§r\n\n§0" + room.intro());
        } else {
            pages.add("§0§l[방 " + room.sequence() + "]\n" + room.title() + "§r\n\n§0" + terminal.question());
            terminal.pages().forEach(page -> pages.add("§0" + page));
            pages.add("§0§l정답 형식§r\n" + terminal.answerFormat()
                    + "\n\n§0§l제출§r\n§1/maze answer <정답>"
                    + "\n\n§8오답 후 " + terminal.cooldownSeconds() + "초 동안 파티 입력이 잠깁니다.");
        }
        meta.setPages(pages);
        book.setItemMeta(meta);
        return book;
    }
}
