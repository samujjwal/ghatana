package com.ghatana.phr.kernel.service;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for the PHR encounter billing critical path.
 *
 * @doc.type class
 * @doc.purpose Baseline benchmark for encounter create->close path
 * @doc.layer product
 * @doc.pattern Benchmark
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BillingServiceBenchmark {

    private BillingService billingService;
    private int sequence;

    @Setup
    public void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud = new PhrTestInfrastructure.StubDataCloudAdapter();
        billingService = new BillingService(PhrTestInfrastructure.createTestContext(dataCloud));
        billingService.start().toCompletableFuture().join();
    }

    @TearDown
    public void tearDown() {
        billingService.stop().toCompletableFuture().join();
    }

    @Benchmark
    public BillingService.BillingEncounter createAndCloseEncounter() {
        int id = ++sequence;
        BillingService.BillingEncounter created = billingService.createEncounter(
            new BillingService.BillingEncounter(
                null,
                "patient-" + id,
                "provider-" + id,
                "facility-1",
                List.of(new BillingService.ServiceLine("99213", "Visit", 1, new BigDecimal("1200.00"), "NPR")),
                new BigDecimal("1200.00"),
                "NPR",
                BillingService.EncounterStatus.OPEN,
                null,
                null
            )
        ).toCompletableFuture().join();

        return billingService.closeEncounter(created.id()).toCompletableFuture().join();
    }
}
