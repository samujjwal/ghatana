const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

// Project root is the mobile app directory
const projectRoot = __dirname;

const config = getDefaultConfig(projectRoot);

// Force project root to prevent monorepo resolution issues
config.projectRoot = projectRoot;
config.watchFolders = [
    projectRoot,
    path.resolve(projectRoot, '../../../..'),
];

// Enhanced node_modules resolution for pnpm
config.resolver.nodeModulesPaths = [
    path.resolve(projectRoot, 'node_modules'),
    path.resolve(projectRoot, '../../../..', 'node_modules'),
];

// Add additional extensions
config.resolver.sourceExts = [...config.resolver.sourceExts, 'cjs', 'mjs'];

// Platform-specific resolution for web
config.resolver.platforms = ['ios', 'android', 'web', 'native'];

// Add react-native-web resolution for web platform
config.resolver.alias = {
    ...config.resolver.alias,
    'react-native': 'react-native-web',
};

// Disable package exports to help with resolution
config.resolver.unstable_enablePackageExports = false;

module.exports = config;
