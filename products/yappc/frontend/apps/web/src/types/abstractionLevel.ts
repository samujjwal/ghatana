/**
 * @doc.type type
 * @doc.purpose Define abstraction levels for canvas navigation
 * @doc.layer product
 * @doc.pattern ValueObject
 */

// Abstraction levels from high-level (system) to low-level (code)
export type AbstractionLevel = 'system' | 'component' | 'file' | 'code';

/**
 * Constants for abstraction levels reflecting the UX design spec (L1-L3)
 */
export const AbstractionLevel = {
    MACRO: 'system' as AbstractionLevel,
    MESO: 'component' as AbstractionLevel,
    MICRO: 'file' as AbstractionLevel,
    NANO: 'code' as AbstractionLevel
} as const;

// Level metadata for UI display
export interface AbstractionLevelInfo {
    level: AbstractionLevel;
    label: string;
    description: string;
    icon: string;
    shortcut: string;
    order: number;
}

// Level-specific context data
export interface SystemLevelContext {
    services: string[];
    databases: string[];
    externalApis: string[];
    infrastructure: string[];
}

export interface ComponentLevelContext {
    components: string[];
    modules: string[];
    packages: string[];
    dependencies: string[];
}

export interface FileLevelContext {
    files: string[];
    currentFile?: string;
    imports: string[];
    exports: string[];
}

export interface CodeLevelContext {
    functions: string[];
    classes: string[];
    variables: string[];
    currentSymbol?: string;
}

export type LevelContext =
    | { level: 'system'; data: SystemLevelContext }
    | { level: 'component'; data: ComponentLevelContext }
    | { level: 'file'; data: FileLevelContext }
    | { level: 'code'; data: CodeLevelContext };

// Breadcrumb for navigation history
export interface AbstractionBreadcrumb {
    id: string; // Added to match usage in CanvasWorkspace
    level: AbstractionLevel;
    label: string;
    context?: Record<string, unknown>;
}

// Navigation state
export interface AbstractionNavigationState {
    currentLevel: AbstractionLevel;
    breadcrumbs: AbstractionBreadcrumb[];
    history: AbstractionLevel[];
    selectedContext?: LevelContext;
}

// Level metadata registry
export const ABSTRACTION_LEVELS: AbstractionLevelInfo[] = [
    {
        level: 'system',
        label: 'System',
        description: 'High-level system architecture view',
        icon: '🏗️',
        shortcut: 'Alt+1',
        order: 1,
    },
    {
        level: 'component',
        label: 'Component',
        description: 'Component and module relationships',
        icon: '📦',
        shortcut: 'Alt+2',
        order: 2,
    },
    {
        level: 'file',
        label: 'File',
        description: 'File-level structure and imports',
        icon: '📄',
        shortcut: 'Alt+3',
        order: 3,
    },
    {
        level: 'code',
        label: 'Code',
        description: 'Code-level details and symbols',
        icon: '⚡',
        shortcut: 'Alt+4',
        order: 4,
    },
];

// Helper to get level info
export function getLevelInfo(level: AbstractionLevel): AbstractionLevelInfo {
    const info = ABSTRACTION_LEVELS.find(l => l.level === level);
    if (!info) throw new Error(`Unknown abstraction level: ${level}`);
    return info;
}

// Helper to get next level (drill down)
export function getNextLevel(current: AbstractionLevel): AbstractionLevel | null {
    const currentOrder = getLevelInfo(current).order;
    const next = ABSTRACTION_LEVELS.find(l => l.order === currentOrder + 1);
    return next?.level ?? null;
}

// Helper to get previous level (zoom out)
export function getPreviousLevel(current: AbstractionLevel): AbstractionLevel | null {
    const currentOrder = getLevelInfo(current).order;
    const prev = ABSTRACTION_LEVELS.find(l => l.order === currentOrder - 1);
    return prev?.level ?? null;
}

// Check if can drill down
export function canDrillDown(level: AbstractionLevel): boolean {
    return getNextLevel(level) !== null;
}

// Check if can zoom out
export function canZoomOut(level: AbstractionLevel): boolean {
    return getPreviousLevel(level) !== null;
}

// Default context for each level
export const DEFAULT_LEVEL_CONTEXT: Record<AbstractionLevel, LevelContext['data']> = {
    system: {
        services: [],
        databases: [],
        externalApis: [],
        infrastructure: [],
    } as SystemLevelContext,
    component: {
        components: [],
        modules: [],
        packages: [],
        dependencies: [],
    } as ComponentLevelContext,
    file: {
        files: [],
        imports: [],
        exports: [],
    } as FileLevelContext,
    code: {
        functions: [],
        classes: [],
        variables: [],
    } as CodeLevelContext,
};
