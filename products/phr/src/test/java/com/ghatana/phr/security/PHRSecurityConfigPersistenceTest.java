package com.ghatana.phr.security;

import com.ghatana.kernel.security.KernelSecurityManager;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.phr.model.PHRUser;
import com.ghatana.phr.model.PatientConsent;
import com.ghatana.phr.model.TenantConfig;
import com.ghatana.phr.repository.PhrRepositoryRuntimeConfig;
import com.ghatana.phr.support.PhrPersistenceTestSupport;
import com.ghatana.platform.database.connection.ConnectionPool;
import com.ghatana.platform.security.crypto.PasswordHasher;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PHRSecurityConfigPersistenceTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();

    private PostgreSQLContainer<?> postgres;
    private ConnectionPool connectionPool;

    @BeforeEach
    void setUp() {
        postgres = PhrPersistenceTestSupport.startPostgres();
        PhrRepositoryRuntimeConfig runtimeConfig = PhrPersistenceTestSupport.createRuntimeConfig(postgres, "phr-security-runtime-test");
        connectionPool = ConnectionPool.create(runtimeConfig.getDataSourceConfig().orElseThrow());
    }

    @AfterEach
    void tearDown() {
        PhrPersistenceTestSupport.closeConnectionPool(connectionPool);
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void persistentFactoryReusesStoredSecurityDataAcrossInstances() {
        PHRSecurityConfig firstConfig = PHRSecurityConfig.persistent(connectionPool.getDataSource(), new AllowingConsentService());

        PHRUser user = new PHRUser("provider-1", "dr.smith", "dr.smith@hospital.com");
        user.addRole("HEALTHCARE_PROVIDER");
        user.addPermission("read:patient-records");
        user.setPasswordHash(passwordHasher.hash("Password123!"));
        firstConfig.getUserRepository().save(user);

        TenantConfig tenantConfig = new TenantConfig("tenant-1", "Nepal Health");
        firstConfig.getTenantConfigRepository().save(tenantConfig);

        PatientConsent consent = new PatientConsent();
        consent.setPatientId("patient-1");
        consent.setTenantId("tenant-1");
        consent.setPurpose("treatment");
        consent.setGranted(true);
        consent.setTimestamp(Instant.now());
        firstConfig.getConsentRepository().save(consent);

        PHRSecurityConfig secondConfig = PHRSecurityConfig.persistent(connectionPool.getDataSource(), new AllowingConsentService());
        KernelSecurityManager.ValidationResult validationResult = secondConfig.kernelSecurityManager().validateCredentials(
            new KernelSecurityManager.Credentials("dr.smith", "Password123!", null)
        );

        assertTrue(validationResult.isValid());
        assertNotNull(secondConfig.getConsentRepository().findByPatientAndPurpose("patient-1", "treatment"));
        assertEquals("Nepal Health", secondConfig.getTenantConfigRepository().findById("tenant-1").getTenantName());
    }

    private static final class AllowingConsentService implements ConsentService {
        @Override
        public Promise<ConsentAccessDecision> checkAccess(ConsentCheckRequest request) {
            return Promise.of(ConsentAccessDecision.allow(
                ReasonCode.EXPLICIT_GRANT,
                "grant-1",
                CacheStatus.MISS,
                Instant.now().plusSeconds(60)
            ));
        }

        @Override
        public Promise<ConsentAccessDecision> assertAccess(ConsentCheckRequest request) {
            return checkAccess(request);
        }

        @Override
        public Promise<Void> invalidatePatientAccessCache(CacheInvalidationRequest request) {
            return Promise.complete();
        }

        @Override
        public Promise<ConsentRevokeResult> revokeConsent(ConsentRevokeRequest request) {
            return Promise.of(new ConsentRevokeResult(true, request.target().resourceId()));
        }
    }
}
