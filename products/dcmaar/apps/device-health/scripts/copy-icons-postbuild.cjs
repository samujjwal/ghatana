const fs = require('fs');
const path = require('path');

// Simple, safe postbuild script to copy icons into each dist/<browser>/icons folder.
// It only copies if the source icons dir exists and dist/<browser> exists.

const root = path.resolve(__dirname, '..');
const srcIcons = path.join(root, 'icons');
const distDir = path.join(root, 'dist');

function copyFileSync(srcFile, destFile) {
  try {
    fs.mkdirSync(path.dirname(destFile), { recursive: true });
    fs.copyFileSync(srcFile, destFile);
  } catch (e) {
    // swallow file copy errors — postbuild should not fail the build in production
    console.warn('copy-icons-postbuild: warning copying', srcFile, '->', destFile, e && e.message);
  }
}

function copyIcons() {
  try {
    if (!fs.existsSync(srcIcons)) {
      console.log('copy-icons-postbuild: no icons directory found, skipping');
      return;
    }
    const browsers = fs
      .readdirSync(distDir, { withFileTypes: true })
      .filter((d) => d.isDirectory())
      .map((d) => d.name);
    for (const b of browsers) {
      const dest = path.join(distDir, b, 'icons');
      try {
        fs.mkdirSync(dest, { recursive: true });
      } catch {}
      const files = fs.readdirSync(srcIcons);
      for (const f of files) {
        const s = path.join(srcIcons, f);
        const d = path.join(dest, f);
        copyFileSync(s, d);
      }
      console.log('copy-icons-postbuild: copied icons to', dest);
    }
  } catch (e) {
    console.warn('copy-icons-postbuild: error', e && e.message);
  }
}

copyIcons();
