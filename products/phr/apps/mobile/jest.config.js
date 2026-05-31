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
    moduleNameMapper: {
      ...(preset.moduleNameMapper ?? {}),
      '^@ghatana/kernel-product-contracts/policy$':
        '<rootDir>/../../../../platform/typescript/kernel-product-contracts/src/policy/MobilePhiPolicyContract.ts',
      '^@ghatana/kernel-product-contracts/mobile-privacy$':
        '<rootDir>/../../../../platform/typescript/kernel-product-contracts/src/privacy/MobilePrivacyPlugin.ts',
    },
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
