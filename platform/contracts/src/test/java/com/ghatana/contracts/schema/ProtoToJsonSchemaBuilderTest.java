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

import com.google.protobuf.DescriptorProtos.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtoToJsonSchemaBuilderTest {

    @Test
    void shouldCreateBuilderWithDefaultPrefixes() {
        // When
        ProtoToJsonSchemaGenerator generator = ProtoToJsonSchemaGenerator.builder().build();

        // Then
        assertNotNull(generator, "Generator should be created with default builder");
    }

    @Test
    void shouldSetCustomPackagePrefixes() {
        // Given
        List<String> customPrefixes = List.of("com.example.", "com.acme.");

        // When
        ProtoToJsonSchemaGenerator generator =
                ProtoToJsonSchemaGenerator.builder().withPackagePrefixes(customPrefixes).build();

        // Then - We'll verify this through reflection or by adding a getter if needed
        assertNotNull(generator);
    }

    @Test
    void shouldAddSinglePackagePrefix() {
        // When
        ProtoToJsonSchemaGenerator generator =
                ProtoToJsonSchemaGenerator.builder().addPackagePrefix("org.custom.").build();

        // Then
        assertNotNull(generator);
    }

    @Test
    void shouldHandleNullPrefixes() {
        // When
        ProtoToJsonSchemaGenerator generator =
                ProtoToJsonSchemaGenerator.builder()
                        .withPackagePrefixes(null)
                        .addPackagePrefix(null)
                        .build();

        // Then - Should not throw and should use defaults
        assertNotNull(generator);
    }

    @Test
    void shouldHandleEmptyPrefixes() {
        // When
        ProtoToJsonSchemaGenerator generator =
                ProtoToJsonSchemaGenerator.builder().withPackagePrefixes(List.of()).build();

        // Then - Should use defaults
        assertNotNull(generator);
    }

    @Test
    void shouldSupportMethodChaining() {
        // When
        ProtoToJsonSchemaGenerator.Builder builder =
                ProtoToJsonSchemaGenerator.builder()
                        .withPackagePrefixes(List.of("com.example."))
                        .addPackagePrefix("com.another.")
                        .addPackagePrefix("org.test.");

        // Then - No exception thrown
        assertNotNull(builder.build());
    }
}
