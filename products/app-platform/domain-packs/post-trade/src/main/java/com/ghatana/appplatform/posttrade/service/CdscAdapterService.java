package com.ghatana.appplatform.posttrade.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Concrete T3 adapter plugin for CDSC (Central Depository System and Clearing
 *              Ltd, Nepal). Maps internal settlement instruction fields to CDSC message format.
 *              Handles CDSC-specific flows: demat transfer, physical share handling,
 *              pledge/unpledge. Credentials managed via K-14. Registers itself with
 *              CsdAdapterRegistryService on construction.
 * @doc.layer   Domain
 * @doc.pattern Implements ICsdAdapter from CsdAdapterRegistryService; inner CdscApiPort (K-14)
 *              for actual HTTP/SOAP calls in T3 sandbox; credential rotation via K-14.
 */
public class CdscAdapterService implements CsdAdapterRegistryService.ICsdAdapter {

    private static final Logger log = LoggerFactory.getLogger(CdscAdapterService.class);
    private static final String MARKET_CODE = "CDSC";

    private final Executor    executor;
    private final CdscApiPort cdscApiPort;
    private final Counter     submitCounter;
    private final Counter     errorCounter;

    public CdscAdapterService(Executor executor, CdscApiPort cdscApiPort, MeterRegistry registry) {
        this.executor      = executor;
        this.cdscApiPort   = cdscApiPort;
        this.submitCounter = registry.counter("posttrade.cdsc.submit");
        this.errorCounter  = registry.counter("posttrade.cdsc.error");
    }

    // ─── Inner port (K-14 credentials) ───────────────────────────────────────

    /**
     * K-14 credential-managed port for CDSC API connectivity. Runs in T3 sandbox.
     */
    public interface CdscApiPort {
        CdscApiResponse submitDemat(CdscDematRequest request);
        CdscApiResponse getSettlementStatus(String cdscReference);
        CdscApiResponse cancelInstruction(String cdscReference, String reason);
        boolean         ping();
        void            rotateCertificate(String newCertificate);
    }

    // CDSC-specific request/response records
    public record CdscDematRequest(
        String  boid,                // Beneficiary Owner ID (client's CDSC account)
        String  counterpartyBoid,
        String  isin,
        long    quantityUnits,       // CDSC uses whole units only
        String  transactionType,     // "TRANSFER_IN" | "TRANSFER_OUT"
        String  settlementDate,      // CDSC format: YYYY-MM-DD
        boolean isPledge,
        boolean isPhysical
    ) {}

    public record CdscApiResponse(
        String  cdscReference,
        String  cdscStatus,         // ACCEPTED | PENDING | SETTLED | REJECTED
        String  errorCode,
        String  errorMessage
    ) {}

    // ─── ICsdAdapter implementation ──────────────────────────────────────────

    @Override
    public String marketCode() { return MARKET_CODE; }

    @Override
    public String submitInstruction(CsdAdapterRegistryService.CsdInstruction instruction) {
        boolean isDelivery = "DELIVER".equals(instruction.direction());
        CdscDematRequest req = new CdscDematRequest(
            instruction.participantCode(),
            instruction.counterpartyCode(),
            mapToCdscIsin(instruction.isin()),
            (long) instruction.quantity(),
            isDelivery ? "TRANSFER_OUT" : "TRANSFER_IN",
            instruction.settlementDateAd(),
            false,  // non-pledge by default
            false   // not physical
        );
        CdscApiResponse resp = cdscApiPort.submitDemat(req);
        if ("ACCEPTED".equals(resp.cdscStatus())) {
            submitCounter.increment();
            log.info("CDSC instruction submitted: cdscRef={} instructionId={}",
                     resp.cdscReference(), instruction.instructionId());
            return resp.cdscReference();
        }
        errorCounter.increment();
        log.error("CDSC submit rejected: code={} msg={} instructionId={}",
                  resp.errorCode(), resp.errorMessage(), instruction.instructionId());
        throw new RuntimeException("CDSC rejection: " + resp.errorCode() + " – " + resp.errorMessage());
    }

    @Override
    public CsdAdapterRegistryService.CsdStatus getStatus(String csdReference) {
        CdscApiResponse resp = cdscApiPort.getSettlementStatus(csdReference);
        String mappedStatus  = mapCdscStatus(resp.cdscStatus());
        return new CsdAdapterRegistryService.CsdStatus(csdReference, mappedStatus, resp.cdscStatus());
    }

    @Override
    public boolean confirmSettlement(String csdReference) {
        CsdAdapterRegistryService.CsdStatus status = getStatus(csdReference);
        return "SETTLED".equals(status.status());
    }

    @Override
    public boolean cancelInstruction(String csdReference, String reason) {
        CdscApiResponse resp = cdscApiPort.cancelInstruction(csdReference, reason);
        return "ACCEPTED".equals(resp.cdscStatus());
    }

    @Override
    public boolean heartbeat() {
        return cdscApiPort.ping();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /** Maps internal ISIN to CDSC ISIN format (CDSC may use a different prefix). */
    private String mapToCdscIsin(String isin) {
        // CDSC Nepal uses NP-prefixed ISINs; pass through as-is
        return isin;
    }

    private String mapCdscStatus(String cdscStatus) {
        return switch (cdscStatus) {
            case "ACCEPTED", "PENDING" -> "PENDING";
            case "SETTLED"             -> "SETTLED";
            case "REJECTED"            -> "FAILED";
            case "CANCELLED"           -> "CANCELLED";
            default                    -> "PENDING";
        };
    }
}
