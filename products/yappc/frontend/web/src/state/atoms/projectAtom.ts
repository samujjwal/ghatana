import { atom } from 'jotai';

/**
 *
 */
export interface ProjectConfig {
  name: string;
  description?: string;
  version: string;
  framework: 'react' | 'vue' | 'angular' | 'svelte' | 'vanilla';
  language: 'typescript' | 'javascript';
  buildTool: 'vite' | 'webpack' | 'rollup' | 'parcel';
  packageManager: 'npm' | 'yarn' | 'pnpm';
  features: {
    pwa: boolean;
    i18n: boolean;
    ssr: boolean;
    testing: boolean;
    linting: boolean;
    formatting: boolean;
  };
}

/**
 *
 */
export interface ProjectMetadata {
  id: string;
  workspaceId: string;
  createdAt: Date;
  updatedAt: Date;
  createdBy: string;
  lastModifiedBy: string;
  tags: string[];
  isArchived: boolean;
  visibility: 'private' | 'team' | 'organization' | 'public';
}

/**
 *
 */
export interface ProjectSettings {
  autoSave: boolean;
  autoFormat: boolean;
  liveReload: boolean;
  hotReload: boolean;
  sourceMap: boolean;
  minification: boolean;
  treeshaking: boolean;
  codesplitting: boolean;
}

/**
 *
 */
export interface ProjectState {
  metadata: ProjectMetadata | null;
  config: ProjectConfig | null;
  settings: ProjectSettings;
  isLoading: boolean;
  isDirty: boolean;
  lastSavedAt?: Date;
  error?: string;
}

export const projectAtom = atom<ProjectState>({
  metadata: null,
  config: null,
  settings: {
    autoSave: true,
    autoFormat: true,
    liveReload: true,
    hotReload: true,
    sourceMap: true,
    minification: false,
    treeshaking: true,
    codesplitting: true,
  },
  isLoading: false,
  isDirty: false,
});

// Derived atoms
export const isProjectLoadedAtom = atom((get) => {
  const project = get(projectAtom);
  return !!(project.metadata && project.config);
});

export const projectNameAtom = atom((get) => {
  const project = get(projectAtom);
  return project.config?.name || 'Untitled Project';
});

export const projectFrameworkAtom = atom((get) => {
  const project = get(projectAtom);
  return project.config?.framework;
});

// Actions
export const loadProjectAtom = atom(
  null,
  (get, set, metadata: ProjectMetadata, config: ProjectConfig) => {
    set(projectAtom, {
      ...get(projectAtom),
      metadata,
      config,
      isLoading: false,
      isDirty: false,
      error: undefined,
    });
  }
);

export const updateProjectConfigAtom = atom(
  null,
  (get, set, updates: Partial<ProjectConfig>) => {
    const current = get(projectAtom);
    if (!current.config) return;

    set(projectAtom, {
      ...current,
      config: { ...current.config, ...updates },
      isDirty: true,
      error: undefined,
    });
  }
);

export const updateProjectSettingsAtom = atom(
  null,
  (get, set, updates: Partial<ProjectSettings>) => {
    const current = get(projectAtom);
    set(projectAtom, {
      ...current,
      settings: { ...current.settings, ...updates },
      isDirty: true,
    });
  }
);

export const saveProjectAtom = atom(null, (get, set) => {
  const current = get(projectAtom);
  set(projectAtom, {
    ...current,
    isDirty: false,
    lastSavedAt: new Date(),
    metadata: current.metadata
      ? {
          ...current.metadata,
          updatedAt: new Date(),
        }
      : null,
  });
});

export const setProjectLoadingAtom = atom(
  null,
  (get, set, isLoading: boolean) => {
    set(projectAtom, {
      ...get(projectAtom),
      isLoading,
    });
  }
);

export const setProjectErrorAtom = atom(null, (get, set, error?: string) => {
  set(projectAtom, {
    ...get(projectAtom),
    error,
    isLoading: false,
  });
});
