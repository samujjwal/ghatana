/**
 * Layer Detection Hook
 * 
 * React hook for automatic semantic layer detection based on zoom level.
 * Integrates with canvas zoom events and updates global state.
 * 
 * @doc.type hook
 * @doc.purpose Layer detection integration
 * @doc.layer presentation
 */

import { useEffect, useCallback, useRef } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import {
    chromeZoomLevelAtom,
    chromeSemanticLayerAtom,
    SemanticLayer,
} from '../chrome';
import {
    getLayerFromZoom,
    getLayerDetector,
    LayerTransitionDetector,
    getLayerConfig,
} from '../core/layer-detector';

export interface LayerDetectionOptions {
    /** Enable automatic layer detection */
    enabled?: boolean;
    /** Callback when layer changes */
    onLayerChange?: (layer: SemanticLayer, previousLayer: SemanticLayer) => void;
    /** Enable transition animations */
    enableTransitions?: boolean;
    /** Custom debounce time in ms */
    debounceMs?: number;
}

/**
 * Hook for automatic layer detection from zoom level
 */
export function useLayerDetection(options: LayerDetectionOptions = {}) {
    const {
        enabled = true,
        onLayerChange,
        enableTransitions = true,
    } = options;

    const zoomLevel = useAtomValue(chromeZoomLevelAtom);
    const [currentLayer, setCurrentLayer] = useAtom(chromeSemanticLayerAtom);
    const detectorRef = useRef<LayerTransitionDetector | null>(null);

    // Initialize detector
    useEffect(() => {
        if (!enabled) return;

        detectorRef.current = getLayerDetector();

        return () => {
            // Cleanup on unmount
            if (detectorRef.current) {
                detectorRef.current.clearDebounce();
            }
        };
    }, [enabled]);

    // Subscribe to layer changes
    useEffect(() => {
        if (!enabled || !detectorRef.current) return;

        const unsubscribe = detectorRef.current.subscribe((layer, previousLayer) => {
            // Update atom
            setCurrentLayer(layer);

            // Call user callback
            if (onLayerChange) {
                onLayerChange(layer, previousLayer);
            }

            // Log transition
            console.log(`🔍 Layer transition: ${previousLayer} → ${layer}`);
        });

        return unsubscribe;
    }, [enabled, onLayerChange, setCurrentLayer]);

    // Update detector when zoom changes
    useEffect(() => {
        if (!enabled || !detectorRef.current) return;

        detectorRef.current.updateZoom(zoomLevel);
    }, [enabled, zoomLevel]);

    const forceLayer = useCallback((layer: SemanticLayer) => {
        if (detectorRef.current) {
            detectorRef.current.forceTransition(layer);
        }
    }, []);

    const getLayerInfo = useCallback(() => {
        return getLayerConfig(currentLayer);
    }, [currentLayer]);

    return {
        currentLayer,
        zoomLevel,
        forceLayer,
        getLayerInfo,
        isEnabled: enabled,
    };
}

/**
 * Hook for zoom-based layer transitions
 */
export function useZoomToLayer() {
    const setZoomLevel = useSetAtom(chromeZoomLevelAtom);
    const setSemanticLayer = useSetAtom(chromeSemanticLayerAtom);

    const zoomToLayer = useCallback((layer: SemanticLayer, animate: boolean = true) => {
        const config = getLayerConfig(layer);
        const [min, max] = config.zoomRange;
        const targetZoom = max === Infinity ? min * 2 : (min + max) / 2;

        // Update zoom level (this will trigger layer detection)
        setZoomLevel(targetZoom);

        // Immediately update layer if not animating
        if (!animate) {
            setSemanticLayer(layer);
        }

        console.log(`🎯 Zooming to ${layer} layer (${targetZoom}x)`);
    }, [setZoomLevel, setSemanticLayer]);

    return { zoomToLayer };
}

/**
 * Hook for layer-aware actions
 */
export function useLayerAwareActions() {
    const currentLayer = useAtomValue(chromeSemanticLayerAtom);

    const isLayerActive = useCallback((layer: SemanticLayer) => {
        return currentLayer === layer;
    }, [currentLayer]);

    const getLayerDescription = useCallback(() => {
        const config = getLayerConfig(currentLayer);
        return config.description;
    }, [currentLayer]);

    const getPrimaryFocus = useCallback(() => {
        const config = getLayerConfig(currentLayer);
        return config.primaryFocus;
    }, [currentLayer]);

    return {
        currentLayer,
        isLayerActive,
        getLayerDescription,
        getPrimaryFocus,
    };
}
