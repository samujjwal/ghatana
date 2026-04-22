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
@DisplayName("NotificationChannelType Enum Tests [GH-90000]")
class NotificationChannelTypeTest {

    @Nested
    @DisplayName("Enum Values Tests [GH-90000]")
    class EnumValuesTests {

        @Test
        @DisplayName("should have all expected notification channel types [GH-90000]")
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
        @DisplayName("all enum values should have non-null display name [GH-90000]")
        void allValuesShouldHaveDisplayName(NotificationChannelType type) { // GH-90000
            assertThat(type.getDisplayName()).isNotNull(); // GH-90000
            assertThat(type.getDisplayName()).isNotBlank(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Display Name Tests [GH-90000]")
    class DisplayNameTests {

        @Test
        @DisplayName("EMAIL should have display name 'Email' [GH-90000]")
        void emailDisplayName() { // GH-90000
            assertThat(NotificationChannelType.EMAIL.getDisplayName()).isEqualTo("Email [GH-90000]");
        }

        @Test
        @DisplayName("SLACK should have display name 'Slack' [GH-90000]")
        void slackDisplayName() { // GH-90000
            assertThat(NotificationChannelType.SLACK.getDisplayName()).isEqualTo("Slack [GH-90000]");
        }

        @Test
        @DisplayName("TEAMS should have display name 'Microsoft Teams' [GH-90000]")
        void teamsDisplayName() { // GH-90000
            assertThat(NotificationChannelType.TEAMS.getDisplayName()).isEqualTo("Microsoft Teams [GH-90000]");
        }

        @Test
        @DisplayName("WEBHOOK should have display name 'Webhook' [GH-90000]")
        void webhookDisplayName() { // GH-90000
            assertThat(NotificationChannelType.WEBHOOK.getDisplayName()).isEqualTo("Webhook [GH-90000]");
        }

        @Test
        @DisplayName("PAGERDUTY should have display name 'PagerDuty' [GH-90000]")
        void pagerdutyDisplayName() { // GH-90000
            assertThat(NotificationChannelType.PAGERDUTY.getDisplayName()).isEqualTo("PagerDuty [GH-90000]");
        }

        @Test
        @DisplayName("OPSGENIE should have display name 'Opsgenie' [GH-90000]")
        void opsgenieDisplayName() { // GH-90000
            assertThat(NotificationChannelType.OPSGENIE.getDisplayName()).isEqualTo("Opsgenie [GH-90000]");
        }

        @Test
        @DisplayName("JIRA should have display name 'Jira' [GH-90000]")
        void jiraDisplayName() { // GH-90000
            assertThat(NotificationChannelType.JIRA.getDisplayName()).isEqualTo("Jira [GH-90000]");
        }

        @Test
        @DisplayName("SMS should have display name 'SMS' [GH-90000]")
        void smsDisplayName() { // GH-90000
            assertThat(NotificationChannelType.SMS.getDisplayName()).isEqualTo("SMS [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Rich Content Support Tests [GH-90000]")
    class RichContentSupportTests {

        @Test
        @DisplayName("EMAIL should support rich content [GH-90000]")
        void emailSupportsRichContent() { // GH-90000
            assertThat(NotificationChannelType.EMAIL.supportsRichContent()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("SLACK should support rich content [GH-90000]")
        void slackSupportsRichContent() { // GH-90000
            assertThat(NotificationChannelType.SLACK.supportsRichContent()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("TEAMS should support rich content [GH-90000]")
        void teamsSupportsRichContent() { // GH-90000
            assertThat(NotificationChannelType.TEAMS.supportsRichContent()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("WEBHOOK should not support rich content [GH-90000]")
        void webhookDoesNotSupportRichContent() { // GH-90000
            assertThat(NotificationChannelType.WEBHOOK.supportsRichContent()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("PAGERDUTY should support rich content [GH-90000]")
        void pagerdutySupportsRichContent() { // GH-90000
            assertThat(NotificationChannelType.PAGERDUTY.supportsRichContent()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("OPSGENIE should support rich content [GH-90000]")
        void opsgenieSupportsRichContent() { // GH-90000
            assertThat(NotificationChannelType.OPSGENIE.supportsRichContent()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("JIRA should not support rich content [GH-90000]")
        void jiraDoesNotSupportRichContent() { // GH-90000
            assertThat(NotificationChannelType.JIRA.supportsRichContent()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("SMS should support rich content [GH-90000]")
        void smsSupportsRichContent() { // GH-90000
            assertThat(NotificationChannelType.SMS.supportsRichContent()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Incident Management Tests [GH-90000]")
    class IncidentManagementTests {

        @Test
        @DisplayName("PAGERDUTY should be incident management [GH-90000]")
        void pagerdutyIsIncidentManagement() { // GH-90000
            assertThat(NotificationChannelType.PAGERDUTY.isIncidentManagement()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("OPSGENIE should be incident management [GH-90000]")
        void opsgenieIsIncidentManagement() { // GH-90000
            assertThat(NotificationChannelType.OPSGENIE.isIncidentManagement()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("EMAIL should not be incident management [GH-90000]")
        void emailIsNotIncidentManagement() { // GH-90000
            assertThat(NotificationChannelType.EMAIL.isIncidentManagement()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("SLACK should not be incident management [GH-90000]")
        void slackIsNotIncidentManagement() { // GH-90000
            assertThat(NotificationChannelType.SLACK.isIncidentManagement()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("JIRA should not be incident management [GH-90000]")
        void jiraIsNotIncidentManagement() { // GH-90000
            assertThat(NotificationChannelType.JIRA.isIncidentManagement()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Real-Time Messaging Tests [GH-90000]")
    class RealTimeMessagingTests {

        @Test
        @DisplayName("SLACK should be real-time messaging [GH-90000]")
        void slackIsRealTimeMessaging() { // GH-90000
            assertThat(NotificationChannelType.SLACK.isRealTimeMessaging()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("TEAMS should be real-time messaging [GH-90000]")
        void teamsIsRealTimeMessaging() { // GH-90000
            assertThat(NotificationChannelType.TEAMS.isRealTimeMessaging()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("SMS should be real-time messaging [GH-90000]")
        void smsIsRealTimeMessaging() { // GH-90000
            assertThat(NotificationChannelType.SMS.isRealTimeMessaging()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("EMAIL should not be real-time messaging [GH-90000]")
        void emailIsNotRealTimeMessaging() { // GH-90000
            assertThat(NotificationChannelType.EMAIL.isRealTimeMessaging()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("WEBHOOK should not be real-time messaging [GH-90000]")
        void webhookIsNotRealTimeMessaging() { // GH-90000
            assertThat(NotificationChannelType.WEBHOOK.isRealTimeMessaging()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("PAGERDUTY should not be real-time messaging [GH-90000]")
        void pagerdutyIsNotRealTimeMessaging() { // GH-90000
            assertThat(NotificationChannelType.PAGERDUTY.isRealTimeMessaging()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("valueOf Tests [GH-90000]")
    class ValueOfTests {

        @Test
        @DisplayName("valueOf should return correct enum for valid string [GH-90000]")
        void valueOfReturnsCorrectEnum() { // GH-90000
            assertThat(NotificationChannelType.valueOf("EMAIL [GH-90000]")).isEqualTo(NotificationChannelType.EMAIL);
            assertThat(NotificationChannelType.valueOf("SLACK [GH-90000]")).isEqualTo(NotificationChannelType.SLACK);
            assertThat(NotificationChannelType.valueOf("TEAMS [GH-90000]")).isEqualTo(NotificationChannelType.TEAMS);
            assertThat(NotificationChannelType.valueOf("WEBHOOK [GH-90000]")).isEqualTo(NotificationChannelType.WEBHOOK);
            assertThat(NotificationChannelType.valueOf("PAGERDUTY [GH-90000]")).isEqualTo(NotificationChannelType.PAGERDUTY);
            assertThat(NotificationChannelType.valueOf("OPSGENIE [GH-90000]")).isEqualTo(NotificationChannelType.OPSGENIE);
            assertThat(NotificationChannelType.valueOf("JIRA [GH-90000]")).isEqualTo(NotificationChannelType.JIRA);
            assertThat(NotificationChannelType.valueOf("SMS [GH-90000]")).isEqualTo(NotificationChannelType.SMS);
        }
    }

    @Nested
    @DisplayName("Category Summary Tests [GH-90000]")
    class CategorySummaryTests {

        @Test
        @DisplayName("should have exactly 2 incident management channels [GH-90000]")
        void shouldHaveTwoIncidentManagementChannels() { // GH-90000
            long count = java.util.Arrays.stream(NotificationChannelType.values()) // GH-90000
                    .filter(NotificationChannelType::isIncidentManagement) // GH-90000
                    .count(); // GH-90000

            assertThat(count).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("should have exactly 3 real-time messaging channels [GH-90000]")
        void shouldHaveThreeRealTimeMessagingChannels() { // GH-90000
            long count = java.util.Arrays.stream(NotificationChannelType.values()) // GH-90000
                    .filter(NotificationChannelType::isRealTimeMessaging) // GH-90000
                    .count(); // GH-90000

            assertThat(count).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("should have exactly 6 channels supporting rich content [GH-90000]")
        void shouldHaveSixRichContentChannels() { // GH-90000
            long count = java.util.Arrays.stream(NotificationChannelType.values()) // GH-90000
                    .filter(NotificationChannelType::supportsRichContent) // GH-90000
                    .count(); // GH-90000

            assertThat(count).isEqualTo(6); // GH-90000
        }
    }
}
