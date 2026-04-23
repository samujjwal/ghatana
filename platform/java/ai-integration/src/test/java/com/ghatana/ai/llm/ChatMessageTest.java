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
        void systemRoleValue() { // GH-90000
            assertThat(ChatMessage.Role.SYSTEM.getValue()).isEqualTo("system");
        }

        @Test
        @DisplayName("user role has value 'user'")
        void userRoleValue() { // GH-90000
            assertThat(ChatMessage.Role.USER.getValue()).isEqualTo("user");
        }

        @Test
        @DisplayName("assistant role has value 'assistant'")
        void assistantRoleValue() { // GH-90000
            assertThat(ChatMessage.Role.ASSISTANT.getValue()).isEqualTo("assistant");
        }

        @Test
        @DisplayName("tool role has value 'tool'")
        void toolRoleValue() { // GH-90000
            assertThat(ChatMessage.Role.TOOL.getValue()).isEqualTo("tool");
        }

        @Test
        @DisplayName("toString returns enum constant name")
        void toStringReturnsValue() { // GH-90000
            assertThat(ChatMessage.Role.SYSTEM.toString()).isEqualTo("SYSTEM");
            assertThat(ChatMessage.Role.USER.toString()).isEqualTo("USER");
        }
    }

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("system() creates SYSTEM message")
        void systemFactory() { // GH-90000
            ChatMessage msg = ChatMessage.system("You are a helpful assistant");
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.SYSTEM); // GH-90000
            assertThat(msg.getContent()).isEqualTo("You are a helpful assistant");
            assertThat(msg.getName()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("user() creates USER message")
        void userFactory() { // GH-90000
            ChatMessage msg = ChatMessage.user("Hello");
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.USER); // GH-90000
            assertThat(msg.getContent()).isEqualTo("Hello");
            assertThat(msg.getName()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("assistant() creates ASSISTANT message")
        void assistantFactory() { // GH-90000
            ChatMessage msg = ChatMessage.assistant("I can help with that");
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT); // GH-90000
            assertThat(msg.getContent()).isEqualTo("I can help with that");
            assertThat(msg.getName()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("of(role, content) creates message without name")
        void ofWithoutName() { // GH-90000
            ChatMessage msg = ChatMessage.of(ChatMessage.Role.TOOL, "result data"); // GH-90000
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.TOOL); // GH-90000
            assertThat(msg.getContent()).isEqualTo("result data");
            assertThat(msg.getName()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("of(role, content, name) creates message with name")
        void ofWithName() { // GH-90000
            ChatMessage msg = ChatMessage.of(ChatMessage.Role.TOOL, "search results", "search_tool"); // GH-90000
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.TOOL); // GH-90000
            assertThat(msg.getContent()).isEqualTo("search results");
            assertThat(msg.getName()).isEqualTo("search_tool");
        }
    }

    @Nested
    @DisplayName("null validation")
    class NullValidation {

        @Test
        @DisplayName("null role throws NullPointerException")
        void nullRole() { // GH-90000
            assertThatThrownBy(() -> ChatMessage.of(null, "content")) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null content throws NullPointerException")
        void nullContent() { // GH-90000
            assertThatThrownBy(() -> ChatMessage.user(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null content in system() throws NullPointerException")
        void nullContentSystem() { // GH-90000
            assertThatThrownBy(() -> ChatMessage.system(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null content in assistant() throws NullPointerException")
        void nullContentAssistant() { // GH-90000
            assertThatThrownBy(() -> ChatMessage.assistant(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("equality and hashCode")
    class Equality {

        @Test
        @DisplayName("equal messages are equal")
        void equalMessages() { // GH-90000
            ChatMessage msg1 = ChatMessage.user("Hello");
            ChatMessage msg2 = ChatMessage.user("Hello");
            assertThat(msg1).isEqualTo(msg2); // GH-90000
            assertThat(msg1.hashCode()).isEqualTo(msg2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("different content not equal")
        void differentContent() { // GH-90000
            ChatMessage msg1 = ChatMessage.user("Hello");
            ChatMessage msg2 = ChatMessage.user("Goodbye");
            assertThat(msg1).isNotEqualTo(msg2); // GH-90000
        }

        @Test
        @DisplayName("different roles not equal")
        void differentRoles() { // GH-90000
            ChatMessage msg1 = ChatMessage.user("Hello");
            ChatMessage msg2 = ChatMessage.assistant("Hello");
            assertThat(msg1).isNotEqualTo(msg2); // GH-90000
        }

        @Test
        @DisplayName("different names not equal")
        void differentNames() { // GH-90000
            ChatMessage msg1 = ChatMessage.of(ChatMessage.Role.TOOL, "result", "tool_a"); // GH-90000
            ChatMessage msg2 = ChatMessage.of(ChatMessage.Role.TOOL, "result", "tool_b"); // GH-90000
            assertThat(msg1).isNotEqualTo(msg2); // GH-90000
        }

        @Test
        @DisplayName("named vs unnamed not equal")
        void namedVsUnnamed() { // GH-90000
            ChatMessage msg1 = ChatMessage.of(ChatMessage.Role.TOOL, "result"); // GH-90000
            ChatMessage msg2 = ChatMessage.of(ChatMessage.Role.TOOL, "result", "tool_a"); // GH-90000
            assertThat(msg1).isNotEqualTo(msg2); // GH-90000
        }

        @Test
        @DisplayName("not equal to null")
        void notEqualToNull() { // GH-90000
            assertThat(ChatMessage.user("Hello")).isNotEqualTo(null);
        }

        @Test
        @DisplayName("not equal to different type")
        void notEqualToDifferentType() { // GH-90000
            assertThat(ChatMessage.user("Hello")).isNotEqualTo("Hello");
        }

        @Test
        @DisplayName("equal to itself")
        void equalToItself() { // GH-90000
            ChatMessage msg = ChatMessage.user("Hello");
            assertThat(msg).isEqualTo(msg); // GH-90000
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString without name excludes name field")
        void toStringWithoutName() { // GH-90000
            ChatMessage msg = ChatMessage.user("Hello");
            String str = msg.toString(); // GH-90000
            assertThat(str).contains("USER").contains("Hello");
        }

        @Test
        @DisplayName("toString with name includes name field")
        void toStringWithName() { // GH-90000
            ChatMessage msg = ChatMessage.of(ChatMessage.Role.TOOL, "data", "search_tool"); // GH-90000
            String str = msg.toString(); // GH-90000
            assertThat(str).contains("TOOL").contains("data").contains("search_tool");
        }
    }
}
