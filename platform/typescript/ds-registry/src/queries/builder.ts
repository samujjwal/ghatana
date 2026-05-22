/**
 * @fileoverview Builder-facing registry queries.
 *
 * These functions are the primary API that the UI builder and canvas use to
 * retrieve component contracts from the registry. They apply builder-specific
 * filters and transformations so that consuming code does not need to know
 * the internal registry structure.
 *
 * @doc.type module
 * @doc.purpose Builder-facing registry lookup helpers
 * @doc.layer platform
 * @doc.pattern Query
 */

import type {
  ComponentBuilderBinding,
  ComponentContract,
  ComponentPreviewRestrictions,
} from "@ghatana/ds-schema";
import type { ComponentEntry, RegistryStore } from "../registry/store";

// ============================================================================
// Builder Palette Entry
// ============================================================================

/**
 * A resolved palette entry the builder UI uses to populate the component
 * picker. Contains only what is needed to render a palette item and insert a
 * fresh component instance into the document.
 */
export interface BuilderPaletteEntry {
  /** The entry id in the registry store. */
  readonly id: string;
  /** Contract name used as the builder node's `contractName`. */
  readonly name: string;
  /** Display name shown in the palette (falls back to `name`). */
  readonly displayName: string;
  /** Short tooltip or description shown on hover. */
  readonly tooltip: string;
  /** Palette group name (e.g. "Form Controls", "Layout"). */
  readonly group: string;
  /** Sub-group within the palette group, if specified. */
  readonly subGroup: string | undefined;
  /** Rank within the group — lower numbers appear first. */
  readonly rank: number;
  /** Icon identifier. */
  readonly icon: string | undefined;
  /** Default props applied when this component is inserted. */
  readonly defaultProps: Record<string, unknown>;
  /** Whether the component appears in the "Favourites" section. */
  readonly featured: boolean;
  /** Keywords for palette search. */
  readonly searchKeywords: readonly string[];
  /** Contract version registered in the store. */
  readonly version: string;
  /** Semver-stable component status. */
  readonly status: string;
}

export interface BuilderCompatibilityViolation {
  readonly path: string;
  readonly message: string;
}

export interface BuilderCompatibilityResult {
  readonly compatible: boolean;
  readonly violations: readonly BuilderCompatibilityViolation[];
}

export interface BuilderCompatibleComponent {
  readonly entry: ComponentEntry;
  readonly contract: ComponentContract;
  readonly bindings: readonly ComponentBuilderBinding[];
}

// ============================================================================
// Codegen Resolution
// ============================================================================

/**
 * Resolved codegen metadata returned for a single contract. All optional
 * contract fields are normalised so that codegen does not need to guard for
 * `undefined`.
 */
export interface ResolvedCodegenContract {
  readonly contractName: string;
  readonly version: string;
  /** Module import path for the generated `import` statement. */
  readonly importPath: string;
  /** The exported React component name. */
  readonly componentName: string;
  /** Whether the export is a named export (`true`) or default export (`false`). */
  readonly namedExport: boolean;
  /**
   * HTML custom-element tag name used by the web/SSR renderer.
   * Defaults to `ghatana-{contractName.toLowerCase()}`.
   */
  readonly htmlTagName: string;
  /** Default props the builder inserts when the contract is dropped. */
  readonly defaultProps: Record<string, unknown>;
}

// ============================================================================
// Preview Policy Resolution
// ============================================================================

/**
 * Resolved preview restrictions for a component. All fields are normalised
 * from the optional contract shape to definite values consumed by
 * `buildPreviewPolicy`.
 */
export interface ResolvedPreviewPolicy {
  readonly contractName: string;
  /**
   * Minimum iframe trust level this component requires.
   * Maps directly to `PreviewMode` in `@ghatana/ui-builder`.
   */
  readonly minimumTrustLevel: ComponentPreviewRestrictions["minimumTrustLevel"];
  readonly requiresNetwork: boolean;
  readonly requiresStorage: boolean;
  readonly requiresConsent: boolean;
}

// ============================================================================
// Query Functions
// ============================================================================

/**
 * Returns all component entries that are suitable for the builder palette.
 *
 * A component is considered palette-suitable when:
 * - its `metadata.status` is `'stable'` or `'experimental'`
 * - it is NOT `'deprecated'`
 * - it has a `builder` section declared in its contract (palette intent)
 *
 * Results are sorted by palette group, then by rank (ascending), then by name.
 */
export function findBuilderComponents(
  store: RegistryStore,
): readonly BuilderPaletteEntry[] {
  const entries = store.getAllComponents();

  const palette: BuilderPaletteEntry[] = [];
  for (const entry of entries) {
    const contract = entry.contract;
    if (contract.metadata.status === "deprecated") continue;
    if (!contract.builder) continue;

    const p = contract.builder.palette;
    const defaultProps = {
      ...Object.fromEntries(
        contract.props
          .filter((prop) => prop.defaultValue !== undefined)
          .map((prop) => [prop.name, prop.defaultValue]),
      ),
      ...((contract.builder.defaultProps ?? {}) as Record<string, unknown>),
    };
    palette.push({
      id: entry.id,
      name: contract.name,
      displayName: p?.displayName ?? contract.name,
      tooltip: p?.tooltip ?? contract.description ?? contract.name,
      group: p?.group ?? "Components",
      subGroup: p?.subGroup,
      rank: p?.rank ?? Number.MAX_SAFE_INTEGER,
      icon: contract.builder.icon,
      defaultProps,
      featured: p?.featured ?? false,
      searchKeywords: p?.searchKeywords ?? [],
      version: entry.version,
      status: contract.metadata.status,
    });
  }

  palette.sort((a, b) => {
    const groupCmp = a.group.localeCompare(b.group);
    if (groupCmp !== 0) return groupCmp;
    const rankCmp = a.rank - b.rank;
    if (rankCmp !== 0) return rankCmp;
    return a.name.localeCompare(b.name);
  });

  return palette;
}

export function validateBuilderCompatibleContract(
  contract: ComponentContract,
): BuilderCompatibilityResult {
  const violations: BuilderCompatibilityViolation[] = [];

  if (!contract.builder) {
    violations.push({
      path: "builder",
      message: "Builder-compatible components must declare builder metadata.",
    });
  }

  if (contract.props.length === 0 && contract.slots.length === 0) {
    violations.push({
      path: "props",
      message:
        "Builder-compatible components must expose at least one prop or slot.",
    });
  }

  if (!contract.metadata.a11y && !contract.builderA11y) {
    violations.push({
      path: "builderA11y",
      message:
        "Builder-compatible components must declare accessibility requirements.",
    });
  }

  if (
    contract.builder?.canvas?.container === true &&
    contract.slots.length === 0
  ) {
    violations.push({
      path: "slots",
      message: "Builder container components must declare at least one slot.",
    });
  }

  const propNames = new Set(contract.props.map((prop) => prop.name));
  const bindingPropNames = new Set(
    (contract.builder?.bindings ?? []).map((binding) => binding.propName),
  );
  for (const binding of contract.builder?.bindings ?? []) {
    if (!propNames.has(binding.propName)) {
      violations.push({
        path: `builder.bindings.${binding.propName}`,
        message: `Builder binding references unknown prop "${binding.propName}".`,
      });
    }
  }

  for (const prop of contract.props) {
    if (
      prop.builderMetadata?.bindable === true &&
      !bindingPropNames.has(prop.name)
    ) {
      violations.push({
        path: `props.${prop.name}.builderMetadata.bindable`,
        message: `Bindable prop "${prop.name}" must have a matching builder binding declaration.`,
      });
    }
  }

  return {
    compatible: violations.length === 0,
    violations,
  };
}

export function findBuilderCompatibleComponents(
  store: RegistryStore,
): readonly BuilderCompatibleComponent[] {
  const components: BuilderCompatibleComponent[] = [];
  for (const entry of store.getAllComponents()) {
    const contract = entry.contract;
    if (contract.metadata.status === "deprecated") continue;
    if (!validateBuilderCompatibleContract(contract).compatible) continue;
    components.push({
      entry,
      contract,
      bindings: contract.builder?.bindings ?? [],
    });
  }
  return components;
}

/**
 * Resolves codegen metadata for the given contract name.
 *
 * Looks up the latest version of the contract in the registry. Returns
 * `undefined` when the contract is not found or lacks a `builder.codegen`
 * section.
 */
export function resolveContractForCodegen(
  store: RegistryStore,
  contractName: string,
): ResolvedCodegenContract | undefined {
  const entry = store.resolveLatestComponent(contractName);
  if (!entry) return undefined;

  const contract = entry.contract;
  const codegen = contract.builder?.codegen;
  if (!codegen) return undefined;

  const htmlTagName =
    codegen.htmlTagName ??
    `ghatana-${contract.name.toLowerCase().replace(/\s+/g, "-")}`;

  return {
    contractName: contract.name,
    version: entry.version,
    importPath: codegen.importPath,
    componentName: codegen.componentName,
    namedExport: codegen.namedExport,
    htmlTagName,
    defaultProps: (contract.builder?.defaultProps ?? {}) as Record<
      string,
      unknown
    >,
  };
}

/**
 * Resolves codegen metadata for every registered contract at once.
 *
 * Returns a `Map` keyed by contract name. Only contracts that have a
 * `builder.codegen` section are included.
 */
export function resolveAllContractsForCodegen(
  store: RegistryStore,
): ReadonlyMap<string, ResolvedCodegenContract> {
  const result = new Map<string, ResolvedCodegenContract>();
  for (const entry of store.getAllComponents()) {
    const resolved = resolveContractForCodegen(store, entry.contract.name);
    if (resolved) {
      result.set(resolved.contractName, resolved);
    }
  }
  return result;
}

/**
 * Resolves the preview policy for the given contract name.
 *
 * Falls back to `{ minimumTrustLevel: 'semi-trusted', requiresNetwork: false,
 * requiresStorage: false, requiresConsent: false }` when the contract has no
 * `preview` section.
 */
export function resolvePreviewPolicy(
  store: RegistryStore,
  contractName: string,
): ResolvedPreviewPolicy {
  const entry = store.resolveLatestComponent(contractName);
  const preview = entry?.contract.preview;

  return {
    contractName,
    minimumTrustLevel: preview?.minimumTrustLevel ?? "semi-trusted",
    requiresNetwork: preview?.requiresNetwork ?? false,
    requiresStorage: preview?.requiresStorage ?? false,
    requiresConsent: preview?.requiresConsent ?? false,
  };
}

/**
 * Resolves the preview policy for every registered contract at once.
 *
 * Returns a `Map` keyed by contract name. All contracts are included even
 * when they have no explicit `preview` section (fallback defaults are applied).
 */
export function resolveAllPreviewPolicies(
  store: RegistryStore,
): ReadonlyMap<string, ResolvedPreviewPolicy> {
  const result = new Map<string, ResolvedPreviewPolicy>();
  const seen = new Set<string>();
  for (const entry of store.getAllComponents()) {
    const name = entry.contract.name;
    if (seen.has(name)) continue;
    seen.add(name);
    result.set(name, resolvePreviewPolicy(store, name));
  }
  return result;
}

/**
 * Returns the full `ComponentContract` for the given name at its latest
 * registered version. Returns `undefined` when not found.
 */
export function resolveLatestContract(
  store: RegistryStore,
  contractName: string,
): ComponentContract | undefined {
  return store.resolveLatestComponent(contractName)?.contract;
}

/**
 * Returns the full `ComponentContract` for the given name at a specific
 * semver version. Returns `undefined` when not found.
 */
export function resolveContractAtVersion(
  store: RegistryStore,
  contractName: string,
  version: string,
): ComponentContract | undefined {
  return store.getComponentByNameAndVersion(contractName, version)?.contract;
}

/**
 * Returns all registered versions of a contract sorted chronologically
 * (oldest first, newest last). Useful for migration tooling and changelogs.
 */
export function resolveAllContractVersions(
  store: RegistryStore,
  contractName: string,
): readonly ComponentEntry[] {
  return store.getAllVersionsOfComponent(contractName);
}

/**
 * Builds a `ReadonlyMap<string, ComponentContract>` from the current registry,
 * keyed by contract name. Only the latest version of each contract is included.
 *
 * This map is the primary input expected by codegen and validation functions
 * in `@ghatana/ui-builder`.
 */
export function buildContractMap(
  store: RegistryStore,
): ReadonlyMap<string, ComponentContract> {
  const result = new Map<string, ComponentContract>();
  const seen = new Set<string>();
  for (const entry of store.getAllComponents()) {
    const name = entry.contract.name;
    if (seen.has(name)) continue;
    seen.add(name);
    // Prefer the latest by using resolveLatestComponent
    const latest = store.resolveLatestComponent(name);
    if (latest) result.set(name, latest.contract);
  }
  return result;
}
