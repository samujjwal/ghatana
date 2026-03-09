#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');

function walk(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const ent of entries) {
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) {
      walk(p);
    } else if (ent.isFile()) {
      if (/\.(js|js.map|map|d\.ts)$/.test(ent.name)) {
        // only remove generated files under a src/ directory
        if (p.includes(`${path.sep  }src${  path.sep}`)) {
          console.log('unlink', p);
          try { fs.unlinkSync(p); } catch (e) { console.error('failed to unlink', p, e.message); }
        }
      }
    }
  }
}

walk(root);
