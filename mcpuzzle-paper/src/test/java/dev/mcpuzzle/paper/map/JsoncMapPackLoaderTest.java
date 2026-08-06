package dev.mcpuzzle.paper.map;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsoncMapPackLoaderTest {
    @Test
    void loadsTheCommittedTwentyRoomPack() throws Exception {
        Path source = Path.of("map-packs", "a-to-z-archive-20", "map.jsonc");
        if (!Files.exists(source)) source = Path.of("..", "map-packs", "a-to-z-archive-20", "map.jsonc");
        MapPack pack = new JsoncMapPackLoader().load(source);

        assertEquals("a-to-z-archive-20", pack.mazeId());
        assertEquals(1, pack.minPlayers());
        assertEquals(4, pack.maxPlayers());
        assertEquals("GENERATED_VOID", pack.world().mode());
        assertEquals(20, pack.rooms().size());
        assertInstanceOf(MapPack.LogicAnswer.class, pack.room(20).mechanics().get(0));
    }

    @Test
    void rejectsUnknownRuntimeFields() {
        String json = "{\"schemaVersion\":1,\"mazeId\":\"x\",\"mapVersion\":\"1\","
                + "\"displayName\":\"x\",\"unexpected\":true}";
        assertThrows(MapPackLoadException.class,
                () -> new JsoncMapPackLoader().parse(com.google.gson.JsonParser.parseString(json).getAsJsonObject()));
    }

    @Test
    void stripsCommentsWithoutDamagingUrls() throws Exception {
        String value = "{\"url\":\"https://example.test/a//b\",/*x*/\"n\":1}//tail";
        String stripped = JsoncMapPackLoader.stripComments(value);
        assertEquals("https://example.test/a//b",
                com.google.gson.JsonParser.parseString(stripped).getAsJsonObject().get("url").getAsString());
    }
}
