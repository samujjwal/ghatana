package com.ghatana.virtualorg.framework.collaboration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for InMemoryConversationManager.
 *
 * @doc.type class
 * @doc.purpose Unit tests for conversation manager
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("InMemoryConversationManager Tests")
class InMemoryConversationManagerTest extends EventloopTestBase {

    private InMemoryConversationManager manager;

    @BeforeEach
    void setUp() {
        manager = new InMemoryConversationManager();
    }

    @Test
    @DisplayName("should send message")
    void shouldSendMessage() {
        // GIVEN
        AgentMessage message = AgentMessage.builder()
                .from("alice")
                .to("bob")
                .subject("Hello")
                .content("Hi Bob!")
                .build();

        // WHEN
        AgentMessage sent = runPromise(() -> manager.send(message));

        // THEN
        assertThat(sent.status()).isEqualTo(AgentMessage.MessageStatus.DELIVERED);
    }

    @Test
    @DisplayName("should get pending messages")
    void shouldGetPendingMessages() {
        // GIVEN
        runPromise(() -> manager.send(AgentMessage.builder()
                .from("alice")
                .to("bob")
                .content("Message 1")
                .build()));

        runPromise(() -> manager.send(AgentMessage.builder()
                .from("charlie")
                .to("bob")
                .content("Message 2")
                .build()));

        // WHEN
        List<AgentMessage> pending = runPromise(() -> manager.getPending("bob"));

        // THEN
        assertThat(pending).hasSize(2);
    }

    @Test
    @DisplayName("should notify subscribers")
    void shouldNotifySubscribers() {
        // GIVEN
        AtomicReference<AgentMessage> received = new AtomicReference<>();
        manager.subscribe("bob", received::set);

        // WHEN
        runPromise(() -> manager.send(AgentMessage.builder()
                .from("alice")
                .to("bob")
                .content("Hello!")
                .build()));

        // THEN
        assertThat(received.get()).isNotNull();
        assertThat(received.get().content()).isEqualTo("Hello!");
    }

    @Test
    @DisplayName("should get conversation thread")
    void shouldGetConversationThread() {
        // GIVEN
        AgentMessage first = runPromise(() -> manager.send(AgentMessage.builder()
                .from("alice")
                .to("bob")
                .subject("Discussion")
                .content("Start")
                .build()));

        AgentMessage reply = first.reply("Reply").from("bob").build();
        runPromise(() -> manager.send(reply));

        // WHEN
        List<AgentMessage> thread = runPromise(()
                -> manager.getConversation(first.conversationId()));

        // THEN
        assertThat(thread).hasSize(2);
        assertThat(thread.get(0).content()).isEqualTo("Start");
        assertThat(thread.get(1).content()).isEqualTo("Reply");
    }

    @Test
    @DisplayName("should mark message as read")
    void shouldMarkAsRead() {
        // GIVEN
        AgentMessage sent = runPromise(() -> manager.send(AgentMessage.builder()
                .from("alice")
                .to("bob")
                .content("Hello")
                .build()));

        // WHEN
        Boolean marked = runPromise(() -> manager.markRead(sent.id()));
        List<AgentMessage> pending = runPromise(() -> manager.getPending("bob"));

        // THEN
        assertThat(marked).isTrue();
        // After being marked as read, it shouldn't appear in pending
        assertThat(pending).isEmpty();
    }

    @Test
    @DisplayName("should handle priority sorting")
    void shouldSortByPriority() {
        // GIVEN
        runPromise(() -> manager.send(AgentMessage.builder()
                .from("alice")
                .to("bob")
                .priority(AgentMessage.Priority.LOW)
                .content("Low priority")
                .build()));

        runPromise(() -> manager.send(AgentMessage.builder()
                .from("charlie")
                .to("bob")
                .priority(AgentMessage.Priority.URGENT)
                .content("Urgent")
                .build()));

        // WHEN
        List<AgentMessage> pending = runPromise(() -> manager.getPending("bob"));

        // THEN
        assertThat(pending.get(0).content()).isEqualTo("Urgent");
        assertThat(pending.get(1).content()).isEqualTo("Low priority");
    }

    @Test
    @DisplayName("should broadcast to multiple agents")
    void shouldBroadcast() {
        // GIVEN
        AgentMessage message = AgentMessage.builder()
                .from("manager")
                .subject("Announcement")
                .content("Team meeting at 3pm")
                .build();

        // WHEN
        List<AgentMessage> sent = runPromise(()
                -> manager.broadcast(message, List.of("alice", "bob", "charlie")));

        // THEN
        assertThat(sent).hasSize(3);

        List<AgentMessage> alicePending = runPromise(() -> manager.getPending("alice"));
        List<AgentMessage> bobPending = runPromise(() -> manager.getPending("bob"));

        assertThat(alicePending).hasSize(1);
        assertThat(bobPending).hasSize(1);
    }

    @Test
    @DisplayName("should unsubscribe from messages")
    void shouldUnsubscribe() {
        // GIVEN
        AtomicReference<AgentMessage> received = new AtomicReference<>();
        ConversationManager.Subscription sub = manager.subscribe("bob", received::set);

        // WHEN
        sub.unsubscribe();
        runPromise(() -> manager.send(AgentMessage.builder()
                .from("alice")
                .to("bob")
                .content("After unsubscribe")
                .build()));

        // THEN
        assertThat(sub.isActive()).isFalse();
        assertThat(received.get()).isNull();
    }

    @Test
    @DisplayName("should get stats")
    void shouldGetStats() {
        // GIVEN
        runPromise(() -> manager.send(AgentMessage.builder()
                .from("alice")
                .to("bob")
                .content("Test")
                .build()));

        // WHEN
        ConversationManager.ConversationStats stats
                = runPromise(() -> manager.getStats(null));

        // THEN
        assertThat(stats.totalMessages()).isEqualTo(1);
        assertThat(stats.activeConversations()).isEqualTo(1);
    }
}
