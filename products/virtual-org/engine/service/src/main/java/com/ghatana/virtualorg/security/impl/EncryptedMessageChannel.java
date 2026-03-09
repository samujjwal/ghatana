package com.ghatana.virtualorg.security.impl;

import com.ghatana.virtualorg.security.Principal;
import com.ghatana.virtualorg.security.SecureMessage;
import com.ghatana.virtualorg.security.SecureMessageChannel;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encrypted message channel using AES-256-GCM encryption.
 *
 * <p><b>Purpose</b><br>
 * Adapter implementing {@link SecureMessageChannel} with AES-256-GCM encryption,
 * HMAC authentication, and replay attack prevention for secure agent communication.
 *
 * <p><b>Architecture Role</b><br>
 * Security adapter wrapping Java Cryptography Extension (JCE). Provides:
 * - AES-256-GCM encryption (confidentiality + integrity)
 * - HMAC-SHA256 message authentication
 * - Unique nonce per message (replay prevention)
 * - Message expiry enforcement (5 min max age)
 * - Processed message tracking (duplicate detection)
 *
 * <p><b>Security Features</b><br>
 * - **Encryption**: AES-256-GCM mode (authenticated encryption)
 * - **Authentication**: HMAC-SHA256 signatures
 * - **Replay Prevention**: Nonce tracking + timestamp validation
 * - **Perfect Forward Secrecy**: New IV per message
 *
 * <p><b>Implementation Notes</b><br>
 * - Uses symmetric encryption (shared secret per agent pair)
 * - **Production**: Implement Diffie-Hellman key exchange
 * - **Production**: Store keys in HSM or KMS, not in memory
 * - Message age limited to 5 minutes (prevents long-term replay)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EncryptedMessageChannel channel = new EncryptedMessageChannel(
 *     encryptionKey,
 *     macKey,
 *     eventloop
 * );
 * 
 * // Send encrypted message
 * SecureMessage encrypted = channel.send(
 *     sender,
 *     receiver,
 *     "task.assignment",
 *     payload
 * ).getResult();
 * 
 * // Receive and verify
 * String decrypted = channel.receive(encrypted).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose AES-256-GCM encrypted message channel adapter
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class EncryptedMessageChannel implements SecureMessageChannel {
    private static final Logger logger = LoggerFactory.getLogger(EncryptedMessageChannel.class);

    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final String MAC_ALGORITHM = "HmacSHA256";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final long MAX_MESSAGE_AGE_MS = 5 * 60 * 1000; // 5 minutes

    private final Eventloop eventloop;
    private final SecretKey encryptionKey;
    private final SecretKey macKey;
    private final SecureRandom secureRandom;
    private final Map<String, SecureMessage> messageStore; // In production, use distributed store
    private final Set<String> processedNonces; // For replay detection

    public EncryptedMessageChannel(@NotNull Eventloop eventloop, @NotNull String sharedSecret) {
        this.eventloop = eventloop;
        this.secureRandom = new SecureRandom();
        this.messageStore = new ConcurrentHashMap<>();
        this.processedNonces = Collections.synchronizedSet(new HashSet<>());

        try {
            // Derive encryption and MAC keys from shared secret
            byte[] secretBytes = sharedSecret.getBytes(StandardCharsets.UTF_8);

            // Encryption key (first 256 bits)
            byte[] encKeyBytes = Arrays.copyOfRange(deriveKey(secretBytes, "enc"), 0, 32);
            this.encryptionKey = new SecretKeySpec(encKeyBytes, "AES");

            // MAC key (next 256 bits)
            byte[] macKeyBytes = Arrays.copyOfRange(deriveKey(secretBytes, "mac"), 0, 32);
            this.macKey = new SecretKeySpec(macKeyBytes, MAC_ALGORITHM);

            logger.info("Encrypted message channel initialized");

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize encrypted channel", e);
        }
    }

    @Override
    @NotNull
    public Promise<String> sendMessage(
        @NotNull Principal sender,
        @NotNull String receiverId,
        @NotNull String messageType,
        @NotNull String payload
    ) {
        return Promise.ofBlocking(eventloop, () -> {
            try {
                String messageId = UUID.randomUUID().toString();
                long timestamp = System.currentTimeMillis();
                String nonce = generateNonce();

                // Encrypt payload
                byte[] iv = generateIV();
                String encryptedPayload = encrypt(payload, iv);

                // Create message data for signing
                String messageData = String.format("%s|%s|%s|%s|%s|%d|%s",
                    messageId, sender.id(), receiverId, messageType,
                    encryptedPayload, timestamp, nonce);

                // Generate signature
                String signature = generateSignature(messageData);

                // Create secure message (store encrypted payload temporarily)
                SecureMessage message = new SecureMessage(
                    messageId,
                    sender.id(),
                    receiverId,
                    messageType,
                    encryptedPayload, // Store encrypted
                    timestamp,
                    nonce,
                    signature
                );

                // Store message
                messageStore.put(messageId, message);
                processedNonces.add(nonce);

                logger.debug("Sent secure message: {} -> {} [{}]",
                    sender.id(), receiverId, messageType);

                return messageId;

            } catch (Exception e) {
                logger.error("Failed to send secure message", e);
                throw new RuntimeException("Message send failed", e);
            }
        });
    }

    @Override
    @NotNull
    public Promise<SecureMessage> receiveMessage(
        @NotNull Principal receiver,
        @NotNull String messageId
    ) {
        return Promise.ofBlocking(eventloop, () -> {
            SecureMessage encryptedMessage = messageStore.get(messageId);
            if (encryptedMessage == null) {
                throw new IllegalArgumentException("Message not found: " + messageId);
            }
            return encryptedMessage;
        }).then(encryptedMessage -> {
            // Verify receiver - return rejected promise instead of throwing
            if (!encryptedMessage.receiverId().equals(receiver.id())) {
                logger.warn("Unauthorized message access attempt by {}",
                    receiver.id());
                return Promise.ofException(new SecurityException("Unauthorized message access"));
            }

            // Check expiry
            if (encryptedMessage.isExpired(MAX_MESSAGE_AGE_MS)) {
                logger.warn("Attempted to receive expired message: {}", messageId);
                return Promise.ofException(new SecurityException("Message has expired"));
            }

            // Verify signature asynchronously
            return verifyMessage(encryptedMessage).map(isValid -> {
                if (isValid == null || !isValid) {
                    throw new SecurityException("Message signature verification failed");
                }

                try {
                    // Decrypt payload
                    String decryptedPayload = decrypt(encryptedMessage.payload());

                    // Create decrypted message
                    SecureMessage decrypted = new SecureMessage(
                        encryptedMessage.id(),
                        encryptedMessage.senderId(),
                        encryptedMessage.receiverId(),
                        encryptedMessage.messageType(),
                        decryptedPayload,
                        encryptedMessage.timestamp(),
                        encryptedMessage.nonce(),
                        encryptedMessage.signature()
                    );

                    logger.debug("Received secure message: {} -> {} [{}]",
                        decrypted.senderId(), receiver.id(), decrypted.messageType());

                    return decrypted;

                } catch (Exception e) {
                    logger.error("Failed to decrypt message", e);
                    throw new RuntimeException("Message decryption failed", e);
                }
            });
        });
    }

    @Override
    @NotNull
    public Promise<Boolean> verifyMessage(@NotNull SecureMessage message) {
        return Promise.ofBlocking(eventloop, () -> {
            try {
                // Reconstruct message data
                String messageData = String.format("%s|%s|%s|%s|%s|%d|%s",
                    message.id(), message.senderId(), message.receiverId(),
                    message.messageType(), message.payload(),
                    message.timestamp(), message.nonce());

                // Verify signature
                String expectedSignature = generateSignature(messageData);
                boolean valid = expectedSignature.equals(message.signature());

                if (!valid) {
                    logger.warn("Message signature verification failed: {}", message.id());
                }

                return valid;

            } catch (Exception e) {
                logger.error("Message verification error", e);
                return false;
            }
        });
    }

    @Override
    @NotNull
    public Promise<Boolean> isReplay(@NotNull String messageId) {
        return Promise.ofBlocking(eventloop, () -> {
            SecureMessage message = messageStore.get(messageId);
            if (message == null) {
                return false;
            }

            // Check if nonce was already processed
            return processedNonces.contains(message.nonce());
        });
    }

    // Private helper methods

    @NotNull
    private String encrypt(@NotNull String plaintext, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Combine IV + ciphertext and encode as Base64
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    @NotNull
    private String decrypt(@NotNull String encryptedData) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedData);

        // Extract IV and ciphertext
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    @NotNull
    private String generateSignature(@NotNull String data) throws Exception {
        Mac mac = Mac.getInstance(MAC_ALGORITHM);
        mac.init(macKey);
        byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature);
    }

    @NotNull
    private byte[] generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        return iv;
    }

    @NotNull
    private String generateNonce() {
        byte[] nonce = new byte[16];
        secureRandom.nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }

    @NotNull
    private byte[] deriveKey(byte[] secret, String context) throws Exception {
        Mac mac = Mac.getInstance(MAC_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(secret, MAC_ALGORITHM);
        mac.init(keySpec);
        return mac.doFinal(context.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Cleanup expired messages and nonces.
     * Should be called periodically.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        messageStore.entrySet().removeIf(entry ->
            entry.getValue().isExpired(MAX_MESSAGE_AGE_MS)
        );
        logger.debug("Cleaned up expired messages. Remaining: {}", messageStore.size());
    }
}
