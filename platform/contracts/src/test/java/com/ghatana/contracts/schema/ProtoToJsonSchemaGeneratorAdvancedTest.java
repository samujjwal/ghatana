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
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ProtoToJsonSchemaGenerator Advanced Tests")
class ProtoToJsonSchemaGeneratorAdvancedTest {

    private ProtoToJsonSchemaGenerator generator;
    private ObjectMapper mapper;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        this.mapper = new ObjectMapper();
        this.generator = new ProtoToJsonSchemaGenerator();
    }

    @Test
    @DisplayName("should handle nested message types")
    void testNestedMessageTypes() throws Exception {
        // Given
        String messageName = "test.NestedMessage";

        // When
        generateSchema(messageName);

        // Then
        Path schemaFile = tempDir.resolve("output/NestedMessage.schema.json");
        assertTrue(Files.exists(schemaFile), "Schema file should exist: " + schemaFile);

        // Check the schema has the expected structure
        JsonNode schema = mapper.readTree(schemaFile.toFile());

        // The schema should either have a $ref or be a full schema with $defs
        boolean hasRef = schema.has("$ref");
        boolean hasDefs = schema.has("$defs");
        assertTrue(hasRef || hasDefs, "Schema should have $ref or $defs");

        // Check the bundle file for definitions
        Path bundleFile = tempDir.resolve("output/bundle.schema.json");
        assertTrue(Files.exists(bundleFile), "Bundle file should exist");

        JsonNode bundle = mapper.readTree(bundleFile.toFile());
        JsonNode defs = bundle.path("$defs");
        assertFalse(defs.isMissingNode(), "Bundle should have $defs section");

        // The nested type might be referenced in different ways
        boolean foundNestedType = false;

        // Check for possible nested type names
        String[] possibleNestedTypeNames = {
            "test.NestedMessage.NestedType",
            "test.NestedMessage.NestedType.NestedType",
            "NestedType"
        };

        for (String typeName : possibleNestedTypeNames) {
            JsonNode nestedType = defs.path(typeName);
            if (!nestedType.isMissingNode()) {
                foundNestedType = true;
                assertTrue(
                        nestedType.has("type") || nestedType.has("$ref"),
                        "Nested type should have a type or reference");
                break;
            }
        }

        if (!foundNestedType) {
            // If we get here, the nested type wasn't found - log the available definitions
            System.out.println("Available definitions: " + defs.fieldNames());
            fail("Could not find nested type definition in schema");
        }
    }

    @Test
    @DisplayName("should handle repeated fields")
    void testRepeatedFields() throws Exception {
        // Given
        String messageName = "test.MessageWithRepeated";

        // When
        generateSchema(messageName);

        // Then
        Path schemaFile = tempDir.resolve("output/MessageWithRepeated.schema.json");
        JsonNode schema = mapper.readTree(schemaFile.toFile());

        // Get the schema with definitions (could be in the file itself or in the bundle)
        JsonNode actualSchema = schema;
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asText();
            String defName = ref.substring(ref.lastIndexOf('/') + 1);
            Path bundleFile = tempDir.resolve("output/bundle.schema.json");
            JsonNode bundle = mapper.readTree(bundleFile.toFile());
            actualSchema = bundle.path("$defs").path(defName);
        }

        // Check that the items field exists and is an array
        JsonNode itemsField = actualSchema.path("properties").path("items");
        assertFalse(itemsField.isMissingNode(), "Should have items field");

        // The field should either be an array or have an items property
        if (itemsField.has("type")) {
            assertEquals(
                    "array", itemsField.path("type").asText(), "Repeated field should be an array");
        }
        assertTrue(
                itemsField.has("items") || itemsField.has("$ref"),
                "Repeated field should have items or $ref property");
    }

    @Test
    @DisplayName("should handle enum types")
    void testEnumTypes() throws Exception {
        // Given
        String messageName = "test.MessageWithEnum";

        // When
        generateSchema(messageName);

        // Then
        Path schemaFile = tempDir.resolve("output/MessageWithEnum.schema.json");
        assertTrue(Files.exists(schemaFile), "Schema file should exist: " + schemaFile);

        // Read the schema file
        JsonNode schema = mapper.readTree(schemaFile.toFile());

        // Check the bundle file for definitions
        Path bundleFile = tempDir.resolve("output/bundle.schema.json");
        assertTrue(Files.exists(bundleFile), "Bundle file should exist");

        JsonNode bundle = mapper.readTree(bundleFile.toFile());
        JsonNode defs = bundle.path("$defs");
        assertFalse(defs.isMissingNode(), "Bundle should have $defs section");

        // Check for the message definition in various possible locations
        String[] possibleMessageNames = {"test.MessageWithEnum", "MessageWithEnum"};

        JsonNode messageDef = null;
        for (String name : possibleMessageNames) {
            messageDef = defs.path(name);
            if (!messageDef.isMissingNode()) break;
        }

        assertFalse(
                messageDef == null || messageDef.isMissingNode(),
                "Should find message definition in schema");

        // Check the status field
        JsonNode properties = messageDef.path("properties");
        assertFalse(properties.isMissingNode(), "Message should have properties");

        JsonNode statusField = properties.path("status");
        assertFalse(statusField.isMissingNode(), "Should have status field");

        // Check if status field has a reference to the enum or inline enum values
        if (statusField.has("$ref")) {
            // It's a reference to the enum definition
            String ref = statusField.get("$ref").asText();

            // Check if the enum is defined in the definitions
            String enumName = ref.substring(ref.lastIndexOf('/') + 1);
            JsonNode enumDef = defs.path(enumName);

            // If not found, try with different naming patterns
            if (enumDef.isMissingNode()) {
                // Try with full name
                enumDef = defs.path("test.MessageWithEnum.Status");

                if (enumDef.isMissingNode()) {
                    // Try with nested message name
                    enumDef = defs.path("test.MessageWithEnum_Status");
                }

                if (enumDef.isMissingNode()) {
                    // Try with just the enum name
                    enumDef = defs.path("Status");
                }
            }

            assertFalse(
                    enumDef.isMissingNode(),
                    "Should find enum definition in schema. Tried: "
                            + enumName
                            + ", test.MessageWithEnum.Status, test.MessageWithEnum_Status, Status");

            // Check enum values
            JsonNode enumValues = enumDef.path("enum");
            assertFalse(
                    enumValues.isMissingNode() || !enumValues.isArray(),
                    "Enum should have values array");

            List<String> values = new ArrayList<>();
            enumValues.elements().forEachRemaining(node -> values.add(node.asText()));

            assertTrue(
                    values.contains("UNKNOWN")
                            && values.contains("ACTIVE")
                            && values.contains("INACTIVE"),
                    "Enum should contain expected values. Found: " + values);
        } else if (statusField.has("enum")) {
            // It's an inline enum
            JsonNode enumValues = statusField.path("enum");
            assertTrue(
                    enumValues.isArray() && enumValues.size() > 0,
                    "Status field should have enum values");

            List<String> values = new ArrayList<>();
            enumValues.elements().forEachRemaining(node -> values.add(node.asText()));

            assertTrue(
                    values.contains("UNKNOWN")
                            && values.contains("ACTIVE")
                            && values.contains("INACTIVE"),
                    "Status enum should contain expected values. Found: " + values);
        } else {
            fail("Status field should have either '$ref' or 'enum' property");
        }
    }

    @Test
    @DisplayName("should handle map fields")
    void testMapFields() throws Exception {
        // Given
        String messageName = "test.MessageWithMap";

        // When
        generateSchema(messageName);

        // Then
        Path schemaFile = tempDir.resolve("output/MessageWithMap.schema.json");
        JsonNode schema = mapper.readTree(schemaFile.toFile());

        // Get the schema with definitions (could be in the file itself or in the bundle)
        JsonNode actualSchema = schema;
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asText();
            String defName = ref.substring(ref.lastIndexOf('/') + 1);
            Path bundleFile = tempDir.resolve("output/bundle.schema.json");
            JsonNode bundle = mapper.readTree(bundleFile.toFile());
            actualSchema = bundle.path("$defs").path(defName);
        }

        // Check that properties field exists and has the expected structure
        JsonNode propertiesField = actualSchema.path("properties").path("properties");
        assertFalse(propertiesField.isMissingNode(), "Should have properties field");

        // The properties field should be object with key value
        assertTrue(
                propertiesField.has("key") && propertiesField.has("value"),
                "Map field should have key and value properties");
    }

    @Test
    @DisplayName("should handle oneof fields")
    void testOneofFields() throws Exception {
        // Given
        String messageName = "test.MessageWithOneof";

        // When
        generateSchema(messageName);

        // Then
        Path schemaFile = tempDir.resolve("output/MessageWithOneof.schema.json");
        JsonNode schema = mapper.readTree(schemaFile.toFile());

        // Get the schema with definitions (could be in the file itself or in the bundle)
        JsonNode actualSchema = schema;
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asText();
            String defName = ref.substring(ref.lastIndexOf('/') + 1);
            Path bundleFile = tempDir.resolve("output/bundle.schema.json");
            JsonNode bundle = mapper.readTree(bundleFile.toFile());
            actualSchema = bundle.path("$defs").path(defName);
        }

        // Check that oneof fields are properly represented
        // They might be represented as oneOf, anyOf, or with individual fields
        boolean hasOneOf =
                actualSchema.has("oneOf")
                        || actualSchema.has("anyOf")
                        || (actualSchema.has("properties")
                                && actualSchema.path("properties").has("test_oneof"));

        if (!hasOneOf) {
            // Check if individual oneof fields exist
            JsonNode props = actualSchema.path("properties");
            hasOneOf = props.has("stringValue") || props.has("intValue");
        }

        assertTrue(
                hasOneOf,
                "oneof fields should be represented with oneOf, anyOf, or individual fields");
    }

    @Test
    @DisplayName("should handle custom options")
    void testCustomOptions() throws Exception {
        // Given
        String messageName = "test.MessageWithOptions";

        // When
        generateSchema(messageName);

        // Then
        Path schemaFile = tempDir.resolve("output/MessageWithOptions.schema.json");
        assertTrue(Files.exists(schemaFile), "Schema file should exist");

        JsonNode schema = mapper.readTree(schemaFile.toFile());

        // Check for custom options in the schema
        boolean hasOptions =
                schema.has("x-protobuf-options")
                        || schema.has("x-options")
                        || schema.path("description").asText("").contains("options");

        // If no options in the main schema, check the bundle
        if (!hasOptions) {
            Path bundleFile = tempDir.resolve("output/bundle.schema.json");
            if (Files.exists(bundleFile)) {
                JsonNode bundle = mapper.readTree(bundleFile.toFile());
                JsonNode defs = bundle.path("$defs");

                // Check all definitions for the message
                List<String> fieldNames = new ArrayList<>();
                defs.fieldNames().forEachRemaining(fieldNames::add);
                for (String defName : fieldNames) {
                    if (defName.endsWith("MessageWithOptions")) {
                        JsonNode def = defs.path(defName);
                        hasOptions =
                                def.has("x-protobuf-options")
                                        || def.has("x-options")
                                        || def.path("description").asText("").contains("options");
                        if (hasOptions) break;
                    }
                }
            }
        }

        assertTrue(hasOptions, "Should have protobuf options in schema or bundle");
    }

    @Test
    @DisplayName("should handle invalid message name")
    void testInvalidMessageName() {
        // Given
        String invalidMessageName = "non.existing.Message";

        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> generateSchema(invalidMessageName),
                "Should throw for non-existent message");
    }

    private void generateSchema(String messageName) throws Exception {
        // Create a simple descriptor file for testing
        Path descriptorFile = tempDir.resolve("test.desc");
        createTestDescriptor(descriptorFile);

        // Generate schema
        String[] args = {
            "--descriptorSet=" + descriptorFile.toString(),
            "--outDir=" + tempDir.resolve("output").toString(),
            "--messages=" + messageName
        };

        ProtoToJsonSchemaGenerator.main(args);
    }

    private void createTestDescriptor(Path outputFile) throws Exception {
        // Create a FileDescriptorSet that will contain all our test proto files
        FileDescriptorSet.Builder fileSetBuilder = FileDescriptorSet.newBuilder();

        // Define common options
        FileOptions fileOptions =
                FileOptions.newBuilder()
                        .setJavaPackage("com.ghatana.contracts.test")
                        .setJavaOuterClassname("TestProtos")
                        .build();

        // 1. Nested message type
        DescriptorProto nestedType =
                DescriptorProto.newBuilder()
                        .setName("NestedType")
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("nestedField")
                                        .setNumber(1)
                                        .setType(FieldDescriptorProto.Type.TYPE_STRING)
                                        .build())
                        .build();

        DescriptorProto messageWithNested =
                DescriptorProto.newBuilder()
                        .setName("NestedMessage")
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("nested")
                                        .setNumber(1)
                                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                                        .setTypeName("test.NestedMessage.NestedType")
                                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL))
                        .addNestedType(nestedType)
                        .build();

        // Create a file with just the nested message
        FileDescriptorProto nestedFile =
                FileDescriptorProto.newBuilder()
                        .setName("test/nested.proto")
                        .setPackage("test")
                        .addMessageType(messageWithNested)
                        .build();

        fileSetBuilder.addFile(nestedFile);

        // 2. Message with repeated fields
        FileDescriptorProto repeatedFile =
                createFileDescriptor(
                        "test/repeated.proto",
                        "test",
                        fileOptions,
                        DescriptorProto.newBuilder()
                                .setName("MessageWithRepeated")
                                .addField(
                                        FieldDescriptorProto.newBuilder()
                                                .setName("items")
                                                .setNumber(1)
                                                .setType(FieldDescriptorProto.Type.TYPE_STRING)
                                                .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                                                .build())
                                .build());

        // 3. Message with nested enum
        // Create the message first
        DescriptorProto.Builder messageBuilder =
                DescriptorProto.newBuilder().setName("MessageWithEnum");

        // Add the enum as a nested type
        EnumDescriptorProto statusEnum =
                EnumDescriptorProto.newBuilder()
                        .setName("Status")
                        .addValue(
                                EnumValueDescriptorProto.newBuilder()
                                        .setName("UNKNOWN")
                                        .setNumber(0))
                        .addValue(
                                EnumValueDescriptorProto.newBuilder()
                                        .setName("ACTIVE")
                                        .setNumber(1))
                        .addValue(
                                EnumValueDescriptorProto.newBuilder()
                                        .setName("INACTIVE")
                                        .setNumber(2))
                        .build();

        messageBuilder.addEnumType(statusEnum);

        // Add the field that references the nested enum
        messageBuilder.addField(
                FieldDescriptorProto.newBuilder()
                        .setName("status")
                        .setNumber(1)
                        .setType(FieldDescriptorProto.Type.TYPE_ENUM)
                        .setTypeName("MessageWithEnum.Status") // Reference the nested enum
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL));

        // Create a file with the message (which contains the nested enum)
        FileDescriptorProto enumFile =
                FileDescriptorProto.newBuilder()
                        .setName("test/enum.proto")
                        .setPackage("test")
                        .addMessageType(messageBuilder)
                        .build();

        fileSetBuilder.addFile(enumFile);

        // 4. Message with map
        FileDescriptorProto mapFile =
                createFileDescriptor(
                        "test/map.proto",
                        "test",
                        fileOptions,
                        DescriptorProto.newBuilder()
                                .setName("MessageWithMap")
                                .addNestedType(
                                        DescriptorProto.newBuilder()
                                                .setName("PropertiesEntry")
                                                .addField(
                                                        FieldDescriptorProto.newBuilder()
                                                                .setName("key")
                                                                .setNumber(1)
                                                                .setType(
                                                                        FieldDescriptorProto.Type
                                                                                .TYPE_STRING)
                                                                .build())
                                                .addField(
                                                        FieldDescriptorProto.newBuilder()
                                                                .setName("value")
                                                                .setNumber(2)
                                                                .setType(
                                                                        FieldDescriptorProto.Type
                                                                                .TYPE_STRING)
                                                                .build())
                                                .setOptions(
                                                        MessageOptions.newBuilder()
                                                                .setMapEntry(true)
                                                                .build())
                                                .build())
                                .addField(
                                        FieldDescriptorProto.newBuilder()
                                                .setName("properties")
                                                .setNumber(1)
                                                .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                                                .setTypeName("test.MessageWithMap.PropertiesEntry")
                                                .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                                                .build())
                                .build());

        // 5. Message with oneof
        FileDescriptorProto oneofFile =
                createFileDescriptor(
                        "test/oneof.proto",
                        "test",
                        fileOptions,
                        DescriptorProto.newBuilder()
                                .setName("MessageWithOneof")
                                .addField(
                                        FieldDescriptorProto.newBuilder()
                                                .setName("string_value")
                                                .setNumber(1)
                                                .setType(FieldDescriptorProto.Type.TYPE_STRING)
                                                .setOneofIndex(0)
                                                .build())
                                .addField(
                                        FieldDescriptorProto.newBuilder()
                                                .setName("int_value")
                                                .setNumber(2)
                                                .setType(FieldDescriptorProto.Type.TYPE_INT32)
                                                .setOneofIndex(0)
                                                .build())
                                .addOneofDecl(
                                        OneofDescriptorProto.newBuilder()
                                                .setName("test_oneof")
                                                .build())
                                .build());

        // 6. Message with custom options
        FileDescriptorProto optionsFile =
                createFileDescriptor(
                        "test/options.proto",
                        "test",
                        FileOptions.newBuilder(fileOptions).setJavaMultipleFiles(true).build(),
                        DescriptorProto.newBuilder()
                                .setName("MessageWithOptions")
                                .setOptions(
                                        MessageOptions.newBuilder()
                                                .setNoStandardDescriptorAccessor(true)
                                                .build())
                                .addField(
                                        FieldDescriptorProto.newBuilder()
                                                .setName("id")
                                                .setNumber(1)
                                                .setType(FieldDescriptorProto.Type.TYPE_STRING)
                                                .build())
                                .build());

        // Add all files to the descriptor set
        fileSetBuilder.addFile(nestedFile);
        fileSetBuilder.addFile(repeatedFile);
        fileSetBuilder.addFile(enumFile);
        fileSetBuilder.addFile(mapFile);
        fileSetBuilder.addFile(oneofFile);
        fileSetBuilder.addFile(optionsFile);

        // Write the descriptor set to the output file
        try (FileOutputStream output = new FileOutputStream(outputFile.toFile())) {
            fileSetBuilder.build().writeTo(output);
        }
    }

    private FileDescriptorProto createFileDescriptor(
            String name, String packageName, FileOptions fileOptions, DescriptorProto message) {
        return FileDescriptorProto.newBuilder()
                .setName(name)
                .setPackage(packageName)
                .addMessageType(message)
                .setOptions(fileOptions)
                .setSyntax("proto3")
                .build();
    }
}
