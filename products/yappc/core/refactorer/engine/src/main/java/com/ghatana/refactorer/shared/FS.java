/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/**

 * @doc.type class

 * @doc.purpose Handles fs operations

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public final class FS {
    private FS() {}

    public static void atomicWrite(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), ".tmp", ".write");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        Files.move(
                tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
