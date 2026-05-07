/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.benchmarks;

import com.ghatana.datacloud.feature.DataCloudFeature;
import com.ghatana.datacloud.feature.DataCloudFeatureFlags;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for {@link DataCloudFeatureFlags} — the centralized feature
 * flag evaluation utility added as part of DC-018 (Feature Flag Standardization).
 *
 * <h2>What is measured</h2>
 * <ol>
 *   <li><b>defaultLookup</b> — {@code isEnabled()} without a global instance (falls back to enum
 *       default); represents the cold / unconfigured deployment path.</li>
 *   <li><b>globalInstanceLookup</b> — {@code isEnabled()} through a pre-initialized global instance
 *       (EnumMap lookup + volatileRead); represents normal steady-state runtime.</li>
 *   <li><b>instanceDirectLookup</b> — {@code flags.enabled(feature)} on an injected instance
 *       (avoids the singleton indirection); represents the DI injection path.</li>
 *   <li><b>overrideLookup</b> — {@code isEnabled()} with a ConcurrentHashMap test override;
 *       validates that override path is not measurably slower than global path.</li>
 *   <li><b>allFeaturesGlobalLookup</b> — iterates all {@link DataCloudFeature} values via the global
 *       instance to measure full-flag-set evaluation cost.</li>
 * </ol>
 *
 * <h2>Expected characteristics</h2>
 * All benchmarks should complete in the single-nanosecond to low-microsecond range.
 * Any result > 10 µs/op on a modern JVM suggests unintended I/O, contention, or
 * classloading and should be investigated.
 *
 * <h2>Running</h2>
 * <pre>{@code
 * ./gradlew :products:data-cloud:delivery:runtime-composition:jmh
 *
 * # Run only FeatureFlags benchmarks:
 * ./gradlew :products:data-cloud:delivery:runtime-composition:jmh \
 *   -Pjmh.include="FeatureFlagsBenchmark"
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose JMH benchmarks for DataCloudFeatureFlags evaluation overhead
 * @doc.layer product
 * @doc.pattern Benchmark
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class FeatureFlagsBenchmark {

    // =========================================================================
    // State: no global instance (default-only path)
    // =========================================================================

    /**
     * State that ensures no global instance is set. Each trial resets the global to
     * simulate a fresh JVM startup or an un-configured deployment.
     */
    @State(Scope.Benchmark)
    public static class NoGlobalState {

        @Setup(Level.Trial)
        public void setup() {
            DataCloudFeatureFlags.resetGlobalForTesting();
        }

        @TearDown(Level.Trial)
        public void teardown() {
            DataCloudFeatureFlags.resetGlobalForTesting();
        }
    }

    // =========================================================================
    // State: global instance initialized from defaults (no env vars override)
    // =========================================================================

    /**
     * State with a {@link DataCloudFeatureFlags} global instance pre-initialized
     * from environment defaults. The instance is shared by all benchmark threads
     * via the volatile singleton — this is the normal production configuration.
     */
    @State(Scope.Benchmark)
    public static class GlobalInstanceState {

        DataCloudFeatureFlags flags;

        @Setup(Level.Trial)
        public void setup() {
            flags = DataCloudFeatureFlags.fromEnvironment();
            DataCloudFeatureFlags.setGlobal(flags);
        }

        @TearDown(Level.Trial)
        public void teardown() {
            DataCloudFeatureFlags.resetGlobalForTesting();
        }
    }

    // =========================================================================
    // State: override applied to one flag (ConcurrentHashMap lookup path)
    // =========================================================================

    /**
     * State with a test override for {@link DataCloudFeature#DATA_CLOUD_AI_ASSIST}
     * (which is off-by-default). The override is stored in a ConcurrentHashMap and
     * consulted before the global instance during {@code isEnabled()}.
     */
    @State(Scope.Benchmark)
    public static class OverrideState {

        @Setup(Level.Trial)
        public void setup() {
            DataCloudFeatureFlags.setGlobal(DataCloudFeatureFlags.fromEnvironment());
            DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_AI_ASSIST, true);
        }

        @TearDown(Level.Trial)
        public void teardown() {
            DataCloudFeatureFlags.clearOverrides();
            DataCloudFeatureFlags.resetGlobalForTesting();
        }
    }

    // =========================================================================
    // Benchmark 1 — Default-only lookup (no global instance)
    //
    // Falls back to DataCloudFeature.defaultEnabled() — a simple boolean field on
    // the enum constant. Expected: ~1–5 ns/op (volatileRead + enum field access).
    // =========================================================================

    /**
     * Evaluates an off-by-default feature without any global instance set.
     * Tests the {@code feature.defaultEnabled()} fallback branch.
     */
    @Benchmark
    public boolean defaultLookupOffFlag(NoGlobalState state) {
        return DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST);
    }

    /**
     * Evaluates an on-by-default feature without any global instance set.
     */
    @Benchmark
    public boolean defaultLookupOnFlag(NoGlobalState state) {
        return DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_ADVANCED_ANALYTICS);
    }

    // =========================================================================
    // Benchmark 2 — Global instance lookup (steady-state production path)
    //
    // Reads the volatile global field, gets the EnumMap and does a lookup.
    // Expected: ~20–100 ns/op on modern JVM.
    // =========================================================================

    /**
     * Evaluates a feature through the global instance (EnumMap lookup path).
     */
    @Benchmark
    public boolean globalInstanceLookup(GlobalInstanceState state) {
        return DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_ADVANCED_ANALYTICS);
    }

    // =========================================================================
    // Benchmark 3 — Injected instance lookup
    //
    // Calls flags.enabled(feature) directly, bypassing the volatile singleton read.
    // Useful for services that inject DataCloudFeatureFlags rather than using the static API.
    // =========================================================================

    /**
     * Evaluates a feature through a directly injected {@link DataCloudFeatureFlags} instance.
     */
    @Benchmark
    public boolean instanceDirectLookup(GlobalInstanceState state) {
        return state.flags.enabled(DataCloudFeature.DATA_CLOUD_REAL_TIME_STREAMING);
    }

    // =========================================================================
    // Benchmark 4 — Override path lookup (test override present)
    //
    // The ConcurrentHashMap is consulted first. Expected to be slightly heavier than
    // the global-instance path but should stay under ~500 ns/op.
    // =========================================================================

    /**
     * Evaluates a feature that has a test override applied (ConcurrentHashMap consulted first).
     */
    @Benchmark
    public boolean overrideLookup(OverrideState state) {
        return DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST);
    }

    /**
     * Evaluates a feature without an override, while another feature has one (map lookup
     * confirms absence of key, then falls through to global instance).
     */
    @Benchmark
    public boolean overrideLookupMiss(OverrideState state) {
        return DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_ADVANCED_ANALYTICS);
    }

    // =========================================================================
    // Benchmark 5 — Full feature-set sweep
    //
    // Iterates all DataCloudFeature values through the global instance.
    // Measures the cost of evaluating every flag in one hot loop —
    // useful for services that pre-check all flags during startup.
    // =========================================================================

    /**
     * Iterates all {@link DataCloudFeature} values and evaluates each via the global instance.
     */
    @Benchmark
    public void allFeaturesGlobalLookup(GlobalInstanceState state, Blackhole bh) {
        for (DataCloudFeature feature : DataCloudFeature.values()) {
            bh.consume(DataCloudFeatureFlags.isEnabled(feature));
        }
    }
}
