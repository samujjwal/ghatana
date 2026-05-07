package com.ghatana.digitalmarketing.bridge;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.notification.NotificationPlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DmosNotificationChannelRouter} (KERNEL-P1-3).
 */
@DisplayName("DmosNotificationChannelRouter")
class DmosNotificationChannelRouterTest extends EventloopTestBase {

    private TestNotificationPlugin notificationPlugin;
    private DmosNotificationChannelRouter router;

    @BeforeEach
    void setUp() {
        notificationPlugin = new TestNotificationPlugin();
        router = new DmosNotificationChannelRouter(notificationPlugin);
    }

    @Test
    @DisplayName("dispatch routes email.* template and returns notification ID")
    void dispatchEmailTemplate() {
        notificationPlugin.setDispatchResult("notif-email-1");

        String notifId = runPromise(() -> router.dispatch("recipient-1",
            DmosNotificationChannelRouter.EMAIL_CAMPAIGN_LAUNCHED, Map.of("campaignId", "c-1")));

        assertThat(notifId).isEqualTo("notif-email-1");
        assertThat(notificationPlugin.getLastRecipient()).isEqualTo("recipient-1");
        assertThat(notificationPlugin.getLastTemplate()).isEqualTo(DmosNotificationChannelRouter.EMAIL_CAMPAIGN_LAUNCHED);
        assertThat(notificationPlugin.getLastContext()).isEqualTo(Map.of("campaignId", "c-1"));
    }

    @Test
    @DisplayName("dispatch routes sms.* template")
    void dispatchSmsTemplate() {
        notificationPlugin.setDispatchResult("notif-sms-1");

        String notifId = runPromise(() -> router.dispatch("recipient-2",
            DmosNotificationChannelRouter.SMS_BUDGET_ALERT, Map.of("amount", "5000")));

        assertThat(notifId).isEqualTo("notif-sms-1");
    }

    @Test
    @DisplayName("dispatch routes push.* template")
    void dispatchPushTemplate() {
        notificationPlugin.setDispatchResult("notif-push-1");

        String notifId = runPromise(() -> router.dispatch("recipient-3",
            DmosNotificationChannelRouter.PUSH_APPROVAL_REQUIRED, Map.of("workflowId", "wf-1")));

        assertThat(notifId).isEqualTo("notif-push-1");
    }

    @Test
    @DisplayName("dispatch propagates plugin failure as rejected promise")
    void dispatchPropagatesFailure() {
        notificationPlugin.setFailure(new RuntimeException("dispatch-failure"));

        assertThatThrownBy(() -> runPromise(() -> router.dispatch("recipient-4",
            DmosNotificationChannelRouter.EMAIL_DSAR_ERASURE_COMPLETE, Map.of())))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("dispatch-failure");
    }

    @Test
    @DisplayName("resolveChannel returns 'email' for email.* prefix")
    void resolveChannelEmail() {
        assertThat(DmosNotificationChannelRouter.resolveChannel("email.campaign-launched")).isEqualTo("email");
    }

    @Test
    @DisplayName("resolveChannel returns 'sms' for sms.* prefix")
    void resolveChannelSms() {
        assertThat(DmosNotificationChannelRouter.resolveChannel("sms.budget-alert")).isEqualTo("sms");
    }

    @Test
    @DisplayName("resolveChannel returns 'push' for push.* prefix")
    void resolveChannelPush() {
        assertThat(DmosNotificationChannelRouter.resolveChannel("push.ai-action")).isEqualTo("push");
    }

    @Test
    @DisplayName("resolveChannel returns 'default' for unknown prefix")
    void resolveChannelDefault() {
        assertThat(DmosNotificationChannelRouter.resolveChannel("webhook.some-event")).isEqualTo("default");
    }

    @Test
    @DisplayName("resolveChannel returns 'unknown' for null template")
    void resolveChannelNull() {
        assertThat(DmosNotificationChannelRouter.resolveChannel(null)).isEqualTo("unknown");
    }

    @Test
    @DisplayName("constructor throws NullPointerException when plugin is null")
    void constructorNullPluginThrows() {
        assertThatThrownBy(() -> new DmosNotificationChannelRouter(null))
            .isInstanceOf(NullPointerException.class);
    }

    private static final class TestNotificationPlugin implements NotificationPlugin {
        private String dispatchResult = "default-notif-id";
        private RuntimeException failure;
        private String lastRecipient;
        private String lastTemplate;
        private Map<String, String> lastContext;

        void setDispatchResult(String result) {
            this.dispatchResult = result;
            this.failure = null;
        }

        void setFailure(RuntimeException failure) {
            this.failure = failure;
        }

        String getLastRecipient() {
            return lastRecipient;
        }

        String getLastTemplate() {
            return lastTemplate;
        }

        Map<String, String> getLastContext() {
            return lastContext;
        }

        @Override
        public com.ghatana.platform.plugin.PluginMetadata metadata() {
            return com.ghatana.platform.plugin.PluginMetadata.builder()
                .id("test-notification")
                .name("Test Notification Plugin")
                .version("1.0.0")
                .build();
        }

        @Override
        public com.ghatana.platform.plugin.PluginState getState() {
            return com.ghatana.platform.plugin.PluginState.RUNNING;
        }

        @Override
        public Promise<Void> initialize(com.ghatana.platform.plugin.PluginContext context) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            return Promise.complete();
        }

        @Override
        public Promise<String> dispatch(String recipient, String template, Map<String, String> context) {
            this.lastRecipient = recipient;
            this.lastTemplate = template;
            this.lastContext = context;
            if (failure != null) {
                return Promise.ofException(failure);
            }
            return Promise.of(dispatchResult);
        }

        @Override
        public Promise<DeliveryStatus> getDeliveryStatus(String notificationId) {
            return Promise.of(new DeliveryStatus(notificationId, lastRecipient, lastTemplate, DeliveryState.DELIVERED, 1, Instant.now(), null, Instant.now()));
        }

        @Override
        public Promise<Void> retry(String notificationId) {
            return Promise.complete();
        }

        @Override
        public Promise<java.util.List<DeadLetterEntry>> listDeadLetterQueue(int limit, int offset) {
            return Promise.of(java.util.List.of());
        }

        @Override
        public Promise<Void> reprocessDeadLetter(String notificationId) {
            return Promise.complete();
        }
    }
}
