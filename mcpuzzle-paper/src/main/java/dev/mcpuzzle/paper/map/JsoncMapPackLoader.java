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
            "NUMERIC_KEYPAD", "LOGIC_ANSWER", "PROXIMITY_ESCORT", "DYNAMIC_PARTY_PADS_AND_TARGET"
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
                    "spawn", "checkpoint", "visual", "completionMode", "mechanics", "hints", "messages", "reset", "source");
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
            List<MapPack.MechanicDefinition> mechanics = mechanics(room, build, path);
            List<MapPack.Hint> hints = hints(room, path);
            JsonObject messages = object(room, "messages", path);
            rejectUnknown(messages, path + ".messages", "intro", "completion", "failure");
            validateReset(object(room, "reset", path), path + ".reset");
            rooms.add(new MapPack.RoomDefinition(id, sequence,
                    integer(room, "originalStage", path, 1, Integer.MAX_VALUE), text(room, "title", path),
                    build, play, spawn, checkpoint, visual, mechanics, hints,
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
                        "wrongAnswerSamples");
                exactText(value, "normalization", path, "LETTERS_AND_DIGITS");
                List<String> pages = strings(array(value, "pages", path), path + ".pages", 1, 8, 240);
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
                        text(value, "solutionExplanation", path), wrongSamples);
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
