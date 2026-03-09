package com.ghatana.refactorer.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**

 * @doc.type class

 * @doc.purpose Handles parser golden test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class ParserGoldenTest {
    private static final Path FIXTURES_DIR = Paths.get("src/test/resources/fixtures");

    static Stream<Arguments> provideFixtures() {
        return Stream.of(
                Arguments.of("java-npe.txt", new JavaStackTraceParser()),
                Arguments.of("python-import-error.txt", new PyStackTraceParser()),
                Arguments.of("node-type-error.txt", new NodeStackTraceParser()),
                Arguments.of("rust-panic.txt", new RustStackTraceParser()),
                Arguments.of("go-panic.txt", new GoStackTraceParser()));
    }

    @ParameterizedTest
    @MethodSource("provideFixtures")
    void testParser(String fixture, StackTraceParser parser) throws IOException {
        Path path = FIXTURES_DIR.resolve(fixture);
        String content = Files.readString(path);

        System.out.println("\n=== Testing " + fixture + " ===");
        System.out.println("Raw content (first 5 lines):");
        content.lines().limit(5).forEach(System.out::println);
        if (content.lines().count() > 5) System.out.println("...");

        List<StackTraceParser.TraceFrame> frames = parser.parse(content);

        System.out.println("Parsed frames: " + frames.size());
        for (int i = 0; i < Math.min(frames.size(), 5); i++) {
            var f = frames.get(i);
            System.out.printf("  %d: %s:%d - %s%n", i, f.file(), f.line(), f.function());
        }

        assertNotNull(frames, "Parser returned null");
        assertFalse(frames.isEmpty(), "No frames parsed from " + fixture);

        // Basic validation of first frame
        var first = frames.get(0);
        assertNotNull(first.file(), "Missing file in first frame");
        if (!first.file().equals("<panic>")) { // Skip line check for panic frames
            assertTrue(first.line() > 0, "Invalid line number in first frame");
        }
        assertNotNull(first.function(), "Missing function in first frame");
    }
}
