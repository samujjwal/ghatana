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
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProtoToJsonSchemaMessageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtoToJsonSchemaGenerator generator;
    private ObjectNode definitions;
    private Set<String> visiting;
    private String bundleName = "test.package";

    @Mock private ProtoToJsonSchemaGenerator.Registry registry;

    @BeforeEach
    void setUp() {
        generator = new ProtoToJsonSchemaGenerator();
        definitions = MAPPER.createObjectNode();
        visiting = new HashSet<>();
    }

    @Test
    void testBuildMessageSchema_WithSimpleMessage() {
        // Given
        String messageType = "test.package.SimpleMessage";
        DescriptorProto message =
                DescriptorProto.newBuilder()
                        .setName("SimpleMessage")
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("field1")
                                        .setNumber(1)
                                        .setType(Type.TYPE_STRING))
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("field2")
                                        .setNumber(2)
                                        .setType(Type.TYPE_INT32))
                        .build();

        when(registry.message(messageType)).thenReturn(message);

        // When
        ObjectNode schema =
                generator.buildMessageSchema(
                        bundleName, registry, messageType, definitions, visiting);

        // Then
        assertThat(schema.has("$ref")).isTrue();
        assertThat(definitions.has(messageType)).isTrue();

        ObjectNode messageSchema = (ObjectNode) definitions.get(messageType);
        assertThat(messageSchema.get("type").asText()).isEqualTo("object");
        assertThat(messageSchema.get("properties").has("field1")).isTrue();
        assertThat(messageSchema.get("properties").has("field2")).isTrue();
    }

    @Test
    void testBuildMessageSchema_WithNestedMessage() {
        // Given
        String outerType = "test.package.OuterMessage";
        String innerType = "test.package.OuterMessage.InnerMessage";

        // Inner message
        DescriptorProto innerMessage =
                DescriptorProto.newBuilder()
                        .setName("InnerMessage")
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("inner_field")
                                        .setNumber(1)
                                        .setType(Type.TYPE_STRING))
                        .build();

        // Outer message with nested message field
        DescriptorProto outerMessage =
                DescriptorProto.newBuilder()
                        .setName("OuterMessage")
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("nested")
                                        .setNumber(1)
                                        .setType(Type.TYPE_MESSAGE)
                                        .setTypeName("." + innerType))
                        .addNestedType(innerMessage)
                        .build();

        when(registry.message(outerType)).thenReturn(outerMessage);
        when(registry.message(innerType)).thenReturn(innerMessage);

        // When
        ObjectNode schema =
                generator.buildMessageSchema(
                        bundleName, registry, outerType, definitions, visiting);

        // Then
        assertThat(schema.has("$ref")).isTrue();
        assertThat(definitions.has(outerType)).isTrue();

        // Debug output
        System.out.println("All definitions: " + definitions.toPrettyString());
        System.out.println("Looking for inner type: " + innerType);
        System.out.println("Definitions contains inner type: " + definitions.has(innerType));

        assertThat(definitions.has(innerType)).isTrue();

        ObjectNode outerSchema = (ObjectNode) definitions.get(outerType);
        ObjectNode properties = (ObjectNode) outerSchema.get("properties");
        assertThat(properties.has("nested")).isTrue();

        ObjectNode nestedSchema = (ObjectNode) properties.get("nested");
        assertThat(nestedSchema.get("$ref").asText()).isEqualTo("#/$defs/" + innerType);
    }

    @Test
    void testBuildMessageSchema_WithOneofField() {
        // Given
        String messageType = "test.package.MessageWithOneof";

        // Oneof field
        OneofDescriptorProto oneof =
                OneofDescriptorProto.newBuilder().setName("test_oneof").build();

        // Message with oneof
        DescriptorProto message =
                DescriptorProto.newBuilder()
                        .setName("MessageWithOneof")
                        .addOneofDecl(oneof)
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("string_field")
                                        .setNumber(1)
                                        .setType(Type.TYPE_STRING)
                                        .setOneofIndex(0))
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("int_field")
                                        .setNumber(2)
                                        .setType(Type.TYPE_INT32)
                                        .setOneofIndex(0))
                        .build();

        when(registry.message(messageType)).thenReturn(message);

        // When
        ObjectNode schema =
                generator.buildMessageSchema(
                        bundleName, registry, messageType, definitions, visiting);

        // Then
        assertThat(schema.has("$ref")).isTrue();
        ObjectNode messageSchema = (ObjectNode) definitions.get(messageType);

        // Check oneOf constraint
        assertThat(messageSchema.has("oneOf")).isTrue();
        ArrayNode oneOf = (ArrayNode) messageSchema.get("oneOf");
        assertThat(oneOf).hasSize(2);

        // Check each oneof variant
        for (int i = 0; i < oneOf.size(); i++) {
            ObjectNode variant = (ObjectNode) oneOf.get(i);
            assertThat(variant.has("required")).isTrue();
            ArrayNode required = (ArrayNode) variant.get("required");
            assertThat(required).hasSize(1);
        }
    }

    @Test
    void testBuildMessageSchema_WithMapField() {
        // Given
        String messageType = "test.package.MessageWithMap";
        String mapEntryType = "test.package.MessageWithMap.PropertiesEntry";

        // Map entry type (generated by protoc)
        DescriptorProto mapEntry =
                DescriptorProto.newBuilder()
                        .setName("PropertiesEntry")
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("key")
                                        .setNumber(1)
                                        .setType(Type.TYPE_STRING))
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("value")
                                        .setNumber(2)
                                        .setType(Type.TYPE_STRING))
                        .setOptions(MessageOptions.newBuilder().setMapEntry(true))
                        .build();

        // Message with map field
        DescriptorProto message =
                DescriptorProto.newBuilder()
                        .setName("MessageWithMap")
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("properties")
                                        .setNumber(1)
                                        .setType(Type.TYPE_MESSAGE)
                                        .setTypeName("." + mapEntryType))
                        .addNestedType(mapEntry)
                        .build();

        when(registry.message(messageType)).thenReturn(message);
        when(registry.message(mapEntryType)).thenReturn(mapEntry);

        // When
        ObjectNode schema =
                generator.buildMessageSchema(
                        bundleName, registry, messageType, definitions, visiting);

        // Then
        assertThat(schema.has("$ref")).isTrue();
        ObjectNode messageSchema = (ObjectNode) definitions.get(messageType);
        System.out.println("Generated message schema: " + messageSchema.toPrettyString());

        ObjectNode properties = (ObjectNode) messageSchema.get("properties").get("properties");
        System.out.println("Properties field schema: " + properties.toPrettyString());

        // Should be treated as a map with key and value fields
        assertThat(properties.get("type").asText()).isEqualTo("object");
        assertThat(properties.has("key")).isTrue();
        assertThat(properties.has("value")).isTrue();

        // Check key and value types
        assertThat(properties.get("key").asText()).isEqualTo("string");
        assertThat(properties.get("value").asText()).isEqualTo("string");
    }

    @Test
    void testBuildMessageSchema_WithWellKnownType() {
        // Given
        String messageType = "google.protobuf.Timestamp";

        // When
        ObjectNode schema =
                generator.buildMessageSchema(
                        bundleName, registry, messageType, definitions, visiting);

        // Then - Should use well-known type mapping
        assertThat(schema.get("type").asText()).isEqualTo("string");
        assertThat(schema.get("format").asText()).isEqualTo("date-time");
    }

    @Test
    void testBuildMessageSchema_WithRecursiveType() {
        // Given
        String messageType = "test.package.RecursiveMessage";

        // Recursive message definition
        DescriptorProto message =
                DescriptorProto.newBuilder()
                        .setName("RecursiveMessage")
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("value")
                                        .setNumber(1)
                                        .setType(Type.TYPE_STRING))
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("child")
                                        .setNumber(2)
                                        .setType(Type.TYPE_MESSAGE)
                                        .setTypeName("." + messageType))
                        .build();

        when(registry.message(messageType)).thenReturn(message);

        // When
        ObjectNode schema =
                generator.buildMessageSchema(
                        bundleName, registry, messageType, definitions, visiting);

        // Then - Should handle recursion without stack overflow
        assertThat(schema.has("$ref")).isTrue();
        assertThat(definitions.has(messageType)).isTrue();

        // The recursive reference should be properly handled
        ObjectNode messageSchema = (ObjectNode) definitions.get(messageType);
        ObjectNode properties = (ObjectNode) messageSchema.get("properties");
        ObjectNode childField = (ObjectNode) properties.get("child");
        assertThat(childField.get("$ref").asText()).isEqualTo("#/$defs/" + messageType);
    }

    @Test
    void testBuildMessageSchema_WithDocumentation() {
        // Given
        String messageType = "test.package.DocumentedMessage";

        DescriptorProto message =
                DescriptorProto.newBuilder()
                        .setName("DocumentedMessage")
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("field1")
                                        .setNumber(1)
                                        .setType(Type.TYPE_STRING))
                        .build();

        when(registry.message(messageType)).thenReturn(message);
        when(registry.messageDoc(messageType))
                .thenReturn(Optional.of("This is a documented message"));

        // When
        ObjectNode schema =
                generator.buildMessageSchema(
                        bundleName, registry, messageType, definitions, visiting);

        // Then
        ObjectNode messageSchema = (ObjectNode) definitions.get(messageType);
        assertThat(messageSchema.get("description").asText())
                .isEqualTo("This is a documented message");
    }

    @Test
    void testBuildMessageSchema_WithDeprecatedMessage() {
        // Given
        String messageType = "test.package.DeprecatedMessage";

        DescriptorProto message =
                DescriptorProto.newBuilder()
                        .setName("DeprecatedMessage")
                        .setOptions(MessageOptions.newBuilder().setDeprecated(true))
                        .build();

        when(registry.message(messageType)).thenReturn(message);

        // When
        ObjectNode schema =
                generator.buildMessageSchema(
                        bundleName, registry, messageType, definitions, visiting);

        // Then
        ObjectNode messageSchema = (ObjectNode) definitions.get(messageType);
        assertThat(messageSchema.get("deprecated").asBoolean()).isTrue();
    }

    @Test
    void testBuildMessageSchema_WithRequiredFields() {
        // Given
        String messageType = "test.package.MessageWithRequiredFields";

        DescriptorProto message =
                DescriptorProto.newBuilder()
                        .setName("MessageWithRequiredFields")
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("required_field")
                                        .setNumber(1)
                                        .setType(Type.TYPE_STRING)
                                        .setLabel(Label.LABEL_REQUIRED))
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("optional_field")
                                        .setNumber(2)
                                        .setType(Type.TYPE_STRING)
                                        .setLabel(Label.LABEL_OPTIONAL))
                        .build();

        when(registry.message(messageType)).thenReturn(message);

        // When
        ObjectNode schema =
                generator.buildMessageSchema(
                        bundleName, registry, messageType, definitions, visiting);

        // Then
        ObjectNode messageSchema = (ObjectNode) definitions.get(messageType);
        assertThat(messageSchema.has("required")).isTrue();

        ArrayNode required = (ArrayNode) messageSchema.get("required");
        assertThat(required).hasSize(1);
        assertThat(required.get(0).asText()).isEqualTo("requiredField");
    }

    @Test
    void testBuildMessageSchema_WithObjectMapField() {
        // Arrange
        String messageFqn = "test.package.ObjectMapMessage";
        String mapEntryName = "ObjectMapMessage.MapFieldEntry";

        // Create a map entry message type for Object to Object map
        DescriptorProto mapEntry =
                DescriptorProto.newBuilder()
                        .setName("MapFieldEntry")
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("key")
                                        .setType(Type.TYPE_STRING)
                                        .setNumber(1)
                                        .build())
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("value")
                                        .setTypeName(".google.protobuf.Struct")
                                        .setType(Type.TYPE_MESSAGE)
                                        .setNumber(2)
                                        .build())
                        .setOptions(MessageOptions.newBuilder().setMapEntry(true).build())
                        .build();

        // Create the main message with a map field of Object type
        DescriptorProto message =
                DescriptorProto.newBuilder()
                        .setName("ObjectMapMessage")
                        .addField(
                                FieldDescriptorProto.newBuilder()
                                        .setName("objectMap")
                                        .setTypeName(".test.package.ObjectMapMessage.MapFieldEntry")
                                        .setType(Type.TYPE_MESSAGE)
                                        .setNumber(1)
                                        .setLabel(Label.LABEL_REPEATED)
                                        .build())
                        .addNestedType(mapEntry)
                        .build();

        // Mock the registry
        when(registry.message(anyString()))
                .thenAnswer(
                        invocation -> {
                            String name = invocation.getArgument(0);
                            if (name.equals("test.package.ObjectMapMessage")) {
                                return message;
                            } else if (name.endsWith("MapFieldEntry")) {
                                return mapEntry;
                            }
                            return null;
                        });

        // Act
        ObjectNode result =
                generator.buildMessageSchema(
                        bundleName, registry, messageFqn, definitions, visiting);

        // Assert
        assertThat(result).isNotNull();

        // The result should be a reference to the schema in definitions
        assertThat(result.has("$ref")).isTrue();
        String schemaRef = result.get("$ref").asText();

        // The reference format is "test.package#/$defs/test.package.ObjectMapMessage"
        assertThat(schemaRef).contains("#/$defs/");

        // Extract the definition key (everything after #/$defs/)
        String definitionKey = schemaRef.substring(schemaRef.indexOf("#/$defs/") + 8);

        // Get the actual schema from definitions
        JsonNode schemaToCheck = definitions.get(definitionKey);

        if (schemaToCheck == null) {
            schemaToCheck = definitions.get(messageFqn);
            assertThat(schemaToCheck)
                    .withFailMessage("Schema not found in definitions for key: " + messageFqn)
                    .isNotNull();
        }

        // Print full schema structure for debugging
        System.out.println("Schema being tested: " + schemaToCheck.toPrettyString());

        assertThat(schemaToCheck.has("properties"))
                .withFailMessage("Schema should have 'properties'")
                .isTrue();

        // Get the properties node
        JsonNode properties = schemaToCheck.get("properties");
        assertThat(properties).isNotNull();

        // The map field should be present in properties
        assertThat(properties.has("objectMap"))
                .withFailMessage("Map field 'objectMap' not found in properties")
                .isTrue();

        JsonNode mapFieldNode = properties.get("objectMap");
        System.out.println("Map field schema: " + mapFieldNode.toPrettyString());

        // Print the full schema for debugging
        System.out.println("Full schema: " + schemaToCheck.toPrettyString());
        System.out.println("Definitions: " + definitions.toPrettyString());

        // The map field should be an object with key and value properties
        assertThat(mapFieldNode.isObject()).isTrue();

        // It should have a type property
        assertThat(mapFieldNode.has("type")).isTrue();
        assertThat(mapFieldNode.get("type").asText()).isEqualTo("object");

        // It should have key and value properties
        assertThat(mapFieldNode.has("key")).isTrue();
        assertThat(mapFieldNode.get("key").asText()).isEqualTo("string");

        assertThat(mapFieldNode.has("value")).isTrue();
        assertThat(mapFieldNode.get("value").asText()).isEqualTo("object");
    }
}
