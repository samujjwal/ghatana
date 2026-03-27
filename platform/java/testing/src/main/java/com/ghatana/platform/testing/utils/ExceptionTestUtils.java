package com.ghatana.platform.testing.utils;

import com.ghatana.platform.core.exception.BaseException;
import org.assertj.core.api.AbstractThrowableAssert;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Testing utilities for exception-related assertions.
 *
 * <p>Provides ergonomic helpers for verifying that code under test throws the
 * correct exception types with expected error codes, messages, and metadata.
 *
 * <p>Usage:
 * <pre>{@code
 * ExceptionTestUtils.assertThrowsBaseException(ValidationException.class,
 *     () -> service.create(null),
 *     ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Test utilities for exception-related assertions
 * @doc.layer platform
 * @doc.pattern Utility, TestHelper
 *
 * @since 2026-03-27
 */
public final class ExceptionTestUtils {

    private ExceptionTestUtils() {} // Utility class

    /**
     * Asserts that the callable throws the given exception type and invokes the
     * optional extra assertions on the caught exception.
     *
     * @param exceptionType the expected exception type
     * @param callable      the code to invoke
     * @param extraAsserts  optional consumer for additional assertions on the exception
     * @param <E>           the exception type
     */
    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void assertThrows(
        Class<E> exceptionType,
        Callable<?> callable,
        Consumer<E> extraAsserts
    ) {
        AbstractThrowableAssert<?, ? extends Throwable> assertion =
            assertThatThrownBy(() -> {
                try {
                    callable.call();
                } catch (Exception e) {
                    throw e;
                }
            }).isInstanceOf(exceptionType);

        if (extraAsserts != null) {
            assertion.satisfies(e -> extraAsserts.accept((E) e));
        }
    }

    /**
     * Asserts that the callable throws the given {@link BaseException} subtype.
     *
     * @param exceptionType the expected BaseException subtype
     * @param callable      the code to invoke
     * @param extraAsserts  optional consumer for additional assertions
     * @param <E>           the BaseException subtype
     */
    public static <E extends BaseException> void assertThrowsBaseException(
        Class<E> exceptionType,
        Callable<?> callable,
        Consumer<E> extraAsserts
    ) {
        assertThrows(exceptionType, callable, extraAsserts);
    }

    /**
     * Asserts that the callable throws the given {@link BaseException} subtype
     * with the given message substring.
     *
     * @param exceptionType     the expected exception type
     * @param callable          the code to invoke
     * @param messageSubstring  expected substring in the exception message
     * @param <E>               the BaseException subtype
     */
    public static <E extends BaseException> void assertThrowsWithMessage(
        Class<E> exceptionType,
        Callable<?> callable,
        String messageSubstring
    ) {
        assertThatThrownBy(() -> {
            try {
                callable.call();
            } catch (Exception e) {
                throw e;
            }
        }).isInstanceOf(exceptionType)
          .hasMessageContaining(messageSubstring);
    }

    /**
     * Asserts that the callable does NOT throw any exception.
     *
     * @param callable the code to invoke
     */
    public static void assertNoException(Callable<?> callable) {
        assertThatCode(() -> callable.call()).doesNotThrowAnyException();
    }
}
