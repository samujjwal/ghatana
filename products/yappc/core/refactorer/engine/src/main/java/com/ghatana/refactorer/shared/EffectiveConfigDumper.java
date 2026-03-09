/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/**

 * @doc.type class

 * @doc.purpose Handles effective config dumper operations

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public final class EffectiveConfigDumper {
    private EffectiveConfigDumper() {}

    public static void dump(Path outDir, PolyfixConfig cfg) {
        try {
            Json.writePretty(
                    outDir.resolve("effective-config.json"),
                    new Record(cfg, Instant.now().toString()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record Record(PolyfixConfig config, String ts) {}
}
