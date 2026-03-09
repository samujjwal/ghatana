/**
 * Jest Configuration for Canvas Refactoring Tests
 */

import type { Config } from '@jest/types';

const config: Config.InitialOptions = {
  displayName: 'Canvas Refactoring Tests',
  testEnvironment: 'jsdom',

  // Test file patterns
  testMatch: [
    '<rootDir>/src/components/canvas/**/*.test.{ts,tsx}',
    '<rootDir>/src/components/canvas/__tests__/**/*.{ts,tsx}',
    '<rootDir>/e2e/**/*.spec.{ts,tsx}',
  ],

  // Setup files
  setupFilesAfterEnv: ['<rootDir>/src/components/canvas/__tests__/setup.ts'],

  // Module name mapping for absolute imports
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
    '^@components/(.*)$': '<rootDir>/src/components/$1',
    '^@services/(.*)$': '<rootDir>/src/services/$1',
  },

  // Transform configuration
  transform: {
    '^.+\\.(ts|tsx)$': 'ts-jest',
    '^.+\\.(js|jsx)$': 'babel-jest',
  },

  // Coverage configuration
  collectCoverageFrom: [
    'src/components/canvas/**/*.{ts,tsx}',
    'src/services/registry/**/*.{ts,tsx}',
    '!src/components/canvas/**/*.test.{ts,tsx}',
    '!src/components/canvas/__tests__/**/*',
    '!src/**/*.d.ts',
  ],

  coverageThreshold: {
    global: {
      branches: 80,
      functions: 80,
      lines: 80,
      statements: 80,
    },
  },

  // Mock configuration
  clearMocks: true,
  restoreMocks: true,

  // Performance settings
  maxWorkers: '50%',

  // Error handling
  bail: 0,
  verbose: true,

  // Extensions
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json'],

  // Ignore patterns
  testPathIgnorePatterns: [
    '<rootDir>/node_modules/',
    '<rootDir>/dist/',
    '<rootDir>/build/',
  ],

  // Global setup/teardown
  globalSetup: '<rootDir>/src/components/canvas/__tests__/global-setup.ts',
  globalTeardown:
    '<rootDir>/src/components/canvas/__tests__/global-teardown.ts',
};

export default config;
