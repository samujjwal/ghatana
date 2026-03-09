// services/extension/manifest.config.ts
import { defineManifest } from '@crxjs/vite-plugin';
import { randomUUID } from 'crypto';

// Generate a stable extension ID for development
const EXTENSION_ID =
  process.env.NODE_ENV === 'production' ? undefined : `dcmaar-${randomUUID().substring(0, 8)}@dev`;

// Type for native messaging configuration
type _NativeMessagingConfig = {
  name: string;
  description: string;
  path:
    | string
    | {
        win32: string;
        darwin: string;
        linux: string;
      };
  type: string;
};

// Get browser from environment variable or default to chrome
const BROWSER = process.env.BROWSER || 'chrome';
const isFirefox = BROWSER === 'firefox';
const isEdge = BROWSER === 'edge';

// Common manifest for all browsers
const API_HOSTS = [
  'https://api-dev.dcmaar.example.com',
  'https://api-staging.dcmaar.example.com',
  'https://api.dcmaar.example.com',
];

// Development mode detection
const isDevelopment = process.env.NODE_ENV === 'development';

// Localhost connections only in development
const DEV_HOSTS = isDevelopment
  ? ['http://localhost:9001', 'https://localhost:9001', 'http://127.0.0.1:9001']
  : [];

const CONNECT_SRC = [
  "'self'",
  ...API_HOSTS,
  ...DEV_HOSTS,
  'ws://localhost:*',
  'ws://127.0.0.1:*',
].join(' ');

// Browser-specific settings
const browserSpecificSettings = isFirefox
  ? {
      browser_specific_settings: {
        gecko: {
          id: EXTENSION_ID || 'dcmaar@example.com',
          strict_min_version: '109.0',
        },
      },
    }
  : {};

// Chrome/Edge native messaging configuration
const externallyConnectable = !isFirefox
  ? {
      // This will be merged with the base externally_connectable
      // configuration in the baseManifest
    }
  : {};

const baseManifest = {
  ...browserSpecificSettings,
  ...externallyConnectable,
  manifest_version: 3,
  name: 'DCMAR',
  version: '0.1.0',
  description: 'DCMAR Extension',
  permissions: [
    'storage',
    'tabs',
    'webNavigation',
    'scripting',
    'activeTab',
    'webRequest',
    'nativeMessaging', // For desktop app communication
    'alarms', // For connection retries
    'notifications', // For connection status notifications
    'management' // For extension management
  ],
  host_permissions: [
    // Production API hosts
    ...API_HOSTS.map((host) => `${host}/*`),
    // Development hosts (only in dev mode)
    ...(isDevelopment
      ? [
          'https://*/*',
          'http://*/*',
          'file://*/*',
          'http://localhost/*',
          'http://127.0.0.1/*',
          'ws://localhost:*/*',
          'ws://127.0.0.1:*/*',
        ]
      : []),
  ],
  // UI Configuration
  action: {
    default_popup: 'src/popup/index.html',
    default_icon: {
      '16': 'icons/icon16.png',
      '32': 'icons/icon32.png',
      '48': 'icons/icon48.png',
      '128': 'icons/icon128.png',
    },
    default_title: 'DCMAR Extension',
  },
  options_page: 'src/options/options.html',
  icons: {
    '16': 'icons/icon16.png',
    '32': 'icons/icon32.png',
    '48': 'icons/icon48.png',
    '128': 'icons/icon128.png',
  },
  background: {
    service_worker: 'src/app/background/index.ts',
    type: 'module' as const,
  },
  content_scripts: [
    {
      matches: ['<all_urls>', 'file://*/*'],
      js: ['src/interactions/content/index.ts'],
      run_at: 'document_start' as const,
      all_frames: true,
      match_about_blank: true,
    },
  ],
  web_accessible_resources: [
    {
      resources: ['*'],
      matches: ['<all_urls>'],
    },
  ],
  content_security_policy: {
    // For extension pages (popup, options, etc.)
    extension_pages: [
      "script-src 'self' 'wasm-unsafe-eval';",
      `connect-src 'self' ws: wss: ${CONNECT_SRC};`,
      "object-src 'self'",
    ].join(' '),
    // For sandboxed pages
    sandbox: [
      'sandbox allow-scripts allow-forms allow-popups allow-modals;',
      `script-src 'self' 'wasm-unsafe-eval' http://localhost:* http://127.0.0.1:* ${API_HOSTS.join(' ')};`,
      `connect-src 'self' ws: wss: ${CONNECT_SRC};`,
      "object-src 'self'",
    ].join(' '),
  },
  externally_connectable: {
    // Allow connections from any extension (for development)
    // In production, you should specify the exact extension ID
    ids: ['*'],
    // Allow connections from localhost for development
    // Note: Custom protocol schemes (app.dcmaar://, dcmaar://) are not supported in match patterns
    // Desktop app communication should use native messaging (configured below)
    matches: [
      'http://localhost:*/*',
      'http://127.0.0.1:*/*',
      'https://dcmaar-desktop.pages.dev/*', // Example production URL if applicable
    ],
  },
  // NOTE: Native messaging host manifests are OS-level JSON files and
  // must NOT be included as custom keys in the extension manifest. Keep
  // the `nativeMessaging` permission above; host registration is done via
  // OS-specific native messaging host manifests (examples live in
  // `ops/native-messaging/`). See ops/native-messaging/README.md for
  // installation details.
};

// Browser-specific overrides
const browserOverrides = {
  firefox: {
    browser_specific_settings: {
      gecko: {
        id: '{d4c5a4c0-9b7a-4b4a-9e8a-9e8b8c7d6e5f}',
        strict_min_version: '120.0',
      },
    },
    // Firefox requires a different background configuration
    background: {
      scripts: ['src/app/background/index.ts'],
      type: 'module' as const,
    },
  },
  edge: {
    minimum_edge_version: '120.0',
  },
  chrome: {},
};

// Apply browser-specific overrides
const manifest = {
  ...baseManifest,
  ...(isFirefox ? browserOverrides.firefox : {}),
  ...(isEdge ? browserOverrides.edge : {}),
  ...(BROWSER === 'chrome' ? browserOverrides.chrome : {}),
};

// Manifest typing is tightly coupled to @crxjs types; use unknown then cast to avoid transient type mismatch
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export default defineManifest(manifest as any);
