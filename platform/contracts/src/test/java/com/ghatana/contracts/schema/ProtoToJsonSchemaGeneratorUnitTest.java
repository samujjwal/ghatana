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

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.DescriptorProtos.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ProtoToJsonSchemaGenerator Basic Tests")
class ProtoToJsonSchemaGeneratorUnitTest {

    private ProtoToJsonSchemaGenerator generator;
    private ObjectMapper mapper;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        this.mapper = new ObjectMapper();
        this.generator = new ProtoToJsonSchemaGenerator();
    }

    @Test
    @DisplayName("should create generator with default settings")
    void testCreateGenerator() {
        // When
        ProtoToJsonSchemaGenerator gen = new ProtoToJsonSchemaGenerator();

        // Then
        assertNotNull(gen, "Generator should be created");
    }

    @Test
    @DisplayName("should generate schema for simple message")
    void testSimpleMessageSchema() throws Exception {
        // Given
        String messageName = "test.SimpleMessage";

        // When
        generateSchema(messageName);

        // Then
        Path schemaFile = tempDir.resolve("output/SimpleMessage.schema.json");
        assertTrue(Files.exists(schemaFile), "Schema file should exist: " + schemaFile);

        // Print the generated schema for debugging
        String schemaContent = Files.readString(schemaFile);
        System.out.println("Generated Schema:\n" + schemaContent);

        JsonNode schema = mapper.readTree(schemaFile.toFile());

        // Basic schema validation
        assertTrue(schema.has("$schema"), "Schema should have $schema field");
        assertTrue(schema.has("$id"), "Schema should have $id field");
        assertTrue(schema.has("title"), "Schema should have title field");

        // Check the title
        String title = schema.get("title").asText();
        assertEquals("SimpleMessage", title, "Title should be 'SimpleMessage'");

        // Check that we have a $ref to the definition
        assertTrue(schema.has("$ref"), "Schema should have $ref field");
        String ref = schema.get("$ref").asText();

        // The reference should point to a definition. It can be an internal anchor ("#/")
        // or an external file with an anchor (e.g.,
        // "bundle.schema.json#/$defs/test.SimpleMessage").
        assertTrue(
                ref.contains("#/"),
                "$ref should contain an anchor to a definition (internal or external), but was: "
                        + ref);

        // The schema should have a definitions section at the root level
        // or in a separate file. Since we don't see the $defs in the main schema,
        // we'll need to check if there's a separate definitions file.

        // Check for a separate definitions file
        Path definitionsFile = tempDir.resolve("output/definitions.schema.json");
        if (Files.exists(definitionsFile)) {
            // If we have a separate definitions file, load it
            String definitionsContent = Files.readString(definitionsFile);
            System.out.println("Definitions Schema:\n" + definitionsContent);
            JsonNode defs = mapper.readTree(definitionsFile.toFile());

            // The definition should be in the root or in a $defs section
            if (defs.has("$defs")) {
                JsonNode defsSection = defs.get("$defs");
                assertTrue(
                        defsSection.has("test.SimpleMessage"),
                        "Should have definition for test.SimpleMessage in $defs");
                verifyMessageDefinition(defsSection.get("test.SimpleMessage"));
            } else if (defs.has("test.SimpleMessage")) {
                verifyMessageDefinition(defs.get("test.SimpleMessage"));
            } else {
                fail("Could not find message definition in the definitions file");
            }
        } else {
            // If no separate definitions file, the definition should be in the main schema
            // or the reference might be to an external schema
            System.out.println(
                    "No separate definitions file found. Checking for inline definitions...");

            // List all files in the output directory for debugging
            System.out.println("Files in output directory:");
            try (var files = Files.list(tempDir.resolve("output"))) {
                files.forEach(file -> System.out.println("  " + file));
            }

            // Check the bundle.schema.json file for definitions
            Path bundleFile = tempDir.resolve("output/bundle.schema.json");
            if (Files.exists(bundleFile)) {
                System.out.println("Found bundle file, checking for definitions...");
                String bundleContent = Files.readString(bundleFile);
                System.out.println(
                        "Bundle file content (first 1000 chars):\n"
                                + (bundleContent.length() > 1000
                                        ? bundleContent.substring(0, 1000) + "..."
                                        : bundleContent));

                JsonNode bundleNode = mapper.readTree(bundleFile.toFile());

                // The definitions might be at the root level or in a $defs section
                if (bundleNode.has("$defs")) {
                    JsonNode defs = bundleNode.get("$defs");
                    String defKey = "test.SimpleMessage";
                    if (defs.has(defKey)) {
                        System.out.println("Found definition in $defs section of bundle file");
                        verifyMessageDefinition(defs.get(defKey));
                        return;
                    }
                } else if (bundleNode.has("test.SimpleMessage")) {
                    System.out.println("Found definition at root of bundle file");
                    verifyMessageDefinition(bundleNode.get("test.SimpleMessage"));
                    return;
                }

                // If we get here, the definition wasn't found in the expected location
                System.out.println(
                        "Definition not found in bundle file. Full bundle content (first 2000"
                                + " chars):\n"
                                + (bundleContent.length() > 2000
                                        ? bundleContent.substring(0, 2000) + "..."
                                        : bundleContent));
            } else {
                System.out.println("Bundle file not found at: " + bundleFile);
            }

            // If we get here, we couldn't find the definition in the bundle file
            // Try to find it in any other JSON files in the output directory
            try (var files = Files.list(tempDir.resolve("output"))) {
                for (var file : files.collect(Collectors.toList())) {
                    if (file.toString().endsWith(".json")
                            && !file.toString().endsWith("bundle.schema.json")) {
                        System.out.println("Checking file for definitions: " + file);
                        String fileContent = Files.readString(file);
                        JsonNode fileNode = mapper.readTree(fileContent);

                        // Check if this file contains our definition
                        if (fileNode.has("test.SimpleMessage")) {
                            System.out.println("Found definition in file: " + file);
                            verifyMessageDefinition(fileNode.get("test.SimpleMessage"));
                            return;
                        }

                        // Check for $defs section
                        if (fileNode.has("$defs")) {
                            JsonNode defs = fileNode.get("$defs");
                            if (defs.has("test.SimpleMessage")) {
                                System.out.println("Found definition in $defs of file: " + file);
                                verifyMessageDefinition(defs.get("test.SimpleMessage"));
                                return;
                            }
                        }
                    }
                }
            }

            // If we get here, we couldn't find the definition anywhere
            fail(
                    "Could not find the message definition in any of the generated files. Checked"
                            + " bundle.schema.json and all other JSON files in the output directory.");
        }
    }

    private void verifyMessageDefinition(JsonNode messageDef) {
        assertNotNull(messageDef, "Message definition should not be null");

        // Check properties in the message definition
        assertTrue(messageDef.has("properties"), "Message definition should have properties");
        JsonNode properties = messageDef.get("properties");

        // Check basic field types with more flexible assertions
        // Note: The generated schema uses camelCase field names
        assertFieldType(properties, "stringField", "string");
        assertFieldType(properties, "int32Field", "integer");
        assertFieldType(properties, "int64Field", "integer", "int64");
    }

    private void generateSchema(String messageName) throws Exception {
        // Create a minimal descriptor set
        Path descriptorFile = tempDir.resolve("descriptor.pb");
        createSimpleDescriptor(descriptorFile);

        // Create the output directory
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        // Build the config
        String[] args = {
            "--descriptorSet=" + descriptorFile,
            "--outDir=" + outputDir,
            "--messages=" + messageName
        };

        ProtoToJsonSchemaGenerator.Config config = ProtoToJsonSchemaGenerator.Config.parse(args);

        // Generate the schema
        generator.run(config);
    }

    private void createSimpleDescriptor(Path outputFile) throws IOException {
        // Create a minimal FileDescriptorSet with a simple message
        FileDescriptorSet fileDescriptorSet =
                FileDescriptorSet.newBuilder()
                        .addFile(
                                FileDescriptorProto.newBuilder()
                                        .setName("test_simple.proto")
                                        .setPackage("test")
                                        .addMessageType(
                                                DescriptorProto.newBuilder()
                                                        .setName("SimpleMessage")
                                                        .addField(
                                                                FieldDescriptorProto.newBuilder()
                                                                        .setName("string_field")
                                                                        .setNumber(1)
                                                                        .setType(
                                                                                FieldDescriptorProto
                                                                                        .Type
                                                                                        .TYPE_STRING)
                                                                        .build())
                                                        .addField(
                                                                FieldDescriptorProto.newBuilder()
                                                                        .setName("int32_field")
                                                                        .setNumber(2)
                                                                        .setType(
                                                                                FieldDescriptorProto
                                                                                        .Type
                                                                                        .TYPE_INT32)
                                                                        .build())
                                                        .addField(
                                                                FieldDescriptorProto.newBuilder()
                                                                        .setName("int64_field")
                                                                        .setNumber(3)
                                                                        .setType(
                                                                                FieldDescriptorProto
                                                                                        .Type
                                                                                        .TYPE_INT64)
                                                                        .build())))
                        .build();

        // Write the descriptor set to a file
        try (var out = Files.newOutputStream(outputFile)) {
            fileDescriptorSet.writeTo(out);
        }
    }

    private void assertFieldType(JsonNode properties, String fieldName, String expectedType) {
        assertFieldType(properties, fieldName, expectedType, null);
    }

    private void assertFieldType(
            JsonNode properties, String fieldName, String expectedType, String expectedFormat) {
        assertTrue(properties.has(fieldName), "Should have field: " + fieldName);
        JsonNode field = properties.get(fieldName);

        if (expectedType != null) {
            assertTrue(field.has("type"), "Field should have type: " + fieldName);
            assertEquals(
                    expectedType,
                    field.get("type").asText(),
                    "Field type should match for: " + fieldName);
        }

        if (expectedFormat != null) {
            assertTrue(field.has("format"), "Field should have format: " + fieldName);
            assertEquals(
                    expectedFormat,
                    field.get("format").asText(),
                    "Field format should match for: " + fieldName);
        }
    }
}
