/**
 * Manual mock for @capacitor/core
 * Used in tests where Capacitor is not available in the web/jsdom environment.
 */

export const Capacitor = {
    isNativePlatform: () => false,
    isPluginAvailable: (_pluginName: string) => false,
    getPlatform: () => 'web',
};
