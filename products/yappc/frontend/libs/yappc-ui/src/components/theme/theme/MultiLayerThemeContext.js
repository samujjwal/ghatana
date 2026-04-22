import { jsx as _jsx } from "react/jsx-runtime";
import { createContext, useContext, useMemo, useState } from 'react';
export const LayerPriority = {
    BASE: 0,
    BRAND: 100,
    WORKSPACE: 200,
    APP: 300,
    OVERRIDE: 1000,
};
const MultiLayerThemeContext = createContext(undefined);
function deepMergeThemeOptions(...options) {
    const result = {};
    for (const option of options) {
        if (!option)
            continue;
        for (const [key, value] of Object.entries(option)) {
            if (value === undefined)
                continue;
            if (typeof value === 'object' &&
                !Array.isArray(value) &&
                value !== null) {
                result[key] = deepMergeThemeOptions(result[key] || {}, value);
            }
            else {
                result[key] = value;
            }
        }
    }
    return result;
}
export function MultiLayerThemeProvider({ children, initialMode = 'light', baseThemeOptions = {}, brandThemeOptions, workspaceThemeOptions, appThemeOptions, }) {
    const [mode, setMode] = useState(initialMode);
    const [layers, setLayers] = useState(() => {
        const initialLayers = [
            {
                id: 'base',
                name: 'Base',
                description: 'Foundation theme tokens',
                options: baseThemeOptions,
                priority: LayerPriority.BASE,
            },
        ];
        if (brandThemeOptions) {
            initialLayers.push({
                id: 'brand',
                name: 'Brand',
                description: 'Brand-specific customizations',
                options: brandThemeOptions,
                priority: LayerPriority.BRAND,
            });
        }
        if (workspaceThemeOptions) {
            initialLayers.push({
                id: 'workspace',
                name: 'Workspace',
                description: 'Workspace preferences',
                options: workspaceThemeOptions,
                priority: LayerPriority.WORKSPACE,
            });
        }
        if (appThemeOptions) {
            initialLayers.push({
                id: 'app',
                name: 'Application',
                description: 'App-specific overrides',
                options: appThemeOptions,
                priority: LayerPriority.APP,
            });
        }
        return initialLayers;
    });
    const addLayer = (layer) => {
        setLayers((previous) => [...previous.filter((existing) => existing.id !== layer.id), layer].sort((left, right) => left.priority - right.priority));
    };
    const removeLayer = (layerId) => {
        setLayers((previous) => previous.filter((layer) => layer.id !== layerId));
    };
    const updateLayer = (layerId, options) => {
        setLayers((previous) => previous.map((layer) => layer.id === layerId
            ? { ...layer, options: deepMergeThemeOptions(layer.options, options) }
            : layer));
    };
    const setBrandLayer = (options) => {
        updateLayer('brand', options);
    };
    const setWorkspaceLayer = (options) => {
        updateLayer('workspace', options);
    };
    const setAppLayer = (options) => {
        updateLayer('app', options);
    };
    const mergedThemeOptions = useMemo(() => {
        const sortedLayers = [...layers].sort((left, right) => left.priority - right.priority);
        const layerOptions = sortedLayers.map((layer) => layer.options);
        return deepMergeThemeOptions({ palette: { mode } }, ...layerOptions);
    }, [layers, mode]);
    const baseLayer = layers.find((layer) => layer.id === 'base');
    const brandLayer = layers.find((layer) => layer.id === 'brand');
    const workspaceLayer = layers.find((layer) => layer.id === 'workspace');
    const appLayer = layers.find((layer) => layer.id === 'app');
    const value = {
        mode,
        setMode,
        layers,
        addLayer,
        removeLayer,
        updateLayer,
        mergedThemeOptions,
        baseLayer,
        brandLayer,
        workspaceLayer,
        appLayer,
        setBrandLayer,
        setWorkspaceLayer,
        setAppLayer,
    };
    return (_jsx(MultiLayerThemeContext.Provider, { value: value, children: children }));
}
export function useMultiLayerTheme() {
    const context = useContext(MultiLayerThemeContext);
    if (!context) {
        throw new Error('useMultiLayerTheme must be used within MultiLayerThemeProvider');
    }
    return context;
}
export function useThemeMode() {
    const { mode, setMode } = useMultiLayerTheme();
    return { mode, setMode };
}
export function useBrandTheme() {
    const { brandLayer, setBrandLayer } = useMultiLayerTheme();
    return { brandLayer, setBrandLayer };
}
export function useWorkspaceTheme() {
    const { workspaceLayer, setWorkspaceLayer } = useMultiLayerTheme();
    return { workspaceLayer, setWorkspaceLayer };
}
export function useAppTheme() {
    const { appLayer, setAppLayer } = useMultiLayerTheme();
    return { appLayer, setAppLayer };
}
