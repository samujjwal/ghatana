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

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.DescriptorProtos.*;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link ProtoToJsonSchemaGenerator}. */
@DisplayName("ProtoToJsonSchemaGenerator Tests")
class ProtoToJsonSchemaGeneratorTest extends BaseProtoTest {

    private ProtoToJsonSchemaGenerator generator;
    private File outputDir;
    private File descriptorFile;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        this.outputDir = tempDir.resolve("schemas").toFile();
        this.descriptorFile = tempDir.resolve("test.desc").toFile();

        // Create an empty descriptor set for testing
        FileDescriptorSet.newBuilder().build().writeTo(new FileOutputStream(descriptorFile));

        this.generator = new ProtoToJsonSchemaGenerator();
    }

    /**
     * Helper method to find a file in a directory by name pattern.
     *
     * @param directory The directory to search in
     * @param namePattern The pattern to match against file names
     * @return The first matching file, or null if not found
     */
    private File findFile(File directory, String namePattern) {
        File[] files =
                directory.listFiles(
                        (dir, name) -> name.contains(namePattern) && name.endsWith(".json"));
        return (files != null && files.length > 0) ? files[0] : null;
    }

    @Test
    @DisplayName("should create output directory if it doesn't exist")
    void shouldCreateOutputDirectory() throws Exception {
        // Given
        File nonExistentDir = new File(outputDir, "nonexistent");

        // When
        generator.run(
                ProtoToJsonSchemaGenerator.Config.parse(
                        new String[] {
                            "--descriptorSet=" + descriptorFile.getAbsolutePath(),
                            "--outDir=" + nonExistentDir.getAbsolutePath(),
                            "--messages=",
                            "--bundleName=bundle.schema.json"
                        }));

        // Then
        assertThat(nonExistentDir).as("Output directory should be created").exists().isDirectory();

        // Clean up
        nonExistentDir.delete();
    }

    @Test
    @DisplayName("should throw exception when descriptor file doesn't exist")
    void shouldThrowExceptionWhenDescriptorFileMissing() {
        // Given
        File nonExistentFile = new File("nonexistent.desc");

        // When/Then
        assertThatThrownBy(
                        () ->
                                generator.run(
                                        ProtoToJsonSchemaGenerator.Config.parse(
                                                new String[] {
                                                    "--descriptorSet=nonexistent.desc",
                                                    "--outDir=" + outputDir.getAbsolutePath(),
                                                    "--messages=",
                                                    "--bundleName=bundle.schema.json"
                                                })))
                .isInstanceOf(java.nio.file.NoSuchFileException.class);
    }

    @Test
    @DisplayName("should generate schema files in the output directory")
    void shouldGenerateSchemaFiles() throws Exception {
        // Given
        // Create a simple descriptor set with one message type
        FileDescriptorProto fileDesc =
                FileDescriptorProto.newBuilder()
                        .setName("test.proto")
                        .setPackage("test")
                        .addMessageType(
                                DescriptorProto.newBuilder()
                                        .setName("TestMessage")
                                        .addField(
                                                FieldDescriptorProto.newBuilder()
                                                        .setName("test_field")
                                                        .setType(
                                                                FieldDescriptorProto.Type
                                                                        .TYPE_STRING)
                                                        .setNumber(1)))
                        .build();

        FileDescriptorSet descriptorSet = FileDescriptorSet.newBuilder().addFile(fileDesc).build();

        // Write the descriptor set to a file
        try (FileOutputStream out = new FileOutputStream(descriptorFile)) {
            descriptorSet.writeTo(out);
        }

        // When
        generator.run(
                ProtoToJsonSchemaGenerator.Config.parse(
                        new String[] {
                            "--descriptorSet=" + descriptorFile.getAbsolutePath(),
                            "--outDir=" + outputDir.getAbsolutePath(),
                            "--messages=test.TestMessage",
                            "--bundleName=bundle.schema.json"
                        }));

        // Then - Find all generated JSON files
        File[] schemaFiles = outputDir.listFiles((dir, name) -> name.endsWith(".json"));
        assertThat(schemaFiles)
                .as("Should generate schema files")
                .isNotNull()
                .hasSizeGreaterThanOrEqualTo(1); // At least the bundle file should exist

        // Find the bundle file
        File bundleFile = findFile(outputDir, "bundle.schema.json");
        assertThat(bundleFile)
                .as("Bundle file should exist in " + outputDir.getAbsolutePath())
                .exists()
                .isFile();

        // Find the message schema file (it might have a different naming pattern)
        File messageFile = findFile(outputDir, "TestMessage.schema.json");
        if (messageFile == null) {
            // Try alternative naming pattern
            messageFile = findFile(outputDir, "test.TestMessage.schema.json");
        }

        assertThat(messageFile)
                .as("Message schema file should exist in " + outputDir.getAbsolutePath())
                .exists()
                .isFile();

        // Verify the bundle file contains a reference to the message schema
        ObjectMapper mapper = new ObjectMapper();
        JsonNode bundleNode = mapper.readTree(bundleFile);

        // Check for either $ref or $defs in the bundle
        boolean hasRefOrDefs =
                bundleNode.has("$ref")
                        || (bundleNode.has("$defs")
                                && bundleNode.get("$defs").has("test.TestMessage"));

        assertThat(hasRefOrDefs).as("Bundle should have $ref or $defs for message schema").isTrue();
    }

    @Test
    @DisplayName("should throw exception when output directory is not writable")
    void shouldThrowExceptionWhenOutputDirNotWritable() {
        // Given
        File readOnlyDir = new File(outputDir, "readonly");
        boolean created = readOnlyDir.mkdirs() && readOnlyDir.setWritable(false, false);

        assumeThat(created).isTrue();

        try {
            // When/Then - The exact exception might vary by OS, but it should throw some kind of
            // exception
            assertThatThrownBy(
                            () ->
                                    generator.run(
                                            ProtoToJsonSchemaGenerator.Config.parse(
                                                    new String[] {
                                                        "--descriptorSet="
                                                                + descriptorFile.getAbsolutePath(),
                                                        "--outDir=" + readOnlyDir.getAbsolutePath(),
                                                        "--messages=invalid.message",
                                                        "--bundleName=bundle.schema.json"
                                                    })))
                    .isInstanceOf(Exception.class);
        } finally {
            // Clean up
            readOnlyDir.setWritable(true);
            readOnlyDir.delete();
        }
    }

    @Test
    @DisplayName("should handle empty message list")
    void shouldHandleEmptyMessageList() throws Exception {
        // When
        generator.run(
                ProtoToJsonSchemaGenerator.Config.parse(
                        new String[] {
                            "--descriptorSet=" + descriptorFile.getAbsolutePath(),
                            "--outDir=" + outputDir.getAbsolutePath(),
                            "--messages=",
                            "--bundleName=bundle.schema.json"
                        }));

        // Then - Should not throw and should create the bundle file
        File bundleFile = new File(outputDir, "bundle.schema.json");
        assertThat(bundleFile).as("Bundle file should exist").exists().isFile();

        // Bundle should be empty
        ObjectMapper mapper = new ObjectMapper();
        JsonNode bundleNode = mapper.readTree(bundleFile);
        assertThat(bundleNode.isObject()).as("Bundle should be a JSON object").isTrue();
    }

    @Test
    @DisplayName("should handle custom bundle name")
    void shouldHandleCustomBundleName() throws Exception {
        // Given
        String customBundleName = "custom-bundle.json";

        // When
        generator.run(
                ProtoToJsonSchemaGenerator.Config.parse(
                        new String[] {
                            "--descriptorSet=" + descriptorFile.getAbsolutePath(),
                            "--outDir=" + outputDir.getAbsolutePath(),
                            "--messages=",
                            "--bundleName=" + customBundleName
                        }));

        // Then
        File bundleFile = new File(outputDir, customBundleName);
        assertThat(bundleFile).as("Custom bundle file should exist").exists().isFile();
    }

    @Test
    @DisplayName("should use default bundle name when not provided")
    void shouldUseDefaultBundleNameWhenNotProvided() throws Exception {
        // Given - Create a descriptor set with a test message
        FileDescriptorProto fileDesc =
                FileDescriptorProto.newBuilder()
                        .setName("test.proto")
                        .setPackage("test")
                        .addMessageType(
                                DescriptorProto.newBuilder()
                                        .setName("TestMessage")
                                        .addField(
                                                FieldDescriptorProto.newBuilder()
                                                        .setName("test_field")
                                                        .setType(
                                                                FieldDescriptorProto.Type
                                                                        .TYPE_STRING)
                                                        .setNumber(1)
                                                        .build())
                                        .build())
                        .build();

        FileDescriptorSet descriptorSet = FileDescriptorSet.newBuilder().addFile(fileDesc).build();

        // Write the descriptor set to a file
        try (FileOutputStream out = new FileOutputStream(descriptorFile)) {
            descriptorSet.writeTo(out);
        }

        // When - Run the generator without specifying bundleName
        generator.run(
                ProtoToJsonSchemaGenerator.Config.parse(
                        new String[] {
                            "--descriptorSet=" + descriptorFile.getAbsolutePath(),
                            "--outDir=" + outputDir.getAbsolutePath(),
                            "--messages=test.TestMessage"
                            // No bundleName parameter - should use default
                        }));

        // Then - Verify the default bundle file was created
        File bundleFile = new File(outputDir, "bundle.schema.json");
        assertThat(bundleFile).as("Default bundle file should exist").exists().isFile();
    }

    @Test
    @DisplayName("should handle message with all field types")
    void shouldHandleMessageWithAllFieldTypes() throws Exception {
        // Given - Create a descriptor set with a message containing all field types
        FileDescriptorProto fileDesc =
                FileDescriptorProto.newBuilder()
                        .setName("all_types.proto")
                        .setPackage("test")
                        .addMessageType(createAllTypesMessage())
                        .build();

        FileDescriptorSet descriptorSet = FileDescriptorSet.newBuilder().addFile(fileDesc).build();

        // Write the descriptor set to a file
        try (FileOutputStream out = new FileOutputStream(descriptorFile)) {
            descriptorSet.writeTo(out);
        }

        // Create output directory if it doesn't exist
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // When - Run the generator with the test message
        generator.run(
                ProtoToJsonSchemaGenerator.Config.parse(
                        new String[] {
                            "--descriptorSet=" + descriptorFile.getAbsolutePath(),
                            "--outDir=" + outputDir.getAbsolutePath(),
                            "--messages=test.AllTypesMessage",
                            "--bundleName=bundle.schema.json"
                        }));

        // Then - Verify the schema was generated without errors
        // The actual file name might vary, so look for any .json file in the output directory
        File[] jsonFiles = outputDir.listFiles((dir, name) -> name.endsWith(".json"));

        assertThat(jsonFiles).as("At least one JSON schema file should be generated").isNotEmpty();

        // Find the schema file for our message
        File schemaFile = null;
        for (File file : jsonFiles) {
            if (file.getName().contains("AllTypesMessage")
                    && !file.getName().equals("bundle.schema.json")) {
                schemaFile = file;
                break;
            }
        }

        assertThat(schemaFile)
                .as(
                        "Schema file for AllTypesMessage should exist in "
                                + outputDir.getAbsolutePath())
                .isNotNull()
                .exists()
                .isFile();

        // Print the actual content of the schema file for debugging
        String schemaContent =
                new String(Files.readAllBytes(schemaFile.toPath()), StandardCharsets.UTF_8);
        System.out.println("=== SCHEMA FILE CONTENT ===");
        System.out.println(schemaContent);
        System.out.println("===========================");

        try {
            // Verify the schema contains expected properties
            ObjectMapper mapper = new ObjectMapper();
            JsonNode schemaNode = mapper.readTree(schemaFile);

            // Check if the schema is an array (in case it's an array of schemas)
            if (schemaNode.isArray()) {
                System.out.println("Schema is an array with " + schemaNode.size() + " elements");
                if (schemaNode.size() > 0) {
                    schemaNode = schemaNode.get(0);
                }
            }

            // The schema uses $ref to reference a definition, so we need to check for that
            if (schemaNode.has("$ref")) {
                // This is a valid schema that references a definition
                String ref = schemaNode.get("$ref").asText();
                System.out.println("Schema references definition: " + ref);

                // Check for other required fields in the schema
                assertThat(schemaNode.has("$schema"))
                        .as(
                                "Schema should have a '$schema' field. Full schema: "
                                        + schemaNode.toPrettyString())
                        .isTrue();

                assertThat(schemaNode.has("$id"))
                        .as(
                                "Schema should have an '$id' field. Full schema: "
                                        + schemaNode.toPrettyString())
                        .isTrue();

                assertThat(schemaNode.has("title"))
                        .as(
                                "Schema should have a 'title' field. Full schema: "
                                        + schemaNode.toPrettyString())
                        .isTrue();

                // The actual properties are in the $defs section, which is in the bundle file
                // So we'll check if the bundle file exists and has the expected content
                File bundleFile = new File(outputDir, "bundle.schema.json");
                assertThat(bundleFile)
                        .as("Bundle file should exist with schema definitions")
                        .exists()
                        .isFile();

                // Check the bundle file content
                JsonNode bundleNode = mapper.readTree(bundleFile);
                assertThat(bundleNode.has("$defs"))
                        .as(
                                "Bundle should have a '$defs' section. Full bundle: "
                                        + bundleNode.toPrettyString())
                        .isTrue();

                JsonNode defs = bundleNode.get("$defs");
                String defKey =
                        ref.substring(
                                ref.lastIndexOf('/')
                                        + 1); // Extract the definition key from the ref

                assertThat(defs.has(defKey))
                        .as(
                                "Bundle should have definition for "
                                        + defKey
                                        + ". Available definitions: "
                                        + defs.fieldNames())
                        .isTrue();

                JsonNode messageDef = defs.get(defKey);
                assertThat(messageDef.has("type"))
                        .as(
                                "Message definition should have a 'type' field. Definition: "
                                        + messageDef.toPrettyString())
                        .isTrue();

                assertThat(messageDef.has("properties"))
                        .as(
                                "Message definition should have a 'properties' field. Definition: "
                                        + messageDef.toPrettyString())
                        .isTrue();

                // Check if properties exist in the message definition
                JsonNode properties = messageDef.get("properties");
                assertThat(properties.fields().hasNext())
                        .as(
                                "Message should have at least one property defined. Properties: "
                                        + properties.toPrettyString())
                        .isTrue();

                // Log available properties for debugging
                System.out.println("Available properties in message definition:");
                properties
                        .fields()
                        .forEachRemaining(entry -> System.out.println("- " + entry.getKey()));

            } else {
                // If not using $ref, check the old structure (for backward compatibility)
                assertThat(schemaNode.has("type"))
                        .as(
                                "Schema should have a 'type' field. Full schema: "
                                        + schemaNode.toPrettyString())
                        .isTrue();

                assertThat(schemaNode.has("properties"))
                        .as(
                                "Schema should have a 'properties' field. Full schema: "
                                        + schemaNode.toPrettyString())
                        .isTrue();

                // Check if properties exist
                JsonNode properties = schemaNode.get("properties");
                assertThat(properties.fields().hasNext())
                        .as(
                                "Schema should have at least one property defined. Full schema: "
                                        + schemaNode.toPrettyString())
                        .isTrue();

                // Log available properties for debugging
                System.out.println("Available properties in schema:");
                properties
                        .fields()
                        .forEachRemaining(entry -> System.out.println("- " + entry.getKey()));
            }
        } catch (Exception e) {
            System.err.println("Error parsing schema JSON: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private DescriptorProto createAllTypesMessage() {
        return DescriptorProto.newBuilder()
                .setName("AllTypesMessage")
                // Scalar types
                .addField(createField("stringField", FieldDescriptorProto.Type.TYPE_STRING, 1))
                .addField(createField("int32Field", FieldDescriptorProto.Type.TYPE_INT32, 2))
                .addField(createField("int64Field", FieldDescriptorProto.Type.TYPE_INT64, 3))
                .addField(createField("uint32Field", FieldDescriptorProto.Type.TYPE_UINT32, 4))
                .addField(createField("uint64Field", FieldDescriptorProto.Type.TYPE_UINT64, 5))
                .addField(createField("sint32Field", FieldDescriptorProto.Type.TYPE_SINT32, 6))
                .addField(createField("sint64Field", FieldDescriptorProto.Type.TYPE_SINT64, 7))
                .addField(createField("fixed32Field", FieldDescriptorProto.Type.TYPE_FIXED32, 8))
                .addField(createField("fixed64Field", FieldDescriptorProto.Type.TYPE_FIXED64, 9))
                .addField(createField("sfixed32Field", FieldDescriptorProto.Type.TYPE_SFIXED32, 10))
                .addField(createField("sfixed64Field", FieldDescriptorProto.Type.TYPE_SFIXED64, 11))
                .addField(createField("boolField", FieldDescriptorProto.Type.TYPE_BOOL, 12))
                .addField(createField("floatField", FieldDescriptorProto.Type.TYPE_FLOAT, 13))
                .addField(createField("doubleField", FieldDescriptorProto.Type.TYPE_DOUBLE, 14))
                .addField(createField("bytesField", FieldDescriptorProto.Type.TYPE_BYTES, 15))
                // Add more complex types as needed
                .build();
    }

    private FieldDescriptorProto createField(
            String name, FieldDescriptorProto.Type type, int number) {
        return FieldDescriptorProto.newBuilder()
                .setName(name)
                .setType(type)
                .setNumber(number)
                .build();
    }
}
