package com.ghatana.agent.framework.coordination;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Manages conversations between agents.
 * Enables agents to communicate, share context, and collaborate.
 * 
 * <p><b>Features:</b>
 * <ul>
 *   <li>Send messages between agents</li>
 *   <li>Track conversation history</li>
 *   <li>Support for broadcast messages</li>
 *   <li>Message routing and filtering</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * ConversationManager convoMgr = ...;
 * 
 * // Send message to another agent
 * convoMgr.sendMessage(
 *     Message.builder()
 *         .from("ProductOwnerAgent")
 *         .to("ChiefArchitectAgent")
 *         .content("Please review requirements")
 *         .metadata(Map.of("requirementsId", "REQ-123"))
 *         .build(),
 *     context
 * );
 * 
 * // Get conversation history
 * List<Message> history = convoMgr.getConversation(
 *     "ProductOwnerAgent", 
 *     "ChiefArchitectAgent", 
 *     context
 * );
 * }</pre>
 * 
 * @doc.type interface
 * @doc.purpose Agent-to-agent communication management
 * @doc.layer framework
 * @doc.pattern Mediator
 */
public interface ConversationManager {
    
    /**
     * Sends a message from one agent to another.
     * 
     * @param message Message to send
     * @param context Execution context
     * @return Promise of message ID
     */
    @NotNull
    Promise<String> sendMessage(
        @NotNull Message message,
        @NotNull com.ghatana.agent.framework.api.AgentContext context);
    
    /**
     * Broadcasts a message to multiple agents.
     * 
     * @param message Message to broadcast
     * @param recipients List of recipient agent IDs
     * @param context Execution context
     * @return Promise of message ID
     */
    @NotNull
    Promise<String> broadcast(
        @NotNull Message message,
        @NotNull List<String> recipients,
        @NotNull com.ghatana.agent.framework.api.AgentContext context);
    
    /**
     * Gets conversation history between two agents.
     * 
     * @param agentId1 First agent ID
     * @param agentId2 Second agent ID
     * @param context Execution context
     * @return Promise of message list
     */
    @NotNull
    Promise<List<Message>> getConversation(
        @NotNull String agentId1,
        @NotNull String agentId2,
        @NotNull com.ghatana.agent.framework.api.AgentContext context);
    
    /**
     * Gets all messages received by an agent.
     * 
     * @param agentId Agent ID
     * @param limit Maximum number of messages
     * @param context Execution context
     * @return Promise of message list
     */
    @NotNull
    Promise<List<Message>> getInbox(
        @NotNull String agentId,
        int limit,
        @NotNull com.ghatana.agent.framework.api.AgentContext context);
    
    /**
     * Marks a message as read.
     * 
     * @param messageId Message ID
     * @param context Execution context
     * @return Promise of operation result
     */
    @NotNull
    Promise<Boolean> markAsRead(
        @NotNull String messageId,
        @NotNull com.ghatana.agent.framework.api.AgentContext context);
}
