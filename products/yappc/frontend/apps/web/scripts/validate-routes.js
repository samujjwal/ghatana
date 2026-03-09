const fs = require('fs');
const path = require('path');

const ROUTES_FILE = path.join(__dirname, '../src/router/routes.tsx');
const SRC_DIR = path.join(__dirname, '../src/router'); // Relative imports start from here

if (!fs.existsSync(ROUTES_FILE)) {
  console.error(`Routes file not found at ${ROUTES_FILE}`);
  process.exit(1);
}

const content = fs.readFileSync(ROUTES_FILE, 'utf8');
const regex = /lazy\(\(\)\s*=>\s*import\(['"](.+)['"]\)\)/g;

let match;
let errors = 0;
let checked = 0;

console.log('Validating lazy route imports...');

while ((match = regex.exec(content)) !== null) {
  const importPath = match[1];
  checked++;

  // Resolve absolute path
  // Since imports are relative to the file they are in (src/router/routes.tsx)
  let resolvedPath = path.resolve(SRC_DIR, importPath);

  // Checking .tsx, .ts, .jsx, .js, or directory index
  const extensions = ['.tsx', '.ts', '.jsx', '.js', '/index.tsx', '/index.ts'];
  let exists = false;

  // Check exact file first (if extension provided? usually not in react imports)
  if (fs.existsSync(resolvedPath) && fs.statSync(resolvedPath).isFile()) {
    exists = true;
  } else {
    for (const ext of extensions) {
      if (fs.existsSync(resolvedPath + ext)) {
        exists = true;
        break;
      }
    }
  }

  if (!exists) {
    console.error(
      `❌ Missing module: ${importPath} (Resolved: ${resolvedPath})`
    );
    errors++;
  }
}

console.log(`Checked ${checked} imports.`);
if (errors > 0) {
  console.error(`Found ${errors} missing route modules.`);
  process.exit(1);
} else {
  console.log('All route imports are valid! 🎉');
  process.exit(0);
}
