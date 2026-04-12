/**
 * @fileoverview In-memory registry store for design system entities.
 */

import type { ComponentContract } from '@ghatana/ds-schema';
import type { DTCGTokenFile } from '@ghatana/ds-schema';

// ============================================================================
// Registry Entry Types
// ============================================================================

export interface ComponentEntry {
  readonly id: string;
  readonly contract: ComponentContract;
  readonly hash: string;
  readonly registeredAt: string;
  readonly updatedAt: string;
  readonly source: string; // package name/path
  readonly version: string;
}

export interface TokenSetEntry {
  readonly id: string;
  readonly name: string;
  readonly tokens: DTCGTokenFile;
  readonly registeredAt: string;
  readonly updatedAt: string;
  readonly source: string;
  readonly version: string;
}

export interface ThemeEntry {
  readonly id: string;
  readonly name: string;
  readonly tokenSetIds: readonly string[];
  readonly overrides: Record<string, unknown>;
  readonly registeredAt: string;
  readonly updatedAt: string;
}

export interface PatternEntry {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly componentIds: readonly string[];
  readonly category: string;
  readonly registeredAt: string;
}

// ============================================================================
// Registry Store Interface
// ============================================================================

export interface RegistryStore {
  // Components
  registerComponent(entry: Omit<ComponentEntry, 'registeredAt' | 'updatedAt'>): ComponentEntry;
  getComponent(id: string): ComponentEntry | undefined;
  getComponentByName(name: string): ComponentEntry | undefined;
  getAllComponents(): readonly ComponentEntry[];
  updateComponent(id: string, contract: ComponentContract, hash: string): ComponentEntry | undefined;
  unregisterComponent(id: string): boolean;

  // Token Sets
  registerTokenSet(entry: Omit<TokenSetEntry, 'registeredAt' | 'updatedAt'>): TokenSetEntry;
  getTokenSet(id: string): TokenSetEntry | undefined;
  getAllTokenSets(): readonly TokenSetEntry[];
  updateTokenSet(id: string, tokens: DTCGTokenFile): TokenSetEntry | undefined;
  unregisterTokenSet(id: string): boolean;

  // Themes
  registerTheme(entry: Omit<ThemeEntry, 'registeredAt' | 'updatedAt'>): ThemeEntry;
  getTheme(id: string): ThemeEntry | undefined;
  getAllThemes(): readonly ThemeEntry[];
  unregisterTheme(id: string): boolean;

  // Patterns
  registerPattern(entry: Omit<PatternEntry, 'registeredAt'>): PatternEntry;
  getPattern(id: string): PatternEntry | undefined;
  getAllPatterns(): readonly PatternEntry[];
  unregisterPattern(id: string): boolean;

  // Queries
  findComponentsByCategory(category: string): readonly ComponentEntry[];
  findComponentsByTag(tag: string): readonly ComponentEntry[];
  findPatternsByComponent(componentId: string): readonly PatternEntry[];
}

// ============================================================================
// In-Memory Implementation
// ============================================================================

class InMemoryRegistryStore implements RegistryStore {
  private components = new Map<string, ComponentEntry>();
  private tokenSets = new Map<string, TokenSetEntry>();
  private themes = new Map<string, ThemeEntry>();
  private patterns = new Map<string, PatternEntry>();

  // Components
  registerComponent(entry: Omit<ComponentEntry, 'registeredAt' | 'updatedAt'>): ComponentEntry {
    const now = new Date().toISOString();
    const fullEntry: ComponentEntry = {
      ...entry,
      registeredAt: now,
      updatedAt: now,
    };
    this.components.set(entry.id, fullEntry);
    return fullEntry;
  }

  getComponent(id: string): ComponentEntry | undefined {
    return this.components.get(id);
  }

  getComponentByName(name: string): ComponentEntry | undefined {
    for (const entry of this.components.values()) {
      if (entry.contract.name === name) {
        return entry;
      }
    }
    return undefined;
  }

  getAllComponents(): readonly ComponentEntry[] {
    return Array.from(this.components.values());
  }

  updateComponent(id: string, contract: ComponentContract, hash: string): ComponentEntry | undefined {
    const existing = this.components.get(id);
    if (!existing) return undefined;

    const updated: ComponentEntry = {
      ...existing,
      contract,
      hash,
      updatedAt: new Date().toISOString(),
    };
    this.components.set(id, updated);
    return updated;
  }

  unregisterComponent(id: string): boolean {
    return this.components.delete(id);
  }

  // Token Sets
  registerTokenSet(entry: Omit<TokenSetEntry, 'registeredAt' | 'updatedAt'>): TokenSetEntry {
    const now = new Date().toISOString();
    const fullEntry: TokenSetEntry = {
      ...entry,
      registeredAt: now,
      updatedAt: now,
    };
    this.tokenSets.set(entry.id, fullEntry);
    return fullEntry;
  }

  getTokenSet(id: string): TokenSetEntry | undefined {
    return this.tokenSets.get(id);
  }

  getAllTokenSets(): readonly TokenSetEntry[] {
    return Array.from(this.tokenSets.values());
  }

  updateTokenSet(id: string, tokens: DTCGTokenFile): TokenSetEntry | undefined {
    const existing = this.tokenSets.get(id);
    if (!existing) return undefined;

    const updated: TokenSetEntry = {
      ...existing,
      tokens,
      updatedAt: new Date().toISOString(),
    };
    this.tokenSets.set(id, updated);
    return updated;
  }

  unregisterTokenSet(id: string): boolean {
    return this.tokenSets.delete(id);
  }

  // Themes
  registerTheme(entry: Omit<ThemeEntry, 'registeredAt' | 'updatedAt'>): ThemeEntry {
    const now = new Date().toISOString();
    const fullEntry: ThemeEntry = {
      ...entry,
      registeredAt: now,
      updatedAt: now,
    };
    this.themes.set(entry.id, fullEntry);
    return fullEntry;
  }

  getTheme(id: string): ThemeEntry | undefined {
    return this.themes.get(id);
  }

  getAllThemes(): readonly ThemeEntry[] {
    return Array.from(this.themes.values());
  }

  unregisterTheme(id: string): boolean {
    return this.themes.delete(id);
  }

  // Patterns
  registerPattern(entry: Omit<PatternEntry, 'registeredAt'>): PatternEntry {
    const fullEntry: PatternEntry = {
      ...entry,
      registeredAt: new Date().toISOString(),
    };
    this.patterns.set(entry.id, fullEntry);
    return fullEntry;
  }

  getPattern(id: string): PatternEntry | undefined {
    return this.patterns.get(id);
  }

  getAllPatterns(): readonly PatternEntry[] {
    return Array.from(this.patterns.values());
  }

  unregisterPattern(id: string): boolean {
    return this.patterns.delete(id);
  }

  // Queries
  findComponentsByCategory(category: string): readonly ComponentEntry[] {
    return this.getAllComponents().filter(
      (entry) => entry.contract.metadata.category === category
    );
  }

  findComponentsByTag(tag: string): readonly ComponentEntry[] {
    return this.getAllComponents().filter(
      (entry) => entry.contract.metadata.tags?.includes(tag) ?? false
    );
  }

  findPatternsByComponent(componentId: string): readonly PatternEntry[] {
    return this.getAllPatterns().filter(
      (entry) => entry.componentIds.includes(componentId)
    );
  }
}

// ============================================================================
// Singleton Instance
// ============================================================================

let globalStore: RegistryStore | null = null;

export function getRegistryStore(): RegistryStore {
  if (!globalStore) {
    globalStore = new InMemoryRegistryStore();
  }
  return globalStore;
}

export function createRegistryStore(): RegistryStore {
  return new InMemoryRegistryStore();
}

export function resetRegistryStore(): void {
  globalStore = null;
}
