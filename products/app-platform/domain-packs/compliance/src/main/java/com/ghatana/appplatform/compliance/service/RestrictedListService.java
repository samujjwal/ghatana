package com.ghatana.appplatform.compliance.service;

import com.ghatana.appplatform.compliance.domain.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Checks restricted and watch lists per D07-011.
 *              RESTRICTED instruments block the trade; WATCH instruments flag for review;
 *              GREY instruments require enhanced scrutiny.
 * @doc.layer   Application Service
 * @doc.pattern Hexagonal Architecture — Application Service
 */
public class RestrictedListService {

    private static final Logger log = LoggerFactory.getLogger(RestrictedListService.class);

    private final RestrictedListStore store;
    private final Executor executor;

    public RestrictedListService(RestrictedListStore store, Executor executor) {
        this.store = store;
        this.executor = executor;
    }

    public ComplianceCheckResult.RuleEvaluationDetail evaluate(ComplianceCheckRequest request) {
        try {
            String todayBs = store.getTodayBs();
            List<RestrictedListEntry> entries = store
                    .findActive(request.clientId(), request.instrumentId(), todayBs)
                    .get();

            // Check from most severe to least
            for (var entry : entries) {
                if (entry.listType() == RestrictedListType.RESTRICTED) {
                    return new ComplianceCheckResult.RuleEvaluationDetail(
                            "RESTRICTED_LIST_CHECK", "Restricted List Check",
                            ComplianceStatus.FAIL,
                            "Instrument " + request.instrumentId() + " is on RESTRICTED list: " + entry.reason());
                }
                if (entry.listType() == RestrictedListType.WATCH
                        || entry.listType() == RestrictedListType.GREY) {
                    return new ComplianceCheckResult.RuleEvaluationDetail(
                            "RESTRICTED_LIST_CHECK", "Restricted List Check",
                            ComplianceStatus.REVIEW,
                            "Instrument " + request.instrumentId() + " is on " + entry.listType() + " list");
                }
            }

            return new ComplianceCheckResult.RuleEvaluationDetail(
                    "RESTRICTED_LIST_CHECK", "Restricted List Check", ComplianceStatus.PASS, null);

        } catch (Exception e) {
            log.error("Restricted list check error", e);
            return new ComplianceCheckResult.RuleEvaluationDetail(
                    "RESTRICTED_LIST_CHECK", "Restricted List Check", ComplianceStatus.FAIL,
                    "Restricted list check failed: " + e.getMessage());
        }
    }

    public interface RestrictedListStore {
        String getTodayBs();
        Promise<List<RestrictedListEntry>> findActive(String clientId, String instrumentId, String todayBs);
        Promise<Void> save(RestrictedListEntry entry);
        Promise<List<RestrictedListEntry>> findByInstrument(String instrumentId);
    }
}
