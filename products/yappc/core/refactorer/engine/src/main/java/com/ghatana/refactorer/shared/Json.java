/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**

 * @doc.type class

 * @doc.purpose Handles json operations

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public final class Json {
    private static final ObjectMapper M =
            JsonUtils.getDefaultMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private Json() {}

    public static <T> T read(Path p, Class<T> cls) throws IOException {
        return M.readValue(Files.readAllBytes(p), cls);
    }

    public static void writePretty(Path p, Object o) throws IOException {
        Files.createDirectories(p.getParent());
        M.writeValue(p.toFile(), o);
    }
}
