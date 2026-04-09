package com.ghatana.finance.sdk;

import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @doc.type class
 * @doc.purpose Verifies public Finance SDK facades reject missing boundary inputs before delegating to transport ports
 * @doc.layer finance
 * @doc.pattern Test
 */
class FinancePlatformSdkServiceFacadeValidationTest {

    @Test
    void eventFacadeRejectsBlankTopic() {
        FinancePlatformSdkService.FinanceEventClientFacade facade =
            new FinancePlatformSdkService.FinanceEventClientFacade(new StubEventPort());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> facade.publish("   ", "CREATED", "{}", "user-1", "tenant-1")
        );

        assertEquals("topic must not be blank", exception.getMessage());
    }

    @Test
    void configFacadeRejectsMissingNamespace() {
        FinancePlatformSdkService.FinanceConfigClientFacade facade =
            new FinancePlatformSdkService.FinanceConfigClientFacade(new StubConfigPort());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> facade.get(null, "risk-threshold")
        );

        assertEquals("namespace is required", exception.getMessage());
    }

    @Test
    void auditFacadeRejectsMissingEvent() {
        FinancePlatformSdkService.FinanceAuditClientFacade facade =
            new FinancePlatformSdkService.FinanceAuditClientFacade(new StubAuditPort());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> facade.log(null)
        );

        assertEquals("event is required", exception.getMessage());
    }

    @Test
    void rulesFacadeRejectsMissingFacts() {
        FinancePlatformSdkService.FinanceRulesClientFacade facade =
            new FinancePlatformSdkService.FinanceRulesClientFacade(new StubRulesPort());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> facade.evaluate("settlement-rules", null)
        );

        assertEquals("facts is required", exception.getMessage());
    }

    @Test
    void authFacadeRejectsBlankJwt() {
        FinancePlatformSdkService.FinanceAuthClientFacade facade =
            new FinancePlatformSdkService.FinanceAuthClientFacade(new StubAuthPort());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> facade.validateToken("   ")
        );

        assertEquals("jwt must not be blank", exception.getMessage());
    }

    @Test
    void rulesFacadeDelegatesValidatedTransaction() {
        FinancePlatformSdkService.FinanceComplianceResult expected =
            new FinancePlatformSdkService.FinanceComplianceResult(true, List.of("kyc"), List.of(), "LOW");
        FinancePlatformSdkService.FinanceRulesClientFacade facade =
            new FinancePlatformSdkService.FinanceRulesClientFacade(new FinancePlatformSdkService.FinanceRulesBusPort() {
                @Override
                public Promise<FinancePlatformSdkService.FinanceRuleResult> evaluateFinanceRule(
                    String ruleSetId,
                    FinancePlatformSdkService.FinanceRuleFacts facts
                ) {
                    return Promise.of(new FinancePlatformSdkService.FinanceRuleResult(true, List.of(), Map.of()));
                }

                @Override
                public Promise<FinancePlatformSdkService.FinanceComplianceResult> validateTransaction(
                    FinancePlatformSdkService.FinanceTransaction transaction
                ) {
                    return Promise.of(expected);
                }
            });
        FinancePlatformSdkService.FinanceTransaction transaction = new FinancePlatformSdkService.FinanceTransaction(
            "txn-1",
            "PAYMENT",
            "42.50",
            "USD",
            "merchant-1",
            "tenant-1",
            Map.of("channel", "card")
        );

        Promise<FinancePlatformSdkService.FinanceComplianceResult> promise = facade.validate(transaction);

        assertSame(expected, promise.getResult());
    }

    private static final class StubConfigPort implements FinancePlatformSdkService.FinanceConfigBusPort {
        @Override
        public Promise<String> getFinanceConfig(String namespace, String key) {
            return Promise.of(namespace + ":" + key);
        }

        @Override
        public Promise<Void> updateFinanceConfig(String namespace, String key, String value, String changedBy) {
            return Promise.complete();
        }

        @Override
        public Promise<List<String>> listFinanceConfigKeys(String namespacePrefix) {
            return Promise.of(List.of(namespacePrefix));
        }
    }

    private static final class StubEventPort implements FinancePlatformSdkService.FinanceEventBusPort {
        @Override
        public Promise<String> publishFinanceEvent(
            String topic,
            String eventType,
            String payloadJson,
            FinancePlatformSdkService.FinanceComplianceMetadata compliance
        ) {
            return Promise.of("evt-1");
        }

        @Override
        public void subscribeFinanceEvents(
            String topic,
            String consumerGroup,
            FinancePlatformSdkService.FinanceEventBusPort.FinanceEventHandler handler
        ) {
        }
    }

    private static final class StubAuditPort implements FinancePlatformSdkService.FinanceAuditBusPort {
        @Override
        public Promise<Void> logFinanceEvent(FinancePlatformSdkService.FinanceAuditEvent event) {
            return Promise.complete();
        }

        @Override
        public Promise<List<FinancePlatformSdkService.FinanceAuditEvent>> queryFinanceEvents(
            FinancePlatformSdkService.FinanceAuditQuery query
        ) {
            return Promise.of(List.of());
        }
    }

    private static final class StubRulesPort implements FinancePlatformSdkService.FinanceRulesBusPort {
        @Override
        public Promise<FinancePlatformSdkService.FinanceRuleResult> evaluateFinanceRule(
            String ruleSetId,
            FinancePlatformSdkService.FinanceRuleFacts facts
        ) {
            return Promise.of(new FinancePlatformSdkService.FinanceRuleResult(true, List.of(), Map.of()));
        }

        @Override
        public Promise<FinancePlatformSdkService.FinanceComplianceResult> validateTransaction(
            FinancePlatformSdkService.FinanceTransaction transaction
        ) {
            return Promise.of(new FinancePlatformSdkService.FinanceComplianceResult(true, List.of(), List.of(), "LOW"));
        }
    }

    private static final class StubAuthPort implements FinancePlatformSdkService.FinanceAuthBusPort {
        @Override
        public Promise<FinancePlatformSdkService.FinanceAuthClaims> validateFinanceToken(String jwt) {
            return Promise.of(new FinancePlatformSdkService.FinanceAuthClaims(
                "user-1",
                List.of("TRADER"),
                List.of("trade:read"),
                "tenant-1",
                Instant.parse("2026-04-06T12:00:00Z")
            ));
        }

        @Override
        public Promise<Boolean> hasFinancePermission(
            String subject,
            FinancePlatformSdkService.FinancePermission permission
        ) {
            return Promise.of(Boolean.TRUE);
        }

        @Override
        public Promise<Boolean> canAccessFinancialData(String subject, String dataType, String tenantId) {
            return Promise.of(Boolean.TRUE);
        }
    }
}
