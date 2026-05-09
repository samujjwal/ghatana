package com.ghatana.digitalmarketing.application.workspace;

import com.ghatana.digitalmarketing.application.capabilities.DmosCapabilityRegistry;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.workspace.Workspace;
import com.ghatana.digitalmarketing.domain.workspace.WorkspaceStatus;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of {@link WorkspaceService}.
 *
 * <p>Every operation:
 * <ol>
 *   <li>Validates parameters at the boundary.</li>
 *   <li>Checks authorization via {@link DigitalMarketingKernelAdapter#isAuthorized}.</li>
 *   <li>Executes domain logic via {@link Workspace} state machine.</li>
 *   <li>Persists via {@link WorkspaceRepository}.</li>
 *   <li>Records audit via {@link DigitalMarketingKernelAdapter#recordAudit}.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Production DMOS workspace application service
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class WorkspaceServiceImpl implements WorkspaceService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceServiceImpl.class);

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final WorkspaceRepository repository;

    /**
     * Constructs the workspace service.
     *
     * @param kernelAdapter DMOS kernel adapter for auth and audit
     * @param repository    workspace persistence
     */
    public WorkspaceServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            WorkspaceRepository repository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository    = Objects.requireNonNull(repository,    "repository must not be null");
    }

    // -----------------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------------

    @Override
    public Promise<Workspace> createWorkspace(DmOperationContext ctx, CreateWorkspaceCommand command) {
        Objects.requireNonNull(ctx,     "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "workspaces/*", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to create workspaces"));
                }

                String id = UUID.randomUUID().toString();
                Instant now = Instant.now();
                Workspace workspace = Workspace.builder()
                    .id(DmWorkspaceId.of(id))
                    .tenantId(ctx.getTenantId())
                    .name(command.name())
                    .description(command.description() != null ? command.description() : "")
                    .status(WorkspaceStatus.ACTIVE)
                    .createdAt(now)
                    .updatedAt(now)
                    .createdBy(ctx.getActor().getPrincipalId())
                    .build();

                return repository.save(workspace)
                    .then(saved -> {
                        LOG.info("[DMOS] Workspace created: id={} tenantId={} correlationId={}",
                            saved.getId().getValue(),
                            ctx.getTenantId().getValue(),
                            ctx.getCorrelationId().getValue());
                        return kernelAdapter.recordAudit(
                                ctx,
                                "workspaces/" + saved.getId().getValue(),
                                "create",
                                Map.of("workspaceName", saved.getName())
                            ).map(__ -> saved);
                    });
            });
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    @Override
    public Promise<Workspace> getWorkspace(DmOperationContext ctx, String workspaceId) {
        Objects.requireNonNull(ctx,         "ctx must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        if (workspaceId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("workspaceId must not be blank"));
        }

        return kernelAdapter.isAuthorized(ctx, "workspaces/" + workspaceId, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to read workspace " + workspaceId));
                }
                return repository.findById(ctx.getTenantId(), DmWorkspaceId.of(workspaceId))
                    .then(opt -> opt.isPresent()
                        ? Promise.of(opt.get())
                        : Promise.ofException(
                            new NoSuchElementException("Workspace not found: " + workspaceId)));
            });
    }

    @Override
    public Promise<List<Workspace>> listWorkspaces(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "workspaces/*", "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to list workspaces"));
                }
                return repository.listByTenant(ctx.getTenantId());
            });
    }

    // -----------------------------------------------------------------------
    // State transitions
    // -----------------------------------------------------------------------

    @Override
    public Promise<Workspace> suspendWorkspace(DmOperationContext ctx, String workspaceId) {
        Objects.requireNonNull(ctx,         "ctx must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");

        return kernelAdapter.isAuthorized(ctx, "workspaces/" + workspaceId, "suspend")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to suspend workspace " + workspaceId));
                }
                return repository.findById(ctx.getTenantId(), DmWorkspaceId.of(workspaceId))
                    .then(opt -> {
                        if (opt.isEmpty()) {
                            return Promise.ofException(
                                new NoSuchElementException("Workspace not found: " + workspaceId));
                        }
                        Workspace suspended = opt.get().suspend();
                        return repository.save(suspended)
                            .then(saved -> {
                                LOG.info("[DMOS] Workspace suspended: id={} tenantId={} correlationId={}",
                                    workspaceId, ctx.getTenantId().getValue(),
                                    ctx.getCorrelationId().getValue());
                                return kernelAdapter
                                    .recordAudit(ctx, "workspaces/" + workspaceId, "suspend",
                                        Map.of("previousStatus", opt.get().getStatus().name()))
                                    .map(__ -> saved);
                            });
                    });
            });
    }

    @Override
    public Promise<Workspace> reactivateWorkspace(DmOperationContext ctx, String workspaceId) {
        Objects.requireNonNull(ctx,         "ctx must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");

        return kernelAdapter.isAuthorized(ctx, "workspaces/" + workspaceId, "reactivate")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to reactivate workspace " + workspaceId));
                }
                return repository.findById(ctx.getTenantId(), DmWorkspaceId.of(workspaceId))
                    .then(opt -> {
                        if (opt.isEmpty()) {
                            return Promise.ofException(
                                new NoSuchElementException("Workspace not found: " + workspaceId));
                        }
                        Workspace reactivated = opt.get().reactivate();
                        return repository.save(reactivated)
                            .then(saved -> {
                                LOG.info("[DMOS] Workspace reactivated: id={} tenantId={} correlationId={}",
                                    workspaceId, ctx.getTenantId().getValue(),
                                    ctx.getCorrelationId().getValue());
                                return kernelAdapter
                                    .recordAudit(ctx, "workspaces/" + workspaceId, "reactivate",
                                        Map.of("previousStatus", opt.get().getStatus().name()))
                                    .map(__ -> saved);
                            });
                    });
            });
    }

    @Override
    public Promise<List<WorkspaceCapability>> getWorkspaceCapabilities(DmOperationContext ctx, String workspaceId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        if (workspaceId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("workspaceId must not be blank"));
        }

        return kernelAdapter.isAuthorized(ctx, "workspaces/" + workspaceId, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor not authorized to read workspace " + workspaceId));
                }
                return repository.findById(ctx.getTenantId(), DmWorkspaceId.of(workspaceId))
                    .then(opt -> {
                        if (opt.isEmpty()) {
                            return Promise.ofException(
                                new NoSuchElementException("Workspace not found: " + workspaceId));
                        }
                        
                        boolean activeWorkspace = opt.get().getStatus() == WorkspaceStatus.ACTIVE;
                        List<WorkspaceCapability> capabilities = DmosCapabilityRegistry.allCapabilities().stream()
                            .map(cap -> new WorkspaceCapability(
                                cap.key(),
                                isCapabilityEnabledByDefault(cap.key(), activeWorkspace),
                                cap.description(),
                                cap.requiresRole(),
                                cap.tier()
                            ))
                            .toList();

                        return Promise.of(capabilities);
                    });
            });
    }

    /**
     * P1-1: Returns whether a capability is enabled by default for an active workspace.
     * AI Optimization is explicitly disabled until real APIs exist.
     */
    private static boolean isCapabilityEnabledByDefault(String capabilityKey, boolean activeWorkspace) {
        if (!activeWorkspace) {
            return false;
        }
        // P1-1: AI Optimization is disabled until real APIs exist
        if (capabilityKey.equals(DmosCapabilityRegistry.AI_OPTIMIZATION)) {
            return false;
        }
        // Additional route manifest capabilities are disabled by default
        if (capabilityKey.equals(DmosCapabilityRegistry.REPORTING)
            || capabilityKey.equals(DmosCapabilityRegistry.SELF_MARKETING)
            || capabilityKey.equals(DmosCapabilityRegistry.MARKET_RESEARCH)
            || capabilityKey.equals(DmosCapabilityRegistry.ADVANCED_CHANNELS)
            || capabilityKey.equals(DmosCapabilityRegistry.LOCALIZATION)
            || capabilityKey.equals(DmosCapabilityRegistry.AGENCY)) {
            return false;
        }
        return capabilityKey.equals(DmosCapabilityRegistry.CAMPAIGNS)
            || capabilityKey.equals(DmosCapabilityRegistry.STRATEGY)
            || capabilityKey.equals(DmosCapabilityRegistry.BUDGET)
            || capabilityKey.equals(DmosCapabilityRegistry.APPROVALS)
            || capabilityKey.equals(DmosCapabilityRegistry.AI_ACTIONS)
            || capabilityKey.equals(DmosCapabilityRegistry.AD_COPY_GENERATION)
            || capabilityKey.equals(DmosCapabilityRegistry.LANDING_PAGE_GENERATION)
            || capabilityKey.equals(DmosCapabilityRegistry.EMAIL_DRAFT_GENERATION)
            || capabilityKey.equals(DmosCapabilityRegistry.SOW_GENERATION);
    }

    @Override
    public Promise<Boolean> isCapabilityEnabled(DmOperationContext ctx, String capabilityKey) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(capabilityKey, "capabilityKey must not be null");

        // Validate the capability key exists in the registry
        if (!DmosCapabilityRegistry.isDefined(capabilityKey)) {
            return Promise.ofException(
                new IllegalArgumentException("Unknown capability key: " + capabilityKey));
        }

        return repository.findById(ctx.getTenantId(), ctx.getWorkspaceId())
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(
                        new NoSuchElementException("Workspace not found: " + ctx.getWorkspaceId().getValue()));
                }
                boolean activeWorkspace = opt.get().getStatus() == WorkspaceStatus.ACTIVE;
                boolean enabled = isCapabilityEnabledByDefault(capabilityKey, activeWorkspace);
                return Promise.of(enabled);
            });
    }
}
