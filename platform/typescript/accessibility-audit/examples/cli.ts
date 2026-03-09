#!/usr/bin/env -S pnpm dlx tsx
/**
 * Simple CLI for running accessibility audits using the package API.
 * Usage:
 *   pnpm dlx tsx ./libs/accessibility-audit/examples/cli.ts --file=path/to/file.html --format=json
 *   pnpm dlx tsx ./libs/accessibility-audit/examples/cli.ts --html='<html>...</html>' --format=html
 */
import { argv } from 'process';
import { auditTargetToFormat } from '../src/api/runAuditOn';
import fs from 'fs';

function parseArgs() {
  const args: Record<string, string> = {};
  argv.slice(2).forEach((a) => {
    const m = a.match(/^--([a-zA-Z0-9-_]+)=(.*)$/);
    if (m) args[m[1]] = m[2];
  });
  return args;
}

async function main() {
  const args = parseArgs();
  const format = (args.format as any) || 'json';
  const wcag = (args.wcag as any) || 'AA';

  let target: string | undefined;

  if (args.file) {
    const filePath = args.file;
    if (!fs.existsSync(filePath)) {
      console.error('File not found:', filePath);
      process.exit(2);
    }
    target = filePath;
  } else if (args.html) {
    target = args.html;
  } else {
    console.error('Usage: --file=path/to/file.html | --html="<html>...</html>" [--format=json] [--wcag=AA]');
    process.exit(1);
  }

  try {
    const output = await auditTargetToFormat(target as any, format as any, wcag as any);
    // Write to stdout
    process.stdout.write(output);
  } catch (e: any) {
    console.error('Audit failed:', e?.message || e);
    process.exit(3);
  }
}

main().catch((error) => {
  console.error('Unexpected error:', error);
  process.exit(99);
});

export { main };
