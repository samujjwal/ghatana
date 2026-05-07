package com.ghatana.digitalmarketing.bridge;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.notification.NotificationPlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DmosNotificationChannelRouter} (KERNEL-P1-3).
 */
@DisplayName("DmosNotificationChannelRouter")
@ExtendWith(MockitoExtension.class)
class DmosNotificationChannelRouterTest extends EventloopTestBase {

    @Mock
    private NotificationPlugin notificationPlugin;

    private DmosNotificationChannelRouter router;

    @BeforeEach
    void setUp() {
        router = new DmosNotificationChannelRouter(notificationPlugin);
    }

    @Test
    @DisplayName("dispatch routes email.* template and returns notification ID")
    void dispatchEmailTemplate() {
        when(notificationPlugin.dispatch(eq("recipient-1"), eq(DmosNotificationChannelRouter.EMAIL_CAMPAIGN_LAUNCHED), any()))
            .thenReturn(Promise.of("notif-email-1"));

        String notifId = runPromise(() -> router.dispatch("recipient-1",
            DmosNotificationChannelRouter.EMAIL_CAMPAIGN_LAUNCHED, Map.of("campaignId", "c-1")));

        assertThat(notifId).isEqualTo("notif-email-1");
        verify(notificationPlugin).dispatch("recipient-1",
            DmosNotificationChannelRouter.EMAIL_CAMPAIGN_LAUNCHED, Map.of("campaignId", "c-1"));
    }

    @Test
    @DisplayName("dispatch routes sms.* template")
    void dispatchSmsTemplate() {
        when(notificationPlugin.dispatch(eq("recipient-2"), eq(DmosNotificationChannelRouter.SMS_BUDGET_ALERT), any()))
            .thenReturn(Promise.of("notif-sms-1"));

        String notifId = runPromise(() -> router.dispatch("recipient-2",
            DmosNotificationChannelRouter.SMS_BUDGET_ALERT, Map.of("amount", "5000")));

        assertThat(notifId).isEqualTo("notif-sms-1");
    }

    @Test
    @DisplayName("dispatch routes push.* template")
    void dispatchPushTemplate() {
        when(notificationPlugin.dispatch(eq("recipient-3"), eq(DmosNotificationChannelRouter.PUSH_APPROVAL_REQUIRED), any()))
            .thenReturn(Promise.of("notif-push-1"));

        String notifId = runPromise(() -> router.dispatch("recipient-3",
            DmosNotificationChannelRouter.PUSH_APPROVAL_REQUIRED, Map.of("workflowId", "wf-1")));

        assertThat(notifId).isEqualTo("notif-push-1");
    }

    @Test
    @DisplayName("dispatch propagates plugin failure as rejected promise")
    void dispatchPropagatesFailure() {
        when(notificationPlugin.dispatch(any(), any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("dispatch-failure")));

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
}
