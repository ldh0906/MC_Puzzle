package dev.mcpuzzle.maptool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisMapImporterTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void emitsDeterministicContentAndOnlyRepresentativeStages() throws Exception {
        Path input = writeAnalysisFixture();
        Path first = temporaryDirectory.resolve("first.jsonc");
        Path second = temporaryDirectory.resolve("second.jsonc");
        AnalysisMapImporter importer = new AnalysisMapImporter();

        AnalysisMapImporter.ImportResult firstResult = importer.importFile(input, first);
        AnalysisMapImporter.ImportResult secondResult = importer.importFile(input, second);

        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        assertNull(firstResult.backup());
        assertNull(secondResult.backup());
        assertEquals(List.of(1, 3, 8, 19, 42), firstResult.stageNumbers());

        JsonNode pack = MAPPER.readTree(first.toFile());
        assertEquals(List.of(1, 3, 8, 19, 42),
                pack.path("rooms").findValues("originalStage").stream().map(JsonNode::asInt).toList());
        assertEquals(List.of(123, 124, 125, 126, 127, 128, 129),
                integers(room(pack, 19).path("source").path("routeLocationIds")));
        assertEquals(37, room(pack, 8).path("source").path("numericAnswer").asInt());
        assertEquals(7, room(pack, 19).path("mechanics").get(0).path("checkpoints").size());
        assertEquals(54, room(pack, 19).path("source").path("destinationLocationId").asInt());
        assertTrue(room(pack, 19).path("mechanics").get(0).has("destination"));
        assertEquals("DESTINATION", room(pack, 19).path("mechanics").get(0).path("completionAt").asText());
        assertTrue(room(pack, 3).path("mechanics").get(0).path("allowSequentialSolo").asBoolean());
        assertEquals("REDSTONE_LAMP", room(pack, 1).path("mechanics").get(0).path("indicatorMaterial").asText());
        assertEquals("REDSTONE_LAMP", room(pack, 3).path("mechanics").get(0).path("indicatorMaterial").asText());
        assertEquals("PARTY_SIZE", room(pack, 42).path("mechanics").get(0).path("activePadCount").asText());
    }

    @Test
    void backsUpExistingOutputAndAtomicallyReplacesIt() throws Exception {
        Path input = writeAnalysisFixture();
        Path output = temporaryDirectory.resolve("map.jsonc");
        Files.writeString(output, "old map", StandardCharsets.UTF_8);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-08-05T12:34:56.789Z"), ZoneOffset.UTC);

        AnalysisMapImporter.ImportResult result = new AnalysisMapImporter(fixedClock).importFile(input, output);

        Path expectedBackup = temporaryDirectory.resolve("map.jsonc.bak-20260805-123456-789");
        assertEquals(expectedBackup, result.backup());
        assertEquals("old map", Files.readString(expectedBackup, StandardCharsets.UTF_8));
        assertEquals("legacy-50rooms-analysis-draft", MAPPER.readTree(output.toFile()).path("mazeId").asText());
        try (var files = Files.list(temporaryDirectory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void allocatesCollisionSafeBackupNameAtSameTimestamp() throws Exception {
        Path input = writeAnalysisFixture();
        Path output = temporaryDirectory.resolve("map.jsonc");
        Files.writeString(output, "old map", StandardCharsets.UTF_8);
        Files.writeString(temporaryDirectory.resolve("map.jsonc.bak-20260805-123456-789"), "older backup");
        Clock fixedClock = Clock.fixed(Instant.parse("2026-08-05T12:34:56.789Z"), ZoneOffset.UTC);

        AnalysisMapImporter.ImportResult result = new AnalysisMapImporter(fixedClock).importFile(input, output);

        assertNotNull(result.backup());
        assertEquals("map.jsonc.bak-20260805-123456-789-1", result.backup().getFileName().toString());
        assertEquals("old map", Files.readString(result.backup()));
    }

    @Test
    void rejectsMissingRepresentativeStageWithClearMessage() throws Exception {
        ObjectNode fixture = analysisFixture();
        ArrayNode stages = (ArrayNode) fixture.path("stages");
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).path("stage").asInt() == 42) {
                stages.remove(i);
                break;
            }
        }
        Path input = temporaryDirectory.resolve("missing-stage.json");
        MAPPER.writeValue(input.toFile(), fixture);

        try {
            new AnalysisMapImporter().importFile(input, temporaryDirectory.resolve("output.jsonc"));
        } catch (MapImportException exception) {
            assertTrue(exception.getMessage().contains("42"));
            return;
        }
        throw new AssertionError("Expected MapImportException");
    }

    @Test
    void generatedDraftAndCommittedPackValidateAgainstStrictSchema() throws Exception {
        Path input = writeAnalysisFixture();
        Path draft = temporaryDirectory.resolve("draft.jsonc");
        new AnalysisMapImporter().importFile(input, draft);

        Path repositoryRoot = Path.of(System.getProperty("repositoryRoot"));
        JsonNode schemaNode = MAPPER.readTree(repositoryRoot.resolve("map-packs/schema/map-pack.schema.json").toFile());
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
        Set<ValidationMessage> draftErrors = schema.validate(MAPPER.readTree(draft.toFile()));
        assertTrue(draftErrors.isEmpty(), () -> "Draft schema errors: " + draftErrors);

        ObjectNode invalidDraft = (ObjectNode) MAPPER.readTree(draft.toFile());
        invalidDraft.put("unexpectedRuntimeFlag", true);
        assertFalse(schema.validate(invalidDraft).isEmpty(), "The strict schema must reject unknown root properties");

        for (String level : List.of("easy", "normal", "hard")) {
            Path committedPack = repositoryRoot.resolve("map-packs/difficulty-mazes-30/" + level + ".jsonc");
            if (Files.isRegularFile(committedPack)) {
                Set<ValidationMessage> packErrors = schema.validate(MAPPER.readTree(committedPack.toFile()));
                assertTrue(packErrors.isEmpty(), () -> "Committed pack schema errors: " + packErrors);
            }
        }
    }

    private Path writeAnalysisFixture() throws Exception {
        Path path = temporaryDirectory.resolve("analysis.json");
        MAPPER.writeValue(path.toFile(), analysisFixture());
        return path;
    }

    private ObjectNode analysisFixture() {
        ObjectNode root = MAPPER.createObjectNode();
        root.putObject("source").put("sha256", "1325e755dea61cf6146c9b4c84ee76c414ad29ceba065bb38698871c78b40da1");
        ArrayNode stages = root.putArray("stages");
        stages.add(stage(1, 119, 64, 64, 544, 544, null));
        stages.add(stage(2, 120, 640, 64, 1120, 544, null));
        stages.add(stage(3, 121, 1216, 64, 1696, 544, null));
        stages.add(stage(8, 136, 4096, 64, 4576, 544, 37));
        stages.add(stage(19, 147, 640, 640, 1120, 1120, null));
        stages.add(stage(42, 171, 640, 2368, 1120, 2848, null));
        stages.add(stage(50, 179, 64, 2944, 544, 3424, null));

        ArrayNode locations = root.putArray("locations");
        for (int id : List.of(1, 4, 2, 3, 9, 10, 12, 13, 14, 15, 21, 16, 122, 123, 124,
                125, 126, 127, 128, 129, 54, 198, 199, 201, 200, 202)) {
            ObjectNode location = locations.addObject();
            location.put("id", id);
            location.put("left", id * 10);
            location.put("top", id * 10 + 1);
            location.put("right", id * 10 + 32);
            location.put("bottom", id * 10 + 33);
        }
        return root;
    }

    private ObjectNode stage(int number, int trigger, int left, int top, int right, int bottom, Integer answer) {
        ObjectNode stage = MAPPER.createObjectNode();
        stage.put("stage", number);
        stage.put("completion_trigger", trigger);
        ObjectNode room = stage.putObject("room");
        room.put("left", left);
        room.put("top", top);
        room.put("right", right);
        room.put("bottom", bottom);
        if (answer == null) {
            stage.putNull("numeric_answer");
        } else {
            stage.put("numeric_answer", answer);
        }
        stage.putArray("requirements").add("fixture requirement " + number);
        stage.put("completion_explanation", "완료 " + number);
        return stage;
    }

    private JsonNode room(JsonNode pack, int originalStage) {
        for (JsonNode room : pack.path("rooms")) {
            if (room.path("originalStage").asInt() == originalStage) {
                return room;
            }
        }
        throw new AssertionError("Missing room " + originalStage);
    }

    private List<Integer> integers(JsonNode array) {
        List<Integer> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asInt()));
        return List.copyOf(values);
    }
}
