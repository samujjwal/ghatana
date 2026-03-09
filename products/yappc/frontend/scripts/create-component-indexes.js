#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const componentsDir = path.join(__dirname, '../libs/ui/src/components');

// Get all directories in components
const dirs = fs.readdirSync(componentsDir, { withFileTypes: true })
  .filter(dirent => dirent.isDirectory())
  .map(dirent => dirent.name);

dirs.forEach(dir => {
  const dirPath = path.join(componentsDir, dir);
  const indexPath = path.join(dirPath, 'index.ts');
  
  // Skip if index already exists
  if (fs.existsSync(indexPath)) {
    console.log(`✓ ${dir}/index.ts already exists`);
    return;
  }
  
  // Check if there's a main component file
  const componentFile = path.join(dirPath, `${dir}.tsx`);
  if (!fs.existsSync(componentFile)) {
    console.log(`⚠ Skipping ${dir} - no ${dir}.tsx found`);
    return;
  }
  
  // Create index file
  const indexContent = `export * from './${dir}';\n`;
  fs.writeFileSync(indexPath, indexContent);
  console.log(`✓ Created ${dir}/index.ts`);
});

console.log('\nDone!');
