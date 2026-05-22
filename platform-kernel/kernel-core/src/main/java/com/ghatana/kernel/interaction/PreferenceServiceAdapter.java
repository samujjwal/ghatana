package com.ghatana.kernel.interaction;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter for bridging kernel notification preference interactions to a preference service.
 *
 * <p>This adapter uses the GenericServiceAdapter pattern to transform kernel preference requests
 * into preference service calls, and transform responses back into kernel outcomes.</p>
 *
 * @doc.type class
 * @doc.purpose Adapter for preference service integration
 * @doc.layer kernel
 * @doc.pattern Adapter
 */
public final class PreferenceServiceAdapter {

    private final GenericServiceAdapter<
        ProductInteractionRequest<Object>,
        PreferenceServiceRequest,
        PreferenceServiceResponse,
        ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreference>
    > adapter;

    public PreferenceServiceAdapter(
            Eventloop eventloop,
            PreferenceServiceClient preferenceServiceClient) {
        Objects.requireNonNull(eventloop, "eventloop must not be null");
        Objects.requireNonNull(preferenceServiceClient, "preferenceServiceClient must not be null");

        this.adapter = new GenericServiceAdapter.Builder<
            ProductInteractionRequest<Object>,
            PreferenceServiceRequest,
            PreferenceServiceResponse,
            ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreference>
        >()
            .eventloop(eventloop)
            .serviceName("PreferenceService")
            .requestTransformer(this::transformRequest)
            .serviceInvoker(preferenceServiceClient::lookupPreference)
            .responseTransformer(this::transformResponse)
            .errorTransformer(this::transformError)
            .build();
    }

    /**
     * Handles a notification preference lookup request.
     *
     * @param request the kernel preference request
     * @return a Promise of the interaction outcome
     */
    public Promise<ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreference>> handle(
            ProductInteractionRequest<Object> request) {
        return adapter.adapt(request);
    }

    private PreferenceServiceRequest transformRequest(
            ProductInteractionRequest<Object> kernelRequest) {
        return new PreferenceServiceRequest(
            kernelRequest.policyContext().getOrDefault("customerId", ""),
            kernelRequest.policyContext().getOrDefault("preferenceType", ""),
            kernelRequest.policyContext().getOrDefault("channel", ""),
            kernelRequest.tenantId(),
            kernelRequest.policyContext()
        );
    }

    private ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreference> transformResponse(
            PreferenceServiceResponse serviceResponse) {
        return ProductInteractionOutcome.succeeded(
            serviceResponse.eventId(),
            List.of("preferenceDecision:processed"),
            new NotificationPreferenceInteractionHandler.NotificationPreference(
                serviceResponse.customerId(),
                serviceResponse.preferenceType(),
                serviceResponse.enabled(),
                serviceResponse.channel(),
                serviceResponse.updatedAt()
            )
        );
    }

    private ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreference> transformError(
            Throwable error) {
        return ProductInteractionOutcome.failed(
            "unknown",
            ProductInteractionStatus.FAILED,
            "preference_service_error",
            List.of("error:" + Objects.toString(error.getMessage(), "unknown"))
        );
    }

    /**
     * Request structure for the preference service.
     */
    public record PreferenceServiceRequest(
        String customerId,
        String notificationType,
        String channel,
        String tenantId,
        Map<String, String> policyContext
    ) {
        public PreferenceServiceRequest {
            Objects.requireNonNull(customerId, "customerId must not be null");
            Objects.requireNonNull(notificationType, "notificationType must not be null");
            Objects.requireNonNull(channel, "channel must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(policyContext, "policyContext must not be null");
        }
    }

    /**
     * Response structure from the preference service.
     */
    public record PreferenceServiceResponse(
        String eventId,
        String customerId,
        String preferenceType,
        String channel,
        boolean enabled,
        String updatedAt
    ) {
        public PreferenceServiceResponse {
            Objects.requireNonNull(eventId, "eventId must not be null");
            Objects.requireNonNull(customerId, "customerId must not be null");
            Objects.requireNonNull(preferenceType, "preferenceType must not be null");
            Objects.requireNonNull(channel, "channel must not be null");
            Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        }
    }

    /**
     * Client interface for the preference service.
     */
    @FunctionalInterface
    public interface PreferenceServiceClient {
        Promise<PreferenceServiceResponse> lookupPreference(PreferenceServiceRequest request);
    }
}
