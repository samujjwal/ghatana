import { getNativeBinding, getNativeBindingError } from './binding';
let cachedCtor = null;
let lastLoadError = null;
function resolveConstructor() {
    if (cachedCtor) {
        return cachedCtor;
    }
    const module = getNativeBinding();
    if (!module) {
        lastLoadError = getNativeBindingError();
        return null;
    }
    if (!module || typeof module.AgentBridge !== 'function') {
        lastLoadError = new Error('AgentBridge export was not found on native module');
        return null;
    }
    cachedCtor = module.AgentBridge;
    return cachedCtor;
}
export function createNativeBridge() {
    const ctor = resolveConstructor();
    if (!ctor) {
        return null;
    }
    try {
        return new ctor();
    }
    catch (error) {
        lastLoadError = error;
        return null;
    }
}
export function getLastBridgeError() {
    return lastLoadError;
}
export async function parseBridgeStats(bridge) {
    try {
        const stats = await bridge.getStats();
        if (typeof stats === 'string') {
            return JSON.parse(stats);
        }
        return stats;
    }
    catch {
        return null;
    }
}
//# sourceMappingURL=bridge.js.map