package com.ghatana.refactorer.testutil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local test utility stub mirroring refactorer-core's TestUtils.
 * Provides helpers needed by engine test classes.

 * @doc.type class
 * @doc.purpose Handles test utils operations
 * @doc.layer core
 * @doc.pattern Utility
*/
public class CoreTestUtils {

    public static Path createTempDirectory(String prefix) throws IOException { // GH-90000
        return Files.createTempDirectory(prefix); // GH-90000
    }

    public static Path createTempFile(String prefix, String suffix) throws IOException { // GH-90000
        return Files.createTempFile(prefix, suffix); // GH-90000
    }

    public static Path createTempFileWithContent(String prefix, String suffix, String content) // GH-90000
            throws IOException {
        Path tempFile = createTempFile(prefix, suffix); // GH-90000
        Files.writeString(tempFile, content); // GH-90000
        return tempFile;
    }

    /**
     * Checks if a command is available in the system PATH.
     */
    public static boolean isCommandAvailable(String command, String... args) { // GH-90000
        try {
            String[] commandWithArgs = new String[args.length + 1];
            commandWithArgs[0] = command;
            System.arraycopy(args, 0, commandWithArgs, 1, args.length); // GH-90000

            ProcessBuilder processBuilder = new ProcessBuilder(commandWithArgs); // GH-90000
            processBuilder.redirectErrorStream(true); // GH-90000
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD); // GH-90000
            Process process = processBuilder.start(); // GH-90000
            return process.waitFor() == 0; // GH-90000
        } catch (IOException | InterruptedException e) { // GH-90000
            return false;
        }
    }
}
