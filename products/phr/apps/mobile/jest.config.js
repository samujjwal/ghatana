const {
  getAndroidPreset,
  getIOSPreset,
  getNodePreset,
  getWebPreset,
} = require('jest-expo/config/getPlatformPreset');

const setupBeforeEnv = '<rootDir>/jest.globals.js';
const setupAfterEnv = '<rootDir>/jest.setup.js';

function withPhrMobileSetup(preset, options = {}) {
  const configured = {
    ...preset,
    setupFiles: [...(preset.setupFiles ?? []), setupBeforeEnv],
    setupFilesAfterEnv: [...(preset.setupFilesAfterEnv ?? []), setupAfterEnv],
    testPathIgnorePatterns: [
      ...(preset.testPathIgnorePatterns ?? []),
      ...(options.testPathIgnorePatterns ?? []),
    ],
  };
  delete configured.watchPlugins;
  return configured;
}

module.exports = {
  projects: [
    withPhrMobileSetup(getWebPreset()),
    withPhrMobileSetup(getNodePreset(), { testPathIgnorePatterns: ['<rootDir>/e2e/'] }),
    withPhrMobileSetup(getIOSPreset()),
    withPhrMobileSetup(getAndroidPreset()),
  ],
};
