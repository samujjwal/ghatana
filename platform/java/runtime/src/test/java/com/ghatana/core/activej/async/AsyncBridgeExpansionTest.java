/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.core.activej.async;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3 Expansion tests for {@link AsyncBridge}.
 * Tests bidirectional conversion between Promises and CompletableFutures under various scenarios.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for async promise/future bridging
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AsyncBridge - Phase 3 Expansion [GH-90000]")
class AsyncBridgeExpansionTest extends EventloopTestBase {

    // ============================================
    // RUN BLOCKING VARIATIONS (5 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("RunBlocking Variations [GH-90000]")
    class RunBlockingTests {

        @Test
        @DisplayName("Blocks correctly with long-running operations [GH-90000]")
        void blockingLongOperation() { // GH-90000
            String result = runPromise(() -> AsyncBridge.runBlocking(() -> { // GH-90000
                try {
                    Thread.sleep(50); // Simulate work // GH-90000
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                }
                return "completed";
            }));

            assertThat(result).isEqualTo("completed [GH-90000]");
        }

        @Test
        @DisplayName("Multiple sequential blocking operations complete in order [GH-90000]")
        void sequentialBlocking() { // GH-90000
            List<String> results = new ArrayList<>(); // GH-90000

            for (int i = 0; i < 5; i++) { // GH-90000
                final int idx = i;
                String result = runPromise(() -> AsyncBridge.runBlocking(() -> { // GH-90000
                    try {
                        Thread.sleep(10); // GH-90000
                    } catch (InterruptedException e) { // GH-90000
                        Thread.currentThread().interrupt(); // GH-90000
                    }
                    return "result-" + idx;
                }));
                results.add(result); // GH-90000
            }

            assertThat(results) // GH-90000
                .containsExactly("result-0", "result-1", "result-2", "result-3", "result-4"); // GH-90000
        }

        @Test
        @DisplayName("Blocking operation with null return handled [GH-90000]")
        void blockingNullReturn() { // GH-90000
            String result = runPromise(() -> AsyncBridge.runBlocking(() -> null)); // GH-90000

            assertThat(result).isNull(); // GH-90000
        }

        @Test
        @DisplayName("Blocking interrupted thread is properly handled [GH-90000]")
        void blockingInterruptedThread() { // GH-90000
            assertThatThrownBy(() -> { // GH-90000
                runPromise(() -> AsyncBridge.runBlocking(() -> { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                    try {
                        Thread.sleep(100); // GH-90000
                    } catch (InterruptedException e) { // GH-90000
                        Thread.currentThread().interrupt(); // GH-90000
                        throw new RuntimeException("Interrupted [GH-90000]");
                    }
                    return "should-not-get-here";
                }));
            }).hasMessageContaining("Interrupted [GH-90000]");
            clearFatalError(); // GH-90000
        }

        @Test
        @DisplayName("Blocking with exception is properly propagated [GH-90000]")
        void blockingExceptionPropagation() { // GH-90000
            assertThatThrownBy(() -> { // GH-90000
                runPromise(() -> AsyncBridge.runBlocking(() -> { // GH-90000
                    throw new IllegalArgumentException("invalid input [GH-90000]");
                }));
            }).hasMessageContaining("invalid input [GH-90000]");
            clearFatalError(); // GH-90000
        }
    }

    // ============================================
    // FUTURE TO PROMISE CONVERSION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Future to Promise Conversion [GH-90000]")
    class FutureToPromiseTests {

        @Test
        @DisplayName("Completed future converts to resolved promise [GH-90000]")
        void completedFutureToPromise() { // GH-90000
            CompletableFuture<String> future = CompletableFuture.completedFuture("value [GH-90000]");
            Promise<String> promise = AsyncBridge.fromFuture(future); // GH-90000

            String result = runPromise(() -> promise); // GH-90000

            assertThat(result).isEqualTo("value [GH-90000]");
        }

        @Test
        @DisplayName("Exceptional future converts to failed promise [GH-90000]")
        void exceptionalFutureToPromise() { // GH-90000
            CompletableFuture<String> future = new CompletableFuture<>(); // GH-90000
            future.completeExceptionally(new RuntimeException("future failed [GH-90000]"));
            Promise<String> promise = AsyncBridge.fromFuture(future); // GH-90000

            assertThatThrownBy(() -> { // GH-90000
                runPromise(() -> promise); // GH-90000
            }).hasMessageContaining("future failed [GH-90000]");
            clearFatalError(); // GH-90000
        }

        @Test
        @DisplayName("Future completed asynchronously resolves promise [GH-90000]")
        void asyncCompletedFutureToPromise() { // GH-90000
            CompletableFuture<String> future = new CompletableFuture<>(); // GH-90000

            new Thread(() -> { // GH-90000
                try {
                    Thread.sleep(20); // GH-90000
                    future.complete("async-value [GH-90000]");
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                }
            }).start(); // GH-90000

            Promise<String> promise = AsyncBridge.fromFuture(future); // GH-90000
            String result = runPromise(() -> promise); // GH-90000

            assertThat(result).isEqualTo("async-value [GH-90000]");
        }

        @Test
        @DisplayName("Many futures converted to promises in bulk [GH-90000]")
        void bulkFutureConversion() { // GH-90000
            List<CompletableFuture<Integer>> futures = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                futures.add(CompletableFuture.completedFuture(idx)); // GH-90000
            }

            List<Promise<Integer>> promises = new ArrayList<>(); // GH-90000
            for (CompletableFuture<Integer> future : futures) { // GH-90000
                promises.add(AsyncBridge.fromFuture(future)); // GH-90000
            }

            assertThat(promises).hasSize(100); // GH-90000
        }
    }

    // ============================================
    // PROMISE TO FUTURE CONVERSION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Promise to Future Conversion [GH-90000]")
    class PromiseToFutureTests {

        @Test
        @DisplayName("Resolved promise converts to completed future [GH-90000]")
        void resolvedPromiseToFuture() { // GH-90000
            Promise<String> promise = Promise.of("promise-value [GH-90000]");
            CompletableFuture<String> future = AsyncBridge.toFuture(promise); // GH-90000

            runPromise(() -> promise); // GH-90000

            assertThat(future).isCompletedWithValue("promise-value [GH-90000]");
        }

        @Test
        @DisplayName("Failed promise converts to exceptional future [GH-90000]")
        void failedPromiseToFuture() { // GH-90000
            Promise<String> promise = Promise.ofException(new RuntimeException("promise failed [GH-90000]"));
            CompletableFuture<String> future = AsyncBridge.toFuture(promise); // GH-90000

            assertThat(future).isCompletedExceptionally(); // GH-90000
            assertThatThrownBy(future::join) // GH-90000
                .hasMessageContaining("promise failed [GH-90000]");
        }

        @Test
        @DisplayName("Null promise value converts correctly [GH-90000]")
        void nullPromiseToFuture() { // GH-90000
            Promise<String> promise = Promise.of(null); // GH-90000
            CompletableFuture<String> future = AsyncBridge.toFuture(promise); // GH-90000

            runPromise(() -> promise); // GH-90000

            assertThat(future).isCompletedWithValue(null); // GH-90000
        }

        @Test
        @DisplayName("Many promises converted to futures in bulk [GH-90000]")
        void bulkPromiseConversion() { // GH-90000
            List<Promise<Integer>> promises = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                promises.add(Promise.of(idx)); // GH-90000
            }

            List<CompletableFuture<Integer>> futures = new ArrayList<>(); // GH-90000
            for (Promise<Integer> promise : promises) { // GH-90000
                futures.add(AsyncBridge.toFuture(promise)); // GH-90000
            }

            assertThat(futures).hasSize(100); // GH-90000
        }
    }

    // ============================================
    // ROUND TRIP CONVERSIONS (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Round Trip Conversions [GH-90000]")
    class RoundTripTests {

        @Test
        @DisplayName("Promise → Future → Promise preserves value [GH-90000]")
        void promiseFutureRoundTrip() { // GH-90000
            Promise<String> original = Promise.of("original-value [GH-90000]");
            CompletableFuture<String> future = AsyncBridge.toFuture(original); // GH-90000
            Promise<String> restored = AsyncBridge.fromFuture(future); // GH-90000

            runPromise(() -> original); // GH-90000
            String result = runPromise(() -> restored); // GH-90000

            assertThat(result).isEqualTo("original-value [GH-90000]");
        }

        @Test
        @DisplayName("Future → Promise → Future preserves value [GH-90000]")
        void futurePromiseRoundTrip() { // GH-90000
            CompletableFuture<String> original = CompletableFuture.completedFuture("round-trip [GH-90000]");
            Promise<String> promise = AsyncBridge.fromFuture(original); // GH-90000
            CompletableFuture<String> restored = AsyncBridge.toFuture(promise); // GH-90000

            String result = runPromise(() -> promise); // GH-90000
            assertThat(restored).isCompletedWithValue("round-trip [GH-90000]");
        }

        @Test
        @DisplayName("Exception preserved through round trip conversion [GH-90000]")
        void exceptionRoundTrip() { // GH-90000
            Promise<String> original = Promise.ofException(new RuntimeException("round-trip-error [GH-90000]"));
            CompletableFuture<String> future = AsyncBridge.toFuture(original); // GH-90000
            Promise<String> restored = AsyncBridge.fromFuture(future); // GH-90000

            assertThat(future).isCompletedExceptionally(); // GH-90000
            assertThatThrownBy(() -> { // GH-90000
                runPromise(() -> restored); // GH-90000
            }).hasMessageContaining("round-trip-error [GH-90000]");
            clearFatalError(); // GH-90000
        }
    }

    // ============================================
    // CONCURRENT CONVERSION (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Conversion [GH-90000]")
    class ConcurrentConversionTests {

        @Test
        @DisplayName("Many threads simultaneously converting futures to promises [GH-90000]")
        void concurrentFutureToPromise() { // GH-90000
            int threadCount = 20;
            List<CompletableFuture<String>> futures = new ArrayList<>(); // GH-90000
            List<Promise<String>> promises = new ArrayList<>(); // GH-90000

            for (int i = 0; i < threadCount; i++) { // GH-90000
                final int idx = i;
                futures.add(CompletableFuture.completedFuture("future-" + idx)); // GH-90000
            }

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) { // GH-90000
                final int idx = i;
                threads[i] = new Thread(() -> { // GH-90000
                    synchronized (promises) { // GH-90000
                        promises.add(AsyncBridge.fromFuture(futures.get(idx))); // GH-90000
                    }
                });
                threads[i].start(); // GH-90000
            }

            for (Thread t : threads) { // GH-90000
                try {
                    t.join(); // GH-90000
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                }
            }

            assertThat(promises).hasSize(threadCount); // GH-90000
        }

        @Test
        @DisplayName("Many threads simultaneously converting promises to futures [GH-90000]")
        void concurrentPromiseToFuture() { // GH-90000
            int threadCount = 20;
            List<Promise<String>> promises = new ArrayList<>(); // GH-90000
            List<CompletableFuture<String>> futures = new ArrayList<>(); // GH-90000

            for (int i = 0; i < threadCount; i++) { // GH-90000
                final int idx = i;
                promises.add(Promise.of("promise-" + idx)); // GH-90000
            }

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) { // GH-90000
                final int idx = i;
                threads[i] = new Thread(() -> { // GH-90000
                    synchronized (futures) { // GH-90000
                        futures.add(AsyncBridge.toFuture(promises.get(idx))); // GH-90000
                    }
                });
                threads[i].start(); // GH-90000
            }

            for (Thread t : threads) { // GH-90000
                try {
                    t.join(); // GH-90000
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                }
            }

            assertThat(futures).hasSize(threadCount); // GH-90000
        }

        @Test
        @DisplayName("Mixed conversions under concurrent load [GH-90000]")
        void mixedConcurrentConversions() throws Exception { // GH-90000
            int threadCount = 10;
            AtomicInteger conversionCount = new AtomicInteger(0); // GH-90000
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount); // GH-90000

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int i = 0; i < threadCount; i++) { // GH-90000
                    executor.submit(() -> { // GH-90000
                        try {
                            for (int j = 0; j < 25; j++) { // GH-90000
                                if (j % 2 == 0) { // GH-90000
                                    CompletableFuture<String> future = CompletableFuture.completedFuture("mixed [GH-90000]");
                                    AsyncBridge.fromFuture(future); // GH-90000
                                } else {
                                    Promise<String> promise = Promise.of("mixed [GH-90000]");
                                    AsyncBridge.toFuture(promise); // GH-90000
                                }
                                conversionCount.incrementAndGet(); // GH-90000
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }

                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                executor.shutdownNow(); // GH-90000
            }

            assertThat(conversionCount.get()).isEqualTo(threadCount * 25); // GH-90000
        }
    }

    // ============================================
    // SPECIAL CASES (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Special Cases [GH-90000]")
    class SpecialCaseTests {

        @Test
        @DisplayName("Chained blocking operations execute serially [GH-90000]")
        void chainedBlockingOps() { // GH-90000
            List<Integer> order = new ArrayList<>(); // GH-90000

            for (int i = 0; i < 5; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> AsyncBridge.runBlocking(() -> { // GH-90000
                    try {
                        Thread.sleep(5); // GH-90000
                    } catch (InterruptedException e) { // GH-90000
                        Thread.currentThread().interrupt(); // GH-90000
                    }
                    synchronized (order) { // GH-90000
                        order.add(idx); // GH-90000
                    }
                    return null;
                }));
            }

            assertThat(order).containsExactly(0, 1, 2, 3, 4); // GH-90000
        }

        @Test
        @DisplayName("Future with delay completes correctly after conversion [GH-90000]")
        void delayedFutureConversion() throws Exception { // GH-90000
            CompletableFuture<String> future = new CompletableFuture<>(); // GH-90000

            new Thread(() -> { // GH-90000
                try {
                    Thread.sleep(30); // GH-90000
                    future.complete("delayed [GH-90000]");
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                }
            }).start(); // GH-90000

            Promise<String> promise = AsyncBridge.fromFuture(future); // GH-90000
            String result = runPromise(() -> promise); // GH-90000

            assertThat(result).isEqualTo("delayed [GH-90000]");
        }

        @Test
        @DisplayName("Many levels of nested conversions [GH-90000]")
        void nestedConversions() { // GH-90000
            // Future → Promise → Future → Promise ...
            CompletableFuture<Integer> future = CompletableFuture.completedFuture(42); // GH-90000
            Promise<Integer> p1 = AsyncBridge.fromFuture(future); // GH-90000
            CompletableFuture<Integer> f1 = AsyncBridge.toFuture(p1); // GH-90000
            Promise<Integer> p2 = AsyncBridge.fromFuture(f1); // GH-90000
            CompletableFuture<Integer> f2 = AsyncBridge.toFuture(p2); // GH-90000
            Promise<Integer> p3 = AsyncBridge.fromFuture(f2); // GH-90000

            Integer result = runPromise(() -> p3); // GH-90000

            assertThat(result).isEqualTo(42); // GH-90000
        }

        @Test
        @DisplayName("Conversion with large payload completes [GH-90000]")
        void largePayloadConversion() { // GH-90000
            String largeString = "x".repeat(10000); // GH-90000
            CompletableFuture<String> future = CompletableFuture.completedFuture(largeString); // GH-90000
            Promise<String> promise = AsyncBridge.fromFuture(future); // GH-90000

            String result = runPromise(() -> promise); // GH-90000

            assertThat(result).hasSize(10000); // GH-90000
            assertThat(result).isEqualTo(largeString); // GH-90000
        }
    }

    // ============================================
    // ERROR RECOVERY (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Error Recovery [GH-90000]")
    class ErrorRecoveryTests {

        @Test
        @DisplayName("Recovers from exception in converted promise [GH-90000]")
        void errorRecoveryFromPromise() { // GH-90000
            Promise<String> failed = Promise.ofException(new RuntimeException("test error [GH-90000]"));
            CompletableFuture<String> future = AsyncBridge.toFuture(failed); // GH-90000

            assertThat(future).isCompletedExceptionally(); // GH-90000
            assertThatThrownBy(future::join) // GH-90000
                .hasMessageContaining("test error [GH-90000]");
        }

        @Test
        @DisplayName("Recovers from exception in blocking operation [GH-90000]")
        void errorRecoveryFromBlocking() { // GH-90000
            assertThatThrownBy(() -> { // GH-90000
                runPromise(() -> AsyncBridge.runBlocking(() -> { // GH-90000
                    throw new IllegalStateException("blocking failed [GH-90000]");
                }));
            }).hasMessageContaining("blocking failed [GH-90000]");
            clearFatalError(); // GH-90000
        }
    }
}
