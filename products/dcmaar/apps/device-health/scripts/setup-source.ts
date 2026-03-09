#!/usr/bin/env node
// TypeScript version of setup-source helper
import fs from 'fs';
import path from 'path';

type SourceConfig = {
  version: string;
  autoExport: boolean;
  maxLocalEvents: number;
};

type CliOptions = {
  outDir: string;
  version: string;
  autoExport: boolean;
  maxLocalEvents: number;
};

function parseArgs(argv: string[]): CliOptions {
  const defaults: CliOptions = {
    outDir: path.resolve(process.cwd(), 'tmp', 'fs-source'),
    version: 'test-1.0',
    autoExport: false,
    maxLocalEvents: 1000,
  };

  return argv.reduce<CliOptions>((acc, arg) => {
    if (arg.startsWith('--out=')) {
      acc.outDir = path.resolve(process.cwd(), arg.split('=')[1] ?? acc.outDir);
    } else if (arg.startsWith('--version=')) {
      acc.version = arg.split('=')[1] ?? acc.version;
    } else if (arg === '--auto-export' || arg === '--auto-export=true') {
      acc.autoExport = true;
    } else if (arg === '--auto-export=false') {
      acc.autoExport = false;
    } else if (arg.startsWith('--max-local-events=')) {
      const value = Number(arg.split('=')[1]);
      if (!Number.isNaN(value) && value > 0) {
        acc.maxLocalEvents = value;
      }
    }
    return acc;
  }, defaults);
}

function writeConfig(outDir: string, config: SourceConfig): void {
  fs.mkdirSync(outDir, { recursive: true });

  fs.writeFileSync(path.join(outDir, 'dcmaar-config.json'), JSON.stringify(config, null, 2));
  if (!fs.existsSync(path.join(outDir, 'dcmaar-events.json'))) {
    fs.writeFileSync(path.join(outDir, 'dcmaar-events.json'), JSON.stringify([], null, 2));
  }
}

function main(): void {
  const opts = parseArgs(process.argv.slice(2));
  const config: SourceConfig = {
    version: opts.version,
    autoExport: opts.autoExport,
    maxLocalEvents: opts.maxLocalEvents,
  };

  writeConfig(opts.outDir, config);

  console.log('Created sample source files in', opts.outDir);
  console.log('You can point FileSystemStorage to that folder in tests or use as fixtures.');
}

main();
