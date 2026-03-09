const fs = require('fs');
const path = require('path');

const tsconfigPath = path.join(__dirname, '..', 'products', 'extension', 'tsconfig.json');

// Create a clean tsconfig.json
const cleanTsConfig = {
  "$schema": "https://json.schemastore.org/tsconfig",
  "extends": "./config/tsconfig.base.json",
  "compilerOptions": {
    /* Base Options */
    "esModuleInterop": true,
    "strict": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "allowImportingTsExtensions": true,
    
    /* Type Checking */
    "strictNullChecks": true,
    "strictFunctionTypes": true,
    "strictBindCallApply": true,
    "noImplicitAny": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noImplicitReturns": true,
    "noFallthroughCasesInSwitch": true,
    
    /* Module Resolution */
    "moduleDetection": "force",
    "allowSyntheticDefaultImports": true,
    "preserveSymlinks": true,
    
    /* Emit */
    "declaration": true,
    "declarationMap": true,
    "sourceMap": true,
    "inlineSources": true,
    "outDir": "dist",
    
    /* Language and Environment */
    "target": "ES2020",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    
    /* Paths */
    "baseUrl": ".",
    "paths": {
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
    }
  },
  "include": [
    "src/**/*.ts",
    "src/**/*.tsx",
    "src/**/*.d.ts",
    "tests/**/*.ts",
    "tests/**/*.tsx"
  ],
  "exclude": [
    "node_modules",
    "dist",
    "**/*.spec.ts",
    "**/*.test.ts",
    "**/*.spec.tsx",
    "**/*.test.tsx"
  ]
};

// Write the clean tsconfig to disk
fs.writeFileSync(
  tsconfigPath,
  JSON.stringify(cleanTsConfig, null, 2) + '\n',
  'utf8'
);

console.log('✅ Successfully created a clean tsconfig.json');
console.log('\nYou can now run: pnpm type-check');
