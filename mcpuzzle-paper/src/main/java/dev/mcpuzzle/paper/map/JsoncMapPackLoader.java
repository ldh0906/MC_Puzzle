package dev.mcpuzzle.paper.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.mechanic.LogicAnswerNormalizer;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class JsoncMapPackLoader {
    private static final Set<String> SUPPORTED_MECHANICS = Set.of(
            "LATCHING_PRESSURE_PADS", "CORNER_OBJECTIVES", "DESTRUCTIBLE_TARGET",
            "NUMERIC_KEYPAD", "LOGIC_ANSWER", "CLUE_REGIONS", "ORDERED_INPUT",
            "CHOICE_INPUT", "TOGGLE_INPUT", "PROXIMITY_ESCORT", "DYNAMIC_PARTY_PADS_AND_TARGET"
    );

    public MapPack load(Path path) throws MapPackLoadException {
        try {
            JsonElement parsed = JsonParser.parseString(stripComments(Files.readString(path, StandardCharsets.UTF_8)));
            if (!parsed.isJsonObject()) throw error("$", "root must be an object");
            return parse(parsed.getAsJsonObject());
        } catch (MapPackLoadException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw new MapPackLoadException("맵 팩을 읽을 수 없습니다: " + path + " (" + failure.getMessage() + ")", failure);
        }
    }

    MapPack parse(JsonObject root) throws MapPackLoadException {
        rejectUnknown(root, "$", "$schema", "schemaVersion", "mapVersion", "mazeId", "displayName",
                "description", "locale", "party", "world", "partySpawns", "source", "rooms");
        int schema = integer(root, "schemaVersion", "$", 1, 1);
        if (schema != 1) throw error("$.schemaVersion", "supported value is 1");
        String mazeId = text(root, "mazeId", "$");
        String version = text(root, "mapVersion", "$");
        String display = text(root, "displayName", "$");

        JsonObject party = object(root, "party", "$");
        rejectUnknown(party, "$.party", "minPlayers", "maxPlayers");
        int minPlayers = integer(party, "minPlayers", "$.party", 1, 4);
        int maxPlayers = integer(party, "maxPlayers", "$.party", 1, 4);
        if (minPlayers > maxPlayers) throw error("$.party", "minPlayers must not exceed maxPlayers");

        JsonObject worldJson = object(root, "world", "$");
        rejectUnknown(worldJson, "$.world", "mode", "environment", "bounds", "floorY", "generator");
        String mode = text(worldJson, "mode", "$.world");
        if (!Set.of("GENERATED_VOID", "TEMPLATE_CLONE").contains(mode)) {
            throw error("$.world.mode", "must be GENERATED_VOID or TEMPLATE_CLONE");
        }
        String environment = exactText(worldJson, "environment", "$.world", "NORMAL");
        MapPack.Bounds worldBounds = bounds(object(worldJson, "bounds", "$.world"), "$.world.bounds");
        int floorY = integer(worldJson, "floorY", "$.world", -64, 319);
        JsonObject generator = object(worldJson, "generator", "$.world");
        rejectUnknown(generator, "$.world.generator", "type", "roomSpacing", "floorMaterial", "wallMaterial",
                "ceilingMaterial", "lightMaterial");
        String generatorType = exactText(generator, "type", "$.world.generator", "ROOM_STACK");
        int roomSpacing = integer(generator, "roomSpacing", "$.world.generator", 1, 10000);
        String floor = material(generator, "floorMaterial", "$.world.generator");
        String wall = material(generator, "wallMaterial", "$.world.generator");
        String ceiling = material(generator, "ceilingMaterial", "$.world.generator");
        String light = material(generator, "lightMaterial", "$.world.generator");
        MapPack.WorldDefinition world = new MapPack.WorldDefinition(mode, environment, worldBounds, floorY,
                generatorType, roomSpacing, floor, wall, ceiling, light);

        JsonArray spawnArray = array(root, "partySpawns", "$");
        if (spawnArray.size() < maxPlayers) throw error("$.partySpawns", "requires at least maxPlayers positions");
        List<MapPack.Position> spawns = positions(spawnArray, "$.partySpawns");
        for (MapPack.Position spawn : spawns) {
            requireInBounds(worldBounds, spawn, "$.partySpawns");
        }

        JsonArray roomArray = array(root, "rooms", "$");
        if (roomArray.isEmpty()) throw error("$.rooms", "must not be empty");
        List<MapPack.RoomDefinition> rooms = new ArrayList<>();
        Set<String> roomIds = new HashSet<>();
        Set<Integer> sequences = new HashSet<>();
        for (int i = 0; i < roomArray.size(); i++) {
            String path = "$.rooms[" + i + "]";
            JsonObject room = requireObject(roomArray.get(i), path);
            rejectUnknown(room, path, "id", "sequence", "originalStage", "title", "buildBounds", "playBounds",
                    "spawn", "checkpoint", "visual", "structure", "completionMode", "mechanics", "hints", "messages", "reset", "source");
            exactText(room, "completionMode", path, "ALL_MECHANICS");
            String id = text(room, "id", path);
            int sequence = integer(room, "sequence", path, 1, roomArray.size());
            if (!roomIds.add(id)) throw error(path + ".id", "duplicate room id");
            if (!sequences.add(sequence)) throw error(path + ".sequence", "duplicate sequence");
            MapPack.Bounds build = bounds(object(room, "buildBounds", path), path + ".buildBounds");
            MapPack.Bounds play = bounds(object(room, "playBounds", path), path + ".playBounds");
            requireBoundsInside(worldBounds, build, path + ".buildBounds");
            requireBoundsInside(build, play, path + ".playBounds");
            MapPack.Position spawn = position(object(room, "spawn", path), path + ".spawn");
            MapPack.Position checkpoint = position(object(room, "checkpoint", path), path + ".checkpoint");
            requireInBounds(play, spawn, path + ".spawn");
            requireInBounds(play, checkpoint, path + ".checkpoint");
            Optional<MapPack.VisualBlueprint> visual = visual(room, build, path);
            Optional<MapPack.StructureBlueprint> structure = structure(room, build, path);
            List<MapPack.MechanicDefinition> mechanics = mechanics(room, build, path);
            List<MapPack.Hint> hints = hints(room, path);
            JsonObject messages = object(room, "messages", path);
            rejectUnknown(messages, path + ".messages", "intro", "completion", "failure");
            validateReset(object(room, "reset", path), path + ".reset");
            rooms.add(new MapPack.RoomDefinition(id, sequence,
                    integer(room, "originalStage", path, 1, Integer.MAX_VALUE), text(room, "title", path),
                    build, play, spawn, checkpoint, visual, structure, mechanics, hints,
                    text(messages, "intro", path + ".messages"),
                    text(messages, "completion", path + ".messages"),
                    text(messages, "failure", path + ".messages")));
        }
        for (int sequence = 1; sequence <= rooms.size(); sequence++) {
            if (!sequences.contains(sequence)) throw error("$.rooms", "sequences must be contiguous from 1");
        }
        rooms.sort(java.util.Comparator.comparingInt(MapPack.RoomDefinition::sequence));
        return new MapPack(mazeId, new MapVersion(version), display, minPlayers, maxPlayers, world, spawns, rooms);
    }

    private Optional<MapPack.VisualBlueprint> visual(JsonObject room, MapPack.Bounds bounds, String roomPath)
            throws MapPackLoadException {
        if (!room.has("visual")) return Optional.empty();
        String path = roomPath + ".visual";
        JsonObject value = object(room, "visual", roomPath);
        rejectUnknown(value, path, "origin", "scale", "width", "height", "palette", "cells");
        MapPack.Position origin = position(object(value, "origin", path), path + ".origin");
        int scale = integer(value, "scale", path, 1, 4);
        int width = integer(value, "width", path, 1, 64);
        int height = integer(value, "height", path, 1, 64);

        JsonArray paletteJson = array(value, "palette", path);
        if (paletteJson.isEmpty()) throw error(path + ".palette", "must not be empty");
        Map<Integer, String> palette = new HashMap<>();
        for (int index = 0; index < paletteJson.size(); index++) {
            String entryPath = path + ".palette[" + index + "]";
            JsonObject entry = requireObject(paletteJson.get(index), entryPath);
            rejectUnknown(entry, entryPath, "tile", "material");
            int tile = integer(entry, "tile", entryPath, 0, 65535);
            if (palette.put(tile, material(entry, "material", entryPath)) != null) {
                throw error(entryPath + ".tile", "duplicate tile");
            }
        }

        JsonArray cellsJson = array(value, "cells", path);
        if (cellsJson.size() != width * height) {
            throw error(path + ".cells", "must contain width * height entries");
        }
        List<Integer> cells = new ArrayList<>(cellsJson.size());
        for (int index = 0; index < cellsJson.size(); index++) {
            JsonElement cell = cellsJson.get(index);
            try {
                int tile = cell.getAsInt();
                if (!cell.isJsonPrimitive() || cell.getAsDouble() != tile || tile < 0 || tile > 65535) {
                    throw new NumberFormatException();
                }
                if (!palette.containsKey(tile)) throw error(path + ".cells[" + index + "]", "tile is missing from palette");
                cells.add(tile);
            } catch (MapPackLoadException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw error(path + ".cells[" + index + "]", "integer tile must be between 0 and 65535");
            }
        }

        MapPack.Position maximum = new MapPack.Position(
                origin.x() + width * scale - 1,
                origin.y(),
                origin.z() + height * scale - 1,
                origin.yaw(), origin.pitch());
        requireInBounds(bounds, origin, path + ".origin");
        requireInBounds(bounds, maximum, path + ".extent");
        return Optional.of(new MapPack.VisualBlueprint(origin, scale, width, height, cells, palette));
    }

    private Optional<MapPack.StructureBlueprint> structure(JsonObject room, MapPack.Bounds bounds, String roomPath)
            throws MapPackLoadException {
        if (!room.has("structure")) return Optional.empty();
        String path = roomPath + ".structure";
        JsonObject value = object(room, "structure", roomPath);
        rejectUnknown(value, path, "blocks", "cuboids", "signs");
        List<MapPack.StructureBlock> blocks = new ArrayList<>();
        JsonArray blockValues = array(value, "blocks", path);
        for (int index = 0; index < blockValues.size(); index++) {
            String itemPath = path + ".blocks[" + index + "]";
            JsonObject item = requireObject(blockValues.get(index), itemPath);
            rejectUnknown(item, itemPath, "position", "material");
            MapPack.Position position = position(object(item, "position", itemPath), itemPath + ".position");
            requireInBounds(bounds, position, itemPath + ".position");
            blocks.add(new MapPack.StructureBlock(position, material(item, "material", itemPath)));
        }
        List<MapPack.StructureCuboid> cuboids = new ArrayList<>();
        JsonArray cuboidValues = array(value, "cuboids", path);
        for (int index = 0; index < cuboidValues.size(); index++) {
            String itemPath = path + ".cuboids[" + index + "]";
            JsonObject item = requireObject(cuboidValues.get(index), itemPath);
            rejectUnknown(item, itemPath, "bounds", "material");
            MapPack.Bounds cuboid = bounds(object(item, "bounds", itemPath), itemPath + ".bounds");
            requireBoundsInside(bounds, cuboid, itemPath + ".bounds");
            cuboids.add(new MapPack.StructureCuboid(cuboid, material(item, "material", itemPath)));
        }
        List<MapPack.StructureSign> signs = new ArrayList<>();
        JsonArray signValues = array(value, "signs", path);
        for (int index = 0; index < signValues.size(); index++) {
            String itemPath = path + ".signs[" + index + "]";
            JsonObject item = requireObject(signValues.get(index), itemPath);
            rejectUnknown(item, itemPath, "position", "facing", "lines");
            MapPack.Position position = position(object(item, "position", itemPath), itemPath + ".position");
            requireInBounds(bounds, position, itemPath + ".position");
            String facing = text(item, "facing", itemPath).toUpperCase(Locale.ROOT);
            if (!Set.of("NORTH", "SOUTH", "EAST", "WEST").contains(facing)) throw error(itemPath + ".facing", "invalid facing");
            signs.add(new MapPack.StructureSign(position, facing,
                    strings(array(item, "lines", itemPath), itemPath + ".lines", 1, 4, 80)));
        }
        return Optional.of(new MapPack.StructureBlueprint(blocks, cuboids, signs));
    }

    private List<MapPack.MechanicDefinition> mechanics(JsonObject room, MapPack.Bounds bounds, String roomPath) throws MapPackLoadException {
        JsonArray values = array(room, "mechanics", roomPath);
        if (values.isEmpty()) throw error(roomPath + ".mechanics", "must not be empty");
        Set<String> ids = new HashSet<>();
        List<MapPack.MechanicDefinition> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String path = roomPath + ".mechanics[" + i + "]";
            JsonObject mechanic = requireObject(values.get(i), path);
            if (!ids.add(text(mechanic, "id", path))) throw error(path + ".id", "duplicate mechanic id");
            String type = text(mechanic, "type", path);
            if (!SUPPORTED_MECHANICS.contains(type)) throw error(path + ".type", "unsupported mechanic " + type);
            result.add(parseMechanic(mechanic, type, bounds, path));
        }
        Map<String, MapPack.MechanicDefinition> mechanicsById = result.stream().collect(
                java.util.stream.Collectors.toMap(MapPack.MechanicDefinition::id, definition -> definition));
        int completable = 0;
        for (MapPack.MechanicDefinition definition : result) {
            if (definition instanceof MapPack.LogicAnswer answer) {
                Set<String> uniqueRequirements = new HashSet<>();
                for (String required : answer.requires()) {
                    MapPack.MechanicDefinition prerequisite = mechanicsById.get(required);
                    if (prerequisite == null || required.equals(answer.id())) {
                        throw error(roomPath + ".mechanics", "logic answer requires unknown mechanic " + required);
                    }
                    if (!uniqueRequirements.add(required)) {
                        throw error(roomPath + ".mechanics", "logic answer repeats prerequisite " + required);
                    }
                    if (prerequisite instanceof MapPack.LogicAnswer) {
                        throw error(roomPath + ".mechanics", "logic answer prerequisite must be an environmental mechanic");
                    }
                }
                if (answer.chatSubmissionEnabled()) completable++;
            } else {
                completable++;
            }
        }
        if (completable == 0) throw error(roomPath + ".mechanics", "room requires at least one completable mechanic");
        return result;
    }

    private MapPack.MechanicDefinition parseMechanic(JsonObject value, String type, MapPack.Bounds bounds, String path) throws MapPackLoadException {
        String id = text(value, "id", path);
        return switch (type) {
            case "LATCHING_PRESSURE_PADS", "CORNER_OBJECTIVES" -> {
                rejectUnknown(value, path, "id", "type", "pads", "requiredCount", "latch", "allowSequentialSolo", "indicatorMaterial");
                JsonArray pads = array(value, "pads", path);
                if (pads.isEmpty()) throw error(path + ".pads", "must not be empty");
                List<MapPack.Position> positions = positions(pads, path + ".pads");
                for (MapPack.Position pos : positions) requireInBounds(bounds, pos, path + ".pads");
                int required = integer(value, "requiredCount", path, 1, pads.size());
                boolean latch = bool(value, "latch", path);
                boolean solo = bool(value, "allowSequentialSolo", path);
                if (!latch || !solo) throw error(path, "MVP pad mechanics require latch and allowSequentialSolo=true");
                yield new MapPack.PadMechanic(id, type, positions, required, latch, solo,
                        material(value, "indicatorMaterial", path));
            }
            case "DESTRUCTIBLE_TARGET" -> {
                rejectUnknown(value, path, "id", "type", "target");
                JsonObject target = object(value, "target", path);
                MapPack.DestructibleTarget parsed = target(id, type, target, bounds, path + ".target");
                yield parsed;
            }
            case "NUMERIC_KEYPAD" -> {
                rejectUnknown(value, path, "id", "type", "answer", "minDigits", "maxDigits", "wrongAnswer",
                        "digitButtons", "submitButton", "clearButton");
                int answer = integer(value, "answer", path, 0, Integer.MAX_VALUE);
                int minDigits = integer(value, "minDigits", path, 1, 10);
                int maxDigits = integer(value, "maxDigits", path, minDigits, 10);
                String wrong = exactText(value, "wrongAnswer", path, "RESET_ROOM");
                JsonArray buttons = array(value, "digitButtons", path);
                Set<Integer> digits = new HashSet<>();
                List<MapPack.DigitButton> parsedButtons = new ArrayList<>();
                for (int i = 0; i < buttons.size(); i++) {
                    JsonObject button = requireObject(buttons.get(i), path + ".digitButtons[" + i + "]");
                    rejectUnknown(button, path + ".digitButtons[" + i + "]", "digit", "position");
                    int digit = integer(button, "digit", path, 0, 9);
                    if (!digits.add(digit)) throw error(path + ".digitButtons", "duplicate digit");
                    MapPack.Position position = position(object(button, "position", path), path);
                    requireInBounds(bounds, position, path);
                    parsedButtons.add(new MapPack.DigitButton(digit, position));
                }
                if (digits.size() != 10) throw error(path + ".digitButtons", "digits 0..9 are required");
                MapPack.Position submit = position(object(value, "submitButton", path), path);
                MapPack.Position clear = position(object(value, "clearButton", path), path);
                requireInBounds(bounds, submit, path);
                requireInBounds(bounds, clear, path);
                yield new MapPack.NumericKeypad(id, type, answer, minDigits, maxDigits, wrong, parsedButtons, submit, clear);
            }
            case "LOGIC_ANSWER" -> {
                rejectUnknown(value, path, "id", "type", "question", "pages", "answerFormat", "answers",
                        "normalization", "cooldownSeconds", "difficulty", "inspiration", "solutionExplanation",
                        "wrongAnswerSamples", "submissionMode", "requires");
                exactText(value, "normalization", path, "LETTERS_AND_DIGITS");
                List<MapPack.BookPage> pages = bookPages(array(value, "pages", path), path + ".pages");
                List<String> answers = strings(array(value, "answers", path), path + ".answers", 1, 8, 64);
                Set<String> normalized = new HashSet<>();
                for (String answer : answers) {
                    String candidate = LogicAnswerNormalizer.normalize(answer);
                    if (candidate.isEmpty()) throw error(path + ".answers", "answers must contain a letter or digit");
                    if (!normalized.add(candidate)) throw error(path + ".answers", "duplicate normalized answer");
                }
                List<String> wrongSamples = strings(array(value, "wrongAnswerSamples", path),
                        path + ".wrongAnswerSamples", 2, 12, 64);
                for (String wrong : wrongSamples) {
                    if (normalized.contains(LogicAnswerNormalizer.normalize(wrong))) {
                        throw error(path + ".wrongAnswerSamples", "wrong sample matches an accepted answer");
                    }
                }
                yield new MapPack.LogicAnswer(id, type, text(value, "question", path), pages,
                        text(value, "answerFormat", path), answers,
                        integer(value, "cooldownSeconds", path, 3, 60),
                        integer(value, "difficulty", path, 1, 5), text(value, "inspiration", path),
                        text(value, "solutionExplanation", path), wrongSamples,
                        value.has("submissionMode") ? text(value, "submissionMode", path) : "CHAT",
                        value.has("requires") ? strings(array(value, "requires", path), path + ".requires", 0, 8, 64) : List.of());
            }
            case "CLUE_REGIONS" -> {
                rejectUnknown(value, path, "id", "type", "regions");
                JsonArray regions = array(value, "regions", path);
                if (regions.isEmpty()) throw error(path + ".regions", "must not be empty");
                List<MapPack.ClueRegion> parsed = new ArrayList<>();
                Set<String> regionIds = new HashSet<>();
                for (int index = 0; index < regions.size(); index++) {
                    String itemPath = path + ".regions[" + index + "]";
                    JsonObject item = requireObject(regions.get(index), itemPath);
                    rejectUnknown(item, itemPath, "id", "bounds", "message", "sound", "pitch");
                    String regionId = text(item, "id", itemPath);
                    if (!regionIds.add(regionId)) throw error(itemPath + ".id", "duplicate clue region");
                    MapPack.Bounds regionBounds = bounds(object(item, "bounds", itemPath), itemPath + ".bounds");
                    requireBoundsInside(bounds, regionBounds, itemPath + ".bounds");
                    parsed.add(new MapPack.ClueRegion(regionId, regionBounds, text(item, "message", itemPath),
                            item.has("sound") ? Optional.of(text(item, "sound", itemPath)) : Optional.empty(),
                            (float) optionalNumber(item, "pitch", 1.0)));
                }
                yield new MapPack.ClueRegions(id, type, parsed);
            }
            case "ORDERED_INPUT" -> {
                rejectUnknown(value, path, "id", "type", "controls", "expected", "groups", "operatorLockSeconds",
                        "resultText", "clearButton");
                List<MapPack.Control> controls = controls(array(value, "controls", path), bounds, path + ".controls");
                Set<String> controlIds = controls.stream().map(MapPack.Control::id).collect(java.util.stream.Collectors.toSet());
                JsonArray expectedValues = array(value, "expected", path);
                if (expectedValues.isEmpty()) throw error(path + ".expected", "must not be empty");
                List<MapPack.ExpectedStep> expected = new ArrayList<>();
                for (int index = 0; index < expectedValues.size(); index++) {
                    String itemPath = path + ".expected[" + index + "]";
                    JsonObject item = requireObject(expectedValues.get(index), itemPath);
                    rejectUnknown(item, itemPath, "control", "display");
                    String control = text(item, "control", itemPath);
                    if (!controlIds.contains(control)) throw error(itemPath + ".control", "unknown control " + control);
                    expected.add(new MapPack.ExpectedStep(control, item.has("display") ? string(item, "display", itemPath) : control));
                }
                List<Integer> groups = value.has("groups") ? integers(array(value, "groups", path), path + ".groups", 1, 64) : List.of();
                int visibleSteps = expected.stream().mapToInt(step -> step.display().isEmpty() ? 0 : 1).sum();
                if (!groups.isEmpty() && groups.stream().mapToInt(Integer::intValue).sum() != visibleSteps) {
                    throw error(path + ".groups", "group sizes must equal visible expected steps");
                }
                Optional<MapPack.Position> clear = Optional.empty();
                if (value.has("clearButton")) {
                    MapPack.Position clearPosition = position(object(value, "clearButton", path), path + ".clearButton");
                    requireInBounds(bounds, clearPosition, path + ".clearButton");
                    clear = Optional.of(clearPosition);
                }
                yield new MapPack.OrderedInput(id, type, controls, expected, groups,
                        integer(value, "operatorLockSeconds", path, 1, 60), text(value, "resultText", path), clear);
            }
            case "CHOICE_INPUT" -> {
                rejectUnknown(value, path, "id", "type", "controls", "correctControl", "operatorLockSeconds", "resultText");
                List<MapPack.Control> controls = controls(array(value, "controls", path), bounds, path + ".controls");
                String correct = text(value, "correctControl", path);
                if (controls.stream().noneMatch(control -> control.id().equals(correct))) {
                    throw error(path + ".correctControl", "unknown control " + correct);
                }
                yield new MapPack.ChoiceInput(id, type, controls, correct,
                        integer(value, "operatorLockSeconds", path, 1, 60), text(value, "resultText", path));
            }
            case "TOGGLE_INPUT" -> {
                rejectUnknown(value, path, "id", "type", "controls", "expectedActive", "maxSelections",
                        "operatorLockSeconds", "submitButton", "clearButton", "resultText");
                List<MapPack.Control> controls = controls(array(value, "controls", path), bounds, path + ".controls");
                Set<String> controlIds = controls.stream().map(MapPack.Control::id).collect(java.util.stream.Collectors.toSet());
                List<String> expected = strings(array(value, "expectedActive", path), path + ".expectedActive", 1, controls.size(), 64);
                if (!controlIds.containsAll(expected)) throw error(path + ".expectedActive", "contains unknown control");
                MapPack.Position submit = position(object(value, "submitButton", path), path + ".submitButton");
                MapPack.Position clear = position(object(value, "clearButton", path), path + ".clearButton");
                requireInBounds(bounds, submit, path + ".submitButton");
                requireInBounds(bounds, clear, path + ".clearButton");
                yield new MapPack.ToggleInput(id, type, controls, expected,
                        integer(value, "maxSelections", path, expected.size(), controls.size()),
                        integer(value, "operatorLockSeconds", path, 1, 60), submit, clear,
                        text(value, "resultText", path));
            }
            case "PROXIMITY_ESCORT" -> {
                rejectUnknown(value, path, "id", "type", "entity", "checkpoints", "destination", "proximityRadius",
                        "movementSpeed", "gateMode", "failureIfUngatedCheckpointReached", "failurePolicy", "completionAt");
                JsonObject entity = object(value, "entity", path);
                rejectUnknown(entity, path + ".entity", "entityType", "customName", "spawn", "invulnerable");
                String entityType = text(entity, "entityType", path + ".entity").toUpperCase(Locale.ROOT);
                try { EntityType.valueOf(entityType); } catch (IllegalArgumentException failure) {
                    throw error(path + ".entity.entityType", "unknown entity type " + entityType);
                }
                MapPack.Position entitySpawn = position(object(entity, "spawn", path + ".entity"), path);
                requireInBounds(bounds, entitySpawn, path);
                JsonArray checkpoints = array(value, "checkpoints", path);
                if (checkpoints.size() != 7) throw error(path + ".checkpoints", "MVP escort requires exactly 7 checkpoints");
                List<MapPack.Position> points = positions(checkpoints, path + ".checkpoints");
                for (MapPack.Position pos : points) requireInBounds(bounds, pos, path);
                MapPack.Position destination = position(object(value, "destination", path), path);
                requireInBounds(bounds, destination, path);
                double radius = positiveDouble(value, "proximityRadius", path);
                double speed = positiveDouble(value, "movementSpeed", path);
                String gate = exactText(value, "gateMode", path, "REQUIRE_PLAYER_AT_NEXT_CHECKPOINT");
                boolean failUngated = bool(value, "failureIfUngatedCheckpointReached", path);
                if (!failUngated) throw error(path + ".failureIfUngatedCheckpointReached", "must be true");
                String failurePolicy = exactText(value, "failurePolicy", path, "RESET_ROOM");
                String completionAt = exactText(value, "completionAt", path, "DESTINATION");
                yield new MapPack.Escort(id, type,
                        new MapPack.EscortEntity(entityType, text(entity, "customName", path + ".entity"), entitySpawn,
                                bool(entity, "invulnerable", path + ".entity")),
                        points, destination, radius, speed, gate, failUngated, failurePolicy, completionAt);
            }
            case "DYNAMIC_PARTY_PADS_AND_TARGET" -> {
                rejectUnknown(value, path, "id", "type", "activePadCount", "playerPads", "latch", "target", "completionMode");
                String active = exactText(value, "activePadCount", path, "PARTY_SIZE");
                JsonArray pads = array(value, "playerPads", path);
                if (pads.size() != 4) throw error(path + ".playerPads", "four roster-index pads are required");
                Set<Integer> indices = new HashSet<>();
                List<MapPack.IndexedPad> parsedPads = new ArrayList<>();
                for (int i = 0; i < pads.size(); i++) {
                    JsonObject pad = requireObject(pads.get(i), path + ".playerPads[" + i + "]");
                    rejectUnknown(pad, path + ".playerPads[" + i + "]", "partyIndex", "position");
                    int index = integer(pad, "partyIndex", path, 1, 4);
                    if (!indices.add(index)) throw error(path + ".playerPads", "duplicate partyIndex");
                    MapPack.Position pos = position(object(pad, "position", path), path);
                    requireInBounds(bounds, pos, path);
                    parsedPads.add(new MapPack.IndexedPad(index, pos));
                }
                boolean latch = bool(value, "latch", path);
                if (!latch) throw error(path + ".latch", "must be true");
                MapPack.DestructibleTarget target = target(id + "-target", "DESTRUCTIBLE_TARGET",
                        object(value, "target", path), bounds, path + ".target");
                String completion = exactText(value, "completionMode", path, "ACTIVE_PADS_AND_TARGET");
                yield new MapPack.DynamicPartyPads(id, type, active, parsedPads, latch, target, completion);
            }
            default -> throw error(path, "unsupported mechanic");
        };
    }

    private List<MapPack.Hint> hints(JsonObject room, String path) throws MapPackLoadException {
        JsonArray values = array(room, "hints", path);
        if (values.size() != 3) throw error(path + ".hints", "exactly three hint tiers are required");
        List<MapPack.Hint> result = new ArrayList<>();
        Set<Integer> tiers = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            JsonObject hint = requireObject(values.get(i), path + ".hints[" + i + "]");
            rejectUnknown(hint, path + ".hints[" + i + "]", "tier", "text");
            int tier = integer(hint, "tier", path, 1, 3);
            if (!tiers.add(tier)) throw error(path + ".hints", "duplicate tier");
            result.add(new MapPack.Hint(tier, text(hint, "text", path)));
        }
        result.sort(java.util.Comparator.comparingInt(MapPack.Hint::tier));
        return result;
    }

    private static MapPack.Bounds bounds(JsonObject value, String path) throws MapPackLoadException {
        rejectUnknown(value, path, "min", "max");
        return new MapPack.Bounds(position(object(value, "min", path), path + ".min"),
                position(object(value, "max", path), path + ".max"));
    }

    private static MapPack.Position position(JsonObject value, String path) throws MapPackLoadException {
        rejectUnknown(value, path, "x", "y", "z", "yaw", "pitch");
        return new MapPack.Position(number(value, "x", path), number(value, "y", path), number(value, "z", path),
                (float) optionalNumber(value, "yaw", 0.0), (float) optionalNumber(value, "pitch", 0.0));
    }

    private static List<MapPack.Position> positions(JsonArray values, String path) throws MapPackLoadException {
        List<MapPack.Position> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) result.add(position(requireObject(values.get(i), path + "[" + i + "]"), path));
        return result;
    }

    private static List<MapPack.BookPage> bookPages(JsonArray values, String path) throws MapPackLoadException {
        if (values.isEmpty() || values.size() > 8) throw error(path, "array size must be between 1 and 8");
        List<MapPack.BookPage> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            JsonElement value = values.get(index);
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String text = value.getAsString().trim();
                if (text.isEmpty() || text.codePointCount(0, text.length()) > 240) {
                    throw error(path + "[" + index + "]", "non-blank page must be at most 240 characters");
                }
                result.add(new MapPack.BookPage(MapPack.PageLayout.PROSE, text));
                continue;
            }
            String itemPath = path + "[" + index + "]";
            JsonObject page = requireObject(value, itemPath);
            rejectUnknown(page, itemPath, "layout", "text");
            MapPack.PageLayout layout;
            try { layout = MapPack.PageLayout.valueOf(text(page, "layout", itemPath)); }
            catch (IllegalArgumentException failure) { throw error(itemPath + ".layout", "must be PROSE or GRID"); }
            String text = text(page, "text", itemPath);
            if (text.codePointCount(0, text.length()) > 240) throw error(itemPath + ".text", "must be at most 240 characters");
            result.add(new MapPack.BookPage(layout, text));
        }
        return result;
    }

    private static List<MapPack.Control> controls(JsonArray values, MapPack.Bounds bounds, String path)
            throws MapPackLoadException {
        if (values.isEmpty()) throw error(path, "must not be empty");
        List<MapPack.Control> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String itemPath = path + "[" + index + "]";
            JsonObject item = requireObject(values.get(index), itemPath);
            rejectUnknown(item, itemPath, "id", "token", "label", "activation", "position", "material", "indicator");
            String id = text(item, "id", itemPath);
            if (!ids.add(id)) throw error(itemPath + ".id", "duplicate control");
            String activation = text(item, "activation", itemPath);
            if (!Set.of("STEP", "CLICK").contains(activation)) throw error(itemPath + ".activation", "must be STEP or CLICK");
            MapPack.Position position = position(object(item, "position", itemPath), itemPath + ".position");
            requireInBounds(bounds, position, itemPath + ".position");
            Optional<MapPack.Position> indicator = Optional.empty();
            if (item.has("indicator")) {
                MapPack.Position indicatorPosition = position(object(item, "indicator", itemPath), itemPath + ".indicator");
                requireInBounds(bounds, indicatorPosition, itemPath + ".indicator");
                indicator = Optional.of(indicatorPosition);
            }
            result.add(new MapPack.Control(id, string(item, "token", itemPath), text(item, "label", itemPath),
                    activation, position, material(item, "material", itemPath), indicator));
        }
        return result;
    }

    private static List<Integer> integers(JsonArray values, String path, int min, int max)
            throws MapPackLoadException {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            JsonElement value = values.get(index);
            try {
                int number = value.getAsInt();
                if (number < min || number > max || value.getAsDouble() != number) throw new NumberFormatException();
                result.add(number);
            } catch (RuntimeException failure) {
                throw error(path + "[" + index + "]", "integer must be between " + min + " and " + max);
            }
        }
        return result;
    }

    private static List<String> strings(JsonArray values, String path, int minSize, int maxSize, int maxLength)
            throws MapPackLoadException {
        if (values.size() < minSize || values.size() > maxSize) {
            throw error(path, "array size must be between " + minSize + " and " + maxSize);
        }
        List<String> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            JsonElement value = values.get(index);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw error(path + "[" + index + "]", "string is required");
            }
            String text = value.getAsString().trim();
            if (text.isEmpty() || text.codePointCount(0, text.length()) > maxLength) {
                throw error(path + "[" + index + "]", "non-blank string must be at most " + maxLength + " characters");
            }
            result.add(text);
        }
        return result;
    }

    private static String material(JsonObject owner, String key, String path) throws MapPackLoadException {
        String value = text(owner, key, path).toUpperCase(Locale.ROOT);
        if (Material.matchMaterial(value) == null) throw error(path + "." + key, "unknown material " + value);
        return value;
    }

    private static void requireBoundsInside(MapPack.Bounds outer, MapPack.Bounds inner, String path) throws MapPackLoadException {
        requireInBounds(outer, inner.min(), path);
        requireInBounds(outer, inner.max(), path);
    }

    private static void requireInBounds(MapPack.Bounds bounds, MapPack.Position position, String path) throws MapPackLoadException {
        if (!bounds.contains(position)) throw error(path, "coordinate is outside declared bounds");
    }

    private static JsonObject object(JsonObject owner, String key, String path) throws MapPackLoadException {
        JsonElement value = owner.get(key);
        if (value == null || !value.isJsonObject()) throw error(path + "." + key, "object is required");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject owner, String key, String path) throws MapPackLoadException {
        JsonElement value = owner.get(key);
        if (value == null || !value.isJsonArray()) throw error(path + "." + key, "array is required");
        return value.getAsJsonArray();
    }

    private static JsonObject requireObject(JsonElement value, String path) throws MapPackLoadException {
        if (value == null || !value.isJsonObject()) throw error(path, "object is required");
        return value.getAsJsonObject();
    }

    private static String text(JsonObject owner, String key, String path) throws MapPackLoadException {
        JsonElement value = owner.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().trim().isEmpty()) throw error(path + "." + key, "non-blank string is required");
        return value.getAsString().trim();
    }

    private static String string(JsonObject owner, String key, String path) throws MapPackLoadException {
        JsonElement value = owner.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw error(path + "." + key, "string is required");
        }
        return value.getAsString();
    }

    private static int integer(JsonObject owner, String key, String path, int min, int max) throws MapPackLoadException {
        JsonElement value = owner.get(key);
        try {
            int result = value == null ? Integer.MIN_VALUE : value.getAsInt();
            if (result < min || result > max || value.getAsDouble() != result) throw new NumberFormatException();
            return result;
        } catch (RuntimeException failure) {
            throw error(path + "." + key, "integer must be between " + min + " and " + max);
        }
    }

    private static double number(JsonObject owner, String key, String path) throws MapPackLoadException {
        JsonElement value = owner.get(key);
        try {
            double result = value.getAsDouble();
            if (!Double.isFinite(result)) throw new NumberFormatException();
            return result;
        } catch (RuntimeException failure) {
            throw error(path + "." + key, "finite number is required");
        }
    }

    private static double optionalNumber(JsonObject owner, String key, double fallback) throws MapPackLoadException {
        if (!owner.has(key)) return fallback;
        return number(owner, key, "position");
    }

    private static double positiveDouble(JsonObject owner, String key, String path) throws MapPackLoadException {
        double result = number(owner, key, path);
        if (result <= 0) throw error(path + "." + key, "must be positive");
        return result;
    }

    private static MapPack.DestructibleTarget target(String id, String type, JsonObject target,
                                                      MapPack.Bounds bounds, String path) throws MapPackLoadException {
        rejectUnknown(target, path, "material", "position", "interaction");
        String material = material(target, "material", path);
        MapPack.Position position = position(object(target, "position", path), path + ".position");
        requireInBounds(bounds, position, path);
        String interaction = exactText(target, "interaction", path, "BREAK_BLOCK");
        return new MapPack.DestructibleTarget(id, type, material, position, interaction);
    }

    private static void validateReset(JsonObject reset, String path) throws MapPackLoadException {
        rejectUnknown(reset, path, "scope", "teleportPartyToCheckpoint", "restoreBlocks",
                "removeOwnedEntities", "clearMechanicState");
        exactText(reset, "scope", path, "ROOM");
        if (!bool(reset, "teleportPartyToCheckpoint", path)
                || !bool(reset, "restoreBlocks", path)
                || !bool(reset, "removeOwnedEntities", path)
                || !bool(reset, "clearMechanicState", path)) {
            throw error(path, "MVP reset flags must all be true");
        }
    }

    private static String exactText(JsonObject owner, String key, String path, String expected) throws MapPackLoadException {
        String value = text(owner, key, path);
        if (!expected.equals(value)) throw error(path + "." + key, "must be " + expected);
        return value;
    }

    private static boolean bool(JsonObject owner, String key, String path) throws MapPackLoadException {
        JsonElement value = owner.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw error(path + "." + key, "boolean is required");
        }
        return value.getAsBoolean();
    }

    private static void rejectUnknown(JsonObject object, String path, String... allowed) throws MapPackLoadException {
        Set<String> names = Set.of(allowed);
        for (String key : object.keySet()) {
            if (!names.contains(key)) throw error(path + "." + key, "unknown property");
        }
    }

    private static MapPackLoadException error(String path, String message) {
        return new MapPackLoadException(path + ": " + message);
    }

    static String stripComments(String input) throws MapPackLoadException {
        StringBuilder output = new StringBuilder(input.length());
        boolean string = false;
        boolean escaped = false;
        boolean line = false;
        boolean block = false;
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            char next = i + 1 < input.length() ? input.charAt(i + 1) : '\0';
            if (line) {
                if (current == '\n' || current == '\r') { line = false; output.append(current); }
                else output.append(' ');
                continue;
            }
            if (block) {
                if (current == '*' && next == '/') { output.append("  "); i++; block = false; }
                else output.append(current == '\n' || current == '\r' ? current : ' ');
                continue;
            }
            if (string) {
                output.append(current);
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') string = false;
                continue;
            }
            if (current == '"') { string = true; output.append(current); }
            else if (current == '/' && next == '/') { output.append("  "); i++; line = true; }
            else if (current == '/' && next == '*') { output.append("  "); i++; block = true; }
            else output.append(current);
        }
        if (block) throw error("$", "unterminated block comment");
        return output.toString();
    }
}
