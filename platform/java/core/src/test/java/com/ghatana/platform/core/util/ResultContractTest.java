package com.ghatana.platform.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Result contract")
class ResultContractTest {

    @Test
    @DisplayName("map transforms only success values")
    void mapTransformsOnlySuccessValues() { // GH-90000
        Result<Integer, String> success = Result.success(21); // GH-90000
        Result<Integer, String> failure = Result.failure("bad-input");

        assertThat(success.map(value -> value * 2)) // GH-90000
            .isEqualTo(Result.success(42)); // GH-90000
        assertThat(failure.map(value -> value * 2)) // GH-90000
            .isEqualTo(Result.failure("bad-input"));
    }

    @Test
    @DisplayName("mapError transforms only failure values")
    void mapErrorTransformsOnlyFailureValues() { // GH-90000
        Result<Integer, String> success = Result.success(21); // GH-90000
        Result<Integer, String> failure = Result.failure("bad-input");

        assertThat(success.mapError(String::length)) // GH-90000
            .isEqualTo(Result.success(21)); // GH-90000
        assertThat(failure.mapError(String::length)) // GH-90000
            .isEqualTo(Result.failure(9)); // GH-90000
    }
}
