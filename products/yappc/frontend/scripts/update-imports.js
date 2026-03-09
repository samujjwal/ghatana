const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Configuration
const ROOT_DIR = path.join(__dirname, '..');
const EXTENSIONS = ['.ts', '.tsx', '.js', '.jsx', '.json', '.md'];
const FILES_TO_SKIP = ['node_modules', '.next', 'dist', 'build'];

// Replacement rules
const REPLACEMENTS = [
  // Replace @ghatana/ui imports with @ghatana/yappc-ui
  {
    pattern: /from ['"]@ghatana\/ui(['"])/g,
    replacement: 'from \'@ghatana/yappc-ui\''
  },
  // Fix any relative paths that might be affected
  {
    pattern: /from ['"]\.\.\/node_modules\/@ghatana\/ui(['"])/g,
    replacement: 'from \'@ghatana/yappc-ui\''
  }
];

// Process a single file
function processFile(filePath) {
  try {
    let content = fs.readFileSync(filePath, 'utf8');
    let updated = false;

    REPLACEMENTS.forEach(({ pattern, replacement }) => {
      const newContent = content.replace(pattern, replacement);
      if (newContent !== content) {
        content = newContent;
        updated = true;
      }
    });

    if (updated) {
      fs.writeFileSync(filePath, content, 'utf8');
      console.log(`✅ Updated: ${path.relative(ROOT_DIR, filePath)}`);
      return true;
    }
  } catch (error) {
    console.error(`❌ Error processing ${filePath}:`, error.message);
  }
  return false;
}

// Recursively process directory
function processDirectory(dir) {
  const files = fs.readdirSync(dir);
  let count = 0;

  for (const file of files) {
    if (FILES_TO_SKIP.includes(file)) continue;
    
    const fullPath = path.join(dir, file);
    const stat = fs.statSync(fullPath);

    if (stat.isDirectory()) {
      count += processDirectory(fullPath);
    } else if (EXTENSIONS.includes(path.extname(file).toLowerCase())) {
      if (processFile(fullPath)) {
        count++;
      }
    }
  }

  return count;
}

// Update package.json files
function updatePackageJson() {
  const packageJsonPath = path.join(ROOT_DIR, 'package.json');
  if (fs.existsSync(packageJsonPath)) {
    const pkg = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
    let updated = false;

    // Update dependencies
    ['dependencies', 'devDependencies', 'peerDependencies'].forEach(depType => {
      if (pkg[depType]?.['@ghatana/ui']) {
        pkg[depType]['@ghatana/yappc-ui'] = pkg[depType]['@ghatana/ui'];
        delete pkg[depType]['@ghatana/ui'];
        updated = true;
      }
    });

    if (updated) {
      fs.writeFileSync(packageJsonPath, JSON.stringify(pkg, null, 2) + '\n');
      console.log('✅ Updated package.json');
      return true;
    }
  }
  return false;
}

// Main function
function main() {
  console.log('🚀 Starting import updates...\n');
  
  // Update package.json files first
  const packageUpdated = updatePackageJson();
  
  // Process all source files
  const fileCount = processDirectory(ROOT_DIR);
  
  console.log(`\n✨ Done! Updated ${fileCount} files${packageUpdated ? ' and package.json' : ''}.`);
  
  // Run any post-update commands
  try {
    console.log('\n🔧 Running post-update commands...');
    execSync('npm install', { stdio: 'inherit' });
    console.log('✅ Dependencies updated successfully');
  } catch (error) {
    console.error('❌ Error running post-update commands:', error.message);
  }
}

main();
