/**
 * Tool Manager
 *
 * Manages canvas tools (select, draw, pan, etc.) and their lifecycle.
 * Implements tool switching, shortcuts, and event routing.
 *
 * @doc.type singleton
 * @doc.purpose Tool management and event routing
 * @doc.layer core
 * @doc.pattern State Machine, Strategy
 */

import type { ContentModality } from '../model/contracts';
import type { ToolExtension, ToolContext, ToolEvent } from '../registry/ExtensionPoints';

// ============================================================================
// Built-in Tool Definitions
// ============================================================================

/**
 * Built-in tool IDs
 */
export const BuiltInTools = {
    SELECT: 'select',
    MOVE: 'move',
    PAN: 'pan',
    ZOOM: 'zoom',
    RECTANGLE: 'rectangle',
    ELLIPSE: 'ellipse',
    LINE: 'line',
    TEXT: 'text',
    PEN: 'pen',
    COMMENT: 'comment',
} as const;

export type BuiltInToolId = (typeof BuiltInTools)[keyof typeof BuiltInTools];

/**
 * Tool state
 */
export interface ToolState {
    /** Active tool ID */
    activeToolId: string;
    /** Previous tool (for temporary switches) */
    previousToolId: string | null;
    /** Whether in temporary switch mode (e.g., holding space for pan) */
    isTemporary: boolean;
    /** Tool-specific state */
    toolData: Record<string, unknown>;
}

/**
 * Tool manager events
 */
export type ToolManagerEvent =
    | { type: 'tool-activated'; toolId: string }
    | { type: 'tool-deactivated'; toolId: string }
    | { type: 'tool-registered'; tool: ToolExtension }
    | { type: 'tool-unregistered'; toolId: string };

/**
 * Tool manager event listener
 */
export type ToolManagerListener = (event: ToolManagerEvent) => void;

// ============================================================================
// Tool Manager Implementation
// ============================================================================

/**
 * Configuration for tool manager
 */
export interface ToolManagerConfig {
    /** Default tool ID */
    defaultTool: string;
    /** Enable keyboard shortcuts */
    enableShortcuts: boolean;
    /** Enable temporary tool switching (e.g., space for pan) */
    enableTemporarySwitch: boolean;
    /** Temporary switch key */
    temporarySwitchKey: string;
    /** Temporary switch tool */
    temporarySwitchTool: string;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: ToolManagerConfig = {
    defaultTool: BuiltInTools.SELECT,
    enableShortcuts: true,
    enableTemporarySwitch: true,
    temporarySwitchKey: ' ',
    temporarySwitchTool: BuiltInTools.PAN,
};

/**
 * Tool Manager - Singleton
 *
 * Manages tool registration, activation, and event routing.
 */
export class ToolManager {
    private static instance: ToolManager | null = null;

    private tools: Map<string, ToolExtension> = new Map();
    private shortcutMap: Map<string, string> = new Map();
    private state: ToolState;
    private config: ToolManagerConfig;
    private listeners: Set<ToolManagerListener> = new Set();
    private context: ToolContext | null = null;

    private constructor(config: Partial<ToolManagerConfig> = {}) {
        this.config = { ...DEFAULT_CONFIG, ...config };
        this.state = {
            activeToolId: this.config.defaultTool,
            previousToolId: null,
            isTemporary: false,
            toolData: {},
        };

        // Register built-in tools
        this.registerBuiltInTools();
    }

    /**
     * Get singleton instance
     */
    static getInstance(config?: Partial<ToolManagerConfig>): ToolManager {
        if (!ToolManager.instance) {
            ToolManager.instance = new ToolManager(config);
        }
        return ToolManager.instance;
    }

    /**
     * Reset singleton (for testing)
     */
    static resetInstance(): void {
        ToolManager.instance = null;
    }

    // ============================================================================
    // Public API
    // ============================================================================

    /**
     * Get current state
     */
    getState(): Readonly<ToolState> {
        return { ...this.state };
    }

    /**
     * Get active tool
     */
    getActiveTool(): ToolExtension | undefined {
        return this.tools.get(this.state.activeToolId);
    }

    /**
     * Get all registered tools
     */
    getAllTools(): ToolExtension[] {
        return Array.from(this.tools.values()).sort((a, b) => a.priority - b.priority);
    }

    /**
     * Get tools by group
     */
    getToolsByGroup(group: ToolExtension['group']): ToolExtension[] {
        return this.getAllTools().filter((t) => t.group === group);
    }

    /**
     * Register a tool
     */
    registerTool(tool: ToolExtension): void {
        this.tools.set(tool.id, tool);

        // Register shortcut
        if (tool.shortcut) {
            this.shortcutMap.set(tool.shortcut.toLowerCase(), tool.id);
        }

        this.emit({ type: 'tool-registered', tool });
    }

    /**
     * Unregister a tool
     */
    unregisterTool(toolId: string): void {
        const tool = this.tools.get(toolId);
        if (!tool) return;

        // Remove shortcut
        if (tool.shortcut) {
            this.shortcutMap.delete(tool.shortcut.toLowerCase());
        }

        this.tools.delete(toolId);
        this.emit({ type: 'tool-unregistered', toolId });

        // If active tool was removed, switch to default
        if (this.state.activeToolId === toolId) {
            this.activateTool(this.config.defaultTool);
        }
    }

    /**
     * Activate a tool
     */
    activateTool(toolId: string, temporary = false): boolean {
        const newTool = this.tools.get(toolId);
        if (!newTool) {
            console.warn(`Tool not found: ${toolId}`);
            return false;
        }

        const currentTool = this.getActiveTool();

        // Deactivate current tool
        if (currentTool && this.context) {
            currentTool.deactivate(this.context);
            this.emit({ type: 'tool-deactivated', toolId: currentTool.id });
        }

        // Update state
        if (temporary) {
            this.state.previousToolId = this.state.activeToolId;
            this.state.isTemporary = true;
        } else {
            this.state.previousToolId = null;
            this.state.isTemporary = false;
        }
        this.state.activeToolId = toolId;

        // Activate new tool
        if (this.context) {
            newTool.activate(this.context);
        }
        this.emit({ type: 'tool-activated', toolId });

        return true;
    }

    /**
     * Restore previous tool (after temporary switch)
     */
    restorePreviousTool(): void {
        if (!this.state.isTemporary || !this.state.previousToolId) return;

        this.activateTool(this.state.previousToolId, false);
        this.state.isTemporary = false;
        this.state.previousToolId = null;
    }

    /**
     * Set tool context
     */
    setContext(context: ToolContext): void {
        this.context = context;
    }

    /**
     * Handle canvas event
     */
    handleEvent(event: ToolEvent): void {
        const tool = this.getActiveTool();
        if (!tool || !this.context) return;

        // Handle temporary switch
        if (this.config.enableTemporarySwitch && event.type === 'keyDown') {
            if (event.key === this.config.temporarySwitchKey && !this.state.isTemporary) {
                this.activateTool(this.config.temporarySwitchTool, true);
                return;
            }
        }

        if (this.config.enableTemporarySwitch && event.type === 'keyUp') {
            if (event.key === this.config.temporarySwitchKey && this.state.isTemporary) {
                this.restorePreviousTool();
                return;
            }
        }

        // Handle shortcuts
        if (this.config.enableShortcuts && event.type === 'keyDown' && event.key) {
            const shortcut = this.buildShortcutString(event);
            const toolId = this.shortcutMap.get(shortcut);
            if (toolId) {
                this.activateTool(toolId);
                return;
            }
        }

        // Route to active tool
        tool.handleEvent(event, this.context);
    }

    /**
     * Subscribe to tool manager events
     */
    subscribe(listener: ToolManagerListener): () => void {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    /**
     * Get cursor for current tool
     */
    getCursor(): string {
        return this.getActiveTool()?.cursor ?? 'default';
    }

    /**
     * Update configuration
     */
    updateConfig(config: Partial<ToolManagerConfig>): void {
        this.config = { ...this.config, ...config };
    }

    // ============================================================================
    // Private Methods
    // ============================================================================

    /**
     * Register built-in tools
     */
    private registerBuiltInTools(): void {
        // Select Tool
        this.registerTool({
            id: BuiltInTools.SELECT,
            name: 'Select',
            icon: 'cursor',
            shortcut: 'v',
            group: 'select',
            cursor: 'default',
            modalities: '*',
            priority: 0,
            activate: () => { },
            deactivate: () => { },
            handleEvent: () => { },
        });

        // Move Tool
        this.registerTool({
            id: BuiltInTools.MOVE,
            name: 'Move',
            icon: 'move',
            shortcut: 'm',
            group: 'select',
            cursor: 'move',
            modalities: '*',
            priority: 1,
            activate: () => { },
            deactivate: () => { },
            handleEvent: () => { },
        });

        // Pan Tool
        this.registerTool({
            id: BuiltInTools.PAN,
            name: 'Pan',
            icon: 'hand',
            shortcut: 'h',
            group: 'navigate',
            cursor: 'grab',
            modalities: '*',
            priority: 10,
            activate: () => { },
            deactivate: () => { },
            handleEvent: () => { },
        });

        // Zoom Tool
        this.registerTool({
            id: BuiltInTools.ZOOM,
            name: 'Zoom',
            icon: 'zoom-in',
            shortcut: 'z',
            group: 'navigate',
            cursor: 'zoom-in',
            modalities: '*',
            priority: 11,
            activate: () => { },
            deactivate: () => { },
            handleEvent: () => { },
        });

        // Rectangle Tool
        this.registerTool({
            id: BuiltInTools.RECTANGLE,
            name: 'Rectangle',
            icon: 'square',
            shortcut: 'r',
            group: 'draw',
            cursor: 'crosshair',
            modalities: ['visual', 'diagram', 'drawing'],
            priority: 20,
            activate: () => { },
            deactivate: () => { },
            handleEvent: () => { },
        });

        // Ellipse Tool
        this.registerTool({
            id: BuiltInTools.ELLIPSE,
            name: 'Ellipse',
            icon: 'circle',
            shortcut: 'o',
            group: 'draw',
            cursor: 'crosshair',
            modalities: ['visual', 'diagram', 'drawing'],
            priority: 21,
            activate: () => { },
            deactivate: () => { },
            handleEvent: () => { },
        });

        // Line Tool
        this.registerTool({
            id: BuiltInTools.LINE,
            name: 'Line',
            icon: 'minus',
            shortcut: 'l',
            group: 'draw',
            cursor: 'crosshair',
            modalities: ['diagram', 'drawing'],
            priority: 22,
            activate: () => { },
            deactivate: () => { },
            handleEvent: () => { },
        });

        // Text Tool
        this.registerTool({
            id: BuiltInTools.TEXT,
            name: 'Text',
            icon: 'type',
            shortcut: 't',
            group: 'draw',
            cursor: 'text',
            modalities: '*',
            priority: 23,
            activate: () => { },
            deactivate: () => { },
            handleEvent: () => { },
        });

        // Pen Tool
        this.registerTool({
            id: BuiltInTools.PEN,
            name: 'Pen',
            icon: 'pen-tool',
            shortcut: 'p',
            group: 'draw',
            cursor: 'crosshair',
            modalities: ['drawing'],
            priority: 24,
            activate: () => { },
            deactivate: () => { },
            handleEvent: () => { },
        });

        // Comment Tool
        this.registerTool({
            id: BuiltInTools.COMMENT,
            name: 'Comment',
            icon: 'message-square',
            shortcut: 'c',
            group: 'annotate',
            cursor: 'crosshair',
            modalities: '*',
            priority: 30,
            activate: () => { },
            deactivate: () => { },
            handleEvent: () => { },
        });
    }

    /**
     * Build shortcut string from event
     */
    private buildShortcutString(event: ToolEvent): string {
        if (!event.key) return '';

        const parts: string[] = [];

        if (event.modifiers.ctrl || event.modifiers.meta) parts.push('cmd');
        if (event.modifiers.alt) parts.push('alt');
        if (event.modifiers.shift) parts.push('shift');

        parts.push(event.key.toLowerCase());

        return parts.join('+');
    }

    /**
     * Emit event to listeners
     */
    private emit(event: ToolManagerEvent): void {
        for (const listener of this.listeners) {
            try {
                listener(event);
            } catch (error) {
                console.error('Tool manager listener error:', error);
            }
        }
    }
}

// ============================================================================
// Convenience Functions
// ============================================================================

/**
 * Get the global tool manager
 */
export function getToolManager(config?: Partial<ToolManagerConfig>): ToolManager {
    return ToolManager.getInstance(config);
}

/**
 * Get the active tool
 */
export function getActiveTool(): ToolExtension | undefined {
    return getToolManager().getActiveTool();
}

/**
 * Activate a tool by ID
 */
export function activateTool(toolId: string): boolean {
    return getToolManager().activateTool(toolId);
}
