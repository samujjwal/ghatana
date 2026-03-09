#!/usr/bin/env node
// TypeScript version of setup-sink-addon helper
import fs from 'fs';
import path from 'path';

function main(): void {
  const defaults = {
    outDir: path.resolve(process.cwd(), 'tmp', 'addon-sim'),
    payload: `window.dcmaar_test_addon = {
  sendMetric: (payload) => window.postMessage({ __dcmaar: true, cmd: 'ingest', payload }, window.location.origin)
};
`,
  };

  const opts = process.argv.slice(2).reduce<typeof defaults>((acc, arg) => {
    if (arg.startsWith('--out=')) {
      acc.outDir = path.resolve(process.cwd(), arg.split('=')[1] ?? acc.outDir);
    } else if (arg.startsWith('--payload-file=')) {
      const filePath = path.resolve(process.cwd(), arg.split('=')[1] ?? '');
      if (fs.existsSync(filePath)) {
        acc.payload = fs.readFileSync(filePath, 'utf8');
      }
    }
    return acc;
  }, defaults);

  const outDir = opts.outDir;
  fs.mkdirSync(outDir, { recursive: true });
  const addonPath = path.join(outDir, 'addon-sim.js');
  fs.writeFileSync(addonPath, opts.payload);
  console.log('Created addon-sim at', addonPath);
}

main();
