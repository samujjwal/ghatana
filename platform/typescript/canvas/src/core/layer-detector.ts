/**
 * Layer Detection System
 * 
 * Automatically detects semantic layer based on zoom level.
 * Provides smooth transitions between layers with proper state management.
 * 
 * @doc.type core
 * @doc.purpose Semantic layer detection and management
 * @doc.layer core
 * @doc.pattern Observer + Strategy
 */

import { SemanticLayer } from '../chrome';

export interface LayerConfig {
    name: SemanticLayer;
    zoomRange: [number, number];
    description: string;
    primaryFocus: string;
}

/**
 * Layer configurations with zoom ranges
 */
export const LAYER_CONFIGS: Record<SemanticLayer, LayerConfig> = {
    architecture: {
        name: 'architecture',
        zoomRange: [0.1, 0.5],
        description: 'System design, high-level flows',
        primaryFocus: 'Services, databases, system boundaries',
    },
    design: {
        name: 'design',
        zoomRange: [0.5, 1.0],
        description: 'Component design, wireframes',
        primaryFocus: 'UI components, screens, user flows',
    },
    component: {
        name: 'component',
        zoomRange: [1.0, 2.0],
        description: 'Detailed components, interactions',
        primaryFocus: 'Component details, state, events',
    },
    implementation: {
        name: 'implementation',
        zoomRange: [2.0, 5.0],
        description: 'Code, logic, data structures',
        primaryFocus: 'Code blocks, functions, classes',
    },
    detail: {
        name: 'detail',
        zoomRange: [5.0, Infinity],
        description: 'Line-by-line code, debugging',
        primaryFocus: 'Inline code, breakpoints, variables',
    },
};

/**
 * Get semantic layer from zoom level
 */
export function getLayerFromZoom(zoom: number): SemanticLayer {
    if (zoom < 0.5) return 'architecture';
    if (zoom < 1.0) return 'design';
    if (zoom < 2.0) return 'component';
    if (zoom < 5.0) return 'implementation';
    return 'detail';
}

/**
 * Get layer configuration
 */
export function getLayerConfig(layer: SemanticLayer): LayerConfig {
    return LAYER_CONFIGS[layer];
}

/**
 * Check if zoom level is within layer range
 */
export function isZoomInLayer(zoom: number, layer: SemanticLayer): boolean {
    const config = LAYER_CONFIGS[layer];
    return zoom >= config.zoomRange[0] && zoom < config.zoomRange[1];
}

/**
 * Get recommended zoom for layer
 */
export function getRecommendedZoomForLayer(layer: SemanticLayer): number {
    const config = LAYER_CONFIGS[layer];
    const [min, max] = config.zoomRange;
    // Return midpoint of range
    return max === Infinity ? min * 2 : (min + max) / 2;
}

/**
 * Layer transition detector
 */
export class LayerTransitionDetector {
    private currentLayer: SemanticLayer = 'architecture';
    private listeners: Array<(layer: SemanticLayer, previousLayer: SemanticLayer) => void> = [];
    private debounceTimer: number | null = null;
    private readonly debounceMs: number = 150;

    constructor(initialZoom: number = 1.0) {
        this.currentLayer = getLayerFromZoom(initialZoom);
    }

    /**
     * Update zoom and detect layer changes
     */
    updateZoom(zoom: number): void {
        const newLayer = getLayerFromZoom(zoom);

        if (newLayer !== this.currentLayer) {
            // Debounce layer transitions to avoid rapid switching
            if (this.debounceTimer !== null) {
                clearTimeout(this.debounceTimer);
            }

            this.debounceTimer = window.setTimeout(() => {
                const previousLayer = this.currentLayer;
                this.currentLayer = newLayer;
                this.notifyListeners(newLayer, previousLayer);
                this.debounceTimer = null;
            }, this.debounceMs);
        }
    }

    /**
     * Get current layer
     */
    getCurrentLayer(): SemanticLayer {
        return this.currentLayer;
    }

    /**
     * Subscribe to layer changes
     */
    subscribe(listener: (layer: SemanticLayer, previousLayer: SemanticLayer) => void): () => void {
        this.listeners.push(listener);

        // Return unsubscribe function
        return () => {
            this.listeners = this.listeners.filter(l => l !== listener);
        };
    }

    /**
     * Notify all listeners of layer change
     */
    private notifyListeners(layer: SemanticLayer, previousLayer: SemanticLayer): void {
        this.listeners.forEach(listener => {
            try {
                listener(layer, previousLayer);
            } catch (error) {
                console.error('Error in layer transition listener:', error);
            }
        });
    }

    /**
     * Force immediate layer transition
     */
    forceTransition(layer: SemanticLayer): void {
        if (layer !== this.currentLayer) {
            const previousLayer = this.currentLayer;
            this.currentLayer = layer;
            this.notifyListeners(layer, previousLayer);
        }
    }

    /**
     * Clear debounce timer
     */
    clearDebounce(): void {
        if (this.debounceTimer !== null) {
            clearTimeout(this.debounceTimer);
            this.debounceTimer = null;
        }
    }

    /**
     * Destroy detector and cleanup
     */
    destroy(): void {
        this.clearDebounce();
        this.listeners = [];
    }
}

/**
 * Global layer detector instance
 */
let globalDetector: LayerTransitionDetector | null = null;

/**
 * Get global layer detector
 */
export function getLayerDetector(): LayerTransitionDetector {
    if (!globalDetector) {
        globalDetector = new LayerTransitionDetector();
    }
    return globalDetector;
}

/**
 * Reset global layer detector (useful for testing)
 */
export function resetLayerDetector(): void {
    if (globalDetector) {
        globalDetector.destroy();
    }
    globalDetector = new LayerTransitionDetector();
}
