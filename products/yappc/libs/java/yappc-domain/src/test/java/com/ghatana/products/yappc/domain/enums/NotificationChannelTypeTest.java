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
        void shouldHaveAllExpectedTypes() { // GH-90000
            NotificationChannelType[] values = NotificationChannelType.values(); // GH-90000

            assertThat(values).hasSize(8); // GH-90000
            assertThat(values).contains( // GH-90000
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
        @EnumSource(NotificationChannelType.class) // GH-90000
        @DisplayName("all enum values should have non-null display name")
        void allValuesShouldHaveDisplayName(NotificationChannelType type) { // GH-90000
            assertThat(type.getDisplayName()).isNotNull(); // GH-90000
            assertThat(type.getDisplayName()).isNotBlank(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Display Name Tests")
    class DisplayNameTests {

        @Test
        @DisplayName("EMAIL should have display name 'Email'")
        void emailDisplayName() { // GH-90000
            assertThat(NotificationChannelType.EMAIL.getDisplayName()).isEqualTo("Email");
        }

        @Test
        @DisplayName("SLACK should have display name 'Slack'")
        void slackDisplayName() { // GH-90000
            assertThat(NotificationChannelType.SLACK.getDisplayName()).isEqualTo("Slack");
        }

        @Test
        @DisplayName("TEAMS should have display name 'Microsoft Teams'")
        void teamsDisplayName() { // GH-90000
            assertThat(NotificationChannelType.TEAMS.getDisplayName()).isEqualTo("Microsoft Teams");
        }

        @Test
        @DisplayName("WEBHOOK should have display name 'Webhook'")
        void webhookDisplayName() { // GH-90000
            assertThat(NotificationChannelType.WEBHOOK.getDisplayName()).isEqualTo("Webhook");
        }

        @Test
        @DisplayName("PAGERDUTY should have display name 'PagerDuty'")
        void pagerdutyDisplayName() { // GH-90000
            assertThat(NotificationChannelType.PAGERDUTY.getDisplayName()).isEqualTo("PagerDuty");
        }

        @Test
        @DisplayName("OPSGENIE should have display name 'Opsgenie'")
        void opsgenieDisplayName() { // GH-90000
            assertThat(NotificationChannelType.OPSGENIE.getDisplayName()).isEqualTo("Opsgenie");
        }

        @Test
        @DisplayName("JIRA should have display name 'Jira'")
        void jiraDisplayName() { // GH-90000
            assertThat(NotificationChannelType.JIRA.getDisplayName()).isEqualTo("Jira");
        }

        @Test
        @DisplayName("SMS should have display name 'SMS'")
        void smsDisplayName() { // GH-90000
            assertThat(NotificationChannelType.SMS.getDisplayName()).isEqualTo("SMS");
        }
    }

    @Nested
    @DisplayName("Rich Content Support Tests")
    class RichContentSupportTests {

        @Test
        @DisplayName("EMAIL should support rich content")
        void emailSupportsRichContent() { // GH-90000
            assertThat(NotificationChannelType.EMAIL.supportsRichContent()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("SLACK should support rich content")
        void slackSupportsRichContent() { // GH-90000
            assertThat(NotificationChannelType.SLACK.supportsRichContent()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("TEAMS should support rich content")
        void teamsSupportsRichContent() { // GH-90000
            assertThat(NotificationChannelType.TEAMS.supportsRichContent()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("WEBHOOK should not support rich content")
        void webhookDoesNotSupportRichContent() { // GH-90000
            assertThat(NotificationChannelType.WEBHOOK.supportsRichContent()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("PAGERDUTY should support rich content")
        void pagerdutySupportsRichContent() { // GH-90000
            assertThat(NotificationChannelType.PAGERDUTY.supportsRichContent()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("OPSGENIE should support rich content")
        void opsgenieSupportsRichContent() { // GH-90000
            assertThat(NotificationChannelType.OPSGENIE.supportsRichContent()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("JIRA should not support rich content")
        void jiraDoesNotSupportRichContent() { // GH-90000
            assertThat(NotificationChannelType.JIRA.supportsRichContent()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("SMS should support rich content")
        void smsSupportsRichContent() { // GH-90000
            assertThat(NotificationChannelType.SMS.supportsRichContent()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Incident Management Tests")
    class IncidentManagementTests {

        @Test
        @DisplayName("PAGERDUTY should be incident management")
        void pagerdutyIsIncidentManagement() { // GH-90000
            assertThat(NotificationChannelType.PAGERDUTY.isIncidentManagement()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("OPSGENIE should be incident management")
        void opsgenieIsIncidentManagement() { // GH-90000
            assertThat(NotificationChannelType.OPSGENIE.isIncidentManagement()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("EMAIL should not be incident management")
        void emailIsNotIncidentManagement() { // GH-90000
            assertThat(NotificationChannelType.EMAIL.isIncidentManagement()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("SLACK should not be incident management")
        void slackIsNotIncidentManagement() { // GH-90000
            assertThat(NotificationChannelType.SLACK.isIncidentManagement()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("JIRA should not be incident management")
        void jiraIsNotIncidentManagement() { // GH-90000
            assertThat(NotificationChannelType.JIRA.isIncidentManagement()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Real-Time Messaging Tests")
    class RealTimeMessagingTests {

        @Test
        @DisplayName("SLACK should be real-time messaging")
        void slackIsRealTimeMessaging() { // GH-90000
            assertThat(NotificationChannelType.SLACK.isRealTimeMessaging()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("TEAMS should be real-time messaging")
        void teamsIsRealTimeMessaging() { // GH-90000
            assertThat(NotificationChannelType.TEAMS.isRealTimeMessaging()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("SMS should be real-time messaging")
        void smsIsRealTimeMessaging() { // GH-90000
            assertThat(NotificationChannelType.SMS.isRealTimeMessaging()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("EMAIL should not be real-time messaging")
        void emailIsNotRealTimeMessaging() { // GH-90000
            assertThat(NotificationChannelType.EMAIL.isRealTimeMessaging()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("WEBHOOK should not be real-time messaging")
        void webhookIsNotRealTimeMessaging() { // GH-90000
            assertThat(NotificationChannelType.WEBHOOK.isRealTimeMessaging()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("PAGERDUTY should not be real-time messaging")
        void pagerdutyIsNotRealTimeMessaging() { // GH-90000
            assertThat(NotificationChannelType.PAGERDUTY.isRealTimeMessaging()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("valueOf Tests")
    class ValueOfTests {

        @Test
        @DisplayName("valueOf should return correct enum for valid string")
        void valueOfReturnsCorrectEnum() { // GH-90000
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
        void shouldHaveTwoIncidentManagementChannels() { // GH-90000
            long count = java.util.Arrays.stream(NotificationChannelType.values()) // GH-90000
                    .filter(NotificationChannelType::isIncidentManagement) // GH-90000
                    .count(); // GH-90000

            assertThat(count).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("should have exactly 3 real-time messaging channels")
        void shouldHaveThreeRealTimeMessagingChannels() { // GH-90000
            long count = java.util.Arrays.stream(NotificationChannelType.values()) // GH-90000
                    .filter(NotificationChannelType::isRealTimeMessaging) // GH-90000
                    .count(); // GH-90000

            assertThat(count).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("should have exactly 6 channels supporting rich content")
        void shouldHaveSixRichContentChannels() { // GH-90000
            long count = java.util.Arrays.stream(NotificationChannelType.values()) // GH-90000
                    .filter(NotificationChannelType::supportsRichContent) // GH-90000
                    .count(); // GH-90000

            assertThat(count).isEqualTo(6); // GH-90000
        }
    }
}
