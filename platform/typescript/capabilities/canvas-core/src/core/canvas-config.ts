/**
 * Canvas Configuration System
 * 
 * Generic configuration interface for canvas behavior.
 * Allows applications to customize layers, phases, roles, and actions.
 * 
 * @doc.type core
 * @doc.purpose Pluggable canvas configuration
 * @doc.layer core
 */

/**
 * Generic layer configuration
 */
export interface LayerConfig<TLayer extends string = string> {
    name: TLayer;
    zoomRange: [number, number];
    description: string;
    primaryFocus: string;
}

/**
 * Generic phase configuration
 */
export interface PhaseConfig<TPhase extends string = string> {
    name: TPhase;
    displayName: string;
    color: {
        primary: string;
        background: string;
        text: string;
    };
    description: string;
}

/**
 * Generic role configuration
 */
export interface RoleConfig<TRole extends string = string> {
    name: TRole;
    displayName: string;
    icon: string;
    color: string;
    description: string;
}

/**
 * Generic action definition
 */
export interface GenericActionDefinition<TContext = any> {
    id: string;
    label: string;
    icon: string;
    shortcut?: string;
    category: 'layer' | 'phase' | 'role' | 'universal' | 'selection';
    description?: string;
    handler: (context: TContext) => void | Promise<void>;
    isEnabled?: (context: TContext) => boolean;
    isVisible?: (context: TContext) => boolean;
    priority?: number;
}

/**
 * Canvas configuration interface
 */
export interface CanvasConfig<
    TLayer extends string = string,
    TPhase extends string = string,
    TRole extends string = string,
    TContext = any
> {
    /** Application name */
    appName: string;

    /** Layer configurations */
    layers: Record<TLayer, LayerConfig<TLayer>>;

    /** Phase configurations */
    phases: Record<TPhase, PhaseConfig<TPhase>>;

    /** Role configurations */
    roles: Record<TRole, RoleConfig<TRole>>;

    /** Layer-specific actions */
    layerActions: Record<TLayer, GenericActionDefinition<TContext>[]>;

    /** Phase-specific actions */
    phaseActions: Record<TPhase, GenericActionDefinition<TContext>[]>;

    /** Role-specific actions */
    roleActions: Record<TRole, GenericActionDefinition<TContext>[]>;

    /** Universal actions */
    universalActions: GenericActionDefinition<TContext>[];

    /** Default layer */
    defaultLayer: TLayer;

    /** Default phase */
    defaultPhase: TPhase;

    /** Default roles */
    defaultRoles: TRole[];

    /** Layer detection function */
    getLayerFromZoom: (zoom: number) => TLayer;
}

/**
 * Canvas configuration registry
 */
class CanvasConfigRegistry {
    private config: CanvasConfig | null = null;

    setConfig(config: CanvasConfig): void {
        this.config = config;
        console.log(`✅ Canvas configured for: ${config.appName}`);
    }

    getConfig(): CanvasConfig {
        if (!this.config) {
            throw new Error('Canvas not configured. Call setCanvasConfig() first.');
        }
        return this.config;
    }

    hasConfig(): boolean {
        return this.config !== null;
    }

    clearConfig(): void {
        this.config = null;
    }
}

/**
 * Global configuration registry
 */
const configRegistry = new CanvasConfigRegistry();

/**
 * Set canvas configuration
 */
export function setCanvasConfig(config: CanvasConfig): void {
    configRegistry.setConfig(config);
}

/**
 * Get canvas configuration
 */
export function getCanvasConfig(): CanvasConfig {
    return configRegistry.getConfig();
}

/**
 * Check if canvas is configured
 */
export function hasCanvasConfig(): boolean {
    return configRegistry.hasConfig();
}

/**
 * Clear canvas configuration (useful for testing)
 */
export function clearCanvasConfig(): void {
    configRegistry.clearConfig();
}
