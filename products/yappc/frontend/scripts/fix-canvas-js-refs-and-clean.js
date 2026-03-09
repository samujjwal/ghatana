#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const WORKSPACE_ROOT = path.resolve(__dirname, '..');
const CANVAS_SRC = path.join(WORKSPACE_ROOT, 'libs', 'canvas', 'src');

function findFiles(dir, predicate) {
  const results = [];
  if (!fs.existsSync(dir)) return results;
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
      results.push(...findFiles(full, predicate));
    } else if (entry.isFile()) {
      if (!predicate || predicate(full)) results.push(full);
    }
  }
  return results;
}

function getCanvasJsFiles() {
  return findFiles(CANVAS_SRC, (f) => f.endsWith('.js'));
}

function chooseTsEquivalent(jsFile) {
  const base = jsFile.replace(/\.js$/, '');
  const ts = `${base}.ts`;
  const tsx = `${base}.tsx`;
  if (fs.existsSync(ts)) return ts;
  if (fs.existsSync(tsx)) return tsx;
  return null;
}

function referencedAnywhere(fileName) {
  try {
    // search for occurrences of the explicit filename (e.g. myFile.js) in all ts/tsx/js files
    const cmd = `grep -RIn --exclude-dir=node_modules --exclude-dir=.git --include=\"*.ts\" --include=\"*.tsx\" --include=\"*.js\" --include=\"*.jsx\" \"${fileName}\" ${WORKSPACE_ROOT}`;
    const out = execSync(cmd, { encoding: 'utf8', stdio: 'pipe' });
    return out.trim().length > 0;
  } catch (e) {
    return false;
  }
}

// Note: a more advanced parser could be used, but for now we perform a conservative
// replacement of occurrences like "fileName.js" or 'fileName.js' to the new extension.

function safeReplaceReferences(jsFile, replacementExt) {
  const fileName = path.basename(jsFile); // e.g. foo.js
  try {
    const cmd = `grep -RIl --exclude-dir=node_modules --exclude-dir=.git --include=\"*.ts\" --include=\"*.tsx\" --include=\"*.js\" --include=\"*.jsx\" \"${fileName}\" ${WORKSPACE_ROOT} || true`;
    const out = execSync(cmd, { encoding: 'utf8', stdio: 'pipe' });
    const files = out
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean);
    let changed = false;
    for (const f of files) {
      // For each candidate file, perform a conservative in-file replacement only for occurrences
      // of the exact filename inside single or double quotes: "foo.js" or 'foo.js'.
      const content = fs.readFileSync(f, 'utf8');
      const escaped = fileName.replace(/[-/\\^$*+?.()|[\]{}]/g, '\\$&');
      const regex = new RegExp(`(["'])([^\\n]*?${  escaped  })(["'])`, 'g');
      const newContent = content.replace(regex, (m, q1, inner, q2) => {
        if (inner.endsWith('.js')) {
          return q1 + inner.replace(/\.js$/, `.${  replacementExt}`) + q2;
        }
        return m;
      });
      if (newContent !== content) {
        fs.writeFileSync(f, newContent, 'utf8');
        changed = true;
        console.log(
          '   Updated references in',
          path.relative(WORKSPACE_ROOT, f)
        );
      }
    }
    return changed;
  } catch (err) {
    console.warn('   ⚠️ grep failed for', fileName, err.message);
    return false;
  }
}

function main() {
  if (!fs.existsSync(CANVAS_SRC)) {
    console.error('Canvas src directory not found:', CANVAS_SRC);
    process.exit(1);
  }

  const jsFiles = getCanvasJsFiles();
  console.log(`Found ${jsFiles.length} .js files under libs/canvas/src`);

  let totalReplaced = 0;
  let totalRemoved = 0;
  for (const js of jsFiles) {
    const rel = path.relative(WORKSPACE_ROOT, js);
    const tsEquiv = chooseTsEquivalent(js);
    if (!tsEquiv) {
      console.log(` - Skipping ${rel} (no .ts/.tsx equivalent)`);
      continue;
    }
    const tsExt = path.extname(tsEquiv).slice(1); // 'ts' or 'tsx'
    console.log(` - Processing ${rel} -> replace references to .${tsExt}`);

    const replaced = safeReplaceReferences(js, tsExt);
    if (replaced) totalReplaced++;

    // Re-check references to js file by filename
    const fileName = path.basename(js);
    const stillReferenced = referencedAnywhere(fileName);
    if (!stillReferenced) {
      // safe to remove
      try {
        fs.unlinkSync(js);
        console.log(`   🗑️ Removed ${rel}`);
        totalRemoved++;
      } catch (e) {
        console.warn(`   ❌ Failed to remove ${rel}: ${e.message}`);
      }
    } else {
      console.log(`   ⚠️ ${rel} still referenced; leaving in place`);
    }
  }

  const report = {
    timestamp: new Date().toISOString(),
    action: 'fix-canvas-js-refs-and-clean',
    canvasJsFilesScanned: jsFiles.length,
    referencesUpdated: totalReplaced,
    filesRemoved: totalRemoved,
  };
  const reportPath = path.join(
    WORKSPACE_ROOT,
    'docs',
    'PHASE_D_CLEANUP_REPORT.json'
  );
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
  console.log('\nReport written to', path.relative(WORKSPACE_ROOT, reportPath));
  console.log(
    `Updated references: ${totalReplaced}, files removed: ${totalRemoved}`
  );
}

if (require.main === module) main();
