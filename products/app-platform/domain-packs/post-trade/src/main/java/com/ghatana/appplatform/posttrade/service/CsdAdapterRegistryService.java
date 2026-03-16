package com.ghatana.appplatform.posttrade.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Defines the T3 plugin interface (ICsdAdapter) for CSD/depository connectivity.
 *              Acts as an adapter registry: looks up the correct CSD adapter by market code
 *              and delegates settlement instruction submission/status/cancellation calls.
 *              Manages connection heartbeat and circuit-breaker on consecutive failures.
 * @doc.layer   Domain
 * @doc.pattern Registry pattern over inner ICsdAdapter plugin interface; inner
 *              ConnectionMonitorPort for heartbeat tracking; circuit-breaker via failure counter.
 */
public class CsdAdapterRegistryService {

    private static final Logger log = LoggerFactory.getLogger(CsdAdapterRegistryService.class);

    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;  // consecutive failures before open

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final Map<String, ICsdAdapter> adapters;    // marketCode → adapter
    private final Map<String, Integer>     failureCounts = new HashMap<>();
    private final Counter             submitCounter;
    private final Counter             circuitBreakerCounter;

    public CsdAdapterRegistryService(HikariDataSource dataSource, Executor executor,
                                     MeterRegistry registry) {
        this.dataSource            = dataSource;
        this.executor              = executor;
        this.adapters              = new HashMap<>();
        this.submitCounter         = registry.counter("posttrade.csd.submit");
        this.circuitBreakerCounter = registry.counter("posttrade.csd.circuit_breaker.open");
    }

    // ─── Inner plugin interface (T3 sandbox) ─────────────────────────────────

    /**
     * T3 CSD adapter plugin interface. Concrete implementations run in T3 sandbox
     * with network access to their respective CSD only.
     */
    public interface ICsdAdapter {
        String marketCode();  // e.g. "CDSC" for Nepal, "NSDL" for India
        String submitInstruction(CsdInstruction instruction);          // returns CSD reference
        CsdStatus getStatus(String csdReference);
        boolean   confirmSettlement(String csdReference);
        boolean   cancelInstruction(String csdReference, String reason);
        boolean   heartbeat();  // returns true if connection healthy
    }

    public record CsdInstruction(
        String instructionId,
        String participantCode,
        String counterpartyCode,
        String isin,
        String direction,    // DELIVER | RECEIVE
        double quantity,
        double amount,
        String currency,
        String settlementDateAd
    ) {}

    public record CsdStatus(
        String csdReference,
        String status,          // PENDING | MATCHED | SETTLED | FAILED | CANCELLED
        String statusDescription
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Register a CSD adapter plugin.
     */
    public void registerAdapter(ICsdAdapter adapter) {
        adapters.put(adapter.marketCode(), adapter);
        failureCounts.put(adapter.marketCode(), 0);
        log.info("CSD adapter registered: marketCode={} class={}", adapter.marketCode(), adapter.getClass().getSimpleName());
    }

    /**
     * Submit a settlement instruction to the appropriate CSD.
     */
    public Promise<String> submit(String marketCode, CsdInstruction instruction) {
        return Promise.ofBlocking(executor, () -> {
            ICsdAdapter adapter = resolveAdapter(marketCode);
            if (isCircuitOpen(marketCode)) {
                throw new IllegalStateException("Circuit breaker open for CSD " + marketCode);
            }
            try {
                String ref = adapter.submitInstruction(instruction);
                resetFailureCount(marketCode);
                submitCounter.increment();
                log.info("CSD instruction submitted marketCode={} instructionId={} ref={}",
                         marketCode, instruction.instructionId(), ref);
                return ref;
            } catch (Exception ex) {
                incrementFailure(marketCode);
                log.error("CSD submit failed marketCode={} instructionId={}", marketCode, instruction.instructionId(), ex);
                throw ex;
            }
        });
    }

    /**
     * Query settlement status from the CSD.
     */
    public Promise<CsdStatus> getStatus(String marketCode, String csdReference) {
        return Promise.ofBlocking(executor, () -> resolveAdapter(marketCode).getStatus(csdReference));
    }

    /**
     * Heartbeat monitoring for all registered adapters.
     */
    public Promise<Map<String, Boolean>> heartbeatAll() {
        return Promise.ofBlocking(executor, () -> {
            Map<String, Boolean> results = new HashMap<>();
            for (Map.Entry<String, ICsdAdapter> e : adapters.entrySet()) {
                boolean ok = e.getValue().heartbeat();
                results.put(e.getKey(), ok);
                if (!ok) log.warn("CSD heartbeat failed: marketCode={}", e.getKey());
            }
            return results;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private ICsdAdapter resolveAdapter(String marketCode) {
        ICsdAdapter adapter = adapters.get(marketCode);
        if (adapter == null) throw new IllegalArgumentException("No CSD adapter for marketCode=" + marketCode);
        return adapter;
    }

    private boolean isCircuitOpen(String marketCode) {
        int failures = failureCounts.getOrDefault(marketCode, 0);
        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitBreakerCounter.increment();
            return true;
        }
        return false;
    }

    private void incrementFailure(String marketCode) {
        failureCounts.merge(marketCode, 1, Integer::sum);
    }

    private void resetFailureCount(String marketCode) {
        failureCounts.put(marketCode, 0);
    }
}
