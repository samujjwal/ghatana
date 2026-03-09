const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Configuration
const EXTENSION_DIR = path.join(__dirname, '..', 'products', 'extension');
const FIXES = [
  // Fix 1: Update manifest.config.ts string literals
  {
    file: 'manifest.config.ts',
    patterns: [
      {
        search: /'app\['dcmaar'\]:\/\/\*'/g,
        replace: "'app.dcmaar://*'"
      },
      {
        search: /'https:\/\/api-dev\['dcmaar'\]\.example\.com'/g,
        replace: "'https://api-dev.dcmaar.example.com'"
      },
      {
        search: /'https:\/\/api-staging\['dcmaar'\]\.example\.com'/g,
        replace: "'https://api-staging.dcmaar.example.com'"
      },
      {
        search: /'https:\/\/api\['dcmaar'\]\.example\.com'/g,
        replace: "'https://api.dcmaar.example.com'"
      }
    ]
  },
  // Fix 2: Fix native bridge connection
  {
    file: 'src/communication/bridge/nativeBridge.ts',
    patterns: [
      {
        search: /chrome\.runtime\.connectNative\('com\['dcmaar'\]\.native_host'\)/g,
        replace: "chrome.runtime.connectNative('com.dcmaar.native_host')"
      }
    ]
  },
  // Fix 3: Fix index signature property access
  {
    file: 'src/ui/options.ts',
    patterns: [
      {
        search: /\.__DCMAAR_TEST_HELPERS\b/g,
        replace: "['__DCMAAR_TEST_HELPERS']"
      },
      {
        search: /\.dcmaar\b/g,
        replace: "['dcmaar']"
      },
      {
        search: /\.mountOptions\b/g,
        replace: "['mountOptions']"
      }
    ]
  }
];

// Apply fixes
function applyFixes() {
  let fixedFiles = 0;
  
  for (const fix of FIXES) {
    const filePath = path.join(EXTENSION_DIR, fix.file);
    
    if (!fs.existsSync(filePath)) {
      console.warn(`File not found: ${filePath}`);
      continue;
    }
    
    let content = fs.readFileSync(filePath, 'utf8');
    let updated = false;
    
    for (const pattern of fix.patterns) {
      if (pattern.search.test(content)) {
        content = content.replace(pattern.search, pattern.replace);
        updated = true;
      }
    }
    
    if (updated) {
      fs.writeFileSync(filePath, content, 'utf8');
      console.log(`✅ Fixed ${fix.file}`);
      fixedFiles++;
    }
  }
  
  console.log(`\nFixed ${fixedFiles} files`);
  return fixedFiles > 0;
}

// Run type check
function runTypeCheck() {
  console.log('\nRunning type check...');
  try {
    execSync('pnpm type-check', { 
      stdio: 'inherit', 
      cwd: EXTENSION_DIR 
    });
    console.log('\n✅ Type check passed!');
    return true;
  } catch (_error) {
    console.error('\n❌ Type check failed. Please review the remaining errors.');
    return false;
  }
}

// Main function
function main() {
  console.log('🚀 Starting TypeScript error fixes...');
  
  // Apply all fixes
  const fixed = applyFixes();
  
  if (fixed) {
    console.log('\n🔍 Verifying fixes with type check...');
    runTypeCheck();
  } else {
    console.log('\nℹ️ No files needed fixing.');
  }
  
  console.log('\n✨ Done!');
}

// Run the script
main();
