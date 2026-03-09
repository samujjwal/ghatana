const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Get all TypeScript files in the project
const findFiles = (dir, fileList = []) => {
  const files = fs.readdirSync(dir);
  
  files.forEach(file => {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);
    
    if (stat.isDirectory() && !['node_modules', 'dist', '.git'].includes(file)) {
      findFiles(filePath, fileList);
    } else if (file.endsWith('.ts') || file.endsWith('.tsx')) {
      fileList.push(filePath);
    }
  });
  
  return fileList;
};

// Common patterns that need to be fixed
const patterns = [
  // Fix property access on Record<string, any>
  { 
    regex: /\.__DCMAAR_TEST_HELPERS\b/g, 
    replace: "['__DCMAAR_TEST_HELPERS']" 
  },
  { 
    regex: /\.dcmaar\b/g, 
    replace: "['dcmaar']" 
  },
  { 
    regex: /\.mountOptions\b/g, 
    replace: "['mountOptions']" 
  },
  // Add more patterns as needed
];

// Process each file
const processFile = (filePath) => {
  let content = fs.readFileSync(filePath, 'utf8');
  let updated = false;
  
  patterns.forEach(({ regex, replace }) => {
    if (regex.test(content)) {
      content = content.replace(regex, replace);
      updated = true;
    }
  });
  
  if (updated) {
    fs.writeFileSync(filePath, content, 'utf8');
    console.log(`Updated: ${filePath}`);
    return true;
  }
  
  return false;
};

// Main function
const main = () => {
  const rootDir = path.join(__dirname, '..', 'products', 'extension');
  const files = findFiles(rootDir);
  let updatedCount = 0;
  
  console.log(`Found ${files.length} TypeScript files to check`);
  
  files.forEach(filePath => {
    if (processFile(filePath)) {
      updatedCount++;
    }
  });
  
  console.log(`\nUpdated ${updatedCount} files`);
  
  // Run type check to verify
  console.log('\nRunning type check...');
  try {
    execSync('pnpm type-check', { stdio: 'inherit', cwd: rootDir });
    console.log('\nType check passed successfully!');
  } catch (_error) {
    console.error('\nType check failed. Please review the remaining errors.');
    process.exit(1);
  }
};

main();
