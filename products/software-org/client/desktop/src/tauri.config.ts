/**
 * Tauri Configuration for Desktop App
 *
 * Desktop-specific configuration and capabilities.
 * Enables native features for the Software-Org desktop application.
 *
 * @package @ghatana/software-org-desktop
 */

// Tauri configuration for desktop app
export const tauriConfig = {
  tauri: {
    windows: [
      {
        title: "Software-Org Management",
        width: 1400,
        height: 900,
        minWidth: 1200,
        minHeight: 700,
        resizable: true,
        fullscreen: false,
        decorations: true,
        center: true,
      },
    ],
    security: {
      csp: "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline';",
    },
    updater: {
      active: true,
      endpoints: ["https://updates.ghatana.com/software-org"],
      dialog: true,
      pubkey: "YOUR_PUBLIC_KEY_HERE",
    },
    bundle: {
      identifier: "com.ghatana.software-org",
      icon: [
        "icons/32x32.png",
        "icons/128x128.png",
        "icons/128x128@2x.png",
        "icons/icon.icns",
        "icons/icon.ico",
      ],
      resources: [],
      externalBin: [],
      copyright: "Copyright © 2025 Ghatana",
      category: "Business",
      shortDescription: "Software Organization Management",
      longDescription: "Comprehensive organization management for software teams",
    },
    allowlist: {
      all: false,
      fs: {
        all: false,
        readFile: true,
        writeFile: true,
        readDir: true,
        createDir: true,
        scope: ["$APPDATA/*", "$LOCALDATA/*"],
      },
      window: {
        all: false,
        close: true,
        hide: true,
        show: true,
        maximize: true,
        minimize: true,
        unmaximize: true,
        unminimize: true,
        setTitle: true,
      },
      shell: {
        all: false,
        open: true,
      },
      dialog: {
        all: false,
        open: true,
        save: true,
      },
      notification: {
        all: true,
      },
    },
  },
};

/**
 * Desktop-specific features configuration
 */
export const desktopFeatures = {
  offlineMode: true,
  localCache: true,
  notifications: true,
  systemTray: true,
  autoUpdates: true,
  shortcuts: true,
};

