package com.ghatana.platform.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Result contract")
class ResultContractTest {

    @Test
    @DisplayName("map transforms only success values")
    void mapTransformsOnlySuccessValues() {
        Result<Integer, String> success = Result.success(21);
        Result<Integer, String> failure = Result.failure("bad-input");

        assertThat(success.map(value -> value * 2))
            .isEqualTo(Result.success(42));
        assertThat(failure.map(value -> value * 2))
            .isEqualTo(Result.failure("bad-input"));
    }

    @Test
    @DisplayName("mapError transforms only failure values")
    void mapErrorTransformsOnlyFailureValues() {
        Result<Integer, String> success = Result.success(21);
        Result<Integer, String> failure = Result.failure("bad-input");

        assertThat(success.mapError(String::length))
            .isEqualTo(Result.success(21));
        assertThat(failure.mapError(String::length))
            .isEqualTo(Result.failure(9));
    }
}
