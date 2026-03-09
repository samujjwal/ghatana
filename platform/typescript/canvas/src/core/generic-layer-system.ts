/**
 * Generic Layer System
 * 
 * Application-agnostic layer detection and management.
 * Can be configured for any multi-layer canvas application.
 * 
 * @doc.type core
 * @doc.purpose Generic layer system
 * @doc.layer core
 */

import { LayerConfig, getCanvasConfig } from './canvas-config';

/**
 * Generic layer detector
 */
export class GenericLayerDetector<TLayer extends string = string> {
    private currentLayer: TLayer;
    private listeners: Array<(layer: TLayer, previousLayer: TLayer) => void> = [];
    private debounceTimer: number | null = null;
    private readonly debounceMs: number = 150;

    constructor(initialZoom: number = 1.0) {
        const config = getCanvasConfig();
        this.currentLayer = config.getLayerFromZoom(initialZoom) as TLayer;
    }

    /**
     * Update zoom and detect layer changes
     */
    updateZoom(zoom: number): void {
        const config = getCanvasConfig();
        const newLayer = config.getLayerFromZoom(zoom) as TLayer;

        if (newLayer !== this.currentLayer) {
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

    getCurrentLayer(): TLayer {
        return this.currentLayer;
    }

    subscribe(listener: (layer: TLayer, previousLayer: TLayer) => void): () => void {
        this.listeners.push(listener);
        return () => {
            this.listeners = this.listeners.filter(l => l !== listener);
        };
    }

    private notifyListeners(layer: TLayer, previousLayer: TLayer): void {
        this.listeners.forEach(listener => {
            try {
                listener(layer, previousLayer);
            } catch (error) {
                console.error('Error in layer transition listener:', error);
            }
        });
    }

    forceTransition(layer: TLayer): void {
        if (layer !== this.currentLayer) {
            const previousLayer = this.currentLayer;
            this.currentLayer = layer;
            this.notifyListeners(layer, previousLayer);
        }
    }

    clearDebounce(): void {
        if (this.debounceTimer !== null) {
            clearTimeout(this.debounceTimer);
            this.debounceTimer = null;
        }
    }

    destroy(): void {
        this.clearDebounce();
        this.listeners = [];
    }
}

/**
 * Get layer configuration
 */
export function getLayerConfig<TLayer extends string>(layer: TLayer): LayerConfig<TLayer> {
    const config = getCanvasConfig();
    return config.layers[layer] as LayerConfig<TLayer>;
}

/**
 * Check if zoom level is within layer range
 */
export function isZoomInLayer<TLayer extends string>(zoom: number, layer: TLayer): boolean {
    const layerConfig = getLayerConfig(layer);
    const [min, max] = layerConfig.zoomRange;
    return zoom >= min && zoom < max;
}

/**
 * Get recommended zoom for layer
 */
export function getRecommendedZoomForLayer<TLayer extends string>(layer: TLayer): number {
    const layerConfig = getLayerConfig(layer);
    const [min, max] = layerConfig.zoomRange;
    return max === Infinity ? min * 2 : (min + max) / 2;
}
