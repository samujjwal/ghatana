const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const stripJsonComments = require('strip-json-comments');

const tsconfigPath = path.join(__dirname, '..', 'products', 'extension', 'tsconfig.json');

// Read the tsconfig file
let tsconfigContent = fs.readFileSync(tsconfigPath, 'utf8');

// Parse JSON after stripping comments
const tsconfig = JSON.parse(stripJsonComments(tsconfigContent));

// Define the desired path order with comments
const pathOrder = `{
  // Core
  "@core/*": ["src/core/*"],
  "@core/utils/*": ["src/core/utils/*"],
  "@core/storage/*": ["src/core/storage/*"],
  "@core/interfaces": ["src/core/interfaces/index.ts"],
  
  // Shared
  "@/shared/*": ["src/shared/*"],
  "@/shared/transport/*": ["src/shared/transport/*"],
  "@/shared/types/*": ["src/shared/types/*"],
  
  // Utils
  "@/utils/*": ["src/utils/*"],
  "@/config/*": ["src/config/*"],
  "@/services/*": ["src/services/*"],
  
  // Extension
  "@/extension/*": ["src/extension/*"],
  "@/extension/background/*": ["src/extension/background/*"],
  "@/extension/content/*": ["src/extension/content/*"],
  "@/extension/options/*": ["src/extension/options/*"],
  "@/extension/popup/*": ["src/extension/popup/*"],
  "@/extension/devtools/*": ["src/extension/devtools/*"],
  
  // Communication
  "@communication/*": ["src/communication/*"],
  "@communication/bridge/*": ["src/communication/bridge/*"],
  "@communication/sink/*": ["src/communication/sink/*"],
  "@communication/source/*": ["src/communication/source/*"],
  "@communication/transport/*": ["src/communication/transport/*"],
  "@communication/types": ["src/communication/types/index.ts"],
  
  // Features
  "@features/*": ["src/features/*"],
  "@features/security/*": ["src/features/security/*"],
  "@features/privacy/*": ["src/features/privacy/*"],
  "@features/metrics/*": ["src/features/metrics/*"],
  "@features/analytics/*": ["src/features/analytics/*"],
  
  // UI
  "@ui/*": ["src/ui/*"],
  "@ui/components/*": ["src/ui/components/*"],
  
  // Services
  "@services/*": ["src/services/*"],
  "@services/auth/*": ["src/services/auth/*"]
}`;

// Parse the ordered paths
const orderedPaths = JSON.parse(stripJsonComments(pathOrder));

// Update the tsconfig with the ordered paths
tsconfig.compilerOptions.paths = orderedPaths;

// Convert back to string with formatting
const updatedConfig = JSON.stringify(tsconfig, null, 2);

// Add back the comment at the top
const finalConfig = `{
  "$schema": "https://json.schemastore.org/tsconfig",
  ${updatedConfig.slice(1)}`;

// Write the updated tsconfig back to disk
fs.writeFileSync(tsconfigPath, finalConfig, 'utf8');

console.log('✅ Fixed and reordered paths in tsconfig.json');

// Install strip-json-comments if not already installed
try {
  require.resolve('strip-json-comments');
} catch (_e) {
  console.log('Installing strip-json-comments...');
  execSync('pnpm add -D strip-json-comments', {
    stdio: 'inherit',
    cwd: path.join(__dirname, '..', 'products', 'extension')
  });
}

console.log('\nRunning type check...');

try {
  execSync('pnpm type-check', {
    stdio: 'inherit',
    cwd: path.join(__dirname, '..', 'products', 'extension')
  });
  console.log('✅ Type check passed!');
} catch (_error) {
  console.error('❌ Type check failed. Please review the remaining errors.');
  process.exit(1);
}
