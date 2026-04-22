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
@DisplayName("ChatMessage [GH-90000]")
class ChatMessageTest {

    @Nested
    @DisplayName("Role enum [GH-90000]")
    class RoleEnum {

        @Test
        @DisplayName("system role has value 'system' [GH-90000]")
        void systemRoleValue() { // GH-90000
            assertThat(ChatMessage.Role.SYSTEM.getValue()).isEqualTo("system [GH-90000]");
        }

        @Test
        @DisplayName("user role has value 'user' [GH-90000]")
        void userRoleValue() { // GH-90000
            assertThat(ChatMessage.Role.USER.getValue()).isEqualTo("user [GH-90000]");
        }

        @Test
        @DisplayName("assistant role has value 'assistant' [GH-90000]")
        void assistantRoleValue() { // GH-90000
            assertThat(ChatMessage.Role.ASSISTANT.getValue()).isEqualTo("assistant [GH-90000]");
        }

        @Test
        @DisplayName("tool role has value 'tool' [GH-90000]")
        void toolRoleValue() { // GH-90000
            assertThat(ChatMessage.Role.TOOL.getValue()).isEqualTo("tool [GH-90000]");
        }

        @Test
        @DisplayName("toString returns enum constant name [GH-90000]")
        void toStringReturnsValue() { // GH-90000
            assertThat(ChatMessage.Role.SYSTEM.toString()).isEqualTo("SYSTEM [GH-90000]");
            assertThat(ChatMessage.Role.USER.toString()).isEqualTo("USER [GH-90000]");
        }
    }

    @Nested
    @DisplayName("factory methods [GH-90000]")
    class FactoryMethods {

        @Test
        @DisplayName("system() creates SYSTEM message [GH-90000]")
        void systemFactory() { // GH-90000
            ChatMessage msg = ChatMessage.system("You are a helpful assistant [GH-90000]");
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.SYSTEM); // GH-90000
            assertThat(msg.getContent()).isEqualTo("You are a helpful assistant [GH-90000]");
            assertThat(msg.getName()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("user() creates USER message [GH-90000]")
        void userFactory() { // GH-90000
            ChatMessage msg = ChatMessage.user("Hello [GH-90000]");
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.USER); // GH-90000
            assertThat(msg.getContent()).isEqualTo("Hello [GH-90000]");
            assertThat(msg.getName()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("assistant() creates ASSISTANT message [GH-90000]")
        void assistantFactory() { // GH-90000
            ChatMessage msg = ChatMessage.assistant("I can help with that [GH-90000]");
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.ASSISTANT); // GH-90000
            assertThat(msg.getContent()).isEqualTo("I can help with that [GH-90000]");
            assertThat(msg.getName()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("of(role, content) creates message without name [GH-90000]")
        void ofWithoutName() { // GH-90000
            ChatMessage msg = ChatMessage.of(ChatMessage.Role.TOOL, "result data"); // GH-90000
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.TOOL); // GH-90000
            assertThat(msg.getContent()).isEqualTo("result data [GH-90000]");
            assertThat(msg.getName()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("of(role, content, name) creates message with name [GH-90000]")
        void ofWithName() { // GH-90000
            ChatMessage msg = ChatMessage.of(ChatMessage.Role.TOOL, "search results", "search_tool"); // GH-90000
            assertThat(msg.getRole()).isEqualTo(ChatMessage.Role.TOOL); // GH-90000
            assertThat(msg.getContent()).isEqualTo("search results [GH-90000]");
            assertThat(msg.getName()).isEqualTo("search_tool [GH-90000]");
        }
    }

    @Nested
    @DisplayName("null validation [GH-90000]")
    class NullValidation {

        @Test
        @DisplayName("null role throws NullPointerException [GH-90000]")
        void nullRole() { // GH-90000
            assertThatThrownBy(() -> ChatMessage.of(null, "content")) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null content throws NullPointerException [GH-90000]")
        void nullContent() { // GH-90000
            assertThatThrownBy(() -> ChatMessage.user(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null content in system() throws NullPointerException [GH-90000]")
        void nullContentSystem() { // GH-90000
            assertThatThrownBy(() -> ChatMessage.system(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null content in assistant() throws NullPointerException [GH-90000]")
        void nullContentAssistant() { // GH-90000
            assertThatThrownBy(() -> ChatMessage.assistant(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("equality and hashCode [GH-90000]")
    class Equality {

        @Test
        @DisplayName("equal messages are equal [GH-90000]")
        void equalMessages() { // GH-90000
            ChatMessage msg1 = ChatMessage.user("Hello [GH-90000]");
            ChatMessage msg2 = ChatMessage.user("Hello [GH-90000]");
            assertThat(msg1).isEqualTo(msg2); // GH-90000
            assertThat(msg1.hashCode()).isEqualTo(msg2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("different content not equal [GH-90000]")
        void differentContent() { // GH-90000
            ChatMessage msg1 = ChatMessage.user("Hello [GH-90000]");
            ChatMessage msg2 = ChatMessage.user("Goodbye [GH-90000]");
            assertThat(msg1).isNotEqualTo(msg2); // GH-90000
        }

        @Test
        @DisplayName("different roles not equal [GH-90000]")
        void differentRoles() { // GH-90000
            ChatMessage msg1 = ChatMessage.user("Hello [GH-90000]");
            ChatMessage msg2 = ChatMessage.assistant("Hello [GH-90000]");
            assertThat(msg1).isNotEqualTo(msg2); // GH-90000
        }

        @Test
        @DisplayName("different names not equal [GH-90000]")
        void differentNames() { // GH-90000
            ChatMessage msg1 = ChatMessage.of(ChatMessage.Role.TOOL, "result", "tool_a"); // GH-90000
            ChatMessage msg2 = ChatMessage.of(ChatMessage.Role.TOOL, "result", "tool_b"); // GH-90000
            assertThat(msg1).isNotEqualTo(msg2); // GH-90000
        }

        @Test
        @DisplayName("named vs unnamed not equal [GH-90000]")
        void namedVsUnnamed() { // GH-90000
            ChatMessage msg1 = ChatMessage.of(ChatMessage.Role.TOOL, "result"); // GH-90000
            ChatMessage msg2 = ChatMessage.of(ChatMessage.Role.TOOL, "result", "tool_a"); // GH-90000
            assertThat(msg1).isNotEqualTo(msg2); // GH-90000
        }

        @Test
        @DisplayName("not equal to null [GH-90000]")
        void notEqualToNull() { // GH-90000
            assertThat(ChatMessage.user("Hello [GH-90000]")).isNotEqualTo(null);
        }

        @Test
        @DisplayName("not equal to different type [GH-90000]")
        void notEqualToDifferentType() { // GH-90000
            assertThat(ChatMessage.user("Hello [GH-90000]")).isNotEqualTo("Hello [GH-90000]");
        }

        @Test
        @DisplayName("equal to itself [GH-90000]")
        void equalToItself() { // GH-90000
            ChatMessage msg = ChatMessage.user("Hello [GH-90000]");
            assertThat(msg).isEqualTo(msg); // GH-90000
        }
    }

    @Nested
    @DisplayName("toString [GH-90000]")
    class ToString {

        @Test
        @DisplayName("toString without name excludes name field [GH-90000]")
        void toStringWithoutName() { // GH-90000
            ChatMessage msg = ChatMessage.user("Hello [GH-90000]");
            String str = msg.toString(); // GH-90000
            assertThat(str).contains("USER [GH-90000]").contains("Hello [GH-90000]");
        }

        @Test
        @DisplayName("toString with name includes name field [GH-90000]")
        void toStringWithName() { // GH-90000
            ChatMessage msg = ChatMessage.of(ChatMessage.Role.TOOL, "data", "search_tool"); // GH-90000
            String str = msg.toString(); // GH-90000
            assertThat(str).contains("TOOL [GH-90000]").contains("data [GH-90000]").contains("search_tool [GH-90000]");
        }
    }
}
