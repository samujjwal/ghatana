/**
 * Generic Canvas Chrome
 * 
 * Application-agnostic canvas chrome components.
 * Uses configuration system for layers, phases, and roles.
 * 
 * @doc.type core
 * @doc.purpose Generic canvas chrome
 * @doc.layer presentation
 */

import React, { ReactNode } from 'react';
import { atom } from 'jotai';
import { getCanvasConfig } from './canvas-config';

/**
 * Generic chrome state atoms
 */
export const genericChromeCalmModeAtom = atom<boolean>(false);
export const genericChromeLeftRailVisibleAtom = atom<boolean>(true);
export const genericChromeLeftPanelAtom = atom<string | null>(null);
export const genericChromeInspectorVisibleAtom = atom<boolean>(false);
export const genericChromeMinimapVisibleAtom = atom<boolean>(false);
export const genericChromeCurrentPhaseAtom = atom<string>('');
export const genericChromeZoomLevelAtom = atom<number>(1.0);
export const genericChromeActiveLayersAtom = atom<number>(3);
export const genericChromeCollaboratorsAtom = atom<Array<{ id: string; name: string; color: string }>>([
    { id: '1', name: 'You', color: '#1976d2' }
]);
export const genericChromeSemanticLayerAtom = atom<string>('');
export const genericChromeActiveRolesAtom = atom<string[]>([]);
export const genericChromeAvailableActionsAtom = atom<unknown[]>([]);

/**
 * Generic action interface
 */
export interface GenericAction {
    id: string;
    label: string;
    icon: string;
    shortcut?: string;
    category: string;
    handler: () => void;
}

/**
 * Generic Z-index hierarchy
 */
export const GENERIC_Z_INDEX = {
    BACKGROUND: 0,
    GRID: 1,
    FRAMES: 10,
    EDGES: 15,
    ELEMENTS: 20,
    ANNOTATIONS: 30,
    SELECTION: 40,
    PORTALS: 50,
    CONTEXT_BAR: 100,
    LEFT_RAIL: 200,
    LEFT_PANE: 210,
    INSPECTOR: 220,
    MINIMAP: 230,
    ZOOM_HUD: 240,
    COMMAND_PALETTE: 1000,
    MODAL: 1100,
    TOAST: 1200,
    TOOLTIP: 1300,
} as const;

/**
 * Get phase colors from configuration
 */
export function getPhaseColors(phase: string): { primary: string; background: string; text: string } {
    const config = getCanvasConfig();
    const phaseConfig = config.phases[phase];
    return phaseConfig ? phaseConfig.color : {
        primary: '#1976d2',
        background: '#e3f2fd',
        text: '#0d47a1',
    };
}

/**
 * Get role configuration
 */
export function getRoleConfig(role: string): { displayName: string; icon: string; color: string } {
    const config = getCanvasConfig();
    const roleConfig = config.roles[role];
    return roleConfig ? {
        displayName: roleConfig.displayName,
        icon: roleConfig.icon,
        color: roleConfig.color,
    } : {
        displayName: role,
        icon: '👤',
        color: '#1976d2',
    };
}

/**
 * Get all available phases
 */
export function getAvailablePhases(): string[] {
    const config = getCanvasConfig();
    return Object.keys(config.phases);
}

/**
 * Get all available roles
 */
export function getAvailableRoles(): string[] {
    const config = getCanvasConfig();
    return Object.keys(config.roles);
}

/**
 * Get all available layers
 */
export function getAvailableLayers(): string[] {
    const config = getCanvasConfig();
    return Object.keys(config.layers);
}
