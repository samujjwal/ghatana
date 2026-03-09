package com.ghatana.ai.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ChatMessage}.
 *
 * Covers factory methods, null validation, equality, and Role enum.
 */
@DisplayName("ChatMessage")
class ChatMessageTest {

    @Nested
    @DisplayName("Role enum")
    class RoleEnum {

        @Test
        @DisplayName("system role has value 'system'")
        void systemRoleValue() {
            assertThat(ChatMessage.Role.SYSTEM.getValue()).isEqualTo("system");
        }

        @Test
        @DisplayName("user role has value 'user'")
        void userRoleValue() {
            assertThat(ChatMessage.Role.USER.getValue()).isEqualTo("user");
        }

        @Test
        @DisplayName("assistant role has value 'assistant'")
        void assistantRoleValue() {
            assertThat(ChatMessage.Role.ASSISTANT.getValue()).isEqualTo("assistant");
        }

        @Test
        @DisplayName("tool role has value 'tool'")
        void toolRoleValue() {
            assertThat(ChatMessage.Role.TOOL.getValue()).isEqualTo("tool");
        }

        @Test
        @DisplayName("toString returns enum constant name")
        void toStringReturnsValue() {
            assertThat(ChatMessage.Role.SYSTEM.toString()).isEqualTo("SYSTEM");
            assertThat(ChatMessage.Role.USER.toString()).isEqualTo("USER");
        }
    }

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("system() creates SYSTEM message")
        void systemFactory() {
            ChatMessage msg = ChatMessage.system("You are a helpful assistant");
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.SYSTEM);
            assertThat(msg.getContent()).isEqualTo("You are a helpful assistant");
            assertThat(msg.getName()).isNull();
        }

        @Test
        @DisplayName("user() creates USER message")
        void userFactory() {
            ChatMessage msg = ChatMessage.user("Hello");
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.USER);
            assertThat(msg.getContent()).isEqualTo("Hello");
            assertThat(msg.getName()).isNull();
        }

        @Test
        @DisplayName("assistant() creates ASSISTANT message")
        void assistantFactory() {
            ChatMessage msg = ChatMessage.assistant("I can help with that");
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT);
            assertThat(msg.getContent()).isEqualTo("I can help with that");
            assertThat(msg.getName()).isNull();
        }

        @Test
        @DisplayName("of(role, content) creates message without name")
        void ofWithoutName() {
            ChatMessage msg = ChatMessage.of(ChatMessage.Role.TOOL, "result data");
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.TOOL);
            assertThat(msg.getContent()).isEqualTo("result data");
            assertThat(msg.getName()).isNull();
        }

        @Test
        @DisplayName("of(role, content, name) creates message with name")
        void ofWithName() {
            ChatMessage msg = ChatMessage.of(ChatMessage.Role.TOOL, "search results", "search_tool");
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.TOOL);
            assertThat(msg.getContent()).isEqualTo("search results");
            assertThat(msg.getName()).isEqualTo("search_tool");
        }
    }

    @Nested
    @DisplayName("null validation")
    class NullValidation {

        @Test
        @DisplayName("null role throws NullPointerException")
        void nullRole() {
            assertThatThrownBy(() -> ChatMessage.of(null, "content"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null content throws NullPointerException")
        void nullContent() {
            assertThatThrownBy(() -> ChatMessage.user(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null content in system() throws NullPointerException")
        void nullContentSystem() {
            assertThatThrownBy(() -> ChatMessage.system(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null content in assistant() throws NullPointerException")
        void nullContentAssistant() {
            assertThatThrownBy(() -> ChatMessage.assistant(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("equality and hashCode")
    class Equality {

        @Test
        @DisplayName("equal messages are equal")
        void equalMessages() {
            ChatMessage msg1 = ChatMessage.user("Hello");
            ChatMessage msg2 = ChatMessage.user("Hello");
            assertThat(msg1).isEqualTo(msg2);
            assertThat(msg1.hashCode()).isEqualTo(msg2.hashCode());
        }

        @Test
        @DisplayName("different content not equal")
        void differentContent() {
            ChatMessage msg1 = ChatMessage.user("Hello");
            ChatMessage msg2 = ChatMessage.user("Goodbye");
            assertThat(msg1).isNotEqualTo(msg2);
        }

        @Test
        @DisplayName("different roles not equal")
        void differentRoles() {
            ChatMessage msg1 = ChatMessage.user("Hello");
            ChatMessage msg2 = ChatMessage.assistant("Hello");
            assertThat(msg1).isNotEqualTo(msg2);
        }

        @Test
        @DisplayName("different names not equal")
        void differentNames() {
            ChatMessage msg1 = ChatMessage.of(ChatMessage.Role.TOOL, "result", "tool_a");
            ChatMessage msg2 = ChatMessage.of(ChatMessage.Role.TOOL, "result", "tool_b");
            assertThat(msg1).isNotEqualTo(msg2);
        }

        @Test
        @DisplayName("named vs unnamed not equal")
        void namedVsUnnamed() {
            ChatMessage msg1 = ChatMessage.of(ChatMessage.Role.TOOL, "result");
            ChatMessage msg2 = ChatMessage.of(ChatMessage.Role.TOOL, "result", "tool_a");
            assertThat(msg1).isNotEqualTo(msg2);
        }

        @Test
        @DisplayName("not equal to null")
        void notEqualToNull() {
            assertThat(ChatMessage.user("Hello")).isNotEqualTo(null);
        }

        @Test
        @DisplayName("not equal to different type")
        void notEqualToDifferentType() {
            assertThat(ChatMessage.user("Hello")).isNotEqualTo("Hello");
        }

        @Test
        @DisplayName("equal to itself")
        void equalToItself() {
            ChatMessage msg = ChatMessage.user("Hello");
            assertThat(msg).isEqualTo(msg);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString without name excludes name field")
        void toStringWithoutName() {
            ChatMessage msg = ChatMessage.user("Hello");
            String str = msg.toString();
            assertThat(str).contains("USER").contains("Hello");
        }

        @Test
        @DisplayName("toString with name includes name field")
        void toStringWithName() {
            ChatMessage msg = ChatMessage.of(ChatMessage.Role.TOOL, "data", "search_tool");
            String str = msg.toString();
            assertThat(str).contains("TOOL").contains("data").contains("search_tool");
        }
    }
}
