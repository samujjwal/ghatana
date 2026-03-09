const fs = require('fs');
const path = require('path');

const packageJsonPath = path.join(__dirname, 'package.json');
const packageJson = require(packageJsonPath);

// Add new scripts if they don't exist
const newScripts = {
  "start:bridge": "NODE_ENV=development ts-node src/libs/bridge/bridgeService.ts",
  "dev:bridge": "nodemon --watch 'src/libs/bridge/**/*.ts' --exec 'ts-node' src/libs/bridge/bridgeService.ts",
  "build:bridge": "tsc -p tsconfig.bridge.json",
  "serve:bridge": "node dist/libs/bridge/bridgeService.js"
};

// Update the scripts section
packageJson.scripts = {
  ...packageJson.scripts,
  ...newScripts
};

// Update the dev:all script to include the bridge service
if (packageJson.scripts['dev:all']) {
  packageJson.scripts['dev:all'] = packageJson.scripts['dev:all']
    .replace(
      'concurrently -n VITE,UPDATE,TAURI',
      'concurrently -n VITE,UPDATE,TAURI,BRIDGE -c bgBlue.bold,bgGreen.bold,bgMagenta.bold,yellow.bold'
    )
    .replace('"pnpm dev:bridge"', '')
    .replace('" \"', '\" \"pnpm dev:bridge\" \"') + '\"';
}

// Write the updated package.json back to disk
fs.writeFileSync(
  packageJsonPath,
  JSON.stringify(packageJson, null, 2) + '\n',
  'utf8'
);

console.log('Updated package.json with bridge service scripts');
