package com.ghatana.platform.testing.async;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class AsyncTestUtilsTest {

    @Test
    void await_shouldReturnFutureResult() {
        // Given
        CompletableFuture<String> future = CompletableFuture.completedFuture("test");
        
        // When
        String result = AsyncTestUtils.await(future, Duration.ofSeconds(1));
        
        // Then
        assertThat(result).isEqualTo("test");
    }

    @Test
    void await_shouldThrowOnTimeout() {
        // Given
        CompletableFuture<String> future = new CompletableFuture<>();
        
        // When/Then
        assertThatThrownBy(() -> AsyncTestUtils.await(future, Duration.ofMillis(100)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Timed out");
    }

    @Test
    void await_shouldThrowOnException() {
        // Given
        CompletableFuture<String> future = CompletableFuture.failedFuture(new RuntimeException("test error"));
        
        // When/Then
        assertThatThrownBy(() -> AsyncTestUtils.await(future, Duration.ofSeconds(1)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("test error");
    }

    @Test
    void awaitCondition_shouldReturnWhenConditionIsMet() {
        // Given
        AtomicBoolean condition = new AtomicBoolean(false);
        
        // When
        new Thread(() -> {
            try {
                Thread.sleep(100);
                condition.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        // Then
        assertDoesNotThrow(() -> 
            AsyncTestUtils.await(condition::get, Duration.ofSeconds(1), Duration.ofMillis(10))
        );
    }

    @Test
    void awaitCondition_shouldThrowOnTimeout() {
        // Given
        AtomicBoolean condition = new AtomicBoolean(false);
        
        // When/Then
        assertThatThrownBy(() -> 
            AsyncTestUtils.await(condition::get, Duration.ofMillis(100), Duration.ofMillis(10))
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Condition not met");
    }

    @Test
    void delayedFuture_shouldCompleteAfterDelay() {
        // Given
        long startTime = System.currentTimeMillis();
        CompletableFuture<String> future = AsyncTestUtils.delayedFuture("test", Duration.ofMillis(100));
        
        // When
        String result = future.join();
        long duration = System.currentTimeMillis() - startTime;
        
        // Then
        assertThat(result).isEqualTo("test");
        assertThat(duration).isGreaterThanOrEqualTo(100);
    }

    @Test
    void delayedFailure_shouldFailAfterDelay() {
        // Given
        CompletableFuture<String> future = AsyncTestUtils.delayedFailure(
            new RuntimeException("test error"), 
            Duration.ofMillis(100)
        );
        
        // When/Then
        assertThatThrownBy(future::join)
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(RuntimeException.class)
            .hasMessageContaining("test error");
    }
}
