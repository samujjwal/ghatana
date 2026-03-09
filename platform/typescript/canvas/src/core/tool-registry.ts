/**
 * Tool Registry - Pluggable Tool System
 * 
 * @doc.type class
 * @doc.purpose Registry for pluggable canvas tools with activation, options, and cursor management
 * @doc.layer core
 * @doc.pattern Registry
 * 
 * Provides AFFiNE-style tool extensibility where new tools can be registered
 * dynamically and configured per-instance.
 */

import { BaseTool, ToolManager } from "../tools/index.js";
import type { ToolOptions } from "../types/index.js";

/**
 * Tool constructor type
 */
export type ToolConstructor<T extends BaseTool = BaseTool> = new (
    options: ToolOptions
) => T;

/**
 * Tool registration entry
 */
export interface ToolRegistration<T extends BaseTool = BaseTool> {
    /** Tool identifier */
    name: string;
    /** Tool constructor */
    ctor: ToolConstructor<T>;
    /** Default options */
    defaultOptions: ToolOptions;
    /** Tool category for grouping in UI */
    category?: 'select' | 'draw' | 'shape' | 'text' | 'connector' | 'other';
    /** Keyboard shortcut */
    shortcut?: string;
    /** Tool icon (for UI) */
    icon?: string;
    /** Tool label (for UI) */
    label?: string;
    /** Tool description */
    description?: string;
}

/**
 * Tool state change event
 */
export interface ToolStateChange {
    previousTool: string | null;
    currentTool: string;
    options: ToolOptions;
}

/**
 * Tool state listener
 */
export type ToolStateListener = (state: ToolStateChange) => void;

/**
 * Tool Registry
 * 
 * Central registry for all canvas tools. Provides:
 * - Tool registration/unregistration
 * - Tool instance creation
 * - Keyboard shortcut management
 * - Tool state tracking
 */
export class ToolRegistry {
    private static instance: ToolRegistry;

    private registrations = new Map<string, ToolRegistration>();
    private shortcuts = new Map<string, string>(); // shortcut -> tool name
    private stateListeners: ToolStateListener[] = [];
    private currentTool: string | null = null;

    private constructor() { }

    static getInstance(): ToolRegistry {
        if (!ToolRegistry.instance) {
            ToolRegistry.instance = new ToolRegistry();
        }
        return ToolRegistry.instance;
    }

    /**
     * Register a new tool
     */
    register<T extends BaseTool>(registration: ToolRegistration<T>): void {
        const { name, shortcut } = registration;

        this.registrations.set(name, registration as ToolRegistration);

        if (shortcut) {
            this.shortcuts.set(shortcut.toLowerCase(), name);
        }
    }

    /**
     * Unregister a tool
     */
    unregister(name: string): void {
        const reg = this.registrations.get(name);
        if (reg?.shortcut) {
            this.shortcuts.delete(reg.shortcut.toLowerCase());
        }
        this.registrations.delete(name);
    }

    /**
     * Get tool registration
     */
    getRegistration<T extends BaseTool>(name: string): ToolRegistration<T> | undefined {
        return this.registrations.get(name) as ToolRegistration<T> | undefined;
    }

    /**
     * Get all registered tools
     */
    getRegisteredTools(): string[] {
        return Array.from(this.registrations.keys());
    }

    /**
     * Get tools by category
     */
    getToolsByCategory(category: ToolRegistration['category']): ToolRegistration[] {
        return Array.from(this.registrations.values())
            .filter(reg => reg.category === category);
    }

    /**
     * Create tool instance
     */
    createTool<T extends BaseTool>(
        name: string,
        optionsOverride?: Partial<ToolOptions>
    ): T | null {
        const reg = this.registrations.get(name);
        if (!reg) {
            console.warn(`[ToolRegistry] Unknown tool: ${name}`);
            return null;
        }

        const options = { ...reg.defaultOptions, ...optionsOverride };
        return new reg.ctor(options) as T;
    }

    /**
     * Get tool name by keyboard shortcut
     */
    getToolByShortcut(shortcut: string): string | undefined {
        return this.shortcuts.get(shortcut.toLowerCase());
    }

    /**
     * Check if tool is registered
     */
    hasTool(name: string): boolean {
        return this.registrations.has(name);
    }

    /**
     * Add tool state listener
     */
    addStateListener(listener: ToolStateListener): void {
        this.stateListeners.push(listener);
    }

    /**
     * Remove tool state listener
     */
    removeStateListener(listener: ToolStateListener): void {
        const index = this.stateListeners.indexOf(listener);
        if (index !== -1) {
            this.stateListeners.splice(index, 1);
        }
    }

    /**
     * Notify tool state change
     */
    notifyStateChange(previousTool: string | null, currentTool: string, options: ToolOptions): void {
        this.currentTool = currentTool;
        const state: ToolStateChange = { previousTool, currentTool, options };
        this.stateListeners.forEach(listener => listener(state));
    }

    /**
     * Get current tool name
     */
    getCurrentTool(): string | null {
        return this.currentTool;
    }

    /**
     * Configure a ToolManager instance with all registered tools
     */
    configureToolManager(toolManager: ToolManager): void {
        for (const [name, reg] of this.registrations) {
            const tool = new reg.ctor(reg.defaultOptions);
            toolManager.registerTool(name, tool);
        }
    }
}

// Export singleton instance
export const toolRegistry = ToolRegistry.getInstance();
