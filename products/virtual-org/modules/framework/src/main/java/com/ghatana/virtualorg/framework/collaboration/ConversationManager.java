package com.ghatana.virtualorg.framework.collaboration;

import io.activej.promise.Promise;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for managing inter-agent conversations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Manages message delivery, conversation threading, and communication patterns
 * between agents.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ConversationManager manager = new InMemoryConversationManager();
 *
 * // Send a message
 * manager.send(AgentMessage.builder()
 *     .from("agent-1")
 *     .to("agent-2")
 *     .content("Hello!")
 *     .build());
 *
 * // Subscribe to messages
 * manager.subscribe("agent-2", message -> {
 *     System.out.println("Received: " + message.content());
 * });
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose VirtualOrg inter-agent conversation manager (richer than platform ConversationManager)
 * @doc.layer product
 * @doc.pattern Mediator
 */
public interface ConversationManager {

    /**
     * Sends a message to another agent.
     *
     * @param message Message to send
     * @return Promise of the sent message with updated status
     */
    Promise<AgentMessage> send(AgentMessage message);

    /**
     * Sends a message and waits for a response.
     *
     * @param message Message to send
     * @param timeoutMs Timeout in milliseconds
     * @return Promise of the response message
     */
    Promise<AgentMessage> sendAndWait(AgentMessage message, long timeoutMs);

    /**
     * Broadcasts a message to multiple agents.
     *
     * @param message Message to broadcast
     * @param toAgentIds List of recipient agent IDs
     * @return Promise of the sent messages
     */
    Promise<List<AgentMessage>> broadcast(AgentMessage message, List<String> toAgentIds);

    /**
     * Gets pending messages for an agent.
     *
     * @param agentId Agent ID
     * @return Promise of pending messages
     */
    Promise<List<AgentMessage>> getPending(String agentId);

    /**
     * Gets all messages in a conversation.
     *
     * @param conversationId Conversation ID
     * @return Promise of messages in the conversation
     */
    Promise<List<AgentMessage>> getConversation(String conversationId);

    /**
     * Marks a message as read.
     *
     * @param messageId Message ID
     * @return Promise indicating success
     */
    Promise<Boolean> markRead(String messageId);

    /**
     * Marks a message as processed.
     *
     * @param messageId Message ID
     * @return Promise indicating success
     */
    Promise<Boolean> markProcessed(String messageId);

    /**
     * Subscribes to messages for an agent.
     *
     * @param agentId Agent ID
     * @param handler Message handler
     * @return Subscription handle for unsubscribing
     */
    Subscription subscribe(String agentId, Consumer<AgentMessage> handler);

    /**
     * Subscribes to a specific conversation.
     *
     * @param conversationId Conversation ID
     * @param handler Message handler
     * @return Subscription handle for unsubscribing
     */
    Subscription subscribeToConversation(String conversationId, Consumer<AgentMessage> handler);

    /**
     * Clears expired messages.
     *
     * @return Promise of the number of expired messages removed
     */
    Promise<Integer> clearExpired();

    /**
     * Gets conversation statistics.
     *
     * @param agentId Agent ID (optional, null for all)
     * @return Promise of statistics
     */
    Promise<ConversationStats> getStats(String agentId);

    /**
     * Subscription handle for message subscriptions.
     */
    interface Subscription {

        /**
         * Unsubscribes from messages.
         */
        void unsubscribe();

        /**
         * Checks if the subscription is active.
         *
         * @return True if active
         */
        boolean isActive();
    }

    /**
     * Statistics about conversations.
     */
    record ConversationStats(
            long totalMessages,
            long pendingMessages,
            long activeConversations,
            long messagesLastHour,
            long averageResponseTimeMs
    ) {
    }
}
