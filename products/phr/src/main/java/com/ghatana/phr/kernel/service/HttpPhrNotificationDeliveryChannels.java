package com.ghatana.phr.kernel.service;

import io.activej.promise.Promise;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * @doc.type class
 * @doc.purpose Delivers PHR notifications to configured HTTP provider endpoints for email, SMS, and push channels
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class HttpPhrNotificationDeliveryChannels implements PhrNotificationDeliveryChannels {

    private final HttpClient httpClient;
    private final Executor executor;
    private final PhrNotificationProviderConfig providerConfig;

    public HttpPhrNotificationDeliveryChannels(
            HttpClient httpClient,
            Executor executor,
            PhrNotificationProviderConfig providerConfig) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.providerConfig = Objects.requireNonNull(providerConfig, "providerConfig must not be null");
    }

    @Override
    public Promise<DeliveryReceipt> sendEmail(NotificationEnvelope notification) {
        return sendToEndpoint(providerConfig.emailEndpoint(), notification, PhrNotificationSender.NotificationChannel.EMAIL);
    }

    @Override
    public Promise<DeliveryReceipt> sendSms(NotificationEnvelope notification) {
        return sendToEndpoint(providerConfig.smsEndpoint(), notification, PhrNotificationSender.NotificationChannel.SMS);
    }

    @Override
    public Promise<DeliveryReceipt> sendPush(NotificationEnvelope notification) {
        return sendToEndpoint(providerConfig.pushEndpoint(), notification, PhrNotificationSender.NotificationChannel.PUSH);
    }

    private Promise<DeliveryReceipt> sendToEndpoint(
            Optional<String> endpoint,
            NotificationEnvelope envelope,
            PhrNotificationSender.NotificationChannel channel) {
        String resolvedEndpoint = endpoint.orElseThrow(() -> new IllegalStateException(
            channel.name() + " notification endpoint is not configured"
        ));
        return Promise.ofBlocking(executor, () -> sendBlocking(resolvedEndpoint, envelope, channel));
    }

    private DeliveryReceipt sendBlocking(
            String endpoint,
            NotificationEnvelope envelope,
            PhrNotificationSender.NotificationChannel channel) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(providerConfig.requestTimeout())
            .header("Content-Type", "application/json")
            .header("X-Ghatana-Correlation-Id", envelope.correlationId())
            .header("X-Ghatana-Trace-Operation", envelope.traceOperation())
            .POST(HttpRequest.BodyPublishers.ofString(toJson(channel, envelope)));
        providerConfig.bearerToken().ifPresent(token -> requestBuilder.header("Authorization", "Bearer " + token));

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException(channel.name() + " delivery failed with HTTP " + statusCode);
        }

        String providerMessageId = response.headers()
            .firstValue("X-Provider-Message-Id")
            .orElse(channel.name().toLowerCase() + "-" + envelope.notificationId());
        return new DeliveryReceipt(providerMessageId, Instant.now());
    }

    private static String toJson(
            PhrNotificationSender.NotificationChannel channel,
            NotificationEnvelope envelope) {
        return "{" +
            jsonField("notificationId", envelope.notificationId()) + "," +
            jsonField("patientId", envelope.patientId()) + "," +
            jsonField("recipientId", envelope.recipientId()) + "," +
            jsonField("providerId", envelope.providerId()) + "," +
            jsonField("referenceId", envelope.referenceId()) + "," +
            jsonField("referenceType", envelope.referenceType()) + "," +
            jsonField("notificationType", envelope.notificationType()) + "," +
            jsonField("channel", channel.name()) + "," +
            jsonField("scheduledFor", envelope.scheduledFor().toString()) + "," +
            jsonField("createdAt", envelope.createdAt().toString()) + "," +
            jsonField("correlationId", envelope.correlationId()) + "," +
            jsonField("traceOperation", envelope.traceOperation()) +
            "}";
    }

    private static String jsonField(String name, String value) {
        return "\"" + escape(name) + "\":\"" + escape(value == null ? "" : value) + "\"";
    }

    private static String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }
}
