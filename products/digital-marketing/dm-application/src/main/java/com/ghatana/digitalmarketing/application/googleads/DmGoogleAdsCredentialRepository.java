package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Persistence port for {@link DmGoogleAdsCredential}.
 *
 * @doc.type class
 * @doc.purpose Repository for Google Ads credential storage (DMOS-F2-007)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmGoogleAdsCredentialRepository {

    Promise<DmGoogleAdsCredential> save(DmGoogleAdsCredential credential);

    Promise<Optional<DmGoogleAdsCredential>> findById(String id);

    Promise<Optional<DmGoogleAdsCredential>> findByConnectorId(String connectorId);

    Promise<DmGoogleAdsCredential> update(DmGoogleAdsCredential credential);

    Promise<Void> delete(String id);
}
