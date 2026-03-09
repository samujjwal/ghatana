import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Paths are relative to the extension directory
const projectRoot = path.resolve(__dirname, '..');
const profileDir = path.join(projectRoot, 'tmp/firefox-profile');
const extensionsDir = path.join(profileDir, 'extensions');
const extensionPath = path.join(projectRoot, 'dist/firefox');

console.log('Project root:', projectRoot);
console.log('Extension path:', extensionPath);

function ensureDirectory(directoryPath) {
  if (!fs.existsSync(directoryPath)) {
    fs.mkdirSync(directoryPath, { recursive: true });
  }
}

function resolveManifestPath(basePath) {
  const defaultManifest = path.join(basePath, 'manifest.json');
  if (fs.existsSync(defaultManifest)) {
    return defaultManifest;
  }

  const alternativeManifest = path.join(projectRoot, 'dist/firefox/manifest.json');
  if (fs.existsSync(alternativeManifest)) {
    console.log('Manifest not found at default location. Using alternative path:', alternativeManifest);
    return alternativeManifest;
  }

  throw new Error(
    `Manifest file not found. Tried:\n- ${defaultManifest}\n- ${alternativeManifest}\n\nMake sure to build the Firefox extension first.`
  );
}

function writeExtensionsJson(manifestPath, manifest, extensionId) {
  const extensionsJson = {
    addons: [
      {
        id: extensionId,
        syncGUID: '{dcmaar-extension}',
        location: 'app-profile',
        version: manifest.version,
        type: 'extension',
        updateURL: null,
        optionsURL: null,
        optionsType: null,
        aboutURL: null,
        defaultLocale: {
          name: manifest.name,
          description: manifest.description || '',
          creator: manifest.author || '',
          homepageURL: manifest.homepage_url || '',
        },
        visible: true,
        active: true,
        userDisabled: false,
        appDisabled: false,
        installDate: Date.now(),
        updateDate: Date.now(),
        applyBackgroundUpdates: 1,
        path: path.dirname(manifestPath),
        rootURI: null,
        loader: null,
        hashValue: '',
        signedDate: null,
        signedState: 0,
        seen: true,
        dependencies: [],
        optionalPermissions: [],
        incognito: 'spanning',
      },
    ],
  };

  fs.writeFileSync(path.join(profileDir, 'extensions.json'), JSON.stringify(extensionsJson, null, 2));
}

function writePrefsFile(extensionId) {
  const prefs = [
    `user_pref("extensions.webextensions.uuids", ${
      JSON.stringify(JSON.stringify({ [extensionId]: '{dcmaer-extension@example.com}' }))
    });`,
    'user_pref("xpinstall.signatures.required", false);',
    'user_pref("extensions.experiments.enabled", true);',
    'user_pref("extensions.allowPrivateBrowsingByDefault", true);',
    'user_pref("browser.shell.checkDefaultBrowser", false);',
    'user_pref("browser.tabs.remote.separatePrivilegedMozillaWebContentProcess", false);',
  ].join('\n');

  fs.writeFileSync(path.join(profileDir, 'prefs.js'), prefs);
}

async function setupFirefoxProfile() {
  try {
    // Create directories if they don't exist
    ensureDirectory(profileDir);
    ensureDirectory(extensionsDir);

    const manifestPath = resolveManifestPath(extensionPath);
    console.log('Using manifest at:', manifestPath);

    return await setupWithManifest(manifestPath);
  } catch (error) {
    console.error('Error setting up Firefox profile:', error);
    return false;
  }
}

async function setupWithManifest(manifestPath) {
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
  const extensionId =
    manifest.browser_specific_settings?.gecko?.id || 'dcmaar-extension@example.com';
  console.log('Using extension ID:', extensionId);

  writeExtensionsJson(manifestPath, manifest, extensionId);
  writePrefsFile(extensionId);

  console.log('Firefox test profile created successfully at:', profileDir);
  return true;
}

// Run the setup
setupFirefoxProfile().then((success) => {
  process.exit(success ? 0 : 1);
});
