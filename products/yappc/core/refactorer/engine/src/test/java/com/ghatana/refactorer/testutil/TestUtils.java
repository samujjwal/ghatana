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
public class TestUtils {

    public static Path createTempDirectory(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }

    public static Path createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(prefix, suffix);
    }

    public static Path createTempFileWithContent(String prefix, String suffix, String content)
            throws IOException {
        Path tempFile = createTempFile(prefix, suffix);
        Files.writeString(tempFile, content);
        return tempFile;
    }

    /**
     * Checks if a command is available in the system PATH.
     */
    public static boolean isCommandAvailable(String command, String... args) {
        try {
            String[] commandWithArgs = new String[args.length + 1];
            commandWithArgs[0] = command;
            System.arraycopy(args, 0, commandWithArgs, 1, args.length);

            ProcessBuilder processBuilder = new ProcessBuilder(commandWithArgs);
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = processBuilder.start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
