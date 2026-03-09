package com.ghatana.ai.llm;

import java.util.Objects;

/**
 * Immutable message object for chat-based LLM interactions.
 *
 * <p><b>Purpose:</b> Represents a single message in a conversation thread,
 * with role (user, assistant, system) and content.
 *
 * <p><b>Thread Safety:</b> Fully immutable - safe for concurrent use.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   List<ChatMessage> messages = List.of(
 *       ChatMessage.system("You are a requirements expert"),
 *       ChatMessage.user("Generate requirements for: " + featureName),
 *       ChatMessage.assistant("I'll generate 5 requirements...")
 *   );
 * }</pre>
 *
 * @doc.type Value Object
 * @doc.purpose Chat message encapsulation
 * @doc.layer platform
 * @doc.pattern Immutable value object with factory methods
 */
public final class ChatMessage {

    /**
     * Message role enumeration.
     */
    public enum Role {
        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant"),
        TOOL("tool");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private final Role role;
    private final String content;
    private final String name; // Optional name for the participant

    private ChatMessage(Role role, String content, String name) {
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.name = name;
    }

    public static ChatMessage of(Role role, String content) {
        return new ChatMessage(role, content, null);
    }

    public static ChatMessage of(Role role, String content, String name) {
        return new ChatMessage(role, content, name);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content, null);
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatMessage that = (ChatMessage) o;
        return role == that.role &&
                Objects.equals(content, that.content) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content, name);
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "role=" + role +
                ", content='" + content + '\'' +
                (name != null ? ", name='" + name + '\'' : "") +
                '}';
    }
}
