package com.ghatana.finance.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.resilience.Bulkhead;
import com.ghatana.platform.resilience.CircuitBreakerProfiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Default fraud inference service with remote-model and fallback scoring support.
 *
 * @doc.type class
 * @doc.purpose Provides remote model inference with deterministic finance fallback scoring
 * @doc.layer product
 * @doc.pattern Service
 */
public class DefaultFraudModelInferenceService implements FraudModelInferenceService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFraudModelInferenceService.class);
    private static final int DEFAULT_REMOTE_INFERENCE_MAX_ATTEMPTS = 3;
    private static final int DEFAULT_REMOTE_INFERENCE_MAX_CONCURRENCY = 8;

    private final ModelRepository modelRepository;
    private final FraudInferenceTransport transport;

    public DefaultFraudModelInferenceService(ModelRepository modelRepository) {
        this(modelRepository, defaultTransport());
    }

    public DefaultFraudModelInferenceService(ModelRepository modelRepository,
                                            FraudInferenceTransport transport) {
        this.modelRepository = Objects.requireNonNull(modelRepository, "modelRepository cannot be null");
        this.transport = Objects.requireNonNull(transport, "transport cannot be null");
    }

    @Override
    public FraudModelPrediction predict(String modelId, Map<String, Object> features) {
        Objects.requireNonNull(modelId, "modelId cannot be null");
        Objects.requireNonNull(features, "features cannot be null");

        ModelRecord modelRecord = modelRepository.findByModelId(modelId);
        FraudModelEndpointConfig endpointConfig = resolveEndpoint(modelRecord);
        String modelVersion = resolveModelVersion(modelRecord);

        if (endpointConfig != null) {
            try {
                return transport.predict(endpointConfig, new FraudModelInferenceRequest(modelId, features));
            } catch (RuntimeException exception) {
                logger.warn("Remote fraud inference failed for model '{}' via '{}': {}",
                    modelId, endpointConfig.getEndpoint(), exception.getMessage());
            }
        }

        return fallbackPrediction(features, modelVersion);
    }

    private static FraudModelEndpointConfig resolveEndpoint(ModelRecord modelRecord) {
        return modelRecord == null ? null : FraudModelEndpointConfig.fromMetadata(modelRecord.getMetadata());
    }

    private static String resolveModelVersion(ModelRecord modelRecord) {
        if (modelRecord == null || modelRecord.getMetadata() == null) {
            return null;
        }

        Object modelVersion = modelRecord.getMetadata().get("model_version");
        if (modelVersion == null) {
            modelVersion = modelRecord.getMetadata().get("modelVersion");
        }
        if (modelVersion == null) {
            modelVersion = modelRecord.getMetadata().get("version");
        }
        return modelVersion == null ? null : String.valueOf(modelVersion);
    }

    private static FraudModelPrediction fallbackPrediction(Map<String, Object> features, String modelVersion) {
        double amountFactor = asDouble(features.get("amount_factor"));
        double velocityScore = normalize(asDouble(features.get("velocity_score")), 15.0, 0.25);
        double geolocationRisk = asDouble(features.get("geolocation_risk"));
        double timeRisk = asDouble(features.get("time_risk"));
        double priceDeviation = normalize(asDouble(features.get("price_deviation")), 1.0, 0.35);
        double volumeAnomaly = normalize(asDouble(features.get("volume_anomaly")), 20.0, 0.25);
        double merchantRisk = asDouble(features.getOrDefault("merchant_risk", 0.0));
        double counterpartyRisk = asDouble(features.getOrDefault("counterparty_risk", 0.0));
        double paymentMethodRisk = asDouble(features.getOrDefault("payment_method_risk", 0.0));
        double locationMismatchRisk = asDouble(features.getOrDefault("location_mismatch_risk", 0.0));
        double executionChannelRisk = asDouble(features.getOrDefault("execution_channel_risk", 0.0));

        double fraudScore = clamp(
            amountFactor
                + velocityScore
                + geolocationRisk
                + timeRisk
                + priceDeviation
                + volumeAnomaly
                + merchantRisk
                + counterpartyRisk
                + paymentMethodRisk
                + locationMismatchRisk
                + executionChannelRisk
        );
        boolean fraudulent = fraudScore >= 0.75;
        String riskLevel = fraudScore >= 0.75 ? "HIGH" : fraudScore >= 0.45 ? "MEDIUM" : "LOW";
        String fraudType = fraudulent ? determineFraudType(
            velocityScore,
            priceDeviation,
            merchantRisk,
            counterpartyRisk,
            paymentMethodRisk,
            executionChannelRisk
        ) : null;
        double confidence = clamp(0.72 + (fraudScore * 0.2));
        double accuracy = clamp(0.80 + (fraudScore * 0.1));

        return new FraudModelPrediction(
            fraudScore,
            riskLevel,
            fraudulent,
            confidence,
            accuracy,
            fraudType,
            "FALLBACK",
            modelVersion,
            0L
        );
    }

    private static String determineFraudType(double velocityScore,
                                             double priceDeviation,
                                             double merchantRisk,
                                             double counterpartyRisk,
                                             double paymentMethodRisk,
                                             double executionChannelRisk) {
        double maxRisk = Math.max(
            Math.max(velocityScore, priceDeviation),
            Math.max(Math.max(merchantRisk, counterpartyRisk), Math.max(paymentMethodRisk, executionChannelRisk))
        );

        if (maxRisk == priceDeviation) {
            return "market-anomaly";
        }
        if (maxRisk == counterpartyRisk) {
            return "cross-border-anomaly";
        }
        if (maxRisk == paymentMethodRisk || maxRisk == merchantRisk) {
            return "transaction-anomaly";
        }
        return (maxRisk == executionChannelRisk || maxRisk == velocityScore)
            ? "velocity-anomaly"
            : "transaction-anomaly";
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static double normalize(double value, double maxValue, double weight) {
        if (maxValue <= 0.0) {
            return 0.0;
        }
        return Math.min(value / maxValue, 1.0) * weight;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(value, 1.0));
    }

    public interface FraudInferenceTransport {
        FraudModelPrediction predict(FraudModelEndpointConfig endpointConfig, FraudModelInferenceRequest request);
    }

    private static FraudInferenceTransport defaultTransport() {
        return new RetryingFraudInferenceTransport(
            new ResilientFraudInferenceTransport(
                new HttpFraudInferenceTransport(),
                CircuitBreakerProfiles.standard("finance-fraud-inference"),
                Bulkhead.of("finance-fraud-inference", DEFAULT_REMOTE_INFERENCE_MAX_CONCURRENCY)
            ),
            DEFAULT_REMOTE_INFERENCE_MAX_ATTEMPTS
        );
    }

    private static final class HttpFraudInferenceTransport implements FraudInferenceTransport {

        private final HttpClient httpClient;
        private final ObjectMapper objectMapper;

        private HttpFraudInferenceTransport() {
            this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                new ObjectMapper()
            );
        }

        private HttpFraudInferenceTransport(HttpClient httpClient, ObjectMapper objectMapper) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        }

        @Override
        public FraudModelPrediction predict(FraudModelEndpointConfig endpointConfig, FraudModelInferenceRequest requestPayload) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(endpointConfig.getEndpoint()))
                    .timeout(endpointConfig.getTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestPayload.toPayload())))
                    .build();

                long startNanos = System.nanoTime();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long roundTripLatencyMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("Unexpected status code: " + response.statusCode());
                }

                Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {});
                return FraudModelInferenceResponse.fromPayload(payload)
                    .toPrediction(endpointConfig.getModelVersion(), roundTripLatencyMs);
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("Remote fraud inference request failed", exception);
            }
        }
    }
}
