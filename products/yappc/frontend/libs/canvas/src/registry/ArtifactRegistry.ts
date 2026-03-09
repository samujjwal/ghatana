/**
 * Artifact Registry
 *
 * Central registry for all artifact contracts.
 * Singleton pattern with registration, lookup, and query capabilities.
 *
 * @doc.type singleton
 * @doc.purpose Artifact contract registration and lookup
 * @doc.layer core
 * @doc.pattern Registry, Singleton
 */

import type {
    ArtifactContract,
    ArtifactKind,
    ContentModality,
    SemanticVersion,
    UniqueId,
} from '../model/contracts';

// ============================================================================
// Registry Types
// ============================================================================

/**
 * Registration options
 */
export interface RegistrationOptions {
    /** Replace if already registered */
    replace?: boolean;
    /** Source of registration (core, plugin, user) */
    source?: 'core' | 'plugin' | 'user';
    /** Priority for resolution (higher wins) */
    priority?: number;
}

/**
 * Registry query options
 */
export interface QueryOptions {
    /** Filter by category */
    category?: string;
    /** Filter by tags (any match) */
    tags?: string[];
    /** Filter by modality */
    modality?: ContentModality;
    /** Filter by platform */
    platform?: 'web' | 'desktop' | 'mobile';
    /** Text search in name/description */
    search?: string;
    /** Only return latest version */
    latestOnly?: boolean;
    /** Maximum results */
    limit?: number;
}

/**
 * Registry entry (internal)
 */
interface RegistryEntry {
    contract: ArtifactContract;
    source: 'core' | 'plugin' | 'user';
    priority: number;
    registeredAt: number;
}

/**
 * Registry listener callback
 */
export type RegistryListener = (
    event: 'register' | 'unregister' | 'update',
    kind: ArtifactKind,
    contract?: ArtifactContract
) => void;

// ============================================================================
// Artifact Registry Implementation
// ============================================================================

/**
 * Artifact Registry - Singleton
 *
 * Central registry for all artifact contracts (UI components, diagram nodes, etc.)
 */
export class ArtifactRegistry {
    private static instance: ArtifactRegistry | null = null;

    /** Main registry: kind -> version -> entry */
    private registry: Map<ArtifactKind, Map<SemanticVersion, RegistryEntry>> = new Map();

    /** Index by category */
    private categoryIndex: Map<string, Set<ArtifactKind>> = new Map();

    /** Index by tag */
    private tagIndex: Map<string, Set<ArtifactKind>> = new Map();

    /** Index by modality */
    private modalityIndex: Map<ContentModality, Set<ArtifactKind>> = new Map();

    /** Listeners */
    private listeners: Set<RegistryListener> = new Set();

    /** Sealed (no more core registrations) */
    private sealed = false;

    private constructor() { }

    /**
     * Get singleton instance
     */
    static getInstance(): ArtifactRegistry {
        if (!ArtifactRegistry.instance) {
            ArtifactRegistry.instance = new ArtifactRegistry();
        }
        return ArtifactRegistry.instance;
    }

    /**
     * Reset singleton (for testing)
     */
    static resetInstance(): void {
        ArtifactRegistry.instance = null;
    }

    // ============================================================================
    // Registration
    // ============================================================================

    /**
     * Register an artifact contract
     */
    register(
        contract: ArtifactContract,
        options: RegistrationOptions = {}
    ): boolean {
        const { replace = false, source = 'plugin', priority = 0 } = options;
        const kind = contract.identity.kind;
        const version = contract.identity.version;

        // Check if sealed for core registrations
        if (this.sealed && source === 'core') {
            console.warn(`Registry is sealed, cannot register core artifact: ${kind}`);
            return false;
        }

        // Get or create version map
        let versionMap = this.registry.get(kind);
        if (!versionMap) {
            versionMap = new Map();
            this.registry.set(kind, versionMap);
        }

        // Check for existing registration
        const existing = versionMap.get(version);
        if (existing && !replace) {
            console.warn(`Artifact ${kind}@${version} already registered`);
            return false;
        }

        // Create entry
        const entry: RegistryEntry = {
            contract,
            source,
            priority,
            registeredAt: Date.now(),
        };

        versionMap.set(version, entry);

        // Update indexes
        this.indexContract(contract);

        // Notify listeners
        this.notifyListeners(existing ? 'update' : 'register', kind, contract);

        return true;
    }

    /**
     * Register multiple contracts at once
     */
    registerAll(
        contracts: ArtifactContract[],
        options: RegistrationOptions = {}
    ): number {
        let registered = 0;
        for (const contract of contracts) {
            if (this.register(contract, options)) {
                registered++;
            }
        }
        return registered;
    }

    /**
     * Unregister an artifact contract
     */
    unregister(kind: ArtifactKind, version?: SemanticVersion): boolean {
        const versionMap = this.registry.get(kind);
        if (!versionMap) {
            return false;
        }

        if (version) {
            // Remove specific version
            const entry = versionMap.get(version);
            if (!entry) return false;

            versionMap.delete(version);
            this.removeFromIndexes(entry.contract);

            if (versionMap.size === 0) {
                this.registry.delete(kind);
            }

            this.notifyListeners('unregister', kind);
            return true;
        } else {
            // Remove all versions
            for (const [, entry] of versionMap) {
                this.removeFromIndexes(entry.contract);
            }

            this.registry.delete(kind);
            this.notifyListeners('unregister', kind);
            return true;
        }
    }

    /**
     * Seal the registry (no more core registrations)
     */
    seal(): void {
        this.sealed = true;
    }

    // ============================================================================
    // Lookup
    // ============================================================================

    /**
     * Get a contract by kind (latest version)
     */
    get(kind: ArtifactKind): ArtifactContract | undefined {
        const versionMap = this.registry.get(kind);
        if (!versionMap || versionMap.size === 0) {
            return undefined;
        }

        // Find latest version with highest priority
        let best: RegistryEntry | undefined;
        for (const [, entry] of versionMap) {
            if (!best || this.compareVersions(entry, best) > 0) {
                best = entry;
            }
        }

        return best?.contract;
    }

    /**
     * Get a contract by kind and specific version
     */
    getVersion(kind: ArtifactKind, version: SemanticVersion): ArtifactContract | undefined {
        return this.registry.get(kind)?.get(version)?.contract;
    }

    /**
     * Check if a kind is registered
     */
    has(kind: ArtifactKind): boolean {
        return this.registry.has(kind);
    }

    /**
     * Get all versions of a kind
     */
    getVersions(kind: ArtifactKind): SemanticVersion[] {
        const versionMap = this.registry.get(kind);
        return versionMap ? Array.from(versionMap.keys()) : [];
    }

    /**
     * Get all registered kinds
     */
    getAllKinds(): ArtifactKind[] {
        return Array.from(this.registry.keys());
    }

    /**
     * Get all contracts
     */
    getAll(): ArtifactContract[] {
        const contracts: ArtifactContract[] = [];
        for (const [kind] of this.registry) {
            const contract = this.get(kind);
            if (contract) {
                contracts.push(contract);
            }
        }
        return contracts;
    }

    // ============================================================================
    // Query
    // ============================================================================

    /**
     * Query contracts with filters
     */
    query(options: QueryOptions = {}): ArtifactContract[] {
        let candidates: Set<ArtifactKind>;

        // Start with index-based filtering if available
        if (options.category) {
            candidates = new Set(this.categoryIndex.get(options.category) || []);
        } else if (options.modality) {
            candidates = new Set(this.modalityIndex.get(options.modality) || []);
        } else if (options.tags && options.tags.length > 0) {
            // Union of all tag matches
            candidates = new Set();
            for (const tag of options.tags) {
                const tagged = this.tagIndex.get(tag);
                if (tagged) {
                    for (const kind of tagged) {
                        candidates.add(kind);
                    }
                }
            }
        } else {
            candidates = new Set(this.registry.keys());
        }

        // Filter and collect results
        const results: ArtifactContract[] = [];

        for (const kind of candidates) {
            const contract = options.latestOnly
                ? this.get(kind)
                : this.get(kind); // NOTE: handle multiple versions

            if (!contract) continue;

            // Apply additional filters
            if (options.category && contract.identity.category !== options.category) {
                continue;
            }

            if (options.modality && contract.modality !== options.modality) {
                continue;
            }

            if (options.platform && !contract.platforms.includes(options.platform)) {
                continue;
            }

            if (options.tags && options.tags.length > 0) {
                const hasTag = options.tags.some((t) => contract.identity.tags.includes(t));
                if (!hasTag) continue;
            }

            if (options.search) {
                const searchLower = options.search.toLowerCase();
                const nameMatch = contract.identity.name.toLowerCase().includes(searchLower);
                const descMatch = contract.identity.description
                    .toLowerCase()
                    .includes(searchLower);
                if (!nameMatch && !descMatch) continue;
            }

            results.push(contract);

            if (options.limit && results.length >= options.limit) {
                break;
            }
        }

        return results;
    }

    /**
     * Get all categories
     */
    getCategories(): string[] {
        return Array.from(this.categoryIndex.keys());
    }

    /**
     * Get all tags
     */
    getTags(): string[] {
        return Array.from(this.tagIndex.keys());
    }

    /**
     * Get contracts by category
     */
    getByCategory(category: string): ArtifactContract[] {
        return this.query({ category });
    }

    /**
     * Get contracts by modality
     */
    getByModality(modality: ContentModality): ArtifactContract[] {
        return this.query({ modality });
    }

    // ============================================================================
    // Listeners
    // ============================================================================

    /**
     * Subscribe to registry changes
     */
    subscribe(listener: RegistryListener): () => void {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    // ============================================================================
    // Statistics
    // ============================================================================

    /**
     * Get registry statistics
     */
    getStats(): {
        totalKinds: number;
        totalVersions: number;
        categories: number;
        tags: number;
        byModality: Record<ContentModality, number>;
        bySource: Record<string, number>;
    } {
        let totalVersions = 0;
        const bySource: Record<string, number> = { core: 0, plugin: 0, user: 0 };
        const byModality: Record<ContentModality, number> = {
            visual: 0,
            code: 0,
            diagram: 0,
            drawing: 0,
            text: 0,
            mixed: 0,
        };

        for (const [, versionMap] of this.registry) {
            for (const [, entry] of versionMap) {
                totalVersions++;
                bySource[entry.source] = (bySource[entry.source] || 0) + 1;
                byModality[entry.contract.modality] =
                    (byModality[entry.contract.modality] || 0) + 1;
            }
        }

        return {
            totalKinds: this.registry.size,
            totalVersions,
            categories: this.categoryIndex.size,
            tags: this.tagIndex.size,
            byModality,
            bySource,
        };
    }

    // ============================================================================
    // Private Methods
    // ============================================================================

    /**
     * Index a contract
     */
    private indexContract(contract: ArtifactContract): void {
        const kind = contract.identity.kind;

        // Category index
        const category = contract.identity.category;
        if (!this.categoryIndex.has(category)) {
            this.categoryIndex.set(category, new Set());
        }
        this.categoryIndex.get(category)!.add(kind);

        // Tag index
        for (const tag of contract.identity.tags) {
            if (!this.tagIndex.has(tag)) {
                this.tagIndex.set(tag, new Set());
            }
            this.tagIndex.get(tag)!.add(kind);
        }

        // Modality index
        const modality = contract.modality;
        if (!this.modalityIndex.has(modality)) {
            this.modalityIndex.set(modality, new Set());
        }
        this.modalityIndex.get(modality)!.add(kind);
    }

    /**
     * Remove a contract from indexes
     */
    private removeFromIndexes(contract: ArtifactContract): void {
        const kind = contract.identity.kind;

        // Check if other versions still exist
        if (this.registry.has(kind)) {
            return; // Don't remove from index if other versions exist
        }

        // Category index
        this.categoryIndex.get(contract.identity.category)?.delete(kind);

        // Tag index
        for (const tag of contract.identity.tags) {
            this.tagIndex.get(tag)?.delete(kind);
        }

        // Modality index
        this.modalityIndex.get(contract.modality)?.delete(kind);
    }

    /**
     * Compare two entries for version/priority sorting
     */
    private compareVersions(a: RegistryEntry, b: RegistryEntry): number {
        // First compare by priority
        if (a.priority !== b.priority) {
            return a.priority - b.priority;
        }

        // Then by version (semantic versioning)
        const vA = a.contract.identity.version.split('.').map(Number);
        const vB = b.contract.identity.version.split('.').map(Number);

        for (let i = 0; i < 3; i++) {
            if (vA[i] !== vB[i]) {
                return vA[i] - vB[i];
            }
        }

        // Finally by registration time
        return a.registeredAt - b.registeredAt;
    }

    /**
     * Notify listeners
     */
    private notifyListeners(
        event: 'register' | 'unregister' | 'update',
        kind: ArtifactKind,
        contract?: ArtifactContract
    ): void {
        for (const listener of this.listeners) {
            try {
                listener(event, kind, contract);
            } catch (error) {
                console.error('Registry listener error:', error);
            }
        }
    }
}

// ============================================================================
// Convenience Functions
// ============================================================================

/**
 * Get the global artifact registry instance
 */
export function getArtifactRegistry(): ArtifactRegistry {
    return ArtifactRegistry.getInstance();
}

/**
 * Register an artifact contract
 */
export function registerArtifact(
    contract: ArtifactContract,
    options?: RegistrationOptions
): boolean {
    return getArtifactRegistry().register(contract, options);
}

/**
 * Get an artifact contract by kind
 */
export function getArtifact(kind: ArtifactKind): ArtifactContract | undefined {
    return getArtifactRegistry().get(kind);
}

/**
 * Query artifacts
 */
export function queryArtifacts(options?: QueryOptions): ArtifactContract[] {
    return getArtifactRegistry().query(options);
}
