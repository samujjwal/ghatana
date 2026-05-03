package com.ghatana.digitalmarketing.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for F2-001: Typed Event Schema and Event Registry.
 * Covers {@link DmEvent}, {@link DmEventType}, {@link DmEventRegistry},
 * and {@link DmPiiClassification}.
 */
@DisplayName("F2-001: DmEvent Schema and Registry Tests")
class DmEventRegistryTest {

    private static DmEvent<String> sampleEvent() {
        return DmEvent.<String>builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(DmEventType.CAMPAIGN_CREATED)
            .schemaVersion(DmEventRegistry.SCHEMA_VERSION)
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .actor("user-99")
            .actorType(DmEvent.ActorType.USER)
            .correlationId("corr-abc")
            .idempotencyKey("idem-1")
            .occurredAt(Instant.now())
            .sourceService(DmEventRegistry.SOURCE_SERVICE_APPLICATION)
            .piiClassification(DmPiiClassification.PSEUDONYMOUS)
            .payload("sample-payload")
            .build();
    }

    // ── DmEvent construction ─────────────────────────────────────────────────

    @Test
    @DisplayName("should build a valid DmEvent with all required fields")
    void shouldBuildValidEvent() {
        DmEvent<String> event = sampleEvent();

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getEventType()).isEqualTo(DmEventType.CAMPAIGN_CREATED);
        assertThat(event.getSchemaVersion()).isEqualTo("1.0.0");
        assertThat(event.getTenantId()).isEqualTo("tenant-1");
        assertThat(event.getWorkspaceId()).isEqualTo("ws-1");
        assertThat(event.getActor()).isEqualTo("user-99");
        assertThat(event.getActorType()).isEqualTo(DmEvent.ActorType.USER);
        assertThat(event.getCorrelationId()).isEqualTo("corr-abc");
        assertThat(event.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getSourceService()).isEqualTo(DmEventRegistry.SOURCE_SERVICE_APPLICATION);
        assertThat(event.getPiiClassification()).isEqualTo(DmPiiClassification.PSEUDONYMOUS);
        assertThat(event.getPayload()).isEqualTo("sample-payload");
        assertThat(event.getTags()).isEmpty();
        assertThat(event.getExternalRefs()).isEmpty();
        assertThat(event.getCausationId()).isEqualTo("");
    }

    @Test
    @DisplayName("should default causationId to empty string when not set")
    void shouldDefaultCausationIdToEmpty() {
        DmEvent<Void> event = DmEvent.<Void>builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(DmEventType.WORKFLOW_STARTED)
            .schemaVersion("1.0.0")
            .tenantId("t1")
            .workspaceId("w1")
            .actor("system")
            .actorType(DmEvent.ActorType.SYSTEM)
            .correlationId("corr-1")
            .idempotencyKey("idem-2")
            .occurredAt(Instant.now())
            .sourceService("dm-application")
            .piiClassification(DmPiiClassification.NONE)
            .build();

        assertThat(event.getCausationId()).isEqualTo("");
    }

    @Test
    @DisplayName("should propagate causationId when set")
    void shouldPropagateCausationId() {
        String parentId = UUID.randomUUID().toString();
        DmEvent<Void> event = DmEvent.<Void>builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(DmEventType.WORKFLOW_STEP_COMPLETED)
            .schemaVersion("1.0.0")
            .tenantId("t1")
            .workspaceId("w1")
            .actor("agent-1")
            .actorType(DmEvent.ActorType.AGENT)
            .correlationId("corr-2")
            .causationId(parentId)
            .idempotencyKey("idem-3")
            .occurredAt(Instant.now())
            .sourceService("dm-application")
            .piiClassification(DmPiiClassification.NONE)
            .build();

        assertThat(event.getCausationId()).isEqualTo(parentId);
    }

    @Test
    @DisplayName("should reject blank eventId")
    void shouldRejectBlankEventId() {
        assertThatThrownBy(() ->
            DmEvent.<Void>builder()
                .eventId("  ")
                .eventType(DmEventType.CAMPAIGN_CREATED)
                .schemaVersion("1.0.0")
                .tenantId("t1")
                .workspaceId("w1")
                .actor("u1")
                .actorType(DmEvent.ActorType.USER)
                .correlationId("c1")
                .idempotencyKey("i1")
                .occurredAt(Instant.now())
                .sourceService("dm-api")
                .piiClassification(DmPiiClassification.NONE)
                .build()
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eventId");
    }

    @Test
    @DisplayName("should reject null eventType")
    void shouldRejectNullEventType() {
        assertThatThrownBy(() ->
            DmEvent.<Void>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(null)
                .schemaVersion("1.0.0")
                .tenantId("t1")
                .workspaceId("w1")
                .actor("u1")
                .actorType(DmEvent.ActorType.USER)
                .correlationId("c1")
                .idempotencyKey("i1")
                .occurredAt(Instant.now())
                .sourceService("dm-api")
                .piiClassification(DmPiiClassification.NONE)
                .build()
        ).isInstanceOf(NullPointerException.class).hasMessageContaining("eventType");
    }

    @Test
    @DisplayName("should include optional tags and externalRefs when provided")
    void shouldIncludeOptionalFields() {
        DmEvent.ExternalRef ref = new DmEvent.ExternalRef("google-ads", "campaign", "gads-123");
        DmEvent<Void> event = DmEvent.<Void>builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(DmEventType.CAMPAIGN_LAUNCHED)
            .schemaVersion("1.0.0")
            .tenantId("t1")
            .workspaceId("w1")
            .actor("u1")
            .actorType(DmEvent.ActorType.USER)
            .correlationId("c1")
            .idempotencyKey("i1")
            .occurredAt(Instant.now())
            .sourceService("dm-application")
            .piiClassification(DmPiiClassification.NONE)
            .consentSnapshotId("consent-snap-1")
            .policySnapshotId("policy-v2")
            .tags(Map.of("channel", "google", "env", "prod"))
            .externalRefs(List.of(ref))
            .build();

        assertThat(event.getConsentSnapshotId()).isEqualTo("consent-snap-1");
        assertThat(event.getPolicySnapshotId()).isEqualTo("policy-v2");
        assertThat(event.getTags()).containsEntry("channel", "google");
        assertThat(event.getExternalRefs()).hasSize(1)
            .first().satisfies(r -> {
                assertThat(r.system()).isEqualTo("google-ads");
                assertThat(r.entityType()).isEqualTo("campaign");
                assertThat(r.externalId()).isEqualTo("gads-123");
            });
    }

    @Test
    @DisplayName("event equality is based on eventId only")
    void eventEqualityBasedOnEventId() {
        String sharedId = UUID.randomUUID().toString();
        DmEvent<String> a = DmEvent.<String>builder()
            .eventId(sharedId).eventType(DmEventType.CAMPAIGN_CREATED).schemaVersion("1.0.0")
            .tenantId("t1").workspaceId("w1").actor("u1").actorType(DmEvent.ActorType.USER)
            .correlationId("c1").idempotencyKey("i1").occurredAt(Instant.now())
            .sourceService("dm-api").piiClassification(DmPiiClassification.NONE).payload("a").build();
        DmEvent<String> b = DmEvent.<String>builder()
            .eventId(sharedId).eventType(DmEventType.CAMPAIGN_LAUNCHED).schemaVersion("1.0.0")
            .tenantId("t2").workspaceId("w2").actor("u2").actorType(DmEvent.ActorType.AGENT)
            .correlationId("c2").idempotencyKey("i2").occurredAt(Instant.now())
            .sourceService("dm-api").piiClassification(DmPiiClassification.PERSONAL).payload("b").build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("events with different eventIds are not equal")
    void differentEventIdsAreNotEqual() {
        DmEvent<String> a = sampleEvent();
        DmEvent<String> b = sampleEvent();
        assertThat(a).isNotEqualTo(b);
    }

    // ── DmEventType schema IDs ───────────────────────────────────────────────

    @ParameterizedTest(name = "eventType {0} should have non-blank schemaId")
    @EnumSource(DmEventType.class)
    @DisplayName("all event types should have non-blank schemaIds")
    void allEventTypesShouldHaveSchemaIds(DmEventType type) {
        assertThat(type.getSchemaId()).isNotBlank();
        assertThat(type.getSchemaId()).startsWith("dm.");
    }

    @Test
    @DisplayName("all event type schemaIds should follow dm.<domain>.<action>.v<N> format")
    void schemaIdsShouldFollowConvention() {
        for (DmEventType type : DmEventType.values()) {
            String id = type.getSchemaId();
            assertThat(id).matches("dm\\.[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*\\.v\\d+");
        }
    }

    // ── DmEventRegistry ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "eventType {0} should be registered")
    @EnumSource(DmEventType.class)
    @DisplayName("all event types should be registered in DmEventRegistry")
    void allEventTypesShouldBeRegistered(DmEventType type) {
        assertThat(DmEventRegistry.isRegistered(type))
            .as("Event type %s should be registered", type)
            .isTrue();
    }

    @ParameterizedTest(name = "eventType {0} should have a PII classification")
    @EnumSource(DmEventType.class)
    @DisplayName("all registered event types should have a PII classification")
    void allEventTypesShouldHavePiiClassification(DmEventType type) {
        assertThat(DmEventRegistry.getPiiClassification(type))
            .as("Event type %s should have a PII classification", type)
            .isPresent();
    }

    @Test
    @DisplayName("registry registeredCount should equal DmEventType enum size")
    void registeredCountShouldMatchEnumSize() {
        assertThat(DmEventRegistry.registeredCount()).isEqualTo(DmEventType.values().length);
    }

    @Test
    @DisplayName("schema version constant should be non-blank")
    void schemaVersionShouldBeNonBlank() {
        assertThat(DmEventRegistry.SCHEMA_VERSION).isNotBlank();
        assertThat(DmEventRegistry.currentSchemaVersion()).isEqualTo(DmEventRegistry.SCHEMA_VERSION);
    }

    @Test
    @DisplayName("all() registry should be unmodifiable")
    void registryAllShouldBeUnmodifiable() {
        Map<DmEventType, DmPiiClassification> registry = DmEventRegistry.all();
        assertThatThrownBy(() -> registry.put(DmEventType.CAMPAIGN_CREATED, DmPiiClassification.SENSITIVE))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── PII classification correctness ────────────────────────────────────────

    @Test
    @DisplayName("lead captured event should be classified PERSONAL")
    void leadCapturedShouldBePersonal() {
        assertThat(DmEventRegistry.getPiiClassification(DmEventType.LEAD_CAPTURED))
            .hasValue(DmPiiClassification.PERSONAL);
    }

    @Test
    @DisplayName("consent captured event should be classified PERSONAL")
    void consentCapturedShouldBePersonal() {
        assertThat(DmEventRegistry.getPiiClassification(DmEventType.CONSENT_CAPTURED))
            .hasValue(DmPiiClassification.PERSONAL);
    }

    @Test
    @DisplayName("workflow started event should be classified NONE")
    void workflowStartedShouldBeNone() {
        assertThat(DmEventRegistry.getPiiClassification(DmEventType.WORKFLOW_STARTED))
            .hasValue(DmPiiClassification.NONE);
    }

    @Test
    @DisplayName("campaign created event should be classified PSEUDONYMOUS")
    void campaignCreatedShouldBePseudonymous() {
        assertThat(DmEventRegistry.getPiiClassification(DmEventType.CAMPAIGN_CREATED))
            .hasValue(DmPiiClassification.PSEUDONYMOUS);
    }

    // ── ExternalRef validation ────────────────────────────────────────────────

    @Test
    @DisplayName("ExternalRef should reject null system")
    void externalRefShouldRejectNullSystem() {
        assertThatThrownBy(() -> new DmEvent.ExternalRef(null, "campaign", "gads-1"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ExternalRef should store all fields")
    void externalRefShouldStoreFields() {
        DmEvent.ExternalRef ref = new DmEvent.ExternalRef("hubspot", "contact", "hs-42");
        assertThat(ref.system()).isEqualTo("hubspot");
        assertThat(ref.entityType()).isEqualTo("contact");
        assertThat(ref.externalId()).isEqualTo("hs-42");
    }
}
