package dev.mcpuzzle.paper.map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsoncMapPackLoaderTest {
    @Test
    void loadsTheThreeCommittedDifficultyPacks() throws Exception {
        String[] levels = {"easy", "normal", "hard"};
        String[] ids = {"midnight-easy", "midnight-normal", "midnight-hard"};
        int[] counts = {12, 12, 5};
        for (int index = 0; index < levels.length; index++) {
            Path source = Path.of("map-packs", "difficulty-mazes-30", levels[index] + ".jsonc");
            if (!Files.exists(source)) source = Path.of("..", "map-packs", "difficulty-mazes-30", levels[index] + ".jsonc");
            MapPack pack = new JsoncMapPackLoader().load(source);
            assertEquals(ids[index], pack.mazeId());
            assertEquals(1, pack.minPlayers());
            assertEquals(4, pack.maxPlayers());
            assertEquals("GENERATED_VOID", pack.world().mode());
            assertEquals(counts[index], pack.rooms().size());
            assertInstanceOf(MapPack.LogicAnswer.class, pack.room(counts[index]).mechanics().get(0));
        }
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

    @Test
    void rejectsDuplicateMechanicIds() throws Exception {
        JsonObject pack = pack("easy");
        var mechanics = pack.getAsJsonArray("rooms").get(0).getAsJsonObject().getAsJsonArray("mechanics");
        mechanics.get(1).getAsJsonObject().addProperty("id", mechanics.get(0).getAsJsonObject().get("id").getAsString());
        assertThrows(MapPackLoadException.class, () -> new JsoncMapPackLoader().parse(pack));
    }

    @Test
    void rejectsStructureCoordinatesOutsideTheRoom() throws Exception {
        JsonObject pack = pack("easy");
        JsonObject room = pack.getAsJsonArray("rooms").get(0).getAsJsonObject();
        JsonObject invalid = new JsonObject();
        JsonObject position = new JsonObject();
        position.addProperty("x", 999);
        position.addProperty("y", 65);
        position.addProperty("z", 10);
        invalid.add("position", position);
        invalid.addProperty("material", "STONE");
        room.getAsJsonObject("structure").getAsJsonArray("blocks").add(invalid);
        assertThrows(MapPackLoadException.class, () -> new JsoncMapPackLoader().parse(pack));
    }

    @Test
    void rejectsOrderedInputThatReferencesAnUnknownControl() throws Exception {
        JsonObject pack = pack("normal");
        JsonObject ordered = pack.getAsJsonArray("rooms").get(0).getAsJsonObject()
                .getAsJsonArray("mechanics").get(1).getAsJsonObject();
        ordered.getAsJsonArray("expected").get(0).getAsJsonObject().addProperty("control", "missing");
        assertThrows(MapPackLoadException.class, () -> new JsoncMapPackLoader().parse(pack));
    }

    @Test
    void rejectsRoomsWithoutACompletableMechanic() throws Exception {
        JsonObject pack = pack("hard");
        JsonObject terminal = pack.getAsJsonArray("rooms").get(0).getAsJsonObject()
                .getAsJsonArray("mechanics").get(0).getAsJsonObject();
        terminal.addProperty("submissionMode", "DEVICE_ONLY");
        assertThrows(MapPackLoadException.class, () -> new JsoncMapPackLoader().parse(pack));
    }

    @Test
    void rejectsUnknownDuplicateAndNonEnvironmentalChatPrerequisites() throws Exception {
        JsonObject unknown = pack("easy");
        JsonObject terminal = unknown.getAsJsonArray("rooms").get(1).getAsJsonObject()
                .getAsJsonArray("mechanics").get(0).getAsJsonObject();
        terminal.getAsJsonArray("requires").set(0, JsonParser.parseString("\"missing\""));
        assertThrows(MapPackLoadException.class, () -> new JsoncMapPackLoader().parse(unknown));

        JsonObject duplicate = pack("easy");
        JsonObject duplicateTerminal = duplicate.getAsJsonArray("rooms").get(1).getAsJsonObject()
                .getAsJsonArray("mechanics").get(0).getAsJsonObject();
        duplicateTerminal.getAsJsonArray("requires").add("seven-records");
        assertThrows(MapPackLoadException.class, () -> new JsoncMapPackLoader().parse(duplicate));

        JsonObject logic = pack("easy");
        var logicMechanics = logic.getAsJsonArray("rooms").get(1).getAsJsonObject().getAsJsonArray("mechanics");
        JsonObject logicTerminal = logicMechanics.get(0).getAsJsonObject();
        JsonObject otherLogic = logicTerminal.deepCopy();
        otherLogic.addProperty("id", "other-logic");
        otherLogic.add("requires", new com.google.gson.JsonArray());
        logicMechanics.add(otherLogic);
        logicTerminal.getAsJsonArray("requires").set(0, JsonParser.parseString("\"other-logic\""));
        assertThrows(MapPackLoadException.class, () -> new JsoncMapPackLoader().parse(logic));
    }

    private JsonObject pack(String level) throws Exception {
        Path source = Path.of("map-packs", "difficulty-mazes-30", level + ".jsonc");
        if (!Files.exists(source)) source = Path.of("..", "map-packs", "difficulty-mazes-30", level + ".jsonc");
        return JsonParser.parseString(Files.readString(source)).getAsJsonObject();
    }
}
