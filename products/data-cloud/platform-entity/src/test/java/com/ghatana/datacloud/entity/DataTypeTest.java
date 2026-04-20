/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DataType} enum.
 */
class DataTypeTest {

    @Test
    void allEnumValuesShouldBeAccessible() {
        DataType[] types = DataType.values();
        assertThat(types).hasSizeGreaterThanOrEqualTo(24);
    }

    @ParameterizedTest
    @EnumSource(DataType.class)
    void eachEnumValueShouldHaveNonNullName(DataType type) {
        assertThat(type.name()).isNotNull().isNotEmpty();
    }

    @Test
    void basicTypesShouldExist() {
        // Primitive types
        assertThat(DataType.STRING).isNotNull();
        assertThat(DataType.NUMBER).isNotNull();
        assertThat(DataType.BOOLEAN).isNotNull();
    }

    @Test
    void dateTimeTypesShouldExist() {
        assertThat(DataType.DATE).isNotNull();
        assertThat(DataType.DATETIME).isNotNull();
        assertThat(DataType.TIME).isNotNull();
    }

    @Test
    void complexTypesShouldExist() {
        assertThat(DataType.REFERENCE).isNotNull();
        assertThat(DataType.ARRAY).isNotNull();
        assertThat(DataType.EMBEDDED).isNotNull();
        assertThat(DataType.JSON).isNotNull();
    }

    @Test
    void mediaTypesShouldExist() {
        assertThat(DataType.IMAGE).isNotNull();
        assertThat(DataType.FILE).isNotNull();
    }

    @Test
    void validationTypesShouldExist() {
        assertThat(DataType.EMAIL).isNotNull();
        assertThat(DataType.URL).isNotNull();
        assertThat(DataType.PHONE).isNotNull();
    }

    @Test
    void formattingTypesShouldExist() {
        assertThat(DataType.RICHTEXT).isNotNull();
        assertThat(DataType.MARKDOWN).isNotNull();
        assertThat(DataType.COLOR).isNotNull();
    }

    @Test
    void specializedTypesShouldExist() {
        assertThat(DataType.UUID).isNotNull();
        assertThat(DataType.ENUM).isNotNull();
        assertThat(DataType.GEOLOCATION).isNotNull();
        assertThat(DataType.CURRENCY).isNotNull();
        assertThat(DataType.PERCENTAGE).isNotNull();
        assertThat(DataType.RATING).isNotNull();
        assertThat(DataType.TAGS).isNotNull();
    }

    @Test
    void valueOfShouldReturnCorrectEnum() {
        assertThat(DataType.valueOf("STRING")).isEqualTo(DataType.STRING);
        assertThat(DataType.valueOf("NUMBER")).isEqualTo(DataType.NUMBER);
        assertThat(DataType.valueOf("REFERENCE")).isEqualTo(DataType.REFERENCE);
        assertThat(DataType.valueOf("JSON")).isEqualTo(DataType.JSON);
    }

    @Test
    void ordinalShouldBeConsistent() {
        // Ensure ordinal values are stable (important for serialization)
        assertThat(DataType.STRING.ordinal()).isEqualTo(0);
        assertThat(DataType.NUMBER.ordinal()).isEqualTo(1);
        assertThat(DataType.BOOLEAN.ordinal()).isEqualTo(2);
    }
}
