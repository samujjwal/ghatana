const fs = require('fs');
const path = require('path');

const tsconfigPath = path.join(__dirname, '..', 'products', 'extension', 'tsconfig.json');

// Read and parse the tsconfig file
const tsconfig = JSON.parse(fs.readFileSync(tsconfigPath, 'utf8'));

// Create a new paths object without duplicates
const uniquePaths = {};
const pathOrder = [
  // Core
  '@core/*',
  '@core/utils/*',
  '@core/storage/*',
  '@core/interfaces',
  
  // Shared
  '@/shared/*',
  '@/shared/transport/*',
  '@/shared/types/*',
  
  // Utils
  '@/utils/*',
  '@/config/*',
  '@/services/*',
  
  // Extension
  '@/extension/*',
  '@/extension/background/*',
  '@/extension/content/*',
  '@/extension/options/*',
  '@/extension/popup/*',
  '@/extension/devtools/*',
  
  // Communication
  '@communication/*',
  '@communication/bridge/*',
  '@communication/sink/*',
  '@communication/source/*',
  '@communication/transport/*',
  '@communication/types',
  
  // Features
  '@features/*',
  '@features/security/*',
  '@features/privacy/*',
  '@features/metrics/*',
  '@features/analytics/*',
  
  // UI
  '@ui/*',
  '@ui/components/*',
  
  // Services
  '@services/*',
  '@services/auth/*'
];

// Add paths in the specified order
pathOrder.forEach(path => {
  if (tsconfig.compilerOptions.paths[path]) {
    uniquePaths[path] = tsconfig.compilerOptions.paths[path];
  }
});

// Update the tsconfig with deduplicated paths
tsconfig.compilerOptions.paths = uniquePaths;

// Write the updated tsconfig back to disk
fs.writeFileSync(
  tsconfigPath,
  JSON.stringify(tsconfig, null, 2) + '\n', // Add newline at the end
  'utf8'
);

console.log('✅ Fixed duplicate paths in tsconfig.json');
console.log('\nRunning type check...');

try {
  const { execSync } = require('child_process');
  execSync('pnpm type-check', {
    stdio: 'inherit',
    cwd: path.join(__dirname, '..', 'products', 'extension')
  });
  console.log('✅ Type check passed!');
} catch (_error) {
  console.error('❌ Type check failed. Please review the remaining errors.');
  process.exit(1);
}
