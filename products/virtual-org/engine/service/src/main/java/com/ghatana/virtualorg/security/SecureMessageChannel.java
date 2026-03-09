package com.ghatana.virtualorg.security;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Secure communication channel port for encrypted inter-agent messaging.
 *
 * <p><b>Purpose</b><br>
 * Provides secure communication abstraction for message exchange between agents,
 * services, and users with cryptographic guarantees. Ensures confidentiality,
 * integrity, and authenticity of messages in multi-agent systems.
 *
 * <p><b>Architecture Role</b><br>
 * Port interface in the security layer providing:
 * - Message encryption (AES-256-GCM for confidentiality)
 * - Message authentication (HMAC-SHA256 for integrity)
 * - Replay attack prevention (nonce tracking)
 * - Sender verification (principal-based authentication)
 * - Message integrity verification (tamper detection)
 *
 * <p><b>Security Features</b><br>
 * Cryptographic protections:
 * - <b>Encryption</b>: AES-256-GCM authenticated encryption
 * - <b>Authentication</b>: HMAC-SHA256 message authentication codes
 * - <b>Replay Prevention</b>: Nonce tracking with timestamp validation
 * - <b>Key Management</b>: Per-principal keys with rotation support
 * - <b>Forward Secrecy</b>: Ephemeral keys for session-based communication
 *
 * <p><b>Message Flow</b><br>
 * Sender → Encrypt → Sign → Send → Verify → Decrypt → Receiver
 * 1. Sender encrypts payload with receiver's public key
 * 2. Sender signs message with own private key
 * 3. Channel transmits encrypted + signed message
 * 4. Receiver verifies signature with sender's public key
 * 5. Receiver decrypts payload with own private key
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SecureMessageChannel channel = new EncryptedMessageChannel(
 *     keyManager,
 *     nonceTracker,
 *     eventloop
 * );
 *
 * // Send secure message
 * Principal sender = Principal.of("agent-123", PrincipalType.AGENT);
 * String messageId = channel.sendMessage(
 *     sender,
 *     "agent-456",           // receiver ID
 *     "task:assignment",     // message type
 *     "{\"task\": \"...\"}"  // payload (will be encrypted)
 * ).getResult();
 *
 * // Receive and decrypt message
 * Principal receiver = Principal.of("agent-456", PrincipalType.AGENT);
 * SecureMessage msg = channel.receiveMessage(receiver, messageId).getResult();
 * 
 * // Verify and use decrypted payload
 * if (msg.isVerified()) {
 *     String decryptedPayload = msg.payload();
 *     processTask(decryptedPayload);
 * }
 * }</pre>
 *
 * <p><b>Implementations</b><br>
 * - {@link EncryptedMessageChannel}: Production implementation with full crypto
 * - {@link InMemorySecureChannel}: Test implementation for unit tests
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe for concurrent message operations.
 *
 * @see Principal
 * @see SecureMessage
 * @see EncryptedMessageChannel
 * @doc.type interface
 * @doc.purpose Secure message channel port with encryption
 * @doc.layer product
 * @doc.pattern Port
 */
public interface SecureMessageChannel {

    /**
     * Send a secure message to another agent or service.
     *
     * @param sender The sending principal
     * @param receiverId ID of the receiver
     * @param messageType Type/topic of the message
     * @param payload Message payload (will be encrypted)
     * @return Promise of message ID
     */
    @NotNull
    Promise<String> sendMessage(
        @NotNull Principal sender,
        @NotNull String receiverId,
        @NotNull String messageType,
        @NotNull String payload
    );

    /**
     * Receive and decrypt a message.
     *
     * @param receiver The receiving principal
     * @param messageId ID of the message to receive
     * @return Promise of decrypted secure message
     */
    @NotNull
    Promise<SecureMessage> receiveMessage(
        @NotNull Principal receiver,
        @NotNull String messageId
    );

    /**
     * Verify message authenticity and integrity.
     *
     * @param message The message to verify
     * @return Promise of true if message is valid
     */
    @NotNull
    Promise<Boolean> verifyMessage(@NotNull SecureMessage message);

    /**
     * Check if a message has already been processed (replay detection).
     *
     * @param messageId Message ID to check
     * @return Promise of true if message is a replay
     */
    @NotNull
    Promise<Boolean> isReplay(@NotNull String messageId);
}
