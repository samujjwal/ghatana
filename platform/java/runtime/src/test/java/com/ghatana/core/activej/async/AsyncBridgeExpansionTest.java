/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("AsyncBridge - Phase 3 Expansion")
class AsyncBridgeExpansionTest extends EventloopTestBase {

    // ============================================
    // RUN BLOCKING VARIATIONS (5 tests) 
    // ============================================

    @Nested
    @DisplayName("RunBlocking Variations")
    class RunBlockingTests {

        @Test
        @DisplayName("Blocks correctly with long-running operations")
        void blockingLongOperation() { 
            String result = runPromise(() -> AsyncBridge.runBlocking(() -> { 
                try {
                    Thread.sleep(50); // Simulate work 
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt(); 
                }
                return "completed";
            }));

            assertThat(result).isEqualTo("completed");
        }

        @Test
        @DisplayName("Multiple sequential blocking operations complete in order")
        void sequentialBlocking() { 
            List<String> results = new ArrayList<>(); 

            for (int i = 0; i < 5; i++) { 
                final int idx = i;
                String result = runPromise(() -> AsyncBridge.runBlocking(() -> { 
                    try {
                        Thread.sleep(10); 
                    } catch (InterruptedException e) { 
                        Thread.currentThread().interrupt(); 
                    }
                    return "result-" + idx;
                }));
                results.add(result); 
            }

            assertThat(results) 
                .containsExactly("result-0", "result-1", "result-2", "result-3", "result-4"); 
        }

        @Test
        @DisplayName("Blocking operation with null return handled")
        void blockingNullReturn() { 
            String result = runPromise(() -> AsyncBridge.runBlocking(() -> null)); 

            assertThat(result).isNull(); 
        }

        @Test
        @DisplayName("Blocking interrupted thread is properly handled")
        void blockingInterruptedThread() { 
            assertThatThrownBy(() -> { 
                runPromise(() -> AsyncBridge.runBlocking(() -> { 
                    Thread.currentThread().interrupt(); 
                    try {
                        Thread.sleep(100); 
                    } catch (InterruptedException e) { 
                        Thread.currentThread().interrupt(); 
                        throw new RuntimeException("Interrupted");
                    }
                    return "should-not-get-here";
                }));
            }).hasMessageContaining("Interrupted");
            clearFatalError(); 
        }

        @Test
        @DisplayName("Blocking with exception is properly propagated")
        void blockingExceptionPropagation() { 
            assertThatThrownBy(() -> { 
                runPromise(() -> AsyncBridge.runBlocking(() -> { 
                    throw new IllegalArgumentException("invalid input");
                }));
            }).hasMessageContaining("invalid input");
            clearFatalError(); 
        }
    }

    // ============================================
    // FUTURE TO PROMISE CONVERSION (4 tests) 
    // ============================================

    @Nested
    @DisplayName("Future to Promise Conversion")
    class FutureToPromiseTests {

        @Test
        @DisplayName("Completed future converts to resolved promise")
        void completedFutureToPromise() { 
            CompletableFuture<String> future = CompletableFuture.completedFuture("value");
            Promise<String> promise = AsyncBridge.fromFuture(future); 

            String result = runPromise(() -> promise); 

            assertThat(result).isEqualTo("value");
        }

        @Test
        @DisplayName("Exceptional future converts to failed promise")
        void exceptionalFutureToPromise() { 
            CompletableFuture<String> future = new CompletableFuture<>(); 
            future.completeExceptionally(new RuntimeException("future failed"));
            Promise<String> promise = AsyncBridge.fromFuture(future); 

            assertThatThrownBy(() -> { 
                runPromise(() -> promise); 
            }).hasMessageContaining("future failed");
            clearFatalError(); 
        }

        @Test
        @DisplayName("Future completed asynchronously resolves promise")
        void asyncCompletedFutureToPromise() { 
            CompletableFuture<String> future = new CompletableFuture<>(); 

            new Thread(() -> { 
                try {
                    Thread.sleep(20); 
                    future.complete("async-value");
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt(); 
                }
            }).start(); 

            Promise<String> promise = AsyncBridge.fromFuture(future); 
            String result = runPromise(() -> promise); 

            assertThat(result).isEqualTo("async-value");
        }

        @Test
        @DisplayName("Many futures converted to promises in bulk")
        void bulkFutureConversion() { 
            List<CompletableFuture<Integer>> futures = new ArrayList<>(); 
            for (int i = 0; i < 100; i++) { 
                final int idx = i;
                futures.add(CompletableFuture.completedFuture(idx)); 
            }

            List<Promise<Integer>> promises = new ArrayList<>(); 
            for (CompletableFuture<Integer> future : futures) { 
                promises.add(AsyncBridge.fromFuture(future)); 
            }

            assertThat(promises).hasSize(100); 
        }
    }

    // ============================================
    // PROMISE TO FUTURE CONVERSION (4 tests) 
    // ============================================

    @Nested
    @DisplayName("Promise to Future Conversion")
    class PromiseToFutureTests {

        @Test
        @DisplayName("Resolved promise converts to completed future")
        void resolvedPromiseToFuture() { 
            Promise<String> promise = Promise.of("promise-value");
            CompletableFuture<String> future = AsyncBridge.toFuture(promise); 

            runPromise(() -> promise); 

            assertThat(future).isCompletedWithValue("promise-value");
        }

        @Test
        @DisplayName("Failed promise converts to exceptional future")
        void failedPromiseToFuture() { 
            Promise<String> promise = Promise.ofException(new RuntimeException("promise failed"));
            CompletableFuture<String> future = AsyncBridge.toFuture(promise); 

            assertThat(future).isCompletedExceptionally(); 
            assertThatThrownBy(future::join) 
                .hasMessageContaining("promise failed");
        }

        @Test
        @DisplayName("Null promise value converts correctly")
        void nullPromiseToFuture() { 
            Promise<String> promise = Promise.of(null); 
            CompletableFuture<String> future = AsyncBridge.toFuture(promise); 

            runPromise(() -> promise); 

            assertThat(future).isCompletedWithValue(null); 
        }

        @Test
        @DisplayName("Many promises converted to futures in bulk")
        void bulkPromiseConversion() { 
            List<Promise<Integer>> promises = new ArrayList<>(); 
            for (int i = 0; i < 100; i++) { 
                final int idx = i;
                promises.add(Promise.of(idx)); 
            }

            List<CompletableFuture<Integer>> futures = new ArrayList<>(); 
            for (Promise<Integer> promise : promises) { 
                futures.add(AsyncBridge.toFuture(promise)); 
            }

            assertThat(futures).hasSize(100); 
        }
    }

    // ============================================
    // ROUND TRIP CONVERSIONS (3 tests) 
    // ============================================

    @Nested
    @DisplayName("Round Trip Conversions")
    class RoundTripTests {

        @Test
        @DisplayName("Promise → Future → Promise preserves value")
        void promiseFutureRoundTrip() { 
            Promise<String> original = Promise.of("original-value");
            CompletableFuture<String> future = AsyncBridge.toFuture(original); 
            Promise<String> restored = AsyncBridge.fromFuture(future); 

            runPromise(() -> original); 
            String result = runPromise(() -> restored); 

            assertThat(result).isEqualTo("original-value");
        }

        @Test
        @DisplayName("Future → Promise → Future preserves value")
        void futurePromiseRoundTrip() { 
            CompletableFuture<String> original = CompletableFuture.completedFuture("round-trip");
            Promise<String> promise = AsyncBridge.fromFuture(original); 
            CompletableFuture<String> restored = AsyncBridge.toFuture(promise); 

            String result = runPromise(() -> promise); 
            assertThat(restored).isCompletedWithValue("round-trip");
        }

        @Test
        @DisplayName("Exception preserved through round trip conversion")
        void exceptionRoundTrip() { 
            Promise<String> original = Promise.ofException(new RuntimeException("round-trip-error"));
            CompletableFuture<String> future = AsyncBridge.toFuture(original); 
            Promise<String> restored = AsyncBridge.fromFuture(future); 

            assertThat(future).isCompletedExceptionally(); 
            assertThatThrownBy(() -> { 
                runPromise(() -> restored); 
            }).hasMessageContaining("round-trip-error");
            clearFatalError(); 
        }
    }

    // ============================================
    // CONCURRENT CONVERSION (3 tests) 
    // ============================================

    @Nested
    @DisplayName("Concurrent Conversion")
    class ConcurrentConversionTests {

        @Test
        @DisplayName("Many threads simultaneously converting futures to promises")
        void concurrentFutureToPromise() { 
            int threadCount = 20;
            List<CompletableFuture<String>> futures = new ArrayList<>(); 
            List<Promise<String>> promises = new ArrayList<>(); 

            for (int i = 0; i < threadCount; i++) { 
                final int idx = i;
                futures.add(CompletableFuture.completedFuture("future-" + idx)); 
            }

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) { 
                final int idx = i;
                threads[i] = new Thread(() -> { 
                    synchronized (promises) { 
                        promises.add(AsyncBridge.fromFuture(futures.get(idx))); 
                    }
                });
                threads[i].start(); 
            }

            for (Thread t : threads) { 
                try {
                    t.join(); 
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt(); 
                }
            }

            assertThat(promises).hasSize(threadCount); 
        }

        @Test
        @DisplayName("Many threads simultaneously converting promises to futures")
        void concurrentPromiseToFuture() { 
            int threadCount = 20;
            List<Promise<String>> promises = new ArrayList<>(); 
            List<CompletableFuture<String>> futures = new ArrayList<>(); 

            for (int i = 0; i < threadCount; i++) { 
                final int idx = i;
                promises.add(Promise.of("promise-" + idx)); 
            }

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) { 
                final int idx = i;
                threads[i] = new Thread(() -> { 
                    synchronized (futures) { 
                        futures.add(AsyncBridge.toFuture(promises.get(idx))); 
                    }
                });
                threads[i].start(); 
            }

            for (Thread t : threads) { 
                try {
                    t.join(); 
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt(); 
                }
            }

            assertThat(futures).hasSize(threadCount); 
        }

        @Test
        @DisplayName("Mixed conversions under concurrent load")
        void mixedConcurrentConversions() throws Exception { 
            int threadCount = 10;
            AtomicInteger conversionCount = new AtomicInteger(0); 
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount); 

            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount); 
            try {
                for (int i = 0; i < threadCount; i++) { 
                    executor.submit(() -> { 
                        try {
                            for (int j = 0; j < 25; j++) { 
                                if (j % 2 == 0) { 
                                    CompletableFuture<String> future = CompletableFuture.completedFuture("mixed");
                                    AsyncBridge.fromFuture(future); 
                                } else {
                                    Promise<String> promise = Promise.of("mixed");
                                    AsyncBridge.toFuture(promise); 
                                }
                                conversionCount.incrementAndGet(); 
                            }
                        } finally {
                            latch.countDown(); 
                        }
                    });
                }

                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); 
            } finally {
                executor.shutdownNow(); 
            }

            assertThat(conversionCount.get()).isEqualTo(threadCount * 25); 
        }
    }

    // ============================================
    // SPECIAL CASES (4 tests) 
    // ============================================

    @Nested
    @DisplayName("Special Cases")
    class SpecialCaseTests {

        @Test
        @DisplayName("Chained blocking operations execute serially")
        void chainedBlockingOps() { 
            List<Integer> order = new ArrayList<>(); 

            for (int i = 0; i < 5; i++) { 
                final int idx = i;
                runPromise(() -> AsyncBridge.runBlocking(() -> { 
                    try {
                        Thread.sleep(5); 
                    } catch (InterruptedException e) { 
                        Thread.currentThread().interrupt(); 
                    }
                    synchronized (order) { 
                        order.add(idx); 
                    }
                    return null;
                }));
            }

            assertThat(order).containsExactly(0, 1, 2, 3, 4); 
        }

        @Test
        @DisplayName("Future with delay completes correctly after conversion")
        void delayedFutureConversion() throws Exception { 
            CompletableFuture<String> future = new CompletableFuture<>(); 

            new Thread(() -> { 
                try {
                    Thread.sleep(30); 
                    future.complete("delayed");
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt(); 
                }
            }).start(); 

            Promise<String> promise = AsyncBridge.fromFuture(future); 
            String result = runPromise(() -> promise); 

            assertThat(result).isEqualTo("delayed");
        }

        @Test
        @DisplayName("Many levels of nested conversions")
        void nestedConversions() { 
            // Future → Promise → Future → Promise ...
            CompletableFuture<Integer> future = CompletableFuture.completedFuture(42); 
            Promise<Integer> p1 = AsyncBridge.fromFuture(future); 
            CompletableFuture<Integer> f1 = AsyncBridge.toFuture(p1); 
            Promise<Integer> p2 = AsyncBridge.fromFuture(f1); 
            CompletableFuture<Integer> f2 = AsyncBridge.toFuture(p2); 
            Promise<Integer> p3 = AsyncBridge.fromFuture(f2); 

            Integer result = runPromise(() -> p3); 

            assertThat(result).isEqualTo(42); 
        }

        @Test
        @DisplayName("Conversion with large payload completes")
        void largePayloadConversion() { 
            String largeString = "x".repeat(10000); 
            CompletableFuture<String> future = CompletableFuture.completedFuture(largeString); 
            Promise<String> promise = AsyncBridge.fromFuture(future); 

            String result = runPromise(() -> promise); 

            assertThat(result).hasSize(10000); 
            assertThat(result).isEqualTo(largeString); 
        }
    }

    // ============================================
    // ERROR RECOVERY (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Error Recovery")
    class ErrorRecoveryTests {

        @Test
        @DisplayName("Recovers from exception in converted promise")
        void errorRecoveryFromPromise() { 
            Promise<String> failed = Promise.ofException(new RuntimeException("test error"));
            CompletableFuture<String> future = AsyncBridge.toFuture(failed); 

            assertThat(future).isCompletedExceptionally(); 
            assertThatThrownBy(future::join) 
                .hasMessageContaining("test error");
        }

        @Test
        @DisplayName("Recovers from exception in blocking operation")
        void errorRecoveryFromBlocking() { 
            assertThatThrownBy(() -> { 
                runPromise(() -> AsyncBridge.runBlocking(() -> { 
                    throw new IllegalStateException("blocking failed");
                }));
            }).hasMessageContaining("blocking failed");
            clearFatalError(); 
        }
    }
}
