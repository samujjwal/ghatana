package com.ghatana.virtualorg.security;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a secure, authenticated message.
 *
 * <p><b>Purpose</b><br>
 * Value object for encrypted and signed messages between agents with
 * replay attack prevention and authenticity verification.
 *
 * <p><b>Security Features</b><br>
 * - **Signature**: HMAC-SHA256 message authentication
 * - **Nonce**: Cryptographic nonce prevents replay attacks
 * - **Timestamp**: Time-based message expiration
 * - **Encryption**: Payload encrypted at transport layer (TLS)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SecureMessage message = new SecureMessage(
 *     UUID.randomUUID().toString(),
 *     "agent-123",
 *     "agent-456",
 *     "task.assignment",
 *     encryptedPayload,
 *     System.currentTimeMillis(),
 *     generateNonce(),
 *     signMessage(payload)
 * );
 * 
 * if (!message.isExpired(300000)) { // 5 min max age
 *     processMessage(message);
 * }
 * }</pre>
 *
 * <p><b>Validation</b><br>
 * Canonical constructor validates:
 * - id, senderId, receiverId: non-blank
 *
 * @param id Unique message identifier
 * @param senderId ID of the sender
 * @param receiverId ID of the receiver
 * @param messageType Type/topic of the message
 * @param payload Decrypted message payload
 * @param timestamp Message timestamp (milliseconds since epoch)
 * @param nonce Cryptographic nonce for replay prevention
 * @param signature Message signature for authentication
 * @doc.type record
 * @doc.purpose Secure message value object with signature and replay prevention
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record SecureMessage(
    @NotNull String id,
    @NotNull String senderId,
    @NotNull String receiverId,
    @NotNull String messageType,
    @NotNull String payload,
    long timestamp,
    @NotNull String nonce,
    @NotNull String signature
) {
    public SecureMessage {
        if (id.isBlank()) {
            throw new IllegalArgumentException("Message ID cannot be blank");
        }
        if (senderId.isBlank()) {
            throw new IllegalArgumentException("Sender ID cannot be blank");
        }
        if (receiverId.isBlank()) {
            throw new IllegalArgumentException("Receiver ID cannot be blank");
        }
    }

    /**
     * Check if message is expired (older than max age)
     */
    public boolean isExpired(long maxAgeMillis) {
        long age = System.currentTimeMillis() - timestamp;
        return age > maxAgeMillis;
    }

    /**
     * Get message age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }
}
