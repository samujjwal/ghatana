#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const libsToFix = [
  'libs/mocks',
  'libs/types',
  'libs/diagram',
  'libs/canvas',
  'libs/token-analytics',
  'libs/performance-monitor',
  'libs/test-helpers',
  'libs/token-editor',
  'libs/tokens',
  'libs/store',
  'libs/design-system-core',
  'libs/design-system-cli',
  'libs/ai',
  'libs/agents',
  'libs/ml'
];

const rootDir = path.join(__dirname, '..');

libsToFix.forEach(libPath => {
  const packageJsonPath = path.join(rootDir, libPath, 'package.json');
  
  if (!fs.existsSync(packageJsonPath)) {
    console.log(`Skipping ${libPath} - package.json not found`);
    return;
  }

  try {
    const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
    let modified = false;

    // Update main field
    if (packageJson.main && packageJson.main.includes('dist')) {
      packageJson.main = packageJson.main.replace(/\.\/dist\/(.+)\.js$/, './src/$1.ts');
      modified = true;
      console.log(`Updated main in ${libPath}`);
    }

    // Update types field
    if (packageJson.types && packageJson.types.includes('dist')) {
      packageJson.types = packageJson.types.replace(/\.\/dist\/types\/(.+)\.d\.ts$/, './src/$1.ts')
                                           .replace(/\.\/dist\/(.+)\.d\.ts$/, './src/$1.ts');
      modified = true;
      console.log(`Updated types in ${libPath}`);
    }

    // Update exports field
    if (packageJson.exports) {
      const updateExports = (obj) => {
        for (const key in obj) {
          if (typeof obj[key] === 'string' && obj[key].includes('dist')) {
            obj[key] = obj[key].replace(/\.\/dist\/(.+)\.js$/, './src/$1.ts')
                               .replace(/\.\/dist\/types\/(.+)\.d\.ts$/, './src/$1.ts')
                               .replace(/\.\/dist\/(.+)\.d\.ts$/, './src/$1.ts');
            modified = true;
          } else if (typeof obj[key] === 'object') {
            updateExports(obj[key]);
          }
        }
      };
      updateExports(packageJson.exports);
      if (modified) {
        console.log(`Updated exports in ${libPath}`);
      }
    }

    if (modified) {
      fs.writeFileSync(packageJsonPath, `${JSON.stringify(packageJson, null, 2)  }\n`);
      console.log(`✓ Fixed ${libPath}/package.json`);
    } else {
      console.log(`- No changes needed for ${libPath}`);
    }
  } catch (error) {
    console.error(`Error processing ${libPath}:`, error.message);
  }
});

console.log('\nDone!');
