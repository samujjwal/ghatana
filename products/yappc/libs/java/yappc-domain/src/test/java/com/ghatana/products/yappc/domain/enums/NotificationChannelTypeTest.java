package com.ghatana.products.yappc.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NotificationChannelType} enum.
 *
 * @doc.type class
 * @doc.purpose Validates NotificationChannelType enum values and behavior
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("NotificationChannelType Enum Tests")
class NotificationChannelTypeTest {

    @Nested
    @DisplayName("Enum Values Tests")
    class EnumValuesTests {

        @Test
        @DisplayName("should have all expected notification channel types")
        void shouldHaveAllExpectedTypes() {
            NotificationChannelType[] values = NotificationChannelType.values();

            assertThat(values).hasSize(8);
            assertThat(values).contains(
                    NotificationChannelType.EMAIL,
                    NotificationChannelType.SLACK,
                    NotificationChannelType.TEAMS,
                    NotificationChannelType.WEBHOOK,
                    NotificationChannelType.PAGERDUTY,
                    NotificationChannelType.OPSGENIE,
                    NotificationChannelType.JIRA,
                    NotificationChannelType.SMS
            );
        }

        @ParameterizedTest
        @EnumSource(NotificationChannelType.class)
        @DisplayName("all enum values should have non-null display name")
        void allValuesShouldHaveDisplayName(NotificationChannelType type) {
            assertThat(type.getDisplayName()).isNotNull();
            assertThat(type.getDisplayName()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Display Name Tests")
    class DisplayNameTests {

        @Test
        @DisplayName("EMAIL should have display name 'Email'")
        void emailDisplayName() {
            assertThat(NotificationChannelType.EMAIL.getDisplayName()).isEqualTo("Email");
        }

        @Test
        @DisplayName("SLACK should have display name 'Slack'")
        void slackDisplayName() {
            assertThat(NotificationChannelType.SLACK.getDisplayName()).isEqualTo("Slack");
        }

        @Test
        @DisplayName("TEAMS should have display name 'Microsoft Teams'")
        void teamsDisplayName() {
            assertThat(NotificationChannelType.TEAMS.getDisplayName()).isEqualTo("Microsoft Teams");
        }

        @Test
        @DisplayName("WEBHOOK should have display name 'Webhook'")
        void webhookDisplayName() {
            assertThat(NotificationChannelType.WEBHOOK.getDisplayName()).isEqualTo("Webhook");
        }

        @Test
        @DisplayName("PAGERDUTY should have display name 'PagerDuty'")
        void pagerdutyDisplayName() {
            assertThat(NotificationChannelType.PAGERDUTY.getDisplayName()).isEqualTo("PagerDuty");
        }

        @Test
        @DisplayName("OPSGENIE should have display name 'Opsgenie'")
        void opsgenieDisplayName() {
            assertThat(NotificationChannelType.OPSGENIE.getDisplayName()).isEqualTo("Opsgenie");
        }

        @Test
        @DisplayName("JIRA should have display name 'Jira'")
        void jiraDisplayName() {
            assertThat(NotificationChannelType.JIRA.getDisplayName()).isEqualTo("Jira");
        }

        @Test
        @DisplayName("SMS should have display name 'SMS'")
        void smsDisplayName() {
            assertThat(NotificationChannelType.SMS.getDisplayName()).isEqualTo("SMS");
        }
    }

    @Nested
    @DisplayName("Rich Content Support Tests")
    class RichContentSupportTests {

        @Test
        @DisplayName("EMAIL should support rich content")
        void emailSupportsRichContent() {
            assertThat(NotificationChannelType.EMAIL.supportsRichContent()).isTrue();
        }

        @Test
        @DisplayName("SLACK should support rich content")
        void slackSupportsRichContent() {
            assertThat(NotificationChannelType.SLACK.supportsRichContent()).isTrue();
        }

        @Test
        @DisplayName("TEAMS should support rich content")
        void teamsSupportsRichContent() {
            assertThat(NotificationChannelType.TEAMS.supportsRichContent()).isTrue();
        }

        @Test
        @DisplayName("WEBHOOK should not support rich content")
        void webhookDoesNotSupportRichContent() {
            assertThat(NotificationChannelType.WEBHOOK.supportsRichContent()).isFalse();
        }

        @Test
        @DisplayName("PAGERDUTY should support rich content")
        void pagerdutySupportsRichContent() {
            assertThat(NotificationChannelType.PAGERDUTY.supportsRichContent()).isTrue();
        }

        @Test
        @DisplayName("OPSGENIE should support rich content")
        void opsgenieSupportsRichContent() {
            assertThat(NotificationChannelType.OPSGENIE.supportsRichContent()).isTrue();
        }

        @Test
        @DisplayName("JIRA should not support rich content")
        void jiraDoesNotSupportRichContent() {
            assertThat(NotificationChannelType.JIRA.supportsRichContent()).isFalse();
        }

        @Test
        @DisplayName("SMS should support rich content")
        void smsSupportsRichContent() {
            assertThat(NotificationChannelType.SMS.supportsRichContent()).isTrue();
        }
    }

    @Nested
    @DisplayName("Incident Management Tests")
    class IncidentManagementTests {

        @Test
        @DisplayName("PAGERDUTY should be incident management")
        void pagerdutyIsIncidentManagement() {
            assertThat(NotificationChannelType.PAGERDUTY.isIncidentManagement()).isTrue();
        }

        @Test
        @DisplayName("OPSGENIE should be incident management")
        void opsgenieIsIncidentManagement() {
            assertThat(NotificationChannelType.OPSGENIE.isIncidentManagement()).isTrue();
        }

        @Test
        @DisplayName("EMAIL should not be incident management")
        void emailIsNotIncidentManagement() {
            assertThat(NotificationChannelType.EMAIL.isIncidentManagement()).isFalse();
        }

        @Test
        @DisplayName("SLACK should not be incident management")
        void slackIsNotIncidentManagement() {
            assertThat(NotificationChannelType.SLACK.isIncidentManagement()).isFalse();
        }

        @Test
        @DisplayName("JIRA should not be incident management")
        void jiraIsNotIncidentManagement() {
            assertThat(NotificationChannelType.JIRA.isIncidentManagement()).isFalse();
        }
    }

    @Nested
    @DisplayName("Real-Time Messaging Tests")
    class RealTimeMessagingTests {

        @Test
        @DisplayName("SLACK should be real-time messaging")
        void slackIsRealTimeMessaging() {
            assertThat(NotificationChannelType.SLACK.isRealTimeMessaging()).isTrue();
        }

        @Test
        @DisplayName("TEAMS should be real-time messaging")
        void teamsIsRealTimeMessaging() {
            assertThat(NotificationChannelType.TEAMS.isRealTimeMessaging()).isTrue();
        }

        @Test
        @DisplayName("SMS should be real-time messaging")
        void smsIsRealTimeMessaging() {
            assertThat(NotificationChannelType.SMS.isRealTimeMessaging()).isTrue();
        }

        @Test
        @DisplayName("EMAIL should not be real-time messaging")
        void emailIsNotRealTimeMessaging() {
            assertThat(NotificationChannelType.EMAIL.isRealTimeMessaging()).isFalse();
        }

        @Test
        @DisplayName("WEBHOOK should not be real-time messaging")
        void webhookIsNotRealTimeMessaging() {
            assertThat(NotificationChannelType.WEBHOOK.isRealTimeMessaging()).isFalse();
        }

        @Test
        @DisplayName("PAGERDUTY should not be real-time messaging")
        void pagerdutyIsNotRealTimeMessaging() {
            assertThat(NotificationChannelType.PAGERDUTY.isRealTimeMessaging()).isFalse();
        }
    }

    @Nested
    @DisplayName("valueOf Tests")
    class ValueOfTests {

        @Test
        @DisplayName("valueOf should return correct enum for valid string")
        void valueOfReturnsCorrectEnum() {
            assertThat(NotificationChannelType.valueOf("EMAIL")).isEqualTo(NotificationChannelType.EMAIL);
            assertThat(NotificationChannelType.valueOf("SLACK")).isEqualTo(NotificationChannelType.SLACK);
            assertThat(NotificationChannelType.valueOf("TEAMS")).isEqualTo(NotificationChannelType.TEAMS);
            assertThat(NotificationChannelType.valueOf("WEBHOOK")).isEqualTo(NotificationChannelType.WEBHOOK);
            assertThat(NotificationChannelType.valueOf("PAGERDUTY")).isEqualTo(NotificationChannelType.PAGERDUTY);
            assertThat(NotificationChannelType.valueOf("OPSGENIE")).isEqualTo(NotificationChannelType.OPSGENIE);
            assertThat(NotificationChannelType.valueOf("JIRA")).isEqualTo(NotificationChannelType.JIRA);
            assertThat(NotificationChannelType.valueOf("SMS")).isEqualTo(NotificationChannelType.SMS);
        }
    }

    @Nested
    @DisplayName("Category Summary Tests")
    class CategorySummaryTests {

        @Test
        @DisplayName("should have exactly 2 incident management channels")
        void shouldHaveTwoIncidentManagementChannels() {
            long count = java.util.Arrays.stream(NotificationChannelType.values())
                    .filter(NotificationChannelType::isIncidentManagement)
                    .count();

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("should have exactly 3 real-time messaging channels")
        void shouldHaveThreeRealTimeMessagingChannels() {
            long count = java.util.Arrays.stream(NotificationChannelType.values())
                    .filter(NotificationChannelType::isRealTimeMessaging)
                    .count();

            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("should have exactly 6 channels supporting rich content")
        void shouldHaveSixRichContentChannels() {
            long count = java.util.Arrays.stream(NotificationChannelType.values())
                    .filter(NotificationChannelType::supportsRichContent)
                    .count();

            assertThat(count).isEqualTo(6);
        }
    }
}
