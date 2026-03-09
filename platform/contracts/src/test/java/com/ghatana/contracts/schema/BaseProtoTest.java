/*
 * Copyright (c) 2025 Ghatana Platforms, Inc. All rights reserved.
 *
 * PROPRIETARY/CONFIDENTIAL. Use is subject to the terms of a separate
 * license agreement between you and Ghatana Platforms, Inc. You may not
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of this software, in whole or in part, except as expressly
 * permitted under the applicable written license agreement.
 *
 * Unauthorized use, reproduction, or distribution of this software, or any
 * portion of it, may result in severe civil and criminal penalties, and
 * will be prosecuted to the maximum extent possible under the law.
 */
package com.ghatana.contracts.schema;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

/** Base test class with common test utilities for Protocol Buffer tests. */
public abstract class BaseProtoTest {

    @TempDir protected static Path tempDir;

    protected static final JsonFormat.Printer JSON_PRINTER =
            JsonFormat.printer().omittingInsignificantWhitespace();

    protected static final JsonFormat.Parser JSON_PARSER =
            JsonFormat.parser().ignoringUnknownFields();

    @BeforeAll
    static void setup() {
        // Set up any test-wide configuration here
    }

    /** Helper method to create a temporary file with the given content. */
    protected static File createTempFile(String prefix, String suffix, String content)
            throws IOException {
        File tempFile = Files.createTempFile(tempDir, prefix, suffix).toFile();
        if (content != null) {
            Files.writeString(tempFile.toPath(), content);
        }
        return tempFile;
    }

    /** Helper method to write a protocol buffer message to a file. */
    protected static <T extends Message> void writeMessageToFile(File file, T message)
            throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            message.writeTo(output);
        }
    }

    /** Converts a protocol buffer message to a JSON string. */
    protected static <T extends Message> String toJson(T message) throws IOException {
        return JSON_PRINTER.print(message);
    }
}
