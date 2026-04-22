package com.ghatana.platform.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Result contract [GH-90000]")
class ResultContractTest {

    @Test
    @DisplayName("map transforms only success values [GH-90000]")
    void mapTransformsOnlySuccessValues() { // GH-90000
        Result<Integer, String> success = Result.success(21); // GH-90000
        Result<Integer, String> failure = Result.failure("bad-input [GH-90000]");

        assertThat(success.map(value -> value * 2)) // GH-90000
            .isEqualTo(Result.success(42)); // GH-90000
        assertThat(failure.map(value -> value * 2)) // GH-90000
            .isEqualTo(Result.failure("bad-input [GH-90000]"));
    }

    @Test
    @DisplayName("mapError transforms only failure values [GH-90000]")
    void mapErrorTransformsOnlyFailureValues() { // GH-90000
        Result<Integer, String> success = Result.success(21); // GH-90000
        Result<Integer, String> failure = Result.failure("bad-input [GH-90000]");

        assertThat(success.mapError(String::length)) // GH-90000
            .isEqualTo(Result.success(21)); // GH-90000
        assertThat(failure.mapError(String::length)) // GH-90000
            .isEqualTo(Result.failure(9)); // GH-90000
    }
}
