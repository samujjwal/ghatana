module.exports = {
  preset: 'react-native',
  testEnvironment: 'jest-environment-jsdom',
  setupFiles: ['<rootDir>/src/__tests__/setup-pre.ts', '@testing-library/react-native/dont-cleanup-after-each'],
  setupFilesAfterEnv: ['<rootDir>/src/__tests__/setup.ts'],

  // Ensure babel-jest transforms JS/TS files (including files under
  // whitelisted node_modules such as react-native polyfills)
  transform: {
    '^.+\\.(js|jsx|ts|tsx)$': 'babel-jest',
  },

  // Whitelist react-native and commonly used native modules so Jest
  // will transform them instead of ignoring. Add any other packages
  // that surface similar parse errors here.
  // Transform node_modules that contain react-native or related native packages.
  // Use a broader negative lookahead so pnpm nested paths are also covered.
  transformIgnorePatterns: [
    'node_modules/(?!.*(react-native|@react-native|@react-native-community|@react-navigation|@react-native-async-storage|react-native-gesture-handler|react-native-vector-icons)).*'
  ],

  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
    // Mock react-native js-polyfills (Flow-typed files) used by react-native's
    // setup so Jest doesn't try to parse Flow types from node_modules.
    '^@react-native/js-polyfills/(.*)$': '<rootDir>/jest-mocks/js-polyfills-mock.js',
    '^@react-native/js-polyfills$': '<rootDir>/jest-mocks/js-polyfills-mock.js',
    // react-native-gesture-handler provides a jest setup file in RN projects;
    // if it's not present or causes resolution issues in the monorepo, map it
    // to a lightweight mock so tests can run.
    '^react-native-gesture-handler/jestSetup$': '<rootDir>/jest-mocks/rngh-jest-setup-mock.js',
    '^@notifee/react-native$': '<rootDir>/jest-mocks/notifee-mock.js',
    // NOTE: previously we forced a proxy for react-test-renderer to avoid
    // mixed versions; that can itself cause resolution surprises (older
    // package.json resolution). Removing the explicit mapping lets Node's
    // resolver pick the workspace renderer (which should match React).
  },

  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json', 'node'],

  testMatch: ['**/__tests__/**/*.test.ts?(x)'],
};
