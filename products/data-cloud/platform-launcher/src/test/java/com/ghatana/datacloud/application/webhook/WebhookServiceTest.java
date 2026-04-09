package com.ghatana.datacloud.application.webhook;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.webhook.Webhook;
import com.ghatana.datacloud.entity.webhook.WebhookEventType;
import com.ghatana.datacloud.entity.webhook.WebhookRepository;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link WebhookService}.
 *
 * @doc.type test
 * @doc.purpose Validate webhook registration, listing, enabling/disabling, URL update, and deletion
 * @doc.layer application
 */
@DisplayName("WebhookService Tests")
@ExtendWith(MockitoExtension.class)
class WebhookServiceTest extends EventloopTestBase {

    @Mock
    private WebhookRepository webhookRepository;

    @Mock
    private MetricsCollector metrics;

    private WebhookService webhookService;

    private Webhook sampleWebhook;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(webhookRepository, metrics);
        sampleWebhook = new Webhook(
                UUID.randomUUID(),
                "tenant-1",
                WebhookEventType.ENTITY_CREATED,
                "https://example.com/hook",
                "prod",
                true,
                3,
                5000,
                120
        );
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should throw NullPointerException when repository is null")
        void shouldThrowForNullRepository() {
            assertThatThrownBy(() -> new WebhookService(null, metrics))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException when metrics is null")
        void shouldThrowForNullMetrics() {
            assertThatThrownBy(() -> new WebhookService(webhookRepository, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // REGISTER WEBHOOK
    // =========================================================================

    @Nested
    @DisplayName("Register Webhook")
    class RegisterWebhook {

        @Test
        @DisplayName("should register a new webhook and return saved entity")
        void shouldRegisterWebhook() {
            when(webhookRepository.save(any())).thenReturn(Promise.of(sampleWebhook));

            Webhook result = runPromise(() -> webhookService.registerWebhook(
                    "tenant-1",
                    WebhookEventType.ENTITY_CREATED,
                    "https://example.com/hook",
                    "prod",
                    3,
                    5000,
                    120
            ));

            assertThat(result).isNotNull();
            assertThat(result.getTenantId()).isEqualTo("tenant-1");
            assertThat(result.getEventType()).isEqualTo(WebhookEventType.ENTITY_CREATED);
            verify(webhookRepository).save(any(Webhook.class));
        }

        @Test
        @DisplayName("should generate a non-null ID for registered webhook")
        void shouldPersistWithGeneratedId() {
            when(webhookRepository.save(any())).thenAnswer(inv -> Promise.of(inv.getArgument(0)));

            Webhook result = runPromise(() -> webhookService.registerWebhook(
                    "tenant-2",
                    WebhookEventType.COLLECTION_CREATED,
                    "https://example.com/cb",
                    "staging",
                    2,
                    3000,
                    60
            ));

            assertThat(result.getId()).isNotNull();
        }
    }

    // =========================================================================
    // LIST WEBHOOKS
    // =========================================================================

    @Nested
    @DisplayName("List Webhooks")
    class ListWebhooks {

        @Test
        @DisplayName("should list all webhooks for a tenant")
        void shouldListWebhooksForTenant() {
            when(webhookRepository.findByTenant("tenant-1"))
                    .thenReturn(Promise.of(List.of(sampleWebhook)));

            List<Webhook> webhooks = runPromise(() -> webhookService.listWebhooks("tenant-1"));
            assertThat(webhooks).hasSize(1);
        }

        @Test
        @DisplayName("should list webhooks by event type")
        void shouldListByEventType() {
            when(webhookRepository.findByTenantAndEventType("tenant-1", WebhookEventType.ENTITY_CREATED))
                    .thenReturn(Promise.of(List.of(sampleWebhook)));

            List<Webhook> webhooks = runPromise(() ->
                    webhookService.listWebhooksByEventType("tenant-1", WebhookEventType.ENTITY_CREATED));
            assertThat(webhooks).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list when no webhooks for tenant")
        void shouldReturnEmptyListForUnknownTenant() {
            when(webhookRepository.findByTenant("unknown-tenant"))
                    .thenReturn(Promise.of(List.of()));

            List<Webhook> webhooks = runPromise(() -> webhookService.listWebhooks("unknown-tenant"));
            assertThat(webhooks).isEmpty();
        }
    }

    // =========================================================================
    // GET WEBHOOK
    // =========================================================================

    @Nested
    @DisplayName("Get Webhook")
    class GetWebhook {

        @Test
        @DisplayName("should return webhook by ID and tenant")
        void shouldReturnWebhook() {
            UUID id = sampleWebhook.getId();
            when(webhookRepository.findById(id, "tenant-1"))
                    .thenReturn(Promise.of(Optional.of(sampleWebhook)));

            Optional<Webhook> result = runPromise(() -> webhookService.getWebhook(id, "tenant-1"));
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("should return empty when webhook not found")
        void shouldReturnEmptyWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(webhookRepository.findById(id, "tenant-1"))
                    .thenReturn(Promise.of(Optional.empty()));

            Optional<Webhook> result = runPromise(() -> webhookService.getWebhook(id, "tenant-1"));
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // ENABLE / DISABLE
    // =========================================================================

    @Nested
    @DisplayName("Enable and Disable")
    class EnableDisable {

        @Test
        @DisplayName("should disable a webhook")
        void shouldDisableWebhook() {
            UUID id = sampleWebhook.getId();
            Webhook disabled = new Webhook(id, "tenant-1", WebhookEventType.ENTITY_CREATED,
                    "https://example.com/hook", "prod", false, 3, 5000, 120);

            when(webhookRepository.findById(id, "tenant-1"))
                    .thenReturn(Promise.of(Optional.of(sampleWebhook)));
            when(webhookRepository.save(any())).thenReturn(Promise.of(disabled));

            Webhook result = runPromise(() -> webhookService.updateEnabled(id, "tenant-1", false));
            assertThat(result.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should enable a disabled webhook")
        void shouldEnableWebhook() {
            Webhook disabledHook = new Webhook(sampleWebhook.getId(), "tenant-1",
                    WebhookEventType.ENTITY_CREATED, "https://example.com/hook", "prod",
                    false, 3, 5000, 120);
            UUID id = disabledHook.getId();

            when(webhookRepository.findById(id, "tenant-1"))
                    .thenReturn(Promise.of(Optional.of(disabledHook)));
            when(webhookRepository.save(any())).thenReturn(Promise.of(sampleWebhook));

            Webhook result = runPromise(() -> webhookService.updateEnabled(id, "tenant-1", true));
            assertThat(result.isEnabled()).isTrue();
        }
    }

    // =========================================================================
    // DELETE WEBHOOK
    // =========================================================================

    @Nested
    @DisplayName("Delete Webhook")
    class DeleteWebhook {

        @Test
        @DisplayName("should delete a webhook by ID and tenant")
        void shouldDeleteWebhook() {
            UUID id = sampleWebhook.getId();
            when(webhookRepository.deleteById(id, "tenant-1")).thenReturn(Promise.complete());

            assertThatCode(() -> runPromise(() -> webhookService.deleteWebhook(id, "tenant-1")))
                    .doesNotThrowAnyException();
            verify(webhookRepository).deleteById(id, "tenant-1");
        }
    }
}
