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
@ExtendWith(MockitoExtension.class) // GH-90000
class WebhookServiceTest extends EventloopTestBase {

    @Mock
    private WebhookRepository webhookRepository;

    @Mock
    private MetricsCollector metrics;

    private WebhookService webhookService;

    private Webhook sampleWebhook;

    @BeforeEach
    void setUp() { // GH-90000
        webhookService = new WebhookService(webhookRepository, metrics); // GH-90000
        sampleWebhook = new Webhook( // GH-90000
                UUID.randomUUID(), // GH-90000
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
        void shouldThrowForNullRepository() { // GH-90000
            assertThatThrownBy(() -> new WebhookService(null, metrics)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException when metrics is null")
        void shouldThrowForNullMetrics() { // GH-90000
            assertThatThrownBy(() -> new WebhookService(webhookRepository, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
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
        void shouldRegisterWebhook() { // GH-90000
            when(webhookRepository.save(any())).thenReturn(Promise.of(sampleWebhook)); // GH-90000

            Webhook result = runPromise(() -> webhookService.registerWebhook( // GH-90000
                    "tenant-1",
                    WebhookEventType.ENTITY_CREATED,
                    "https://example.com/hook",
                    "prod",
                    3,
                    5000,
                    120
            ));

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getTenantId()).isEqualTo("tenant-1");
            assertThat(result.getEventType()).isEqualTo(WebhookEventType.ENTITY_CREATED); // GH-90000
            verify(webhookRepository).save(any(Webhook.class)); // GH-90000
        }

        @Test
        @DisplayName("should generate a non-null ID for registered webhook")
        void shouldPersistWithGeneratedId() { // GH-90000
            when(webhookRepository.save(any())).thenAnswer(inv -> Promise.of(inv.getArgument(0))); // GH-90000

            Webhook result = runPromise(() -> webhookService.registerWebhook( // GH-90000
                    "tenant-2",
                    WebhookEventType.COLLECTION_CREATED,
                    "https://example.com/cb",
                    "staging",
                    2,
                    3000,
                    60
            ));

            assertThat(result.getId()).isNotNull(); // GH-90000
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
        void shouldListWebhooksForTenant() { // GH-90000
            when(webhookRepository.findByTenant("tenant-1"))
                    .thenReturn(Promise.of(List.of(sampleWebhook))); // GH-90000

            List<Webhook> webhooks = runPromise(() -> webhookService.listWebhooks("tenant-1"));
            assertThat(webhooks).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("should list webhooks by event type")
        void shouldListByEventType() { // GH-90000
            when(webhookRepository.findByTenantAndEventType("tenant-1", WebhookEventType.ENTITY_CREATED)) // GH-90000
                    .thenReturn(Promise.of(List.of(sampleWebhook))); // GH-90000

            List<Webhook> webhooks = runPromise(() -> // GH-90000
                    webhookService.listWebhooksByEventType("tenant-1", WebhookEventType.ENTITY_CREATED)); // GH-90000
            assertThat(webhooks).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("should return empty list when no webhooks for tenant")
        void shouldReturnEmptyListForUnknownTenant() { // GH-90000
            when(webhookRepository.findByTenant("unknown-tenant"))
                    .thenReturn(Promise.of(List.of())); // GH-90000

            List<Webhook> webhooks = runPromise(() -> webhookService.listWebhooks("unknown-tenant"));
            assertThat(webhooks).isEmpty(); // GH-90000
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
        void shouldReturnWebhook() { // GH-90000
            UUID id = sampleWebhook.getId(); // GH-90000
            when(webhookRepository.findById(id, "tenant-1")) // GH-90000
                    .thenReturn(Promise.of(Optional.of(sampleWebhook))); // GH-90000

            Optional<Webhook> result = runPromise(() -> webhookService.getWebhook(id, "tenant-1")); // GH-90000
            assertThat(result).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("should return empty when webhook not found")
        void shouldReturnEmptyWhenNotFound() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            when(webhookRepository.findById(id, "tenant-1")) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            Optional<Webhook> result = runPromise(() -> webhookService.getWebhook(id, "tenant-1")); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
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
        void shouldDisableWebhook() { // GH-90000
            UUID id = sampleWebhook.getId(); // GH-90000
            Webhook disabled = new Webhook(id, "tenant-1", WebhookEventType.ENTITY_CREATED, // GH-90000
                    "https://example.com/hook", "prod", false, 3, 5000, 120);

            when(webhookRepository.findById(id, "tenant-1")) // GH-90000
                    .thenReturn(Promise.of(Optional.of(sampleWebhook))); // GH-90000
            when(webhookRepository.save(any())).thenReturn(Promise.of(disabled)); // GH-90000

            Webhook result = runPromise(() -> webhookService.updateEnabled(id, "tenant-1", false)); // GH-90000
            assertThat(result.isEnabled()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should enable a disabled webhook")
        void shouldEnableWebhook() { // GH-90000
            Webhook disabledHook = new Webhook(sampleWebhook.getId(), "tenant-1", // GH-90000
                    WebhookEventType.ENTITY_CREATED, "https://example.com/hook", "prod",
                    false, 3, 5000, 120);
            UUID id = disabledHook.getId(); // GH-90000

            when(webhookRepository.findById(id, "tenant-1")) // GH-90000
                    .thenReturn(Promise.of(Optional.of(disabledHook))); // GH-90000
            when(webhookRepository.save(any())).thenReturn(Promise.of(sampleWebhook)); // GH-90000

            Webhook result = runPromise(() -> webhookService.updateEnabled(id, "tenant-1", true)); // GH-90000
            assertThat(result.isEnabled()).isTrue(); // GH-90000
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
        void shouldDeleteWebhook() { // GH-90000
            UUID id = sampleWebhook.getId(); // GH-90000
            when(webhookRepository.deleteById(id, "tenant-1")).thenReturn(Promise.complete()); // GH-90000

            assertThatCode(() -> runPromise(() -> webhookService.deleteWebhook(id, "tenant-1"))) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
            verify(webhookRepository).deleteById(id, "tenant-1"); // GH-90000
        }
    }
}
