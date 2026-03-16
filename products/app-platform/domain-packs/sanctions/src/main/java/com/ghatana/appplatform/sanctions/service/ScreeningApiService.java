package com.ghatana.appplatform.sanctions.service;

import com.ghatana.appplatform.sanctions.domain.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Integration adapter for the sanctions screening engine (D14-002).
 *              Called by OMS (pre-trade), onboarding, and settlement services.
 *              Blocks orders when decision == AUTO_BLOCK, routes to review queue when HIGH/MEDIUM.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — Application Service
 */
public class ScreeningApiService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningApiService.class);

    private final ScreeningEngineService engine;
    private final ScreeningResultStore resultStore;
    private final Executor executor;
    private final Consumer<Object> eventPublisher;

    public ScreeningApiService(ScreeningEngineService engine,
                                ScreeningResultStore resultStore,
                                Executor executor,
                                Consumer<Object> eventPublisher) {
        this.engine = engine;
        this.resultStore = resultStore;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Pre-trade screening called by the OMS before order routing (D14-002).
     *
     * @param orderId   Reference ID for the order being screened.
     * @param clientId  Client identifier.
     * @param name      Client's full legal name.
     * @param entityType INDIVIDUAL or ENTITY.
     * @return Promise resolving to the screening result.
     */
    public Promise<ScreeningResult> screenForOrder(String orderId, String clientId,
                                                    String name, ScreeningEntityType entityType) {
        var request = new ScreeningRequest(
                java.util.UUID.randomUUID().toString(),
                name,
                entityType,
                null,   // nationality — not always available pre-trade
                null,   // dateOfBirth — looked up during onboarding
                java.util.Map.of("clientId", clientId)
        );
        return engine.screen(request, orderId).then(result -> {
            return Promise.ofBlocking(executor, () -> {
                resultStore.save(result);
                if (result.decision().requiresBlock()) {
                    log.warn("OMS sanctions block: orderId={} clientId={} decision={}",
                            orderId, clientId, result.decision());
                    eventPublisher.accept(new OrderBlockedEvent(orderId, clientId, result));
                } else if (result.decision().requiresReview()) {
                    log.info("OMS sanctions review: orderId={} clientId={} decision={}",
                            orderId, clientId, result.decision());
                    eventPublisher.accept(new OrderFlaggedForReviewEvent(orderId, clientId, result));
                }
                return result;
            });
        });
    }

    /**
     * Onboarding screening — called when a new client is registered (D14-002).
     */
    public Promise<ScreeningResult> screenForOnboarding(String onboardingId, String name,
                                                          ScreeningEntityType entityType,
                                                          String nationality, String dateOfBirth) {
        var request = new ScreeningRequest(
                java.util.UUID.randomUUID().toString(),
                name,
                entityType,
                nationality,
                dateOfBirth,
                java.util.Map.of()
        );
        return engine.screen(request, onboardingId).then(result ->
                Promise.ofBlocking(executor, () -> {
                    resultStore.save(result);
                    return result;
                }));
    }

    // ─── Ports ───────────────────────────────────────────────────────────────

    /** Port for persisting screening results. */
    public interface ScreeningResultStore {
        void save(ScreeningResult result);
        java.util.Optional<ScreeningResult> findById(String resultId);
        java.util.List<ScreeningResult> findByReferenceId(String referenceId);
    }

    // ─── Events ──────────────────────────────────────────────────────────────

    public record OrderBlockedEvent(String orderId, String clientId, ScreeningResult result) {}
    public record OrderFlaggedForReviewEvent(String orderId, String clientId, ScreeningResult result) {}
}
