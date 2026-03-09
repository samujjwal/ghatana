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
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProtoToJsonSchemaRegistryTest {

    private FileDescriptorSet fileDescriptorSet;
    private ProtoToJsonSchemaGenerator.Registry registry;

    @BeforeEach
    void setUp() {
        // Create a simple FileDescriptorSet for testing
        FileDescriptorProto file =
                FileDescriptorProto.newBuilder()
                        .setName("test.proto")
                        .setPackage("test.package")
                        .addMessageType(
                                DescriptorProto.newBuilder()
                                        .setName("TestMessage")
                                        .addField(
                                                FieldDescriptorProto.newBuilder()
                                                        .setName("field1")
                                                        .setNumber(1)
                                                        .setType(
                                                                FieldDescriptorProto.Type
                                                                        .TYPE_STRING))
                                        .addField(
                                                FieldDescriptorProto.newBuilder()
                                                        .setName("nested_message")
                                                        .setNumber(2)
                                                        .setType(
                                                                FieldDescriptorProto.Type
                                                                        .TYPE_MESSAGE)
                                                        .setTypeName(".test.package.NestedMessage"))
                                        .addNestedType(
                                                DescriptorProto.newBuilder()
                                                        .setName("NestedMessage")
                                                        .addField(
                                                                FieldDescriptorProto.newBuilder()
                                                                        .setName("nested_field")
                                                                        .setNumber(1)
                                                                        .setType(
                                                                                FieldDescriptorProto
                                                                                        .Type
                                                                                        .TYPE_STRING))))
                        .addEnumType(
                                EnumDescriptorProto.newBuilder()
                                        .setName("TestEnum")
                                        .addValue(
                                                EnumValueDescriptorProto.newBuilder()
                                                        .setName("UNKNOWN")
                                                        .setNumber(0))
                                        .addValue(
                                                EnumValueDescriptorProto.newBuilder()
                                                        .setName("VALUE_ONE")
                                                        .setNumber(1)))
                        .build();

        fileDescriptorSet = FileDescriptorSet.newBuilder().addFile(file).build();

        registry = ProtoToJsonSchemaGenerator.Registry.from(fileDescriptorSet);
    }

    @Test
    void testMessageLookup() {
        // Test top-level message lookup
        DescriptorProto message = registry.message("test.package.TestMessage");
        assertThat(message).isNotNull();
        assertThat(message.getName()).isEqualTo("TestMessage");
        assertThat(message.getFieldCount()).isEqualTo(2);

        // Test nested message lookup
        DescriptorProto nestedMessage = registry.message("test.package.TestMessage.NestedMessage");
        assertThat(nestedMessage).isNotNull();
        assertThat(nestedMessage.getName()).isEqualTo("NestedMessage");
        assertThat(nestedMessage.getFieldCount()).isEqualTo(1);
    }

    @Test
    void testEnumLookup() {
        // Test enum lookup
        EnumDescriptorProto enumType = registry.enumType("test.package.TestEnum");
        assertThat(enumType).isNotNull();
        assertThat(enumType.getName()).isEqualTo("TestEnum");
        assertThat(enumType.getValueCount()).isEqualTo(2);
    }

    @Test
    void testPackageLookup() {
        // Test package lookup for message
        Optional<String> messagePackage = registry.packageOf("test.package.TestMessage");
        assertThat(messagePackage).isPresent().contains("test.package");

        // Test package lookup for enum
        Optional<String> enumPackage = registry.packageOf("test.package.TestEnum");
        assertThat(enumPackage).isPresent().contains("test.package");
    }

    @Test
    void testTopLevelMessageFQNs() {
        // Test getting top-level message FQNs
        assertThat(registry.topLevelMessageFQNs())
                .containsExactlyInAnyOrder("test.package.TestMessage");
    }

    @Test
    void testMessageDoc() {
        // Test message documentation lookup
        // Note: This test assumes the test data has no documentation
        assertThat(registry.messageDoc("test.package.TestMessage")).isEmpty();
    }

    @Test
    void testFieldDoc() {
        // Test field documentation lookup
        // Note: This test assumes the test data has no documentation
        assertThat(registry.fieldDoc("test.package.TestMessage", 1)).isEmpty();
    }

    @Test
    void testEnumDoc() {
        // Test enum documentation lookup
        // Note: This test assumes the test data has no documentation
        assertThat(registry.enumDoc("test.package.TestEnum")).isEmpty();
    }

    @Test
    void testMessageNotFound() {
        // Test non-existent message lookup
        assertThat(registry.message("non.existent.Message")).isNull();
    }

    @Test
    void testEnumNotFound() {
        // Test non-existent enum lookup
        assertThat(registry.enumType("non.existent.Enum")).isNull();
    }

    @Test
    void testEmptyFileDescriptorSet() {
        // Test with empty file descriptor set
        ProtoToJsonSchemaGenerator.Registry emptyRegistry =
                ProtoToJsonSchemaGenerator.Registry.from(FileDescriptorSet.getDefaultInstance());

        assertThat(emptyRegistry.topLevelMessageFQNs()).isEmpty();
        assertThat(emptyRegistry.message("any.message")).isNull();
        assertThat(emptyRegistry.enumType("any.enum")).isNull();
    }

    @Test
    void testWithDocumentation() {
        // Create a FileDescriptorSet with documentation
        SourceCodeInfo.Location location =
                SourceCodeInfo.Location.newBuilder()
                        .addPath(4) // message_type = 4
                        .addPath(0) // index 0
                        .setLeadingComments("Test message documentation")
                        .build();

        FileDescriptorProto file =
                FileDescriptorProto.newBuilder()
                        .setName("doc_test.proto")
                        .setPackage("test.docs")
                        .addMessageType(DescriptorProto.newBuilder().setName("DocumentedMessage"))
                        .setSourceCodeInfo(SourceCodeInfo.newBuilder().addLocation(location))
                        .build();

        FileDescriptorSet docFileDescriptorSet =
                FileDescriptorSet.newBuilder().addFile(file).build();

        ProtoToJsonSchemaGenerator.Registry docRegistry =
                ProtoToJsonSchemaGenerator.Registry.from(docFileDescriptorSet);

        // Test message documentation lookup
        assertThat(docRegistry.messageDoc("test.docs.DocumentedMessage"))
                .isPresent()
                .contains("Test message documentation");
    }

    @Test
    void testFieldDocumentation() {
        // Create a FileDescriptorSet with field documentation
        // The path structure is important here:
        // - 4 = message_type (repeated field in FileDescriptorProto)
        // - 0 = index of the message in message_type (0-based)
        // - 2 = field (repeated field in DescriptorProto)
        // - 0 = index of the field in fields (0-based)
        // The field number is NOT part of the path for field documentation
        SourceCodeInfo.Location location =
                SourceCodeInfo.Location.newBuilder()
                        .addPath(4) // message_type = 4
                        .addPath(0) // first message
                        .addPath(2) // field = 2
                        .addPath(0) // first field
                        .setLeadingComments("Field documentation")
                        .build();

        // Create the field with number 1
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName("documented_field")
                        .setNumber(1)
                        .setType(FieldDescriptorProto.Type.TYPE_STRING)
                        .build();

        // Create the message with the field
        DescriptorProto message =
                DescriptorProto.newBuilder().setName("DocumentedMessage").addField(field).build();

        // Build the file with the message and documentation
        FileDescriptorProto file =
                FileDescriptorProto.newBuilder()
                        .setName("field_doc_test.proto")
                        .setPackage("test.docs")
                        .addMessageType(message)
                        .setSourceCodeInfo(SourceCodeInfo.newBuilder().addLocation(location))
                        .build();

        FileDescriptorSet docFileDescriptorSet =
                FileDescriptorSet.newBuilder().addFile(file).build();

        // Create the registry from the descriptor set
        ProtoToJsonSchemaGenerator.Registry docRegistry =
                ProtoToJsonSchemaGenerator.Registry.from(docFileDescriptorSet);

        // Test field documentation lookup
        assertThat(docRegistry.fieldDoc("test.docs.DocumentedMessage", 1))
                .isPresent()
                .contains("Field documentation");
    }

    @Test
    void testEnumDocumentation() {
        // Create a FileDescriptorSet with enum documentation
        SourceCodeInfo.Location location =
                SourceCodeInfo.Location.newBuilder()
                        .addPath(5) // enum_type = 5
                        .addPath(0) // index 0
                        .setLeadingComments("Enum documentation")
                        .build();

        FileDescriptorProto file =
                FileDescriptorProto.newBuilder()
                        .setName("enum_doc_test.proto")
                        .setPackage("test.docs")
                        .addEnumType(
                                EnumDescriptorProto.newBuilder()
                                        .setName("DocumentedEnum")
                                        .addValue(
                                                EnumValueDescriptorProto.newBuilder()
                                                        .setName("UNKNOWN")
                                                        .setNumber(0)))
                        .setSourceCodeInfo(SourceCodeInfo.newBuilder().addLocation(location))
                        .build();

        FileDescriptorSet docFileDescriptorSet =
                FileDescriptorSet.newBuilder().addFile(file).build();

        ProtoToJsonSchemaGenerator.Registry docRegistry =
                ProtoToJsonSchemaGenerator.Registry.from(docFileDescriptorSet);

        // Test enum documentation lookup
        assertThat(docRegistry.enumDoc("test.docs.DocumentedEnum"))
                .isPresent()
                .contains("Enum documentation");
    }
}
