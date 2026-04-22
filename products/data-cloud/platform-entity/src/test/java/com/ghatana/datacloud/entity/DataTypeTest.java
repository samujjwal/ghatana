/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void allEnumValuesShouldBeAccessible() { // GH-90000
        DataType[] types = DataType.values(); // GH-90000
        assertThat(types).hasSizeGreaterThanOrEqualTo(24); // GH-90000
    }

    @ParameterizedTest
    @EnumSource(DataType.class) // GH-90000
    void eachEnumValueShouldHaveNonNullName(DataType type) { // GH-90000
        assertThat(type.name()).isNotNull().isNotEmpty(); // GH-90000
    }

    @Test
    void basicTypesShouldExist() { // GH-90000
        // Primitive types
        assertThat(DataType.STRING).isNotNull(); // GH-90000
        assertThat(DataType.NUMBER).isNotNull(); // GH-90000
        assertThat(DataType.BOOLEAN).isNotNull(); // GH-90000
    }

    @Test
    void dateTimeTypesShouldExist() { // GH-90000
        assertThat(DataType.DATE).isNotNull(); // GH-90000
        assertThat(DataType.DATETIME).isNotNull(); // GH-90000
        assertThat(DataType.TIME).isNotNull(); // GH-90000
    }

    @Test
    void complexTypesShouldExist() { // GH-90000
        assertThat(DataType.REFERENCE).isNotNull(); // GH-90000
        assertThat(DataType.ARRAY).isNotNull(); // GH-90000
        assertThat(DataType.EMBEDDED).isNotNull(); // GH-90000
        assertThat(DataType.JSON).isNotNull(); // GH-90000
    }

    @Test
    void mediaTypesShouldExist() { // GH-90000
        assertThat(DataType.IMAGE).isNotNull(); // GH-90000
        assertThat(DataType.FILE).isNotNull(); // GH-90000
    }

    @Test
    void validationTypesShouldExist() { // GH-90000
        assertThat(DataType.EMAIL).isNotNull(); // GH-90000
        assertThat(DataType.URL).isNotNull(); // GH-90000
        assertThat(DataType.PHONE).isNotNull(); // GH-90000
    }

    @Test
    void formattingTypesShouldExist() { // GH-90000
        assertThat(DataType.RICHTEXT).isNotNull(); // GH-90000
        assertThat(DataType.MARKDOWN).isNotNull(); // GH-90000
        assertThat(DataType.COLOR).isNotNull(); // GH-90000
    }

    @Test
    void specializedTypesShouldExist() { // GH-90000
        assertThat(DataType.UUID).isNotNull(); // GH-90000
        assertThat(DataType.ENUM).isNotNull(); // GH-90000
        assertThat(DataType.GEOLOCATION).isNotNull(); // GH-90000
        assertThat(DataType.CURRENCY).isNotNull(); // GH-90000
        assertThat(DataType.PERCENTAGE).isNotNull(); // GH-90000
        assertThat(DataType.RATING).isNotNull(); // GH-90000
        assertThat(DataType.TAGS).isNotNull(); // GH-90000
    }

    @Test
    void valueOfShouldReturnCorrectEnum() { // GH-90000
        assertThat(DataType.valueOf("STRING [GH-90000]")).isEqualTo(DataType.STRING);
        assertThat(DataType.valueOf("NUMBER [GH-90000]")).isEqualTo(DataType.NUMBER);
        assertThat(DataType.valueOf("REFERENCE [GH-90000]")).isEqualTo(DataType.REFERENCE);
        assertThat(DataType.valueOf("JSON [GH-90000]")).isEqualTo(DataType.JSON);
    }

    @Test
    void ordinalShouldBeConsistent() { // GH-90000
        // Ensure ordinal values are stable (important for serialization) // GH-90000
        assertThat(DataType.STRING.ordinal()).isEqualTo(0); // GH-90000
        assertThat(DataType.NUMBER.ordinal()).isEqualTo(1); // GH-90000
        assertThat(DataType.BOOLEAN.ordinal()).isEqualTo(2); // GH-90000
    }
}
