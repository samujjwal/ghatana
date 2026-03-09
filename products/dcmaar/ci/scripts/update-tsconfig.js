const fs = require('fs');
const path = require('path');

const tsconfigPath = path.join(__dirname, '..', 'products', 'extension', 'tsconfig.json');

// Read the original tsconfig file
const tsconfig = require(tsconfigPath);

// Define the new paths object with all necessary mappings
const newPaths = {
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
};

// Update the paths in the tsconfig
tsconfig.compilerOptions.paths = newPaths;

// Write the updated tsconfig back to disk
fs.writeFileSync(
  tsconfigPath,
  JSON.stringify(tsconfig, null, 2) + '\n',
  'utf8'
);

console.log('✅ Successfully updated tsconfig.json with clean path mappings');
console.log('\nYou can now run: pnpm type-check');
