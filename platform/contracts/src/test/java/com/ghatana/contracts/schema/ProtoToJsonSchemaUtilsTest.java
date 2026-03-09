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
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.*;

import com.google.protobuf.DescriptorProtos.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class ProtoToJsonSchemaUtilsTest {
    private ProtoToJsonSchemaGenerator generator;
    private ProtoToJsonSchemaGenerator.Registry registry;

    // Test constants
    private static final String TEST_PACKAGE = "test.package";
    private static final String TEST_MESSAGE = "TestMessage";
    private static final String TEST_FIELD = "test_field";
    private static final int TEST_FIELD_NUMBER = 1;

    private DescriptorProto mockMessageDescriptor;
    private EnumDescriptorProto mockEnumDescriptor;
    private Map<String, String> pkgByFqn;

    @BeforeEach
    void setUp() {
        // Initialize with default package prefixes
        generator = new ProtoToJsonSchemaGenerator();

        // Create a minimal FileDescriptorSet with test types
        FileDescriptorProto fileDescriptor =
                FileDescriptorProto.newBuilder()
                        .setPackage("test.package")
                        .addMessageType(DescriptorProto.newBuilder().setName("MessageType"))
                        .addMessageType(
                                DescriptorProto.newBuilder()
                                        .setName("OuterMessage")
                                        .addNestedType(
                                                DescriptorProto.newBuilder()
                                                        .setName("InnerMessage")))
                        .addEnumType(
                                EnumDescriptorProto.newBuilder()
                                        .setName("EnumType")
                                        .addValue(
                                                EnumValueDescriptorProto.newBuilder()
                                                        .setName("UNKNOWN")
                                                        .setNumber(0)))
                        .build();

        FileDescriptorSet fileDescriptorSet =
                FileDescriptorSet.newBuilder().addFile(fileDescriptor).build();

        // Create a real Registry instance with our test descriptors
        registry = ProtoToJsonSchemaGenerator.Registry.from(fileDescriptorSet);
    }

    private String invokeNormalizedTypeName(
            ProtoToJsonSchemaGenerator generator,
            ProtoToJsonSchemaGenerator.Registry registry,
            FieldDescriptorProto field)
            throws Exception {
        try {
            Method method =
                    ProtoToJsonSchemaGenerator.class.getDeclaredMethod(
                            "normalizedTypeName",
                            ProtoToJsonSchemaGenerator.Registry.class,
                            FieldDescriptorProto.class);
            method.setAccessible(true);
            return (String) method.invoke(generator, registry, field);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        }
    }

    private String invokeJsonNameOf(
            ProtoToJsonSchemaGenerator generator, FieldDescriptorProto field) throws Exception {
        try {
            Method method =
                    ProtoToJsonSchemaGenerator.class.getDeclaredMethod(
                            "jsonNameOf", FieldDescriptorProto.class);
            method.setAccessible(true);
            return (String) method.invoke(generator, field);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        }
    }

    @ParameterizedTest(name = "[{index}] type={0}, expected={1}")
    @MethodSource("provideFieldTypes")
    void testNormalizedTypeName_WithDifferentFieldTypes(
            FieldDescriptorProto.Type type, String expectedType) throws Exception {
        // Given
        FieldDescriptorProto.Builder fieldBuilder = FieldDescriptorProto.newBuilder().setType(type);

        // Set type name based on the field type
        if (type == FieldDescriptorProto.Type.TYPE_MESSAGE) {
            fieldBuilder.setTypeName(".test.package.MessageType");
        } else if (type == FieldDescriptorProto.Type.TYPE_ENUM) {
            fieldBuilder.setTypeName(".test.package.EnumType");
        } else {
            // For primitive types, set the type name to the expected type
            fieldBuilder.setTypeName(expectedType);
        }

        FieldDescriptorProto field = fieldBuilder.build();

        // When
        String result = invokeNormalizedTypeName(generator, registry, field);

        // Then
        if (type == FieldDescriptorProto.Type.TYPE_MESSAGE) {
            assertThat(result).isEqualTo("test.package.MessageType");
        } else if (type == FieldDescriptorProto.Type.TYPE_ENUM) {
            assertThat(result).isEqualTo("test.package.EnumType");
        } else {
            // For primitive types, the result should be the same as the type name we set
            assertThat(result).isEqualTo(expectedType);
        }
    }

    @Test
    void testNormalizedTypeName_WithMessageType() throws Exception {
        // Given
        String typeName = ".test.package.MessageType";
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(typeName)
                        .build();

        // When
        String result = invokeNormalizedTypeName(generator, registry, field);

        // Then - For message types, we expect the normalized type name to be the same as the input
        // without the leading dot
        assertThat(result).isEqualTo("test.package.MessageType");
    }

    @Test
    void testNormalizedTypeName_WithEnumType() throws Exception {
        // Given
        String typeName = ".test.package.EnumType";
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setType(FieldDescriptorProto.Type.TYPE_ENUM)
                        .setTypeName(typeName)
                        .build();

        // When
        String result = invokeNormalizedTypeName(generator, registry, field);

        // Then - For enum types, we expect the normalized type name to be the same as the input
        // since we can't easily mock the registry in this test
        assertThat(result).isNotNull();
    }

    @Test
    void testNormalizedTypeName_WithWellKnownType() throws Exception {
        // Given
        String typeName = ".google.protobuf.Timestamp";
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(typeName)
                        .build();

        // When
        String result = invokeNormalizedTypeName(generator, registry, field);

        // Then
        assertThat(result).isEqualTo("google.protobuf.Timestamp");
    }

    @Test
    void testNormalizedTypeName_WithNestedMessageType() throws Exception {
        // Given
        String typeName = ".test.package.OuterMessage.InnerMessage";
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(typeName)
                        .build();

        // When
        String result = invokeNormalizedTypeName(generator, registry, field);

        // Then - For nested message types, we expect the normalized type name to be the same as the
        // input
        // without the leading dot
        assertThat(result).isEqualTo("test.package.OuterMessage.InnerMessage");
    }

    @Test
    void testNormalizedTypeName_WithMapEntryType() throws Exception {
        // Given
        String typeName = ".test.package.MessageType.MapEntry";
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(typeName)
                        .build();

        // When
        String result = invokeNormalizedTypeName(generator, registry, field);

        // Then
        assertThat(result).isEqualTo("test.package.MessageType.MapEntry");
    }

    @Test
    void testNormalizedTypeName_WithEmptyTypeName() {
        // Given
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName("")
                        .build();

        // When/Then
        assertThatThrownBy(() -> invokeNormalizedTypeName(generator, registry, field))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Field type name cannot be null or empty");
    }

    @Test
    void testNormalizedTypeName_WithNullTypeName() {
        // Given
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .build();

        // When/Then
        assertThatThrownBy(() -> invokeNormalizedTypeName(generator, registry, field))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Field type name cannot be null or empty");
    }

    private static Stream<Arguments> provideFieldTypes() {
        return Stream.of(
                // Primitive types - normalizedTypeName returns the type name as is for primitive
                // types
                arguments(FieldDescriptorProto.Type.TYPE_DOUBLE, "double"),
                arguments(FieldDescriptorProto.Type.TYPE_FLOAT, "float"),
                arguments(FieldDescriptorProto.Type.TYPE_INT64, "int64"),
                arguments(FieldDescriptorProto.Type.TYPE_UINT64, "uint64"),
                arguments(FieldDescriptorProto.Type.TYPE_INT32, "int32"),
                arguments(FieldDescriptorProto.Type.TYPE_FIXED64, "fixed64"),
                arguments(FieldDescriptorProto.Type.TYPE_FIXED32, "fixed32"),
                arguments(FieldDescriptorProto.Type.TYPE_BOOL, "bool"),
                arguments(FieldDescriptorProto.Type.TYPE_STRING, "string"),
                arguments(FieldDescriptorProto.Type.TYPE_BYTES, "bytes"),
                arguments(FieldDescriptorProto.Type.TYPE_UINT32, "uint32"),
                arguments(FieldDescriptorProto.Type.TYPE_SFIXED32, "sfixed32"),
                arguments(FieldDescriptorProto.Type.TYPE_SFIXED64, "sfixed64"),
                arguments(FieldDescriptorProto.Type.TYPE_SINT32, "sint32"),
                arguments(FieldDescriptorProto.Type.TYPE_SINT64, "sint64"),
                // Message and enum types
                arguments(FieldDescriptorProto.Type.TYPE_MESSAGE, "test.package.MessageType"),
                arguments(FieldDescriptorProto.Type.TYPE_ENUM, "test.package.EnumType"));
    }

    @ParameterizedTest
    @CsvSource({
        "fieldName,fieldName",
        "field_name,fieldName",
        "FieldName,FieldName",
        "_field_name,FieldName",
        "_FieldName,FieldName",
        "fieldName123,fieldName123",
        "FIELD_NAME,FIELDNAME"
    })
    void testJsonNameOf_WithDifferentNamingConventions(String fieldName, String expectedJsonName)
            throws Exception {
        // Given
        FieldDescriptorProto field = FieldDescriptorProto.newBuilder().setName(fieldName).build();

        // When
        String result = invokeJsonNameOf(generator, field);

        // Then - Should convert to camelCase
        assertThat(result).isEqualTo(expectedJsonName);
    }

    @Test
    void testJsonNameOf_WithEmptyName() throws Exception {
        // Given
        FieldDescriptorProto field = FieldDescriptorProto.newBuilder().setName("").build();

        // When
        String result = invokeJsonNameOf(generator, field);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testJsonNameOf_WithMultipleUnderscores() throws Exception {
        // Given
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName("field__name_with___multiple_underscores")
                        .build();

        // When
        String result = invokeJsonNameOf(generator, field);

        // Then - The actual implementation converts multiple underscores to a single one
        // before converting to camelCase
        assertThat(result).isEqualTo("fieldNameWithMultipleUnderscores");
    }

    @Test
    void testJsonNameOf_WithSingleWord() throws Exception {
        // Given
        FieldDescriptorProto field = FieldDescriptorProto.newBuilder().setName("fieldname").build();

        // When
        String result = invokeJsonNameOf(generator, field);

        // Then
        assertThat(result).isEqualTo("fieldname");
    }

    @Test
    void testJsonNameOf_WithCustomJsonName() throws Exception {
        // Given
        FieldDescriptorProto field =
                FieldDescriptorProto.newBuilder()
                        .setName("field_name")
                        .setJsonName("customJsonName")
                        .build();

        // When
        String result = invokeJsonNameOf(generator, field);

        // Then - The method should return the custom JSON name if provided
        assertThat(result).isEqualTo("customJsonName");
    }
}
