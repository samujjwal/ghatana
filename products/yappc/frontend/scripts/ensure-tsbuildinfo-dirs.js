const fs = require('fs');
const path = require('path');

const cacheDir = path.join(process.cwd(), 'node_modules/.cache/tsbuildinfo');

// Ensure the cache directory exists
if (!fs.existsSync(cacheDir)) {
  fs.mkdirSync(cacheDir, { recursive: true });
  console.log(`Created TypeScript build info cache directory: ${cacheDir}`);
}

// Add .gitkeep to ensure the directory is tracked by git
const gitKeepPath = path.join(cacheDir, '.gitkeep');
if (!fs.existsSync(gitKeepPath)) {
  fs.writeFileSync(gitKeepPath, '');
  console.log(`Created .gitkeep in ${cacheDir}`);
}
