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
const stateTsconfig = path.join(repoRoot, 'libs', 'state', 'tsconfig.json');
const tempStateTsconfig = path.join(
  repoRoot,
  'libs',
  'state',
  'tsconfig.build.tmp.json'
);
const themeTsconfig = path.join(repoRoot, 'libs', 'theme', 'tsconfig.json');
const shortcutsTsconfig = path.join(repoRoot, 'libs', 'shortcuts', 'tsconfig.json');
const baseUiTsconfig = path.join(repoRoot, 'libs', 'base-ui', 'tsconfig.json');
const developmentUiTsconfig = path.join(repoRoot, 'libs', 'development-ui', 'tsconfig.json');
const configHooksTsconfig = path.join(repoRoot, 'libs', 'config-hooks', 'tsconfig.json');
const initializationUiTsconfig = path.join(repoRoot, 'libs', 'initialization-ui', 'tsconfig.json');
const navigationUiTsconfig = path.join(repoRoot, 'libs', 'navigation-ui', 'tsconfig.json');
const tempUiTsconfig = path.join(
  repoRoot,
  'libs',
  'ui',
  'tsconfig.build.tmp.json'
);

try {
  // 1) build types package
  run('pnpm exec tsc --build libs/types/tsconfig.json', { cwd: repoRoot });

  // 2) build state declaration-only first so UI can consume its d.ts
  // Create a temporary state tsconfig that removes any references to ui to avoid cycles
  function stripJsonComments(text) {
    return text
      .replace(/\/\*[^]*?\*\//g, '') // block comments
      .replace(/(^|[^:\\])\/\/.*$/gm, '$1'); // line comments
  }
  function stripTrailingCommas(text) {
    // remove trailing commas before } or ]
    return text.replace(/,\s*(\}|\])/g, '$1');
  }

  const rawStateText = fs.readFileSync(stateTsconfig, 'utf8');
  const stateCfg = JSON.parse(
    stripTrailingCommas(stripJsonComments(rawStateText))
  );
  const stateTmp = JSON.parse(JSON.stringify(stateCfg));
  // remove references to ui if any
  stateTmp.references = (stateTmp.references || []).filter(
    (r) => !r.path || !r.path.includes('/ui')
  );
  fs.writeFileSync(tempStateTsconfig, JSON.stringify(stateTmp, null, 2));
  run('pnpm exec tsc -p libs/state/tsconfig.build.tmp.json', { cwd: repoRoot });

  // 3) build theme declaration-only so UI can consume its d.ts without pulling source files
  run('pnpm exec tsc -p libs/theme/tsconfig.json', { cwd: repoRoot });

  // 4) build shortcuts declaration-only so UI can consume its d.ts without pulling source files
  run('pnpm exec tsc -p libs/shortcuts/tsconfig.json', { cwd: repoRoot });

  // 5) build base-ui declaration-only so UI can consume its d.ts without pulling source files
  run('pnpm exec tsc -p libs/base-ui/tsconfig.json', { cwd: repoRoot });

  // 6) build development-ui declaration-only so UI can consume its d.ts without pulling source files
  run(`pnpm exec tsc -p ${path.relative(repoRoot, developmentUiTsconfig)}`, {
    cwd: repoRoot,
  });

  // 7) build config-hooks declaration-only so UI can consume its d.ts without pulling source files
  run(`pnpm exec tsc -p ${path.relative(repoRoot, configHooksTsconfig)}`, {
    cwd: repoRoot,
  });

  // 8) build initialization-ui declaration-only so UI can consume its d.ts without pulling source files
  run(`pnpm exec tsc -p ${path.relative(repoRoot, initializationUiTsconfig)}`, {
    cwd: repoRoot,
  });

  // 9) build navigation-ui declaration-only so UI can consume its d.ts without pulling source files
  run(`pnpm exec tsc -p ${path.relative(repoRoot, navigationUiTsconfig)}`, {
    cwd: repoRoot,
  });

  // 10) build UI declaration-only, but instruct it to resolve extracted package imports to emitted d.ts
  run('rm -rf libs/ui/dist/types || true', { cwd: repoRoot });
  const uiCfgPath = path.join(repoRoot, 'libs', 'ui', 'tsconfig.types.json');
  const rawUiText = fs.readFileSync(uiCfgPath, 'utf8');
  const uiCfg = JSON.parse(stripTrailingCommas(stripJsonComments(rawUiText)));
  const uiTmp = JSON.parse(JSON.stringify(uiCfg));
  uiTmp.compilerOptions = uiTmp.compilerOptions || {};
  uiTmp.compilerOptions.paths = uiTmp.compilerOptions.paths || {};
  const stateDeclIndex = path.join(
    repoRoot,
    'libs',
    'state',
    'dist',
    'types',
    'index.d.ts'
  );
  const themeDeclIndex = path.join(
    repoRoot,
    'libs',
    'theme',
    'dist',
    'types',
    'index.d.ts'
  );
  const shortcutsDeclIndex = path.join(
    repoRoot,
    'libs',
    'shortcuts',
    'dist',
    'types',
    'index.d.ts'
  );
  const baseUiDeclIndex = path.join(
    repoRoot,
    'libs',
    'base-ui',
    'dist',
    'types',
    'index.d.ts'
  );
  const configHooksDeclIndex = path.join(
    repoRoot,
    'libs',
    'config-hooks',
    'dist',
    'types',
    'index.d.ts'
  );
  const initializationUiDeclIndex = path.join(
    repoRoot,
    'libs',
    'initialization-ui',
    'dist',
    'types',
    'index.d.ts'
  );
  const navigationUiDeclIndex = path.join(
    repoRoot,
    'libs',
    'navigation-ui',
    'dist',
    'types',
    'index.d.ts'
  );
  uiTmp.compilerOptions.paths['@yappc/state'] = [stateDeclIndex];
  uiTmp.compilerOptions.paths['@yappc/state/*'] = [
    path.join(repoRoot, 'libs', 'state', 'dist', 'types', '*'),
  ];
  uiTmp.compilerOptions.paths['@yappc/theme'] = [themeDeclIndex];
  uiTmp.compilerOptions.paths['@yappc/theme/*'] = [
    path.join(repoRoot, 'libs', 'theme', 'dist', 'types', '*'),
  ];
  uiTmp.compilerOptions.paths['@yappc/shortcuts'] = [shortcutsDeclIndex];
  uiTmp.compilerOptions.paths['@yappc/shortcuts/*'] = [
    path.join(repoRoot, 'libs', 'shortcuts', 'dist', 'types', '*'),
  ];
  uiTmp.compilerOptions.paths['@yappc/base-ui'] = [baseUiDeclIndex];
  uiTmp.compilerOptions.paths['@yappc/base-ui/*'] = [
    path.join(repoRoot, 'libs', 'base-ui', 'dist', 'types', '*'),
  ];
  uiTmp.compilerOptions.paths['@yappc/config-hooks'] = [configHooksDeclIndex];
  uiTmp.compilerOptions.paths['@yappc/config-hooks/*'] = [
    path.join(repoRoot, 'libs', 'config-hooks', 'dist', 'types', '*'),
  ];
  uiTmp.compilerOptions.paths['@yappc/initialization-ui'] = [initializationUiDeclIndex];
  uiTmp.compilerOptions.paths['@yappc/initialization-ui/*'] = [
    path.join(repoRoot, 'libs', 'initialization-ui', 'dist', 'types', '*'),
  ];
  uiTmp.compilerOptions.paths['@yappc/navigation-ui'] = [navigationUiDeclIndex];
  uiTmp.compilerOptions.paths['@yappc/navigation-ui/*'] = [
    path.join(repoRoot, 'libs', 'navigation-ui', 'dist', 'types', '*'),
  ];
  uiTmp.compilerOptions.paths['@ghatana/yappc-store'] = [stateDeclIndex];
  uiTmp.compilerOptions.paths['@ghatana/yappc-store/*'] = [
    path.join(repoRoot, 'libs', 'state', 'dist', 'types', '*'),
  ];
  // @ghatana/yappc-state removed in P2-6 - deprecated package deleted
  fs.writeFileSync(tempUiTsconfig, JSON.stringify(uiTmp, null, 2));
  run('pnpm exec tsc -p libs/ui/tsconfig.build.tmp.json', { cwd: repoRoot });
  // 11) cleanup temporary tsconfigs
  fs.unlinkSync(tempStateTsconfig);
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

  // 12) optional: run a workspace build to check everything else
  run('pnpm -w -s exec tsc --build --verbose', { cwd: repoRoot });

  console.log('\nAll declaration builds finished.');
} catch (err) {
  console.error('\nBuild script failed:', err.message);
  process.exit(err.status || 1);
}
