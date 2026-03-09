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

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtoToJsonSchemaConfigTest {

    @TempDir Path tempDir;

    @Test
    void testParse_WithAllParameters() {
        // Given
        String[] args =
                new String[] {
                    "--descriptorSet=path/to/descriptor.desc",
                    "--outDir=output/dir",
                    "--messages=com.example.Message1,com.example.Message2",
                    "--bundleName=custom.bundle.json"
                };

        // When
        ProtoToJsonSchemaGenerator.Config config = ProtoToJsonSchemaGenerator.Config.parse(args);

        // Then
        assertThat(config.descriptorSet().toString()).isEqualTo("path/to/descriptor.desc");
        assertThat(config.outDir().toString()).isEqualTo("output/dir");
        assertThat(config.messages())
                .containsExactlyInAnyOrder("com.example.Message1", "com.example.Message2");
        assertThat(config.bundleName()).isEqualTo("custom.bundle.json");
    }

    @Test
    void testParse_WithRequiredParametersOnly() {
        // Given
        String[] args =
                new String[] {"--descriptorSet=path/to/descriptor.desc", "--outDir=output/dir"};

        // When
        ProtoToJsonSchemaGenerator.Config config = ProtoToJsonSchemaGenerator.Config.parse(args);

        // Then
        assertThat(config.descriptorSet().toString()).isEqualTo("path/to/descriptor.desc");
        assertThat(config.outDir().toString()).isEqualTo("output/dir");
        assertThat(config.messages()).isEmpty();
        assertThat(config.bundleName()).isEqualTo("bundle.schema.json");
    }

    @Test
    void testParse_WithEmptyMessages() {
        // Given
        String[] args =
                new String[] {
                    "--descriptorSet=path/to/descriptor.desc", "--outDir=output/dir", "--messages="
                };

        // When
        ProtoToJsonSchemaGenerator.Config config = ProtoToJsonSchemaGenerator.Config.parse(args);

        // Then
        assertThat(config.messages()).isEmpty();
    }

    @Test
    void testParse_WithWhitespaceInMessages() {
        // Given
        String[] args =
                new String[] {
                    "--descriptorSet=path/to/descriptor.desc",
                    "--outDir=output/dir",
                    "--messages=  com.example.Message1 ,  com.example.Message2  "
                };

        // When
        ProtoToJsonSchemaGenerator.Config config = ProtoToJsonSchemaGenerator.Config.parse(args);

        // Then
        assertThat(config.messages())
                .containsExactlyInAnyOrder("com.example.Message1", "com.example.Message2");
    }

    @Test
    void testParse_MissingDescriptorSet() {
        // Given
        String[] args = new String[] {"--outDir=output/dir"};

        // When/Then
        assertThatThrownBy(() -> ProtoToJsonSchemaGenerator.Config.parse(args))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("--descriptorSet is required");
    }

    @Test
    void testParse_MissingOutDir() {
        // Given
        String[] args = new String[] {"--descriptorSet=path/to/descriptor.desc"};

        // When/Then
        assertThatThrownBy(() -> ProtoToJsonSchemaGenerator.Config.parse(args))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("--outDir is required");
    }

    @Test
    void testEqualsAndHashCode() {
        // Given
        Path descriptorSet1 = tempDir.resolve("desc1.desc");
        Path outDir1 = tempDir.resolve("out1");
        Path descriptorSet2 = tempDir.resolve("desc2.desc");
        Path outDir2 = tempDir.resolve("out2");

        // Create descriptor set files
        try {
            Files.createDirectories(descriptorSet1.getParent());
            Files.createFile(descriptorSet1);
            Files.createDirectories(descriptorSet2.getParent());
            Files.createFile(descriptorSet2);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test files", e);
        }

        // When
        ProtoToJsonSchemaGenerator.Config config1 =
                ProtoToJsonSchemaGenerator.Config.parse(
                        new String[] {
                            "--descriptorSet=" + descriptorSet1.toAbsolutePath(),
                            "--outDir=" + outDir1.toAbsolutePath(),
                            "--messages=com.example.Message1",
                            "--bundleName=bundle1.json"
                        });

        ProtoToJsonSchemaGenerator.Config config2 =
                ProtoToJsonSchemaGenerator.Config.parse(
                        new String[] {
                            "--descriptorSet=" + descriptorSet1.toAbsolutePath(),
                            "--outDir=" + outDir1.toAbsolutePath(),
                            "--messages=com.example.Message1",
                            "--bundleName=bundle1.json"
                        });

        ProtoToJsonSchemaGenerator.Config config3 =
                ProtoToJsonSchemaGenerator.Config.parse(
                        new String[] {
                            "--descriptorSet=" + descriptorSet2.toAbsolutePath(),
                            "--outDir=" + outDir2.toAbsolutePath(),
                            "--messages=com.example.Message2",
                            "--bundleName=bundle2.json"
                        });

        // Then
        assertThat(config1).isEqualTo(config2);
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());

        assertThat(config1).isNotEqualTo(config3);
        assertThat(config1.hashCode()).isNotEqualTo(config3.hashCode());
    }

    @Test
    void testToString() {
        // Given
        Path descriptorSet = tempDir.resolve("test.desc");
        Path outDir = tempDir.resolve("out");

        // Create descriptor set file
        try {
            Files.createDirectories(descriptorSet.getParent());
            Files.createFile(descriptorSet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test file", e);
        }

        // When
        ProtoToJsonSchemaGenerator.Config config =
                ProtoToJsonSchemaGenerator.Config.parse(
                        new String[] {
                            "--descriptorSet=" + descriptorSet.toAbsolutePath(),
                            "--outDir=" + outDir.toAbsolutePath(),
                            "--messages=com.example.Message1,com.example.Message2",
                            "--bundleName=custom.bundle.json"
                        });

        String str = config.toString();

        // Then
        assertThat(str).contains(descriptorSet.toString());
        assertThat(str).contains(outDir.toString());
        assertThat(str).contains("com.example.Message1");
        assertThat(str).contains("com.example.Message2");
        assertThat(str).contains("custom.bundle.json");
    }
}
