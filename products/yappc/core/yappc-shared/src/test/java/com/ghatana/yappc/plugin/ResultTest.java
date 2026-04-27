package com.ghatana.yappc.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for Result canonical discriminated-result type
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Result")
class ResultTest {

    // =========================================================================
    // Factory: success()
    // =========================================================================

    @Test
    @DisplayName("success() creates a Success with the given value")
    void success_createsSuccessWithValue() {
        Result<String> result = Result.success("hello");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isFailure()).isFalse();
        assertThat(result.getValue()).isEqualTo("hello");
    }

    @Test
    @DisplayName("success() throws when value is null")
    void success_throwsOnNullValue() {
        assertThatThrownBy(() -> Result.success(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // Factory: failure()
    // =========================================================================

    @Test
    @DisplayName("failure(message) creates a Failure with the given message")
    void failure_createsFailureWithMessage() {
        Result<String> result = Result.failure("something went wrong");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("something went wrong");
        assertThat(result.getCause()).isEmpty();
    }

    @Test
    @DisplayName("failure(message, cause) carries the cause")
    void failure_createsFailureWithMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        Result<String> result = Result.failure("wrapped", cause);

        assertThat(result.getError()).isEqualTo("wrapped");
        assertThat(result.getCause()).isPresent().containsSame(cause);
    }

    @Test
    @DisplayName("failure() throws when message is blank")
    void failure_throwsOnBlankMessage() {
        assertThatThrownBy(() -> Result.failure("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // getValue / getError — wrong-side access
    // =========================================================================

    @Test
    @DisplayName("getValue() on Failure throws NoSuchElementException")
    void getValue_onFailureThrows() {
        Result<String> result = Result.failure("err");

        assertThatThrownBy(result::getValue)
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("getError() on Success throws NoSuchElementException")
    void getError_onSuccessThrows() {
        Result<String> result = Result.success("ok");

        assertThatThrownBy(result::getError)
                .isInstanceOf(NoSuchElementException.class);
    }

    // =========================================================================
    // getOrElse
    // =========================================================================

    @Test
    @DisplayName("getOrElse() returns value on success")
    void getOrElse_returnsValueOnSuccess() {
        assertThat(Result.success("real").getOrElse("default")).isEqualTo("real");
    }

    @Test
    @DisplayName("getOrElse() returns default on failure")
    void getOrElse_returnsDefaultOnFailure() {
        assertThat(Result.<String>failure("err").getOrElse("default")).isEqualTo("default");
    }

    // =========================================================================
    // getOrThrow
    // =========================================================================

    @Test
    @DisplayName("getOrThrow() returns value on success")
    void getOrThrow_returnsValueOnSuccess() {
        assertThat(Result.success(42).getOrThrow()).isEqualTo(42);
    }

    @Test
    @DisplayName("getOrThrow() re-throws RuntimeException cause when present")
    void getOrThrow_rethrowsRuntimeExceptionCause() {
        RuntimeException cause = new RuntimeException("bang");
        Result<String> result = Result.failure("wrapped", cause);

        assertThatThrownBy(result::getOrThrow).isSameAs(cause);
    }

    @Test
    @DisplayName("getOrThrow() wraps checked exception cause")
    void getOrThrow_wrapsCheckedExceptionCause() {
        Exception checkedException = new Exception("checked");
        Result<String> result = Result.failure("wrapped", checkedException);

        assertThatThrownBy(result::getOrThrow)
                .isInstanceOf(RuntimeException.class)
                .hasCause(checkedException);
    }

    @Test
    @DisplayName("getOrThrow() throws RuntimeException with message when no cause")
    void getOrThrow_throwsRuntimeExceptionWithMessageWhenNoCause() {
        assertThatThrownBy(Result.<String>failure("no cause")::getOrThrow)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no cause");
    }

    // =========================================================================
    // map
    // =========================================================================

    @Test
    @DisplayName("map() transforms success value")
    void map_transformsSuccess() {
        Result<Integer> result = Result.success("hello").map(String::length);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isEqualTo(5);
    }

    @Test
    @DisplayName("map() propagates failure unchanged")
    void map_propagatesFailure() {
        Result<Integer> result = Result.<String>failure("err").map(String::length);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).isEqualTo("err");
    }

    @Test
    @DisplayName("map() converts mapper exception to Failure")
    void map_convertsMapperExceptionToFailure() {
        Result<Integer> result = Result.success("hello").map(s -> {
            throw new RuntimeException("mapper error");
        });

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).contains("mapper error");
    }

    // =========================================================================
    // flatMap
    // =========================================================================

    @Test
    @DisplayName("flatMap() chains to next Result on success")
    void flatMap_chainsOnSuccess() {
        Result<Integer> result = Result.success("hello")
                .flatMap(s -> Result.success(s.length()));

        assertThat(result.getValue()).isEqualTo(5);
    }

    @Test
    @DisplayName("flatMap() short-circuits on failure")
    void flatMap_shortCircuitsOnFailure() {
        AtomicBoolean called = new AtomicBoolean(false);
        Result<Integer> result = Result.<String>failure("err")
                .flatMap(s -> {
                    called.set(true);
                    return Result.success(s.length());
                });

        assertThat(called.get()).isFalse();
        assertThat(result.isFailure()).isTrue();
    }

    // =========================================================================
    // tryGet
    // =========================================================================

    @Test
    @DisplayName("tryGet() wraps a successful call in Success")
    void tryGet_wrapsSuccess() {
        Result<String> result = Result.tryGet(() -> "computed");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isEqualTo("computed");
    }

    @Test
    @DisplayName("tryGet() converts exceptions to Failure")
    void tryGet_convertsException() {
        Result<String> result = Result.tryGet(() -> {
            throw new Exception("boom");
        });

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).isEqualTo("boom");
        assertThat(result.getCause()).isPresent();
    }

    // =========================================================================
    // Side-effects: ifSuccess / ifFailure
    // =========================================================================

    @Test
    @DisplayName("ifSuccess() invokes action on success")
    void ifSuccess_invokesActionOnSuccess() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Result.success("val").ifSuccess(v -> invoked.set(true));

        assertThat(invoked.get()).isTrue();
    }

    @Test
    @DisplayName("ifSuccess() does not invoke action on failure")
    void ifSuccess_doesNotInvokeOnFailure() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Result.<String>failure("err").ifSuccess(v -> invoked.set(true));

        assertThat(invoked.get()).isFalse();
    }

    @Test
    @DisplayName("ifFailure() invokes action on failure")
    void ifFailure_invokesActionOnFailure() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Result.failure("err").ifFailure(msg -> invoked.set(true));

        assertThat(invoked.get()).isTrue();
    }

    @Test
    @DisplayName("ifFailure() does not invoke action on success")
    void ifFailure_doesNotInvokeOnSuccess() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Result.success("val").ifFailure(msg -> invoked.set(true));

        assertThat(invoked.get()).isFalse();
    }
}
