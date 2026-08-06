package dev.mcpuzzle.maptool;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapToolMainTest {
    @Test
    void printsUsageForWrongArgumentCount() {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        int exitCode = MapToolMain.run(new String[0], System.out, new PrintStream(errors, true, StandardCharsets.UTF_8));

        assertEquals(2, exitCode);
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("<map_data.json> <출력 map.jsonc>"));
    }
}
