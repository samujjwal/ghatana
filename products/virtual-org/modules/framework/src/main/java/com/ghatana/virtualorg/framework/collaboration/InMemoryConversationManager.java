package com.ghatana.virtualorg.framework.collaboration;

import static com.ghatana.virtualorg.framework.util.BlockingExecutors.blockingExecutor;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory implementation of ConversationManager.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides simple in-memory message passing for development and testing. Not
 * suitable for distributed production use.
 *
 * @doc.type class
 * @doc.purpose In-memory conversation manager
 * @doc.layer product
 * @doc.pattern Mediator
 */
public class InMemoryConversationManager implements ConversationManager {

    private final ConcurrentHashMap<String, AgentMessage> messages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Consumer<AgentMessage>>> agentSubscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Consumer<AgentMessage>>> conversationSubscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentMessage> pendingResponses = new ConcurrentHashMap<>();

    @Override
    public Promise<AgentMessage> send(AgentMessage message) {
        // Store message
        messages.put(message.id(), message);

        // Update status to delivered
        AgentMessage delivered = new AgentMessage(
                message.id(),
                message.fromAgentId(),
                message.toAgentId(),
                message.type(),
                message.subject(),
                message.content(),
                message.payload(),
                message.priority(),
                message.conversationId(),
                message.inReplyTo(),
                message.timestamp(),
                message.expiresAt(),
                AgentMessage.MessageStatus.DELIVERED,
                message.metadata()
        );
        messages.put(message.id(), delivered);

        // Notify subscribers
        notifyAgentSubscribers(message.toAgentId(), delivered);
        notifyConversationSubscribers(message.conversationId(), delivered);

        // Check if this is a response to a pending request
        if (message.inReplyTo() != null) {
            pendingResponses.put(message.inReplyTo(), delivered);
        }

        return Promise.of(delivered);
    }

    @Override
    public Promise<AgentMessage> sendAndWait(AgentMessage message, long timeoutMs) {
        return send(message).then(sent -> Promise.ofBlocking(blockingExecutor(), () -> {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                AgentMessage response = pendingResponses.remove(message.id());
                if (response != null) {
                    return response;
                }
                Thread.sleep(50); // Poll interval
            }
            throw new RuntimeException("Timeout waiting for response");
        }));
    }

    @Override
    public Promise<List<AgentMessage>> broadcast(AgentMessage message, List<String> toAgentIds) {
        List<AgentMessage> sent = new ArrayList<>();
        for (String agentId : toAgentIds) {
            AgentMessage copy = AgentMessage.builder()
                    .from(message.fromAgentId())
                    .to(agentId)
                    .type(message.type())
                    .subject(message.subject())
                    .content(message.content())
                    .payload(message.payload())
                    .priority(message.priority())
                    .conversationId(message.conversationId())
                    .metadata(message.metadata())
                    .build();

            send(copy).getResult();
            sent.add(copy);
        }
        return Promise.of(sent);
    }

    @Override
    public Promise<List<AgentMessage>> getPending(String agentId) {
        List<AgentMessage> pending = messages.values().stream()
                .filter(m -> m.toAgentId().equals(agentId))
                .filter(m -> m.status() == AgentMessage.MessageStatus.DELIVERED
                || m.status() == AgentMessage.MessageStatus.PENDING)
                .filter(m -> m.expiresAt() == null || m.expiresAt().isAfter(Instant.now()))
                .sorted((a, b) -> {
                    // Sort by priority (urgent first), then by timestamp
                    int priorityCompare = b.priority().compareTo(a.priority());
                    if (priorityCompare != 0) {
                        return priorityCompare;
                    }
                    return a.timestamp().compareTo(b.timestamp());
                })
                .toList();
        return Promise.of(pending);
    }

    /**
     * Test utility method to get messages synchronously.
     *
     * @param agentId the agent ID to get messages for
     * @return list of pending messages
     */
    public List<AgentMessage> getMessagesForSync(String agentId) {
        return messages.values().stream()
                .filter(m -> m.toAgentId().equals(agentId))
                .toList();
    }

    @Override
    public Promise<List<AgentMessage>> getConversation(String conversationId) {
        List<AgentMessage> conversation = messages.values().stream()
                .filter(m -> m.conversationId().equals(conversationId))
                .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .toList();
        return Promise.of(conversation);
    }

    @Override
    public Promise<Boolean> markRead(String messageId) {
        return updateStatus(messageId, AgentMessage.MessageStatus.READ);
    }

    @Override
    public Promise<Boolean> markProcessed(String messageId) {
        return updateStatus(messageId, AgentMessage.MessageStatus.PROCESSED);
    }

    private Promise<Boolean> updateStatus(String messageId, AgentMessage.MessageStatus status) {
        AgentMessage message = messages.get(messageId);
        if (message == null) {
            return Promise.of(false);
        }

        AgentMessage updated = new AgentMessage(
                message.id(),
                message.fromAgentId(),
                message.toAgentId(),
                message.type(),
                message.subject(),
                message.content(),
                message.payload(),
                message.priority(),
                message.conversationId(),
                message.inReplyTo(),
                message.timestamp(),
                message.expiresAt(),
                status,
                message.metadata()
        );
        messages.put(messageId, updated);
        return Promise.of(true);
    }

    @Override
    public Subscription subscribe(String agentId, Consumer<AgentMessage> handler) {
        agentSubscribers.computeIfAbsent(agentId, k -> new CopyOnWriteArrayList<>()).add(handler);

        return new Subscription() {
            private boolean active = true;

            @Override
            public void unsubscribe() {
                List<Consumer<AgentMessage>> handlers = agentSubscribers.get(agentId);
                if (handlers != null) {
                    handlers.remove(handler);
                }
                active = false;
            }

            @Override
            public boolean isActive() {
                return active;
            }
        };
    }

    @Override
    public Subscription subscribeToConversation(String conversationId, Consumer<AgentMessage> handler) {
        conversationSubscribers.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>()).add(handler);

        return new Subscription() {
            private boolean active = true;

            @Override
            public void unsubscribe() {
                List<Consumer<AgentMessage>> handlers = conversationSubscribers.get(conversationId);
                if (handlers != null) {
                    handlers.remove(handler);
                }
                active = false;
            }

            @Override
            public boolean isActive() {
                return active;
            }
        };
    }

    @Override
    public Promise<Integer> clearExpired() {
        Instant now = Instant.now();
        List<String> expired = messages.values().stream()
                .filter(m -> m.expiresAt() != null && m.expiresAt().isBefore(now))
                .map(AgentMessage::id)
                .toList();

        expired.forEach(messages::remove);
        return Promise.of(expired.size());
    }

    @Override
    public Promise<ConversationStats> getStats(String agentId) {
        Instant oneHourAgo = Instant.now().minusSeconds(3600);

        long total = agentId == null
                ? messages.size()
                : messages.values().stream()
                        .filter(m -> m.fromAgentId().equals(agentId) || m.toAgentId().equals(agentId))
                        .count();

        long pending = messages.values().stream()
                .filter(m -> agentId == null || m.toAgentId().equals(agentId))
                .filter(m -> m.status() == AgentMessage.MessageStatus.PENDING
                || m.status() == AgentMessage.MessageStatus.DELIVERED)
                .count();

        long activeConvs = messages.values().stream()
                .filter(m -> agentId == null || m.fromAgentId().equals(agentId) || m.toAgentId().equals(agentId))
                .filter(m -> m.timestamp().isAfter(oneHourAgo))
                .map(AgentMessage::conversationId)
                .distinct()
                .count();

        long lastHour = messages.values().stream()
                .filter(m -> agentId == null || m.fromAgentId().equals(agentId) || m.toAgentId().equals(agentId))
                .filter(m -> m.timestamp().isAfter(oneHourAgo))
                .count();

        return Promise.of(new ConversationStats(total, pending, activeConvs, lastHour, 0));
    }

    private void notifyAgentSubscribers(String agentId, AgentMessage message) {
        List<Consumer<AgentMessage>> handlers = agentSubscribers.get(agentId);
        if (handlers != null) {
            for (Consumer<AgentMessage> handler : handlers) {
                try {
                    handler.accept(message);
                } catch (Exception e) {
                    // Log but don't propagate
                }
            }
        }
    }

    private void notifyConversationSubscribers(String conversationId, AgentMessage message) {
        List<Consumer<AgentMessage>> handlers = conversationSubscribers.get(conversationId);
        if (handlers != null) {
            for (Consumer<AgentMessage> handler : handlers) {
                try {
                    handler.accept(message);
                } catch (Exception e) {
                    // Log but don't propagate
                }
            }
        }
    }

    /**
     * Gets total message count.
     *
     * @return Message count
     */
    public int size() {
        return messages.size();
    }

    /**
     * Gets all messages for an agent (alias for getPending, useful in tests).
     *
     * @param agentId The agent ID to get messages for
     * @return List of messages for the agent
     */
    public List<AgentMessage> getMessagesFor(String agentId) {
        return messages.values().stream()
                .filter(m -> m.toAgentId().equals(agentId))
                .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .toList();
    }

    /**
     * Clears all messages and subscriptions.
     */
    public void clear() {
        messages.clear();
        agentSubscribers.clear();
        conversationSubscribers.clear();
        pendingResponses.clear();
    }
}
