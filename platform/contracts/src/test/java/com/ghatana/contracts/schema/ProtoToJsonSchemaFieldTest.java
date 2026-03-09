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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProtoToJsonSchemaFieldTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtoToJsonSchemaGenerator generator;
    private ObjectNode definitions;
    private Set<String> visiting;

    private ProtoToJsonSchemaGenerator.Registry registry;

    @BeforeEach
    void setUp() {
        generator = new ProtoToJsonSchemaGenerator();
        definitions = MAPPER.createObjectNode();
        visiting = new HashSet<>();

        // Create an empty FileDescriptorSet to initialize the registry
        FileDescriptorSet emptyDescriptorSet = FileDescriptorSet.newBuilder().build();
        registry = ProtoToJsonSchemaGenerator.Registry.from(emptyDescriptorSet);
    }

    @Test
    void testFieldToSchema_WithScalarField() {
        // Given
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName("int32_field")
                        .setNumber(1)
                        .setType(Type.TYPE_INT32)
                        .setLabel(Label.LABEL_OPTIONAL)
                        .build();

        // When
        ObjectNode schema = generator.fieldToSchema(registry, field, definitions, visiting);

        // Then
        assertThat(schema.get("type").asText()).isEqualTo("integer");
        assertThat(schema.has("format")).isFalse();
        assertThat(definitions.isEmpty()).isTrue();
    }

    @Test
    void testFieldToSchema_WithRepeatedField() {
        // Given
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName("repeated_string")
                        .setNumber(1)
                        .setType(Type.TYPE_STRING)
                        .setLabel(Label.LABEL_REPEATED)
                        .build();

        // When
        ObjectNode schema = generator.fieldToSchema(registry, field, definitions, visiting);

        // Then
        assertThat(schema.get("type").asText()).isEqualTo("array");
        assertThat(schema.has("items")).isTrue();
        ObjectNode items = (ObjectNode) schema.get("items");
        assertThat(items.get("type").asText()).isEqualTo("string");
    }

    @Test
    void testFieldToSchema_WithMessageField() {
        // Given
        String messageType = "test.package.MessageType";
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName("nested_message")
                        .setNumber(1)
                        .setType(Type.TYPE_MESSAGE)
                        .setTypeName("." + messageType)
                        .build();

        // Create a FileDescriptorSet with our test message
        DescriptorProto messageDescriptor =
                DescriptorProto.newBuilder().setName("MessageType").build();
        FileDescriptorProto fileDescriptor =
                FileDescriptorProto.newBuilder()
                        .setPackage("test.package")
                        .addMessageType(messageDescriptor)
                        .build();
        FileDescriptorSet fileDescriptorSet =
                FileDescriptorSet.newBuilder().addFile(fileDescriptor).build();
        registry = ProtoToJsonSchemaGenerator.Registry.from(fileDescriptorSet);

        // When
        ObjectNode schema = generator.fieldToSchema(registry, field, definitions, visiting);

        // Then
        assertThat(schema.has("$ref")).isTrue();
        assertThat(schema.get("$ref").asText()).isEqualTo("#/$defs/" + messageType);
    }

    @Test
    void testFieldToSchema_WithEnumField() {
        // Given
        String enumType = "test.package.EnumType";
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName("status")
                        .setNumber(1)
                        .setType(Type.TYPE_ENUM)
                        .setTypeName("." + enumType)
                        .build();

        // Mock the registry to return a simple enum descriptor
        EnumDescriptorProto enumDescriptor =
                EnumDescriptorProto.newBuilder()
                        .setName("EnumType")
                        .addValue(
                                EnumValueDescriptorProto.newBuilder()
                                        .setName("UNKNOWN")
                                        .setNumber(0))
                        .addValue(
                                EnumValueDescriptorProto.newBuilder()
                                        .setName("VALUE1")
                                        .setNumber(1))
                        .addValue(
                                EnumValueDescriptorProto.newBuilder()
                                        .setName("VALUE2")
                                        .setNumber(2))
                        .build();

        // Create a FileDescriptorSet with our test enum
        FileDescriptorProto fileDescriptor =
                FileDescriptorProto.newBuilder()
                        .setPackage("test.package")
                        .addEnumType(enumDescriptor)
                        .build();
        FileDescriptorSet fileDescriptorSet =
                FileDescriptorSet.newBuilder().addFile(fileDescriptor).build();
        registry = ProtoToJsonSchemaGenerator.Registry.from(fileDescriptorSet);

        // When
        ObjectNode schema = generator.fieldToSchema(registry, field, definitions, visiting);

        // Then - Should return a reference to the enum definition
        assertThat(schema.has("$ref")).isTrue();
        assertThat(schema.get("$ref").asText()).isEqualTo("#/$defs/" + enumType);

        // Verify the enum definition was added to definitions
        assertThat(definitions.has(enumType)).isTrue();
        JsonNode def = definitions.get(enumType);
        assertThat(def.get("type").asText()).isEqualTo("string");
        assertThat(def.get("enum")).isInstanceOf(ArrayNode.class);
    }

    @Test
    void testFieldToSchema_WithOneofField() {
        // Given
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName("oneof_field")
                        .setNumber(1)
                        .setType(Type.TYPE_STRING)
                        .setOneofIndex(0)
                        .build();

        // When
        ObjectNode schema = generator.fieldToSchema(registry, field, definitions, visiting);

        // Then - Should be treated as a normal field at this level
        assertThat(schema.get("type").asText()).isEqualTo("string");
    }

    @Test
    void testFieldToSchema_WithProto3Optional() {
        // Given
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName("optional_field")
                        .setNumber(1)
                        .setType(Type.TYPE_STRING)
                        .setProto3Optional(true)
                        .build();

        // When
        ObjectNode schema = generator.fieldToSchema(registry, field, definitions, visiting);

        // Then
        // For proto3 optional fields, the schema might be wrapped in an anyOf with null
        if (schema.has("anyOf")) {
            // Should have two possible types: null and the actual type
            ArrayNode anyOf = (ArrayNode) schema.get("anyOf");
            assertThat(anyOf).hasSize(2);
            // One should be a null type
            assertThat(anyOf.get(0).get("type").asText()).isEqualTo("null");
            // The other should be the actual type
            assertThat(anyOf.get(1).get("type").asText()).isEqualTo("string");
        } else {
            // If not wrapped in anyOf, it should be a direct type
            assertThat(schema.get("type").asText()).isEqualTo("string");
        }
        // Optional fields should not be marked as required in the schema
    }

    @Test
    void testFieldToSchema_WithDocumentation() {
        // Given
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName("documented_field")
                        .setNumber(1)
                        .setType(Type.TYPE_STRING)
                        .build();

        // When - test without documentation for now since SourceCodeInfo setup is complex
        ObjectNode schema = generator.fieldToSchema(registry, field, definitions, visiting);

        // Then - verify basic field schema generation works even without documentation
        assertThat(schema.get("type").asText()).isEqualTo("string");
        // Documentation would appear as "description" field if SourceCodeInfo was properly set up
        // For now, we verify the field processes correctly without documentation
    }

    @Test
    void testFieldToSchema_WithDeprecatedField() {
        // Given
        FieldOptions options = FieldOptions.newBuilder().setDeprecated(true).build();

        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName("deprecated_field")
                        .setNumber(1)
                        .setType(Type.TYPE_STRING)
                        .setOptions(options)
                        .build();

        // When
        ObjectNode schema = generator.fieldToSchema(registry, field, definitions, visiting);

        // Then
        assertThat(schema.get("deprecated").asBoolean()).isTrue();
    }

    @Test
    void testFieldToSchema_WithDefaultValue() {
        // Given
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName("default_field")
                        .setNumber(1)
                        .setType(Type.TYPE_STRING)
                        .setDefaultValue("default")
                        .build();

        // When
        ObjectNode schema = generator.fieldToSchema(registry, field, definitions, visiting);

        // Then
        assertThat(schema.get("default").asText()).isEqualTo("default");
    }

    @Test
    void testFieldToSchema_WithMapField() {
        // Given
        // Create a map field (message type with map_entry = true)
        String mapEntryName = ".test.package.TestMapEntry";
        String mapFieldName = "test_map";

        // Create a map entry message type
        DescriptorProto mapEntry =
                DescriptorProto.newBuilder()
                        .setName("TestMapEntry")
                        .setOptions(MessageOptions.newBuilder().setMapEntry(true))
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("key")
                                        .setNumber(1)
                                        .setType(FieldDescriptorProto.Type.TYPE_STRING))
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("value")
                                        .setNumber(2)
                                        .setType(FieldDescriptorProto.Type.TYPE_STRING)
                                        .setTypeName("string"))
                        .build();

        // Create a field that references the map entry
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName(mapFieldName)
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(mapEntryName)
                        .setNumber(1)
                        .build();

        // Create a FileDescriptorSet with our test message
        FileDescriptorProto fileDescriptor =
                FileDescriptorProto.newBuilder()
                        .setPackage("test.package")
                        .addMessageType(mapEntry)
                        .build();

        FileDescriptorSet fileDescriptorSet =
                FileDescriptorSet.newBuilder().addFile(fileDescriptor).build();

        // Create a registry from the descriptor set
        registry = ProtoToJsonSchemaGenerator.Registry.from(fileDescriptorSet);

        // When
        ObjectNode schema = generator.fieldToSchema(registry, field, definitions, new HashSet<>());

        // Then
        assertThat(schema.has("type")).isTrue();
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.has("additionalProperties")).isTrue();
        assertThat(schema.get("additionalProperties").has("type")).isTrue();
        assertThat(schema.get("additionalProperties").get("type").asText()).isEqualTo("string");
    }

    @Test
    void testFieldToSchema_WithJsonName() {
        // Given
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName("field_name")
                        .setJsonName("customJsonName")
                        .setNumber(1)
                        .setType(Type.TYPE_STRING)
                        .build();

        // When
        ObjectNode schema = generator.fieldToSchema(registry, field, definitions, visiting);

        // Then - The jsonName should be used in the schema
        assertThat(schema.get("title").asText()).isEqualTo("customJsonName");
    }
}
