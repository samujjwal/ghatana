// Proxy that forces Jest to load the workspace react-test-renderer@19.2.0
// (absolute path chosen to the pnpm store in this workspace). This avoids
// mixed renderer versions when some packages still reference 18.x in their
// package.json.

module.exports = require('/Users/samujjwal/Development/ghatana/node_modules/.pnpm/react-test-renderer@19.2.0_react@19.2.0/node_modules/react-test-renderer');
