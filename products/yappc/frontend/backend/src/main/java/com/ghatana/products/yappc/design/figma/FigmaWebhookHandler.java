package com.ghatana.products.yappc.design.figma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Handles Figma webhook events for design file updates.
 *
 * <p><b>Purpose</b><br>
 * Processes Figma webhook payloads when design files change. Triggers
 * token extraction, conversion, and GitHub PR creation automatically.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * FigmaWebhookHandler handler = new FigmaWebhookHandler(
 *     figmaClient,
 *     converter,
 *     githubAutomation,
 *     objectMapper,
 *     metrics
 * );
 *
 * // Handle webhook POST request
 * String payload = request.body();
 * String signature = request.header("X-Webhook-Signature");
 *
 * handler.handleWebhook(payload, signature).await();
 * }</pre>
 *
 * <p><b>Webhook Security</b><br>
 * - Validates HMAC-SHA256 signature from X-Webhook-Signature header
 * - Rejects unsigned or invalid requests
 * - Uses webhook secret from configuration
 *
 * <p><b>Event Types</b><br>
 * - FILE_UPDATE: Design file changed (triggers token sync)
 * - FILE_DELETE: File deleted (ignored)
 * - FILE_VERSION_UPDATE: New version published (triggers sync)
 *
 * <p><b>Workflow</b><br>
 * 1. Validate webhook signature
 * 2. Parse event payload
 * 3. Extract file key from event
 * 4. Fetch design tokens from Figma API
 * 5. Convert to W3C format
 * 6. Create GitHub PR with updated tokens
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Stateless processing.
 *
 * @see FigmaClient
 * @see W3CTokenConverter
 * @see GitHubPRAutomation
 * @doc.type class
 * @doc.purpose Handle Figma webhook events
 * @doc.layer product
 * @doc.pattern Event Handler
 */
public class FigmaWebhookHandler {
    private static final Logger logger = LoggerFactory.getLogger(FigmaWebhookHandler.class);
    
    private final FigmaClient figmaClient;
    private final W3CTokenConverter tokenConverter;
    private final GitHubPRAutomation githubAutomation;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metrics;
    private final String webhookSecret;
    
    /**
     * Creates webhook handler
     *
     * @param figmaClient Figma API client
     * @param tokenConverter Token converter
     * @param githubAutomation GitHub automation
     * @param objectMapper JSON mapper
     * @param metrics Metrics collector
     * @param webhookSecret Webhook signing secret
     */
    public FigmaWebhookHandler(
            FigmaClient figmaClient,
            W3CTokenConverter tokenConverter,
            GitHubPRAutomation githubAutomation,
            ObjectMapper objectMapper,
            MetricsCollector metrics,
            String webhookSecret) {
        this.figmaClient = Objects.requireNonNull(figmaClient, "FigmaClient required");
        this.tokenConverter = Objects.requireNonNull(tokenConverter, "TokenConverter required");
        this.githubAutomation = Objects.requireNonNull(githubAutomation, "GitHubAutomation required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper required");
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector required");
        this.webhookSecret = Objects.requireNonNull(webhookSecret, "Webhook secret required");
    }
    
    /**
     * Handle webhook event
     *
     * <p>Validates signature, processes event, updates tokens if needed.
     *
     * @param payload Webhook payload JSON
     * @param signature HMAC signature from X-Webhook-Signature header
     * @return Promise that completes when processing finishes
     * @throws WebhookException if validation or processing fails
     */
    public Promise<Void> handleWebhook(String payload, String signature) {
        logger.info("[FigmaWebhook] Received webhook event");
        
        return Promise.ofCallback(cb -> {
            try {
                // Validate signature
                if (!validateSignature(payload, signature)) {
                    metrics.incrementCounter("figma.webhook.events",
                            "status", "invalid_signature");
                    cb.setException(new WebhookException("Invalid webhook signature"));
                    return;
                }
                
                // Parse payload
                JsonNode event = objectMapper.readTree(payload);
                String eventType = event.path("event_type").asText();
                
                logger.info("[FigmaWebhook] Event type: {}", eventType);
                
                // Process based on event type
                switch (eventType) {
                    case "FILE_UPDATE":
                    case "FILE_VERSION_UPDATE":
                        processFileUpdate(event).run(cb);
                        break;
                        
                    case "FILE_DELETE":
                        logger.info("[FigmaWebhook] File deleted, ignoring");
                        metrics.incrementCounter("figma.webhook.events",
                                "status", "ignored");
                        cb.set(null);
                        break;
                        
                    default:
                        logger.warn("[FigmaWebhook] Unknown event type: {}", eventType);
                        metrics.incrementCounter("figma.webhook.events",
                                "status", "unknown_type");
                        cb.set(null);
                        break;
                }
                
            } catch (Exception e) {
                logger.error("[FigmaWebhook] Failed to process webhook", e);
                metrics.incrementCounter("figma.webhook.events",
                        "status", "error");
                cb.setException(new WebhookException("Failed to process webhook", e));
            }
        });
    }
    
    /**
     * Process file update event
     *
     * <p>Workflow:
     * 1. Extract file key from event
     * 2. Fetch design tokens from Figma
     * 3. Convert to W3C format
     * 4. Create GitHub PR
     *
     * @param event Webhook event payload
     * @return Promise that completes when update processed
     */
    private Promise<Void> processFileUpdate(JsonNode event) {
        String fileKey = event.path("file_key").asText();
        String fileName = event.path("file_name").asText();
        
        if (fileKey.isEmpty()) {
            return Promise.ofException(new WebhookException("Missing file_key in event"));
        }
        
        logger.info("[FigmaWebhook] Processing file update: {} ({})", fileName, fileKey);
        
        return fetchAndConvertTokens(fileKey)
                .then(w3cJson -> createGitHubPR(fileKey, fileName, w3cJson))
                .whenComplete(() -> {
                    logger.info("[FigmaWebhook] Successfully processed file update");
                    metrics.incrementCounter("figma.webhook.events",
                            "status", "success");
                })
                .whenException(e -> {
                    logger.error("[FigmaWebhook] Failed to process file update", e);
                    metrics.incrementCounter("figma.webhook.events",
                            "status", "error");
                });
    }
    
    /**
     * Fetch design tokens and convert to W3C format
     *
     * @param fileKey Figma file key
     * @return Promise with W3C JSON string
     */
    private Promise<String> fetchAndConvertTokens(String fileKey) {
        return figmaClient.getVariables(fileKey)
                .map(variablesJson -> {
                    logger.debug("[FigmaWebhook] Fetched variables, converting to W3C");
                    return tokenConverter.convert(variablesJson);
                })
                .whenException(e -> {
                    logger.error("[FigmaWebhook] Failed to fetch/convert tokens", e);
                });
    }
    
    /**
     * Create GitHub PR with updated tokens
     *
     * @param fileKey Figma file key
     * @param fileName File name for PR title
     * @param w3cJson W3C tokens JSON
     * @return Promise that completes when PR created
     */
    private Promise<Void> createGitHubPR(String fileKey, String fileName, String w3cJson) {
        String branchName = String.format("design-tokens/%s-%d", 
                fileKey, System.currentTimeMillis());
        String commitMessage = String.format("Update design tokens from Figma: %s", fileName);
        String prTitle = String.format("🎨 Design Tokens: %s", fileName);
        String prBody = String.format(
                "Automated update from Figma file: **%s**\n\n" +
                "File Key: `%s`\n\n" +
                "This PR updates design tokens to match the latest Figma design.\n\n" +
                "**Changes:**\n" +
                "- Updated colors, typography, spacing tokens\n" +
                "- Converted to W3C Design Token Format\n\n" +
                "**Review:**\n" +
                "- Verify token values match Figma\n" +
                "- Check for breaking changes\n" +
                "- Update consuming components if needed",
                fileName, fileKey
        );
        
        return githubAutomation.createPullRequest(
                "design-tokens.json",
                w3cJson,
                branchName,
                commitMessage,
                prTitle,
                prBody
        ).toVoid();
    }
    
    /**
     * Validate webhook signature using HMAC-SHA256
     *
     * @param payload Webhook payload
     * @param signature Signature from header
     * @return true if valid
     */
    private boolean validateSignature(String payload, String signature) {
        if (signature == null || signature.isEmpty()) {
            logger.warn("[FigmaWebhook] Missing signature");
            return false;
        }
        
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = 
                    new javax.crypto.spec.SecretKeySpec(
                            webhookSecret.getBytes("UTF-8"), 
                            "HmacSHA256"
                    );
            mac.init(secretKey);
            
            byte[] hash = mac.doFinal(payload.getBytes("UTF-8"));
            String computed = bytesToHex(hash);
            
            // Compare signatures (constant-time to prevent timing attacks)
            return java.security.MessageDigest.isEqual(
                    computed.getBytes(), 
                    signature.getBytes()
            );
            
        } catch (Exception e) {
            logger.error("[FigmaWebhook] Failed to validate signature", e);
            return false;
        }
    }
    
    /**
     * Convert byte array to hex string
     *
     * @param bytes Byte array
     * @return Hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    // ========================================================================
    // Exception
    // ========================================================================
    
    /**
     * Exception thrown when webhook processing fails
     *
     * @doc.type exception
     * @doc.purpose Webhook processing error
     * @doc.layer product
     * @doc.pattern Exception
     */
    public static class WebhookException extends RuntimeException {
        public WebhookException(String message) {
            super(message);
        }
        
        public WebhookException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
