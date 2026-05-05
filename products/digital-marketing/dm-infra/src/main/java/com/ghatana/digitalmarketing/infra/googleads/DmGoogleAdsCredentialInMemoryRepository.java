package com.ghatana.digitalmarketing.infra.googleads;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCredentialRepository;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import io.activej.promise.Promise;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
/**
 * In-memory implementation of {@link DmGoogleAdsCredentialRepository} for local and test profiles.
 *
 * @doc.type class
 * @doc.purpose In-memory Google Ads credential repository for local development and testing
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class DmGoogleAdsCredentialInMemoryRepository implements DmGoogleAdsCredentialRepository {
    private final ConcurrentHashMap<String, DmGoogleAdsCredential> storeById = new ConcurrentHashMap<>();
    @Override
    public Promise<DmGoogleAdsCredential> save(DmGoogleAdsCredential credential) {
        storeById.put(credential.getId(), credential);
        return Promise.of(credential);
    }
    @Override
    public Promise<Optional<DmGoogleAdsCredential>> findById(String id) {
        return Promise.of(Optional.ofNullable(storeById.get(id)));
    }
    @Override
    public Promise<Optional<DmGoogleAdsCredential>> findByConnectorId(String connectorId) {
        return Promise.of(storeById.values().stream()
            .filter(c -> connectorId.equals(c.getConnectorId()))
            .findFirst());
    }
    @Override
    public Promise<DmGoogleAdsCredential> update(DmGoogleAdsCredential credential) {
        storeById.put(credential.getId(), credential);
        return Promise.of(credential);
    }
    @Override
    public Promise<Void> delete(String id) {
        storeById.remove(id);
        return Promise.complete();
    }
}
