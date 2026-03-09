const fs = require('fs');
const path = require('path');

const rootDir = path.join(__dirname, '..', '..');
const sharedDir = path.join(rootDir, 'core/shared-ts/ts-packages/extension-shared/src');
const targetDir = path.join(rootDir, 'apps/extension/src/shared');

// Create target directories if they don't exist
const dirsToCreate = [
  path.join(targetDir, 'crypto'),
  path.join(targetDir, 'transport'),
  path.join(targetDir, 'background'),
  path.join(targetDir, 'content'),
  path.join(targetDir, 'types')
];

dirsToCreate.forEach(dir => {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
});

// Copy files from extension-shared to the extension's shared directory
const filesToCopy = [
  { src: 'index.ts', dest: 'crypto/utils.ts' },
  { src: 'types/messages.ts', dest: 'types/messages.ts' },
  { src: 'types/chrome.d.ts', dest: 'types/chrome.d.ts' },
  { src: 'background/service-worker.ts', dest: 'background/service-worker.ts' },
  { src: 'content/content-script.ts', dest: 'content/content-script.ts' }
];

filesToCopy.forEach(({ src, dest }) => {
  const sourcePath = path.join(sharedDir, src);
  const destPath = path.join(targetDir, dest);
  
  if (fs.existsSync(sourcePath)) {
    fs.copyFileSync(sourcePath, destPath);
    console.log(`Copied ${src} to ${destPath}`);
  } else {
    console.warn(`Source file not found: ${sourcePath}`);
  }
});

console.log('Migration complete!');
