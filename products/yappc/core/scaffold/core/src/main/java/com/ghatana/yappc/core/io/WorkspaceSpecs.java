package com.ghatana.yappc.core.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ghatana.yappc.core.model.WorkspaceSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * WorkspaceSpecs component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose WorkspaceSpecs component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public final class WorkspaceSpecs {
    private static final ObjectMapper MAPPER =
            JsonUtils.getDefaultMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private WorkspaceSpecs() {}

    public static Optional<WorkspaceSpec> read(Path location) {
        if (!Files.exists(location)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(location.toFile(), WorkspaceSpec.class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read workspace spec from " + location, e);
        }
    }

    public static void write(Path location, WorkspaceSpec spec) {
        try {
            Files.createDirectories(location.getParent());
            MAPPER.writeValue(location.toFile(), spec);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write workspace spec", e);
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
