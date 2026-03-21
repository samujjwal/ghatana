package com.ghatana.appplatform.iam;

import com.ghatana.appplatform.iam.mfa.MfaService;
import com.ghatana.appplatform.iam.ownership.BeneficialOwnershipService;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.module.KernelModule;
import io.activej.promise.Promise;

import java.util.Set;

/**
 * AppPlatform implementation of the canonical kernel USER_AUTHENTICATION capability.
 *
 * <p>Per KERNEL_APP_PLATFORM_CONVERGENCE_ADR, AppPlatform modules must implement
 * canonical kernel capabilities rather than providing parallel services. This class
 * bridges AppPlatform's sophisticated IAM system to the canonical kernel contract.</p>
 *
 * <p>Canonical capabilities provided:</p>
 * <ul>
 *   <li>User authentication with identity management</li>
 *   <li>Session management with audit trails</li>
 *   <li>Role-based access control (RBAC)</li>
 *   <li>Client credential management</li>
 * </ul>
 * 
 * <p>Note: Multi-factor auth, OAuth, and tenant isolation are handled by
 * {@link CanonicalSecurityCapability} to avoid capability duplication.</p>
 *
 * @doc.type class
 * @doc.purpose AppPlatform implementation of canonical kernel USER_AUTHENTICATION capability
 * @doc.layer app-platform
 * @doc.pattern Capability Implementation
 */
public class CanonicalIamCapability implements KernelModule {

    private final IamService iamService;
    private final MfaService mfaService;
    private final BeneficialOwnershipService ownershipService;

    /**
     * Creates the canonical IAM capability implementation.
     *
     * @param iamService        AppPlatform's core IAM service
     * @param mfaService        AppPlatform's MFA service
     * @param ownershipService  AppPlatform's beneficial ownership service
     */
    public CanonicalIamCapability(IamService iamService,
                                  MfaService mfaService,
                                  BeneficialOwnershipService ownershipService) {
        this.iamService = iamService;
        this.mfaService = mfaService;
        this.ownershipService = ownershipService;
    }

    @Override
    public String getModuleId() {
        return "canonical.iam";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            KernelCapability.Core.USER_AUTHENTICATION
        );
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of(
            KernelDependency.builder()
                .dependencyId("config.management")
                .version("1.0.0")
                .build(),
            KernelDependency.builder()
                .dependencyId("audit.immutable-trail")
                .version("1.0.0")
                .build(),
            KernelDependency.builder()
                .dependencyId("security.framework")
                .version("1.0.0")
                .build()
        );
    }

    @Override
    public void initialize(KernelContext context) {
        // Initialize AppPlatform IAM services with kernel context
        // Note: AppPlatform services are async, but canonical kernel requires sync initialization
        // We'll need to adapt this or make AppPlatform services sync-compatible
        try {
            iamService.initialize(context).get(); // Block for compatibility
            mfaService.initialize(context).get();
            ownershipService.initialize(context).get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize IAM capability", e);
        }
    }

    @Override
    public Promise<Void> start() {
        // Start all IAM services
        return iamService.start()
                .thenCompose(v -> mfaService.start())
                .thenCompose(v -> ownershipService.start());
    }

    @Override
    public Promise<Void> stop() {
        // Stop all IAM services in reverse order
        return ownershipService.stop()
                .thenCompose(v -> mfaService.stop())
                .thenCompose(v -> iamService.stop());
    }

    @Override
    public Promise<Void> shutdown() {
        // Shutdown all IAM services
        return ownershipService.shutdown()
                .thenCompose(v -> mfaService.shutdown())
                .thenCompose(v -> iamService.shutdown());
    }

    /**
     * Gets the underlying IAM service for advanced operations.
     *
     * @return the AppPlatform IAM service
     */
    public IamService getIamService() {
        return iamService;
    }

    /**
     * Gets the MFA service for multi-factor authentication operations.
     *
     * @return the AppPlatform MFA service
     */
    public MfaService getMfaService() {
        return mfaService;
    }

    /**
     * Gets the beneficial ownership service for compliance operations.
     *
     * @return the AppPlatform beneficial ownership service
     */
    public BeneficialOwnershipService getOwnershipService() {
        return ownershipService;
    }

    /**
     * Interface for core IAM operations.
     * This should be implemented by AppPlatform's IAM service layer.
     */
    public interface IamService {
        Promise<Void> initialize(KernelContext context);
        Promise<Void> start();
        Promise<Void> stop();
        Promise<Void> shutdown();
        // Core IAM operations: authentication, authorization, session management
    }
}
