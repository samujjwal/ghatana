package com.ghatana.appplatform.pack;

import java.util.List;
import java.util.Objects;

/**
 * Canonical manifest for a domain pack — Backlog D1 (standardize pack manifest model).
 *
 * <p>A domain pack is a first-party, trusted bundle of domain-specific business logic
 * (services, ports, domain objects, DB migrations, and workflows) that extends the
 * AppPlatform for a particular subdomain (e.g. healthcare, post-trade, sanctions).</p>
 *
 * <p>This manifest unifies the "plugin" and "pack" concepts by providing a common
 * descriptor vocabulary that runtime infrastructure (pack registry, dependency resolver,
 * UI shell) can consume without domain-specific knowledge.</p>
 *
 * <p>Each section of the manifest maps directly to one of the D1 requirements:</p>
 * <ul>
 *   <li>{@link Identity} — identity and version</li>
 *   <li>{@link CapabilityRequirement} — required kernel capabilities</li>
 *   <li>{@link ContractExport} — exported API/schema contracts</li>
 *   <li>{@link WorkflowDeclaration} — declared workflows</li>
 *   <li>{@link IntegrationDeclaration} — external system integrations</li>
 *   <li>{@link UiContribution} — UI extension points</li>
 *   <li>{@link ActivationConstraint} — deployment activation constraints</li>
 * </ul>
 *
 * <p>Usage — each domain pack implements {@link DomainPackDescriptor} and returns
 * a fully-populated manifest:</p>
 * <pre>{@code
 * public class HealthcareDomainPackDescriptor implements DomainPackDescriptor {
 *     @Override
 *     public DomainPackManifest getManifest() {
 *         return new DomainPackManifest(
 *             new Identity("healthcare", "H-01", "2026.3.1"),
 *             List.of(new CapabilityRequirement("data.storage", true)),
 *             List.of(new ContractExport("phr.consent-api", ContractExport.ContractType.REST)),
 *             List.of(new WorkflowDeclaration("consent-lifecycle", "ConsentWorkflow")),
 *             List.of(new IntegrationDeclaration("fhir-server", "FHIR R4", false)),
 *             List.of(new UiContribution("patient-dashboard", UiContribution.UiSlot.MAIN_CONTENT)),
 *             List.of(new ActivationConstraint("deployment.healthcare.enabled", "true"))
 *         );
 *     }
 * }
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Canonical domain pack manifest model — unifies plugin and pack descriptor vocabulary (D1)
 * @doc.layer product
 * @doc.pattern ValueObject
 * @author Ghatana AppPlatform Team
 * @since 2026.3.0
 */
public record DomainPackManifest(
    /** Pack identity: unique ID, display name, semantic version. */
    Identity identity,
    /** Kernel capabilities this pack REQUIRES to function. */
    List<CapabilityRequirement> requiredCapabilities,
    /** API/schema contracts this pack EXPORTS for other packs / external consumers. */
    List<ContractExport> exportedContracts,
    /** Workflow names and entry-class this pack DECLARES. */
    List<WorkflowDeclaration> workflows,
    /** External integrations this pack CONNECTS to. */
    List<IntegrationDeclaration> integrations,
    /** UI extension points this pack CONTRIBUTES. */
    List<UiContribution> uiContributions,
    /** Deployment-time constraints that must be satisfied to activate this pack. */
    List<ActivationConstraint> activationConstraints
) {

    public DomainPackManifest {
        Objects.requireNonNull(identity, "identity");
        requiredCapabilities  = requiredCapabilities  == null ? List.of() : List.copyOf(requiredCapabilities);
        exportedContracts     = exportedContracts     == null ? List.of() : List.copyOf(exportedContracts);
        workflows             = workflows             == null ? List.of() : List.copyOf(workflows);
        integrations          = integrations          == null ? List.of() : List.copyOf(integrations);
        uiContributions       = uiContributions       == null ? List.of() : List.copyOf(uiContributions);
        activationConstraints = activationConstraints == null ? List.of() : List.copyOf(activationConstraints);
    }

    // ── D1 section: identity / version ──────────────────────────────────────

    /**
     * Pack identity — unique machine-readable ID, display name, and semantic version.
     *
     * @param packId   Unique identifier (lowercase, hyphen-separated). Example: {@code "healthcare"}.
     * @param storyId  Architecture story reference. Example: {@code "H-01"}.
     * @param version  Semantic version. Example: {@code "2026.3.1"}.
     */
    public record Identity(String packId, String storyId, String version) {
        public Identity {
            Objects.requireNonNull(packId,   "packId");
            Objects.requireNonNull(storyId,  "storyId");
            Objects.requireNonNull(version,  "version");
        }
    }

    // ── D1 section: required kernel capabilities ─────────────────────────────

    /**
     * A kernel capability that this domain pack requires at startup.
     *
     * @param capabilityId Kernel capability ID (e.g. {@code "data.storage"}).
     * @param optional     If {@code true}, the pack degrades gracefully when absent.
     */
    public record CapabilityRequirement(String capabilityId, boolean optional) {
        public CapabilityRequirement {
            Objects.requireNonNull(capabilityId, "capabilityId");
        }
    }

    // ── D1 section: exported contracts ──────────────────────────────────────

    /**
     * An API or schema contract that this domain pack exports.
     *
     * @param contractId   Unique contract ID (e.g. {@code "phr.consent-api"}).
     * @param contractType The contract protocol.
     */
    public record ContractExport(String contractId, ContractType contractType) {
        public ContractExport {
            Objects.requireNonNull(contractId,   "contractId");
            Objects.requireNonNull(contractType, "contractType");
        }

        /** The protocol over which the contract is exposed. */
        public enum ContractType { REST, GRPC, GRAPHQL, EVENT, SCHEMA }
    }

    // ── D1 section: workflows ────────────────────────────────────────────────

    /**
     * A workflow declared and owned by this domain pack.
     *
     * @param workflowId   Unique workflow ID within the pack (e.g. {@code "consent-lifecycle"}).
     * @param entryClass   Fully-qualified service class that implements the workflow.
     */
    public record WorkflowDeclaration(String workflowId, String entryClass) {
        public WorkflowDeclaration {
            Objects.requireNonNull(workflowId,  "workflowId");
            Objects.requireNonNull(entryClass,  "entryClass");
        }
    }

    // ── D1 section: integrations ─────────────────────────────────────────────

    /**
     * An external system that this domain pack integrates with.
     *
     * @param integrationId Display name of the integration (e.g. {@code "fhir-server"}).
     * @param protocol      Protocol/standard used (e.g. {@code "FHIR R4"}).
     * @param required      If {@code false}, the integration is optional / degradable.
     */
    public record IntegrationDeclaration(String integrationId, String protocol, boolean required) {
        public IntegrationDeclaration {
            Objects.requireNonNull(integrationId, "integrationId");
            Objects.requireNonNull(protocol,      "protocol");
        }
    }

    // ── D1 section: UI contributions ─────────────────────────────────────────

    /**
     * A UI extension point contributed by this domain pack.
     *
     * @param componentId  Unique component ID (e.g. {@code "patient-dashboard"}).
     * @param slot         The shell slot this component targets.
     */
    public record UiContribution(String componentId, UiSlot slot) {
        public UiContribution {
            Objects.requireNonNull(componentId, "componentId");
            Objects.requireNonNull(slot,        "slot");
        }

        /** Named slots in the AppPlatform UI shell where domain packs can contribute views. */
        public enum UiSlot {
            /** Primary content area. */
            MAIN_CONTENT,
            /** Side navigation items. */
            SIDEBAR_NAV,
            /** Cards on the overview/dashboard page. */
            DASHBOARD_CARD,
            /** Settings page sections contributed by this pack. */
            SETTINGS_SECTION,
            /** Actions exposed in the global quick-action menu. */
            QUICK_ACTIONS,
            /** Items in the notification panel contributed by this pack. */
            NOTIFICATION_PANEL
        }
    }

    // ── D1 section: activation constraints ──────────────────────────────────

    /**
     * A deployment-time constraint that must be satisfied before the pack is activated.
     *
     * <p>Used to implement feature toggles, licensing gates, and environment-scoped
     * activation (e.g. healthcare-only vs finance-only deployments).</p>
     *
     * @param configKey      The config/feature-flag key to check (e.g. {@code "deployment.healthcare.enabled"}).
     * @param requiredValue  The string value that must be present for activation to proceed.
     */
    public record ActivationConstraint(String configKey, String requiredValue) {
        public ActivationConstraint {
            Objects.requireNonNull(configKey,     "configKey");
            Objects.requireNonNull(requiredValue, "requiredValue");
        }
    }
}
