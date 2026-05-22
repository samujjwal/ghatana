package com.ghatana.kernel.interaction;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter for bridging kernel consent interactions to a consent domain service.
 *
 * <p>This adapter uses the GenericServiceAdapter pattern to transform kernel consent requests
 * into consent service calls, and transform responses back into kernel outcomes.</p>
 *
 * @doc.type class
 * @doc.purpose Adapter for consent domain service integration
 * @doc.layer kernel
 * @doc.pattern Adapter
 */
public final class ConsentServiceAdapter {

    private final GenericServiceAdapter<
        ProductInteractionRequest<Object>,
        ConsentServiceRequest,
        ConsentServiceResponse,
        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus>
    > adapter;

    public ConsentServiceAdapter(
            Eventloop eventloop,
            ConsentServiceClient consentServiceClient) {
        Objects.requireNonNull(eventloop, "eventloop must not be null");
        Objects.requireNonNull(consentServiceClient, "consentServiceClient must not be null");

        this.adapter = new GenericServiceAdapter.Builder<
            ProductInteractionRequest<Object>,
            ConsentServiceRequest,
            ConsentServiceResponse,
            ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus>
        >()
            .eventloop(eventloop)
            .serviceName("ConsentService")
            .requestTransformer(this::transformRequest)
            .serviceInvoker(consentServiceClient::checkConsent)
            .responseTransformer(this::transformResponse)
            .errorTransformer(this::transformError)
            .build();
    }

    /**
     * Handles a consent status check request.
     *
     * @param request the kernel consent request
     * @return a Promise of the interaction outcome
     */
    public Promise<ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus>> handle(
            ProductInteractionRequest<Object> request) {
        return adapter.adapt(request);
    }

    private ConsentServiceRequest transformRequest(
            ProductInteractionRequest<Object> kernelRequest) {
        return new ConsentServiceRequest(
            kernelRequest.policyContext().getOrDefault("subjectId", ""),
            kernelRequest.policyContext().getOrDefault("resourceId", ""),
            kernelRequest.policyContext().getOrDefault("action", ""),
            kernelRequest.tenantId(),
            kernelRequest.policyContext()
        );
    }

    private ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> transformResponse(
            ConsentServiceResponse serviceResponse) {
        if (serviceResponse.granted()) {
            return ProductInteractionOutcome.succeeded(
                serviceResponse.eventId(),
                List.of("consentDecision:granted"),
                new ConsentStatusInteractionHandler.ConsentStatus(
                    serviceResponse.subjectId(),
                    serviceResponse.consentType(),
                    true,
                    serviceResponse.grantedAt(),
                    serviceResponse.expirationTime()
                )
            );
        } else {
            return ProductInteractionOutcome.failed(
                serviceResponse.eventId(),
                ProductInteractionStatus.BLOCKED,
                "consent_denied",
                List.of("reason:" + Objects.toString(serviceResponse.reason(), "unknown"))
            );
        }
    }

    private ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> transformError(
            Throwable error) {
        return ProductInteractionOutcome.failed(
            "unknown",
            ProductInteractionStatus.FAILED,
            "consent_service_error",
            List.of("error:" + Objects.toString(error.getMessage(), "unknown"))
        );
    }

    /**
     * Request structure for the consent service.
     */
    public record ConsentServiceRequest(
        String subjectId,
        String resourceId,
        String action,
        String tenantId,
        Map<String, String> policyContext
    ) {
        public ConsentServiceRequest {
            Objects.requireNonNull(subjectId, "subjectId must not be null");
            Objects.requireNonNull(resourceId, "resourceId must not be null");
            Objects.requireNonNull(action, "action must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(policyContext, "policyContext must not be null");
        }
    }

    /**
     * Response structure from the consent service.
     */
    public record ConsentServiceResponse(
        String eventId,
        String subjectId,
        boolean granted,
        String consentType,
        String grantedAt,
        String expirationTime,
        String reason
    ) {
        public ConsentServiceResponse {
            Objects.requireNonNull(eventId, "eventId must not be null");
            Objects.requireNonNull(subjectId, "subjectId must not be null");
            Objects.requireNonNull(consentType, "consentType must not be null");
        }
    }

    /**
     * Client interface for the consent service.
     */
    @FunctionalInterface
    public interface ConsentServiceClient {
        Promise<ConsentServiceResponse> checkConsent(ConsentServiceRequest request);
    }
}
