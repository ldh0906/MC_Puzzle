package dev.mcpuzzle.paper.map;

import dev.mcpuzzle.core.domain.MapVersion;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record MapPack(
        String mazeId, MapVersion mapVersion, String displayName, int minPlayers, int maxPlayers,
        WorldDefinition world, List<Position> partySpawns, List<RoomDefinition> rooms
) {
    public MapPack {
        mazeId = requireText(mazeId, "mazeId");
        Objects.requireNonNull(mapVersion, "mapVersion");
        displayName = requireText(displayName, "displayName");
        if (minPlayers < 1 || maxPlayers > 4 || minPlayers > maxPlayers) {
            throw new IllegalArgumentException("Player count must be within 1..4");
        }
        Objects.requireNonNull(world, "world");
        partySpawns = List.copyOf(partySpawns);
        rooms = List.copyOf(rooms);
    }

    public RoomDefinition room(int sequence) {
        return rooms.stream().filter(room -> room.sequence() == sequence).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown room sequence: " + sequence));
    }

    public record Position(double x, double y, double z, float yaw, float pitch) { }

    public record Bounds(Position min, Position max) {
        public Bounds {
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
            if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
                throw new IllegalArgumentException("Bounds minimum must not exceed maximum");
            }
        }
        public boolean contains(Position point) {
            return point.x() >= min.x() && point.x() <= max.x()
                    && point.y() >= min.y() && point.y() <= max.y()
                    && point.z() >= min.z() && point.z() <= max.z();
        }
    }

    public record WorldDefinition(String mode, String environment, Bounds bounds, int floorY,
                                  String generatorType, int roomSpacing, String floorMaterial,
                                  String wallMaterial, String ceilingMaterial, String lightMaterial) {
        public WorldDefinition {
            mode = requireText(mode, "world.mode");
            environment = requireText(environment, "world.environment");
            Objects.requireNonNull(bounds, "world.bounds");
            generatorType = requireText(generatorType, "world.generator.type");
            if (roomSpacing < 1) throw new IllegalArgumentException("roomSpacing must be positive");
            floorMaterial = requireText(floorMaterial, "floorMaterial");
            wallMaterial = requireText(wallMaterial, "wallMaterial");
            ceilingMaterial = requireText(ceilingMaterial, "ceilingMaterial");
            lightMaterial = requireText(lightMaterial, "lightMaterial");
        }
    }

    public record Hint(int tier, String text) {
        public Hint {
            if (tier < 1 || tier > 3) throw new IllegalArgumentException("Hint tier must be 1..3");
            text = requireText(text, "hint.text");
        }
    }

    public enum PageLayout { PROSE, GRID }

    public record BookPage(PageLayout layout, String text) {
        public BookPage {
            Objects.requireNonNull(layout, "book page layout");
            text = requireText(text, "book page text");
        }
    }

    /** A server-side block mosaic translated from the source map's room terrain. */
    public record VisualBlueprint(Position origin, int scale, int width, int height,
                                  List<Integer> cells, Map<Integer, String> palette) {
        public VisualBlueprint {
            Objects.requireNonNull(origin, "visual.origin");
            if (scale < 1 || scale > 4) throw new IllegalArgumentException("visual.scale must be 1..4");
            if (width < 1 || height < 1) throw new IllegalArgumentException("visual dimensions must be positive");
            cells = List.copyOf(cells);
            if (cells.size() != width * height) {
                throw new IllegalArgumentException("visual.cells must match width * height");
            }
            palette = Map.copyOf(palette);
            for (Integer cell : cells) {
                if (!palette.containsKey(cell)) throw new IllegalArgumentException("visual cell is missing from palette: " + cell);
            }
        }
    }

    public record StructureBlock(Position position, String material) {
        public StructureBlock {
            Objects.requireNonNull(position, "structure block position");
            material = requireText(material, "structure block material");
        }
    }

    public record StructureCuboid(Bounds bounds, String material) {
        public StructureCuboid {
            Objects.requireNonNull(bounds, "structure cuboid bounds");
            material = requireText(material, "structure cuboid material");
        }
    }

    public record StructureSign(Position position, String facing, List<String> lines) {
        public StructureSign {
            Objects.requireNonNull(position, "structure sign position");
            facing = requireText(facing, "structure sign facing");
            lines = List.copyOf(lines);
            if (lines.isEmpty() || lines.size() > 4) throw new IllegalArgumentException("structure sign requires 1..4 lines");
        }
    }

    public record StructureBlueprint(List<StructureBlock> blocks, List<StructureCuboid> cuboids,
                                     List<StructureSign> signs) {
        public StructureBlueprint {
            blocks = List.copyOf(blocks);
            cuboids = List.copyOf(cuboids);
            signs = List.copyOf(signs);
        }
    }

    public sealed interface MechanicDefinition permits PadMechanic, DestructibleTarget,
            NumericKeypad, LogicAnswer, Escort, DynamicPartyPads, ClueRegions,
            OrderedInput, ChoiceInput, ToggleInput {
        String id();
        String type();
    }

    public record PadMechanic(String id, String type, List<Position> pads, int requiredCount,
                              boolean latch, boolean allowSequentialSolo, String indicatorMaterial)
            implements MechanicDefinition {
        public PadMechanic {
            id = requireText(id, "mechanic.id");
            type = requireText(type, "mechanic.type");
            pads = List.copyOf(pads);
            indicatorMaterial = requireText(indicatorMaterial, "indicatorMaterial");
        }
    }

    public record DestructibleTarget(String id, String type, String material, Position position, String interaction)
            implements MechanicDefinition {
        public DestructibleTarget {
            id = requireText(id, "mechanic.id");
            type = requireText(type, "mechanic.type");
            material = requireText(material, "material");
            Objects.requireNonNull(position, "position");
            interaction = requireText(interaction, "interaction");
        }
    }

    public record DigitButton(int digit, Position position) {
        public DigitButton { Objects.requireNonNull(position, "position"); }
    }

    public record NumericKeypad(String id, String type, int answer, int minDigits, int maxDigits,
                                String wrongAnswer, List<DigitButton> digitButtons,
                                Position submitButton, Position clearButton) implements MechanicDefinition {
        public NumericKeypad {
            id = requireText(id, "mechanic.id");
            type = requireText(type, "mechanic.type");
            wrongAnswer = requireText(wrongAnswer, "wrongAnswer");
            digitButtons = List.copyOf(digitButtons);
            Objects.requireNonNull(submitButton, "submitButton");
            Objects.requireNonNull(clearButton, "clearButton");
        }
    }

    public record LogicAnswer(String id, String type, String question, List<BookPage> pages,
                              String answerFormat, List<String> answers, int cooldownSeconds,
                              int difficulty, String inspiration, String solutionExplanation,
                              List<String> wrongAnswerSamples, String submissionMode,
                              List<String> requires) implements MechanicDefinition {
        public LogicAnswer {
            id = requireText(id, "mechanic.id");
            type = requireText(type, "mechanic.type");
            question = requireText(question, "question");
            pages = List.copyOf(pages);
            if (pages.isEmpty()) throw new IllegalArgumentException("logic answer pages must not be empty");
            answerFormat = requireText(answerFormat, "answerFormat");
            answers = List.copyOf(answers);
            if (answers.isEmpty()) throw new IllegalArgumentException("logic answer requires accepted answers");
            if (cooldownSeconds < 3 || cooldownSeconds > 60) {
                throw new IllegalArgumentException("cooldownSeconds must be 3..60");
            }
            if (difficulty < 1 || difficulty > 5) throw new IllegalArgumentException("difficulty must be 1..5");
            inspiration = requireText(inspiration, "inspiration");
            solutionExplanation = requireText(solutionExplanation, "solutionExplanation");
            wrongAnswerSamples = List.copyOf(wrongAnswerSamples);
            if (wrongAnswerSamples.size() < 2) {
                throw new IllegalArgumentException("at least two wrongAnswerSamples are required");
            }
            submissionMode = requireText(submissionMode, "submissionMode");
            if (!submissionMode.equals("CHAT") && !submissionMode.equals("DEVICE_ONLY")) {
                throw new IllegalArgumentException("submissionMode must be CHAT or DEVICE_ONLY");
            }
            requires = List.copyOf(requires);
        }

        public boolean chatSubmissionEnabled() { return submissionMode.equals("CHAT"); }
    }

    public record Control(String id, String token, String label, String activation, Position position,
                          String material, Optional<Position> indicator) {
        public Control {
            id = requireText(id, "control.id");
            token = Objects.requireNonNull(token, "control.token");
            label = requireText(label, "control.label");
            activation = requireText(activation, "control.activation");
            if (!activation.equals("STEP") && !activation.equals("CLICK")) {
                throw new IllegalArgumentException("control.activation must be STEP or CLICK");
            }
            Objects.requireNonNull(position, "control.position");
            material = requireText(material, "control.material");
            indicator = Objects.requireNonNull(indicator, "control.indicator");
        }
    }

    public record ExpectedStep(String controlId, String display) {
        public ExpectedStep {
            controlId = requireText(controlId, "expected.control");
            display = Objects.requireNonNull(display, "expected.display");
        }
    }

    public record ClueRegion(String id, Bounds bounds, String message, Optional<String> sound, float pitch) {
        public ClueRegion {
            id = requireText(id, "clue.id");
            Objects.requireNonNull(bounds, "clue.bounds");
            message = requireText(message, "clue.message");
            sound = Objects.requireNonNull(sound, "clue.sound");
            if (pitch < 0.5f || pitch > 2.0f) throw new IllegalArgumentException("clue.pitch must be 0.5..2.0");
        }
    }

    public record ClueRegions(String id, String type, List<ClueRegion> regions)
            implements MechanicDefinition {
        public ClueRegions {
            id = requireText(id, "mechanic.id");
            type = requireText(type, "mechanic.type");
            regions = List.copyOf(regions);
            if (regions.isEmpty()) throw new IllegalArgumentException("clue regions must not be empty");
        }
    }

    public record OrderedInput(String id, String type, List<Control> controls, List<ExpectedStep> expected,
                               List<Integer> groups, int operatorLockSeconds, String resultText,
                               Optional<Position> clearButton) implements MechanicDefinition {
        public OrderedInput {
            id = requireText(id, "mechanic.id");
            type = requireText(type, "mechanic.type");
            controls = List.copyOf(controls);
            expected = List.copyOf(expected);
            groups = List.copyOf(groups);
            if (controls.isEmpty() || expected.isEmpty()) throw new IllegalArgumentException("ordered input requires controls and expected steps");
            if (operatorLockSeconds < 1 || operatorLockSeconds > 60) throw new IllegalArgumentException("operatorLockSeconds must be 1..60");
            resultText = requireText(resultText, "resultText");
            clearButton = Objects.requireNonNull(clearButton, "clearButton");
        }
    }

    public record ChoiceInput(String id, String type, List<Control> controls, String correctControl,
                              int operatorLockSeconds, String resultText) implements MechanicDefinition {
        public ChoiceInput {
            id = requireText(id, "mechanic.id");
            type = requireText(type, "mechanic.type");
            controls = List.copyOf(controls);
            if (controls.size() < 2) throw new IllegalArgumentException("choice input requires at least two controls");
            correctControl = requireText(correctControl, "correctControl");
            if (operatorLockSeconds < 1 || operatorLockSeconds > 60) throw new IllegalArgumentException("operatorLockSeconds must be 1..60");
            resultText = requireText(resultText, "resultText");
        }
    }

    public record ToggleInput(String id, String type, List<Control> controls, List<String> expectedActive,
                              int maxSelections, int operatorLockSeconds, Position submitButton,
                              Position clearButton, String resultText) implements MechanicDefinition {
        public ToggleInput {
            id = requireText(id, "mechanic.id");
            type = requireText(type, "mechanic.type");
            controls = List.copyOf(controls);
            expectedActive = List.copyOf(expectedActive);
            if (controls.isEmpty() || expectedActive.isEmpty()) throw new IllegalArgumentException("toggle input requires controls and expectedActive");
            if (maxSelections < expectedActive.size() || maxSelections > controls.size()) throw new IllegalArgumentException("invalid maxSelections");
            if (operatorLockSeconds < 1 || operatorLockSeconds > 60) throw new IllegalArgumentException("operatorLockSeconds must be 1..60");
            Objects.requireNonNull(submitButton, "submitButton");
            Objects.requireNonNull(clearButton, "clearButton");
            resultText = requireText(resultText, "resultText");
        }
    }

    public record EscortEntity(String entityType, String customName, Position spawn, boolean invulnerable) {
        public EscortEntity {
            entityType = requireText(entityType, "entityType");
            customName = requireText(customName, "customName");
            Objects.requireNonNull(spawn, "spawn");
        }
    }

    public record Escort(String id, String type, EscortEntity entity, List<Position> checkpoints,
                         Position destination, double proximityRadius, double movementSpeed,
                         String gateMode, boolean failureIfUngatedCheckpointReached,
                         String failurePolicy, String completionAt) implements MechanicDefinition {
        public Escort {
            id = requireText(id, "mechanic.id");
            type = requireText(type, "mechanic.type");
            Objects.requireNonNull(entity, "entity");
            checkpoints = List.copyOf(checkpoints);
            Objects.requireNonNull(destination, "destination");
            gateMode = requireText(gateMode, "gateMode");
            failurePolicy = requireText(failurePolicy, "failurePolicy");
            completionAt = requireText(completionAt, "completionAt");
        }
    }

    public record IndexedPad(int partyIndex, Position position) {
        public IndexedPad { Objects.requireNonNull(position, "position"); }
    }

    public record DynamicPartyPads(String id, String type, String activePadCount, List<IndexedPad> playerPads,
                                   boolean latch, DestructibleTarget target, String completionMode)
            implements MechanicDefinition {
        public DynamicPartyPads {
            id = requireText(id, "mechanic.id");
            type = requireText(type, "mechanic.type");
            activePadCount = requireText(activePadCount, "activePadCount");
            playerPads = List.copyOf(playerPads);
            Objects.requireNonNull(target, "target");
            completionMode = requireText(completionMode, "completionMode");
        }
    }

    public record RoomDefinition(String id, int sequence, int originalStage, String title,
                                 Bounds buildBounds, Bounds playBounds, Position spawn, Position checkpoint,
                                 Optional<VisualBlueprint> visual, Optional<StructureBlueprint> structure,
                                 List<MechanicDefinition> mechanics, List<Hint> hints,
                                 String intro, String completion, String failure) {
        public RoomDefinition {
            id = requireText(id, "room.id");
            if (sequence < 1) throw new IllegalArgumentException("Room sequence must be positive");
            title = requireText(title, "room.title");
            Objects.requireNonNull(buildBounds, "buildBounds");
            Objects.requireNonNull(playBounds, "playBounds");
            Objects.requireNonNull(spawn, "spawn");
            Objects.requireNonNull(checkpoint, "checkpoint");
            visual = Objects.requireNonNull(visual, "visual");
            structure = Objects.requireNonNull(structure, "structure");
            mechanics = List.copyOf(mechanics);
            hints = List.copyOf(hints);
            intro = requireText(intro, "messages.intro");
            completion = requireText(completion, "messages.completion");
            failure = requireText(failure, "messages.failure");
        }

        public RoomDefinition(String id, int sequence, int originalStage, String title,
                              Bounds buildBounds, Bounds playBounds, Position spawn, Position checkpoint,
                              Optional<VisualBlueprint> visual, List<MechanicDefinition> mechanics,
                              List<Hint> hints, String intro, String completion, String failure) {
            this(id, sequence, originalStage, title, buildBounds, playBounds, spawn, checkpoint,
                    visual, Optional.empty(), mechanics, hints, intro, completion, failure);
        }
    }

    private static String requireText(String value, String path) {
        Objects.requireNonNull(value, path);
        String result = value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException(path + " must not be blank");
        return result;
    }
}
