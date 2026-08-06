package dev.mcpuzzle.maptool;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Command-line entry point for the SCX-analysis to JSONC draft importer. */
public final class MapToolMain {
    private MapToolMain() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 2) {
            printUsage(err);
            return 2;
        }

        Path source;
        Path output;
        try {
            source = Path.of(args[0]).toAbsolutePath().normalize();
            output = Path.of(args[1]).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            err.println("경로 형식이 올바르지 않습니다: " + exception.getInput());
            return 2;
        }
        if (!Files.isRegularFile(source)) {
            err.println("분석 JSON 파일을 찾을 수 없습니다: " + source);
            return 2;
        }

        try {
            AnalysisMapImporter.ImportResult result = new AnalysisMapImporter().importFile(source, output);
            out.println("JSONC 맵 팩 초안 생성 완료: " + result.output());
            out.println("변환된 원본 스테이지: " + result.stageNumbers());
            if (result.backup() != null) {
                out.println("기존 파일 백업: " + result.backup());
            }
            return 0;
        } catch (MapImportException exception) {
            err.println("맵 팩 변환 실패: " + exception.getMessage());
            return 1;
        }
    }

    private static void printUsage(PrintStream stream) {
        stream.println("사용법: mcpuzzle-map-tool <map_data.json> <출력 map.jsonc>");
    }
}
