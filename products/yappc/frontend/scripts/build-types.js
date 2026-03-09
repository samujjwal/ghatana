#!/usr/bin/env node
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

function run(cmd, options = {}) {
  console.log('$', cmd);
  execSync(cmd, { stdio: 'inherit', ...options });
}

const repoRoot = path.resolve(__dirname, '..');
const uiDeclIndex = path.join(
  repoRoot,
  'libs',
  'ui',
  'dist',
  'types',
  'libs',
  'ui',
  'src',
  'index.d.ts'
);
const storeTsconfig = path.join(repoRoot, 'libs', 'store', 'tsconfig.json');
const tempStoreTsconfig = path.join(
  repoRoot,
  'libs',
  'store',
  'tsconfig.build.tmp.json'
);
const tempUiTsconfig = path.join(
  repoRoot,
  'libs',
  'ui',
  'tsconfig.build.tmp.json'
);

try {
  // 1) build types package
  run('pnpm exec tsc --build libs/types/tsconfig.json', { cwd: repoRoot });

  // 2) build store declaration-only first so UI can consume its d.ts
  // Create a temporary store tsconfig that removes any references to ui to avoid cycles
  function stripJsonComments(text) {
    return text
      .replace(/\/\*[^]*?\*\//g, '') // block comments
      .replace(/(^|[^:\\])\/\/.*$/gm, '$1'); // line comments
  }
  function stripTrailingCommas(text) {
    // remove trailing commas before } or ]
    return text.replace(/,\s*(\}|\])/g, '$1');
  }

  const rawStoreText = fs.readFileSync(storeTsconfig, 'utf8');
  const storeCfg = JSON.parse(
    stripTrailingCommas(stripJsonComments(rawStoreText))
  );
  const storeTmp = JSON.parse(JSON.stringify(storeCfg));
  // remove references to ui if any
  storeTmp.references = (storeTmp.references || []).filter(
    (r) => !r.path || !r.path.includes('/ui')
  );
  fs.writeFileSync(tempStoreTsconfig, JSON.stringify(storeTmp, null, 2));
  run('pnpm exec tsc -p libs/store/tsconfig.build.tmp.json', { cwd: repoRoot });

  // 3) build UI declaration-only, but instruct it to resolve @ghatana/yappc-store to the emitted store d.ts
  run('rm -rf libs/ui/dist/types || true', { cwd: repoRoot });
  const uiCfgPath = path.join(repoRoot, 'libs', 'ui', 'tsconfig.types.json');
  const rawUiText = fs.readFileSync(uiCfgPath, 'utf8');
  const uiCfg = JSON.parse(stripTrailingCommas(stripJsonComments(rawUiText)));
  const uiTmp = JSON.parse(JSON.stringify(uiCfg));
  uiTmp.compilerOptions = uiTmp.compilerOptions || {};
  uiTmp.compilerOptions.paths = uiTmp.compilerOptions.paths || {};
  const storeDeclIndex = path.join(
    repoRoot,
    'libs',
    'store',
    'dist',
    'types',
    'index.d.ts'
  );
  uiTmp.compilerOptions.paths['@ghatana/yappc-store'] = [storeDeclIndex];
  uiTmp.compilerOptions.paths['@ghatana/yappc-store/*'] = [
    path.join(repoRoot, 'libs', 'store', 'dist', 'types', '*'),
  ];
  fs.writeFileSync(tempUiTsconfig, JSON.stringify(uiTmp, null, 2));
  run('pnpm exec tsc -p libs/ui/tsconfig.build.tmp.json', { cwd: repoRoot });
  // 4) cleanup temporary tsconfigs
  fs.unlinkSync(tempStoreTsconfig);
  fs.unlinkSync(tempUiTsconfig);

  // Remove the temporary UI stub if UI emitted real declarations (overwrite will have happened)
  try {
    const realUiIndex = path.join(
      repoRoot,
      'libs',
      'ui',
      'dist',
      'types',
      'index.d.ts'
    );
    if (fs.existsSync(realUiIndex)) {
      // If the stub file content differs from the real output, remove the stub backup
      // (we wrote directly to the same path, so no extra cleanup required). Nothing to do.
    } else {
      // If real UI index wasn't produced, leave the stub in place so later steps can rely on it.
    }
  } catch (e) {
    // ignore cleanup errors
  }

  // 5) optional: run a workspace build to check everything else
  run('pnpm -w -s exec tsc --build --verbose', { cwd: repoRoot });

  console.log('\nAll declaration builds finished.');
} catch (err) {
  console.error('\nBuild script failed:', err.message);
  process.exit(err.status || 1);
}
