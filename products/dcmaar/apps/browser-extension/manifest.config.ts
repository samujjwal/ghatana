import { defineManifest } from "@crxjs/vite-plugin";

const BROWSER = process.env.BROWSER || "chrome";
const isFirefox = BROWSER === "firefox";
const isEdge = BROWSER === "edge";

const browserSpecificSettings = isFirefox
  ? {
      browser_specific_settings: {
        gecko: {
          id: "guardian@example.com",
          strict_min_version: "109.0",
        },
      },
    }
  : {};

const baseManifest = {
  ...browserSpecificSettings,
  manifest_version: 3,
  name: "Guardian - Performance Monitor",
  version: "1.0.0",
  description:
    "Real-time browser performance monitoring with Web Vitals tracking and analytics dashboard",

  permissions: [
    "storage",
    "tabs",
    "webNavigation",
    "alarms",
    "declarativeNetRequest",
    "declarativeNetRequestFeedback",
  ],

  host_permissions: ["http://*/*", "https://*/*", "file://*/*"],

  icons: {
    "16": "public/icon-16.svg",
    "32": "public/icon-32.svg",
    "48": "public/icon-48.svg",
    "128": "public/icon-128.svg",
  },

  action: {
    default_popup: "src/popup/index.html",
    default_icon: {
      "16": "public/icon-16.svg",
      "32": "public/icon-32.svg",
      "48": "public/icon-48.svg",
    },
    default_title: "Guardian - Performance Monitor",
  },

  options_page: "src/options/index.html",

  background: {
    service_worker: "src/background/index.ts",
    type: "module" as const,
  },

  content_scripts: [
    {
      matches: ["<all_urls>"],
      js: ["src/content/index.ts"],
      run_at: "document_start",
      all_frames: false,
    },
  ],

  web_accessible_resources: [
    {
      resources: ["src/dashboard/index.html", "src/options/index.html", "src/pages/blocked.html", "src/pages/blocked.js"],
      matches: ["<all_urls>"],
    },
  ],

  content_security_policy: {
    extension_pages: [
      "script-src 'self' 'wasm-unsafe-eval';",
      "style-src 'self' 'unsafe-inline';",
      "connect-src 'self' ws: wss:;",
      "object-src 'self'",
    ].join(" "),
  },

  declarative_net_request: {
    rule_resources: [
      {
        id: "ruleset_1",
        enabled: true,
        path: "rules.json",
      },
    ],
  },
};

const browserOverrides = {
  firefox: {
    browser_specific_settings: {
      gecko: {
        id: "guardian@example.com",
        strict_min_version: "109.0",
      },
    },
    background: {
      scripts: ["src/background/index.ts"],
      type: "module" as const,
    },
  },
  edge: {
    minimum_edge_version: "120.0",
  },
  chrome: {},
};

const manifest = {
  ...baseManifest,
  ...(isFirefox ? browserOverrides.firefox : {}),
  ...(isEdge ? browserOverrides.edge : {}),
  ...(BROWSER === "chrome" ? browserOverrides.chrome : {}),
};

export default defineManifest(manifest as any);
