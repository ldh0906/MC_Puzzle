package dev.mcpuzzle.paper.resourcepack;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PuzzleItemModelTest {
    @Test
    void modelDataValuesAreUniqueAndContiguous() {
        int[] values = Arrays.stream(PuzzleItemModel.values()).mapToInt(PuzzleItemModel::customModelData).sorted().toArray();
        assertEquals(16, values.length);
        for (int index = 0; index < values.length; index++) {
            assertEquals(12001 + index, values[index]);
        }
    }
}
