package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import io.activej.promise.Promise;

import java.util.Objects;

/**
 * Service interface for Google Ads OAuth account connection.
 *
 * @doc.type class
 * @doc.purpose Manages Google Ads OAuth flow and token lifecycle (DMOS-F2-007)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmGoogleAdsAuthService {

    /**
     * Initiates an OAuth authorization URL for the given connector.
     *
     * @param ctx         operation context
     * @param connectorId the connector to authorize
     * @param redirectUri the OAuth redirect URI
     * @return authorization URL to redirect the user to
     */
    Promise<String> initiateOAuthFlow(DmOperationContext ctx, String connectorId, String redirectUri);

    /**
     * Exchanges an OAuth authorization code for tokens and stores the credential.
     *
     * @param ctx         operation context
     * @param connectorId the connector being authorized
     * @param code        the OAuth code from the provider
     * @param redirectUri the redirect URI used in the initial flow
     * @return stored credential
     */
    Promise<DmGoogleAdsCredential> exchangeCode(
        DmOperationContext ctx, String connectorId, String code, String redirectUri);

    /**
     * Refreshes an expired access token for the given credential.
     *
     * @param ctx          operation context
     * @param credentialId the credential to refresh
     * @return updated credential with new access token
     */
    Promise<DmGoogleAdsCredential> refreshToken(DmOperationContext ctx, String credentialId);

    /**
     * Revokes access and deletes the credential.
     *
     * @param ctx          operation context
     * @param credentialId the credential to revoke
     */
    Promise<Void> revokeAccess(DmOperationContext ctx, String credentialId);

    /**
     * Request record for {@link #exchangeCode}.
     */
    record ExchangeCodeRequest(String connectorId, String code, String redirectUri) {
        public ExchangeCodeRequest {
            Objects.requireNonNull(connectorId, "connectorId must not be null");
            Objects.requireNonNull(code, "code must not be null");
            Objects.requireNonNull(redirectUri, "redirectUri must not be null");
            if (connectorId.isBlank()) throw new IllegalArgumentException("connectorId must not be blank");
            if (code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        }
    }
}
