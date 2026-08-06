package dev.mcpuzzle.maptool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Converts the five representative SCX stages into a deterministic Minecraft map pack. */
public final class AnalysisMapImporter {
    public static final List<Integer> REPRESENTATIVE_STAGES = List.of(1, 3, 8, 19, 42);

    private static final List<String> VISUAL_MATERIALS = List.of(
            "BLACK_CONCRETE", "CYAN_TERRACOTTA", "WARPED_PLANKS", "PRISMARINE",
            "DARK_PRISMARINE", "OXIDIZED_COPPER", "CUT_COPPER", "PURPUR_BLOCK",
            "MAGENTA_TERRACOTTA", "LIME_TERRACOTTA", "YELLOW_TERRACOTTA", "RED_TERRACOTTA",
            "BLUE_TERRACOTTA", "LIGHT_BLUE_TERRACOTTA", "GRAY_TERRACOTTA", "QUARTZ_BLOCK");

    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private static final Map<Integer, List<Integer>> SOURCE_LOCATIONS = Map.of(
            1, List.of(1, 4, 2, 3, 9, 10),
            3, List.of(12, 13, 14, 15, 21, 16),
            8, List.of(),
            19, List.of(122, 123, 124, 125, 126, 127, 128, 129, 54),
            42, List.of(198, 199, 201, 200, 202));

    private final ObjectMapper mapper;
    private final Clock clock;

    public AnalysisMapImporter() {
        this(Clock.systemUTC());
    }

    AnalysisMapImporter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public ImportResult importFile(Path source, Path output) throws MapImportException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(output, "output");
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedOutput = output.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedSource)) {
            throw new MapImportException("입력 파일이 없거나 일반 파일이 아닙니다: " + normalizedSource);
        }

        JsonNode root;
        try {
            root = mapper.readTree(normalizedSource.toFile());
        } catch (JsonProcessingException exception) {
            throw new MapImportException("JSON 형식이 올바르지 않습니다: " + exception.getOriginalMessage(), exception);
        } catch (IOException exception) {
            throw new MapImportException("입력 파일을 읽지 못했습니다: " + normalizedSource, exception);
        }

        byte[] content;
        try {
            content = (mapper.writeValueAsString(convert(root)) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            throw new MapImportException("맵 팩 JSON 직렬화에 실패했습니다.", exception);
        }
        return writeAtomically(normalizedOutput, content);
    }

    ObjectNode convert(JsonNode root) throws MapImportException {
        if (!root.isObject()) {
            throw new MapImportException("분석 JSON의 최상위 값은 객체여야 합니다.");
        }
        Map<Integer, JsonNode> stages = indexByInteger(root.path("stages"), "stage", "stages");
        Map<Integer, JsonNode> locations = indexByInteger(root.path("locations"), "id", "locations");
        TerrainSource terrain = terrain(root);
        for (int stageNumber : REPRESENTATIVE_STAGES) {
            if (!stages.containsKey(stageNumber)) {
                throw new MapImportException("필수 스테이지가 없습니다: " + stageNumber);
            }
            for (int locationId : SOURCE_LOCATIONS.get(stageNumber)) {
                if (!locations.containsKey(locationId)) {
                    throw new MapImportException("스테이지 " + stageNumber + "의 원본 Location " + locationId + "이 없습니다.");
                }
            }
        }

        ObjectNode pack = mapper.createObjectNode();
        pack.put("$schema", "https://mcpuzzle.dev/schema/map-pack.schema.json");
        pack.put("schemaVersion", 1);
        pack.put("mapVersion", "1.0.0-mvp");
        pack.put("mazeId", "legacy-50rooms-analysis-draft");
        pack.put("displayName", "50개의 방: 대표 5문제");
        pack.put("description", "과거 50 Rooms SCX 분석 자료를 보존하는 비활성 조사 초안");
        pack.put("locale", "ko-KR");
        pack.set("party", partyRules());
        pack.set("world", worldDefinition());
        pack.set("partySpawns", partySpawns());
        ObjectNode source = pack.putObject("source");
        source.put("format", "StarCraft SCX analysis");
        if (root.path("source").path("sha256").isTextual()) {
            source.put("sha256", root.path("source").path("sha256").asText());
        }

        ArrayNode rooms = pack.putArray("rooms");
        for (int index = 0; index < REPRESENTATIVE_STAGES.size(); index++) {
            int stageNumber = REPRESENTATIVE_STAGES.get(index);
            rooms.add(createRoom(index + 1, stageNumber, stages.get(stageNumber), locations, terrain));
        }
        return pack;
    }

    private Map<Integer, JsonNode> indexByInteger(JsonNode array, String key, String path) throws MapImportException {
        if (!array.isArray()) {
            throw new MapImportException("'" + path + "'는 배열이어야 합니다.");
        }
        Map<Integer, JsonNode> indexed = new LinkedHashMap<>();
        for (JsonNode item : array) {
            JsonNode value = item.path(key);
            if (!value.canConvertToInt()) {
                throw new MapImportException("'" + path + "' 항목의 '" + key + "'가 정수가 아닙니다.");
            }
            int id = value.intValue();
            if (indexed.put(id, item) != null) {
                throw new MapImportException("'" + path + "'에 중복 ID가 있습니다: " + id);
            }
        }
        return indexed;
    }

    private ObjectNode partyRules() {
        ObjectNode party = mapper.createObjectNode();
        party.put("minPlayers", 1);
        party.put("maxPlayers", 4);
        return party;
    }

    private ObjectNode worldDefinition() {
        ObjectNode world = mapper.createObjectNode();
        world.put("mode", "GENERATED_VOID");
        world.put("environment", "NORMAL");
        world.set("bounds", region(-40, 48, -16, 40, 96, 464));
        world.put("floorY", 64);
        ObjectNode generator = world.putObject("generator");
        generator.put("type", "ROOM_STACK");
        generator.put("roomSpacing", 96);
        generator.put("floorMaterial", "POLISHED_DEEPSLATE");
        generator.put("wallMaterial", "DEEPSLATE_BRICKS");
        generator.put("ceilingMaterial", "BLACK_CONCRETE");
        generator.put("lightMaterial", "SEA_LANTERN");
        return world;
    }

    private ArrayNode partySpawns() {
        ArrayNode spawns = mapper.createArrayNode();
        spawns.add(position(-3, 65, -8, 0));
        spawns.add(position(-1, 65, -8, 0));
        spawns.add(position(1, 65, -8, 0));
        spawns.add(position(3, 65, -8, 0));
        return spawns;
    }

    private ObjectNode createRoom(int sequence, int stageNumber, JsonNode stage, Map<Integer, JsonNode> locations,
                                  TerrainSource terrain)
            throws MapImportException {
        int offsetZ = (sequence - 1) * 96;
        ObjectNode room = mapper.createObjectNode();
        room.put("id", "stage-" + stageNumber);
        room.put("sequence", sequence);
        room.put("originalStage", stageNumber);
        room.put("title", roomTitle(stageNumber));
        room.set("buildBounds", roomBounds(stageNumber, offsetZ));
        room.set("playBounds", roomBounds(stageNumber, offsetZ));
        room.set("spawn", roomSpawn(stageNumber, offsetZ));
        room.set("checkpoint", roomSpawn(stageNumber, offsetZ));
        if (terrain != null) room.set("visual", visual(stage, offsetZ, terrain));
        room.put("completionMode", "ALL_MECHANICS");
        room.set("mechanics", mechanics(stageNumber, offsetZ));
        room.set("hints", hints(stageNumber));
        room.set("messages", messages(stageNumber, stage));
        room.set("reset", resetPolicy());
        room.set("source", sourceMetadata(stageNumber, stage, locations));
        return room;
    }

    private ObjectNode visual(JsonNode stage, int offsetZ, TerrainSource terrain) throws MapImportException {
        JsonNode sourceRoom = stage.path("room");
        int left = Math.floorDiv(requiredInteger(sourceRoom, "left", "stage.room"), 32);
        int top = Math.floorDiv(requiredInteger(sourceRoom, "top", "stage.room"), 32);
        int rightExclusive = Math.floorDiv(requiredInteger(sourceRoom, "right", "stage.room") + 31, 32);
        int bottomExclusive = Math.floorDiv(requiredInteger(sourceRoom, "bottom", "stage.room") + 31, 32);
        int width = rightExclusive - left;
        int height = bottomExclusive - top;
        if (width < 1 || height < 1 || left < 0 || top < 0
                || rightExclusive > terrain.width() || bottomExclusive > terrain.height()) {
            throw new MapImportException("스테이지 시각 영역이 원본 지형 범위를 벗어났습니다.");
        }

        List<Integer> cells = new ArrayList<>(width * height);
        for (int y = top; y < bottomExclusive; y++) {
            for (int x = left; x < rightExclusive; x++) cells.add(terrain.tile(x, y));
        }
        List<Integer> unique = cells.stream().distinct().sorted().toList();
        if (unique.size() > VISUAL_MATERIALS.size()) {
            throw new MapImportException("한 방의 지형 종류가 시각 팔레트 한도를 초과했습니다: " + unique.size());
        }

        int scale = 2;
        ObjectNode visual = mapper.createObjectNode();
        visual.set("origin", position(-(width * scale) / 2, 64, offsetZ + 10, 0));
        visual.put("scale", scale);
        visual.put("width", width);
        visual.put("height", height);
        ArrayNode palette = visual.putArray("palette");
        for (int index = 0; index < unique.size(); index++) {
            ObjectNode entry = palette.addObject();
            entry.put("tile", unique.get(index));
            entry.put("material", VISUAL_MATERIALS.get(index));
        }
        ArrayNode outputCells = visual.putArray("cells");
        cells.forEach(outputCells::add);
        return visual;
    }

    private TerrainSource terrain(JsonNode root) throws MapImportException {
        JsonNode map = root.path("map");
        JsonNode tiles = root.path("tiles");
        if (!map.isObject() || !tiles.isArray()) return null;
        int width = requiredInteger(map, "width_tiles", "map");
        int height = requiredInteger(map, "height_tiles", "map");
        if (width < 1 || height < 1 || tiles.size() != width * height) {
            throw new MapImportException("분석 JSON의 지형 크기와 tiles 배열이 일치하지 않습니다.");
        }
        List<Integer> values = new ArrayList<>(tiles.size());
        for (int index = 0; index < tiles.size(); index++) {
            JsonNode tile = tiles.get(index);
            if (!tile.canConvertToInt() || tile.intValue() < 0 || tile.intValue() > 65535) {
                throw new MapImportException("tiles[" + index + "]는 0~65535 정수여야 합니다.");
            }
            values.add(tile.intValue());
        }
        return new TerrainSource(width, height, List.copyOf(values));
    }

    private String roomTitle(int stageNumber) {
        return switch (stageNumber) {
            case 1 -> "다른 곳을 찾아라";
            case 3 -> "꼭짓점과 막힌 길";
            case 8 -> "숫자 37";
            case 19 -> "스컬지 호송";
            case 42 -> "심오한 변화";
            default -> throw new IllegalArgumentException("Unsupported stage: " + stageNumber);
        };
    }

    private ObjectNode roomBounds(int stageNumber, int offsetZ) {
        return stageNumber == 19
                ? region(-36, 60, offsetZ, 36, 78, offsetZ + 72)
                : region(-24, 60, offsetZ, 24, 78, offsetZ + 48);
    }

    private ObjectNode roomSpawn(int stageNumber, int offsetZ) {
        return stageNumber == 19 ? position(-28, 65, offsetZ + 6, 0) : position(0, 65, offsetZ + 4, 0);
    }

    private ArrayNode mechanics(int stageNumber, int offsetZ) {
        ArrayNode mechanics = mapper.createArrayNode();
        switch (stageNumber) {
            case 1 -> mechanics.add(latchingPads("four-pads", "LATCHING_PRESSURE_PADS", offsetZ, true));
            case 3 -> {
                mechanics.add(latchingPads("corner-objectives", "CORNER_OBJECTIVES", offsetZ, true));
                ObjectNode core = mechanics.addObject();
                core.put("id", "academy-core");
                core.put("type", "DESTRUCTIBLE_TARGET");
                core.set("target", target("RESPAWN_ANCHOR", position(0, 65, offsetZ + 28, 0), "BREAK_BLOCK"));
            }
            case 8 -> mechanics.add(numericKeypad(offsetZ));
            case 19 -> mechanics.add(escort(offsetZ));
            case 42 -> mechanics.add(dynamicPartyPads(offsetZ));
            default -> throw new IllegalArgumentException("Unsupported stage: " + stageNumber);
        }
        return mechanics;
    }

    private ObjectNode latchingPads(String id, String type, int offsetZ, boolean allowSequentialSolo) {
        ObjectNode mechanic = mapper.createObjectNode();
        mechanic.put("id", id);
        mechanic.put("type", type);
        ArrayNode pads = mechanic.putArray("pads");
        pads.add(position(-16, 64, offsetZ + 12, 0));
        pads.add(position(16, 64, offsetZ + 12, 0));
        pads.add(position(-16, 64, offsetZ + 38, 180));
        pads.add(position(16, 64, offsetZ + 38, 180));
        mechanic.put("requiredCount", 4);
        mechanic.put("latch", true);
        mechanic.put("allowSequentialSolo", allowSequentialSolo);
        mechanic.put("indicatorMaterial", "REDSTONE_LAMP");
        return mechanic;
    }

    private ObjectNode numericKeypad(int offsetZ) {
        ObjectNode mechanic = mapper.createObjectNode();
        mechanic.put("id", "answer-keypad");
        mechanic.put("type", "NUMERIC_KEYPAD");
        mechanic.put("answer", 37);
        mechanic.put("minDigits", 2);
        mechanic.put("maxDigits", 2);
        mechanic.put("wrongAnswer", "RESET_ROOM");
        ArrayNode keys = mechanic.putArray("digitButtons");
        for (int digit = 0; digit <= 9; digit++) {
            ObjectNode key = keys.addObject();
            key.put("digit", digit);
            int column = digit % 3;
            int row = digit / 3;
            key.set("position", position(-3 + column * 3, 65 + row, offsetZ + 26, 180));
        }
        mechanic.set("submitButton", position(6, 65, offsetZ + 26, 180));
        mechanic.set("clearButton", position(-6, 65, offsetZ + 26, 180));
        return mechanic;
    }

    private ObjectNode escort(int offsetZ) {
        ObjectNode mechanic = mapper.createObjectNode();
        mechanic.put("id", "scourge-escort");
        mechanic.put("type", "PROXIMITY_ESCORT");
        ObjectNode entity = mechanic.putObject("entity");
        entity.put("entityType", "ALLAY");
        entity.put("customName", "스컬지");
        entity.set("spawn", position(-24, 65, offsetZ + 12, 0));
        entity.put("invulnerable", true);
        ArrayNode checkpoints = mechanic.putArray("checkpoints");
        checkpoints.add(position(-24, 65, offsetZ + 12, 0));
        checkpoints.add(position(24, 65, offsetZ + 12, 0));
        checkpoints.add(position(24, 65, offsetZ + 24, 90));
        checkpoints.add(position(0, 65, offsetZ + 24, 180));
        checkpoints.add(position(0, 65, offsetZ + 48, 180));
        checkpoints.add(position(-20, 65, offsetZ + 48, -90));
        checkpoints.add(position(-20, 65, offsetZ + 60, 0));
        mechanic.set("destination", position(-28, 65, offsetZ + 66, 0));
        mechanic.put("proximityRadius", 6.0);
        mechanic.put("movementSpeed", 0.22);
        mechanic.put("gateMode", "REQUIRE_PLAYER_AT_NEXT_CHECKPOINT");
        mechanic.put("failureIfUngatedCheckpointReached", true);
        mechanic.put("failurePolicy", "RESET_ROOM");
        mechanic.put("completionAt", "DESTINATION");
        return mechanic;
    }

    private ObjectNode dynamicPartyPads(int offsetZ) {
        ObjectNode mechanic = mapper.createObjectNode();
        mechanic.put("id", "party-pads-and-core");
        mechanic.put("type", "DYNAMIC_PARTY_PADS_AND_TARGET");
        mechanic.put("activePadCount", "PARTY_SIZE");
        ArrayNode pads = mechanic.putArray("playerPads");
        addPlayerPad(pads, 1, -12, 64, offsetZ + 16);
        addPlayerPad(pads, 2, 12, 64, offsetZ + 16);
        addPlayerPad(pads, 3, -12, 64, offsetZ + 34);
        addPlayerPad(pads, 4, 12, 64, offsetZ + 34);
        mechanic.put("latch", true);
        mechanic.set("target", target("RESPAWN_ANCHOR", position(0, 65, offsetZ + 26, 0), "BREAK_BLOCK"));
        mechanic.put("completionMode", "ACTIVE_PADS_AND_TARGET");
        return mechanic;
    }

    private void addPlayerPad(ArrayNode pads, int partyIndex, int x, int y, int z) {
        ObjectNode pad = pads.addObject();
        pad.put("partyIndex", partyIndex);
        pad.set("position", position(x, y, z, 0));
    }

    private ObjectNode target(String material, ObjectNode position, String interaction) {
        ObjectNode target = mapper.createObjectNode();
        target.put("material", material);
        target.set("position", position);
        target.put("interaction", interaction);
        return target;
    }

    private ArrayNode hints(int stageNumber) {
        List<String> texts = switch (stageNumber) {
            case 1 -> List.of("방의 네 방향을 천천히 비교해 보세요.", "네 개의 발판은 한 번 밟으면 기억됩니다.", "파티원이 없으면 네 개를 하나씩 순서대로 밟으세요.");
            case 3 -> List.of("방의 가장자리를 확인해 보세요.", "네 꼭짓점을 모두 활성화한 뒤 중앙을 보세요.", "중앙의 아카데미 코어를 직접 부수면 길이 열립니다.");
            case 8 -> List.of("정답은 두 자리 숫자입니다.", "3 다음에 7을 누르고 확인하세요.", "정답은 37입니다.");
            case 19 -> List.of("스컬지는 다음 경유지에 파티원이 가까이 있어야 이동합니다.", "한 명은 앞의 경유지를 확인하고 나머지는 호송체를 따라가세요.", "호송체만 경유지에 도착하면 방 처음으로 초기화됩니다.");
            case 42 -> List.of("현재 파티 인원수만큼의 발판만 활성화됩니다.", "각 파티원은 자신의 번호 발판을 담당하세요.", "활성 발판을 모두 기억시킨 뒤 중앙 코어를 부수세요.");
            default -> throw new IllegalArgumentException("Unsupported stage: " + stageNumber);
        };
        ArrayNode hints = mapper.createArrayNode();
        for (int i = 0; i < texts.size(); i++) {
            ObjectNode hint = hints.addObject();
            hint.put("tier", i + 1);
            hint.put("text", texts.get(i));
        }
        return hints;
    }

    private ObjectNode messages(int stageNumber, JsonNode stage) {
        ObjectNode messages = mapper.createObjectNode();
        messages.put("intro", switch (stageNumber) {
            case 1 -> "네 발판의 차이를 찾아 모두 활성화하세요.";
            case 3 -> "네 꼭짓점을 확인하고 막힌 길을 뚫으세요.";
            case 8 -> "키패드에 알맞은 두 자리 숫자를 입력하세요.";
            case 19 -> "스컬지가 파티와 함께 일곱 경유지를 통과하게 하세요.";
            case 42 -> "파티원별 발판을 활성화하고 코어를 파괴하세요.";
            default -> throw new IllegalArgumentException("Unsupported stage: " + stageNumber);
        });
        String completion = stage.path("completion_explanation").asText("방을 완료했습니다.");
        messages.put("completion", completion.isBlank() ? "방을 완료했습니다." : completion);
        messages.put("failure", switch (stageNumber) {
            case 8 -> "틀린 숫자입니다. 파티 전체가 체크포인트로 돌아갑니다.";
            case 19 -> "스컬지를 혼자 보냈습니다. 호송을 다시 시작합니다.";
            default -> "조건이 초기화되었습니다. 파티 전체가 체크포인트로 돌아갑니다.";
        });
        return messages;
    }

    private ObjectNode resetPolicy() {
        ObjectNode reset = mapper.createObjectNode();
        reset.put("scope", "ROOM");
        reset.put("teleportPartyToCheckpoint", true);
        reset.put("restoreBlocks", true);
        reset.put("removeOwnedEntities", true);
        reset.put("clearMechanicState", true);
        return reset;
    }

    private ObjectNode sourceMetadata(int stageNumber, JsonNode stage, Map<Integer, JsonNode> locations)
            throws MapImportException {
        ObjectNode source = mapper.createObjectNode();
        source.put("completionTrigger", requiredInteger(stage, "completion_trigger", "스테이지 " + stageNumber));
        source.set("roomRectangle", rectangle(stage.path("room"), "스테이지 " + stageNumber + " room"));
        if (!stage.path("numeric_answer").isNull() && stage.path("numeric_answer").canConvertToInt()) {
            source.put("numericAnswer", stage.path("numeric_answer").intValue());
        }
        ArrayNode requirements = source.putArray("rawRequirements");
        if (!stage.path("requirements").isArray()) {
            throw new MapImportException("스테이지 " + stageNumber + "의 requirements가 배열이 아닙니다.");
        }
        stage.path("requirements").forEach(item -> requirements.add(item.asText()));
        ArrayNode sourceLocations = source.putArray("locations");
        for (int locationId : SOURCE_LOCATIONS.get(stageNumber)) {
            JsonNode raw = locations.get(locationId);
            ObjectNode location = sourceLocations.addObject();
            location.put("id", locationId);
            location.set("rectangle", rectangle(raw, "Location " + locationId));
        }
        if (stageNumber == 19) {
            ArrayNode route = source.putArray("routeLocationIds");
            List.of(123, 124, 125, 126, 127, 128, 129).forEach(route::add);
            source.put("destinationLocationId", 54);
        }
        if (stageNumber == 42) {
            ArrayNode pads = source.putArray("playerPadLocationIds");
            List.of(198, 199, 201, 200).forEach(pads::add);
            source.put("targetRoomLocationId", 202);
        }
        return source;
    }

    private ObjectNode rectangle(JsonNode raw, String context) throws MapImportException {
        if (!raw.isObject()) {
            throw new MapImportException(context + " 좌표가 객체가 아닙니다.");
        }
        int left = requiredInteger(raw, "left", context);
        int top = requiredInteger(raw, "top", context);
        int right = requiredInteger(raw, "right", context);
        int bottom = requiredInteger(raw, "bottom", context);
        if (right <= left || bottom <= top) {
            throw new MapImportException(context + " 좌표 범위가 비어 있거나 뒤집혀 있습니다.");
        }
        ObjectNode rectangle = mapper.createObjectNode();
        rectangle.put("left", left);
        rectangle.put("top", top);
        rectangle.put("right", right);
        rectangle.put("bottom", bottom);
        return rectangle;
    }

    private int requiredInteger(JsonNode parent, String key, String context) throws MapImportException {
        JsonNode value = parent.path(key);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new MapImportException(context + "의 '" + key + "'가 32비트 정수가 아닙니다.");
        }
        return value.intValue();
    }

    private ObjectNode position(int x, int y, int z, double yaw) {
        ObjectNode position = mapper.createObjectNode();
        position.put("x", x);
        position.put("y", y);
        position.put("z", z);
        position.put("yaw", yaw);
        position.put("pitch", 0.0);
        return position;
    }

    private ObjectNode region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        ObjectNode region = mapper.createObjectNode();
        region.set("min", position(minX, minY, minZ, 0));
        region.set("max", position(maxX, maxY, maxZ, 0));
        return region;
    }

    private ImportResult writeAtomically(Path output, byte[] content) throws MapImportException {
        Path parent = output.getParent();
        if (parent == null) {
            throw new MapImportException("출력 파일의 상위 경로를 결정할 수 없습니다: " + output);
        }
        Path temporary = null;
        Path backup = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, output.getFileName().toString() + ".", ".tmp");
            Files.write(temporary, content);
            if (Files.exists(output)) {
                if (!Files.isRegularFile(output)) {
                    throw new MapImportException("출력 경로가 일반 파일이 아닙니다: " + output);
                }
                backup = nextBackupPath(output);
                Files.copy(output, backup);
            }
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new MapImportException("파일 시스템이 원자적 교체를 지원하지 않습니다: " + parent, exception);
            }
            temporary = null;
            return new ImportResult(output, backup, REPRESENTATIVE_STAGES);
        } catch (MapImportException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new MapImportException("출력 파일을 원자적으로 교체하지 못했습니다: " + output, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Keep the original failure. A stale .tmp file is safe to inspect and delete later.
                }
            }
        }
    }

    private Path nextBackupPath(Path output) {
        String timestamp = BACKUP_TIMESTAMP.format(clock.instant());
        Path candidate = output.resolveSibling(output.getFileName() + ".bak-" + timestamp);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = output.resolveSibling(output.getFileName() + ".bak-" + timestamp + "-" + suffix++);
        }
        return candidate;
    }

    private record TerrainSource(int width, int height, List<Integer> tiles) {
        private int tile(int x, int y) {
            return tiles.get(y * width + x);
        }
    }

    public record ImportResult(Path output, Path backup, List<Integer> stageNumbers) {
        public ImportResult {
            stageNumbers = List.copyOf(new ArrayList<>(stageNumbers));
        }
    }
}
