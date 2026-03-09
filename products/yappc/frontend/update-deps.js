import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// List of packages to update
const packages = [
  'libs/responsive-layout-manager',
  'libs/visual-style-panel',
  'libs/responsive-breakpoint-editor',
  'libs/design-system-cli',
  'libs/agents',
  'libs/realtime-sync-service',
  'libs/ml',
  'libs/graphql',
  'libs/token-analytics',
  'libs/performance-monitor',
  'libs/designer',
  'libs/layout-templates',
  'libs/page-builder-ui',
  'libs/token-editor',
  'libs/tokens',
  'libs/conflict-resolution-engine',
  'libs/store',
  'apps/web'
];

// Dependencies to update
const depsToUpdate = {
  '@ghatana/yappc-canvas': 'file:../canvas',
  '@ghatana/yappc-designer': 'file:../designer',
  '@ghatana/yappc-types': 'file:../types',
  '@ghatana/yappc-mocks': 'file:../mocks',
  '@ghatana/yappc-ui': 'file:../../../../libs/typescript/ui',
  '@ghatana/ui': 'file:../../../../libs/typescript/ui'
};

// Update package.json files
async function updateDependencies() {
  for (const pkgPath of packages) {
    const pkgJsonPath = path.join(process.cwd(), pkgPath, 'package.json');
    
    try {
      const pkgJson = JSON.parse(await fs.readFile(pkgJsonPath, 'utf8'));
      let updated = false;

      // Update dependencies
      if (pkgJson.dependencies) {
        for (const [dep, version] of Object.entries(depsToUpdate)) {
          if (pkgJson.dependencies[dep] === 'workspace:*') {
            pkgJson.dependencies[dep] = version;
            updated = true;
          }
        }
      }

      // Update devDependencies
      if (pkgJson.devDependencies) {
        for (const [dep, version] of Object.entries(depsToUpdate)) {
          if (pkgJson.devDependencies[dep] === 'workspace:*') {
            pkgJson.devDependencies[dep] = version;
            updated = true;
          }
        }
      }

      // Save if updated
      if (updated) {
        await fs.writeFile(pkgJsonPath, JSON.stringify(pkgJson, null, 2) + '\n');
        console.log(`Updated ${pkgPath}/package.json`);
      }
    } catch (error) {
      console.error(`Error processing ${pkgPath}:`, error.message);
    }
  }
  
  console.log('Dependency update complete!');
}

// Run the update
updateDependencies().catch(console.error);
