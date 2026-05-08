import { readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const DIST_ASSETS = join(process.cwd(), 'dist', 'assets');
const MAX_ENTRY_BYTES = 400 * 1024;
const MAX_CHUNK_BYTES = 250 * 1024;

function listJsFiles(dir) {
  return readdirSync(dir)
    .filter((name) => name.endsWith('.js'))
    .map((name) => ({
      name,
      path: join(dir, name),
      size: statSync(join(dir, name)).size,
    }));
}

function fail(message) {
  console.error(`[bundle-budget] ${message}`);
  process.exit(1);
}

try {
  const jsFiles = listJsFiles(DIST_ASSETS);
  if (jsFiles.length === 0) {
    fail(`No JavaScript assets found in ${DIST_ASSETS}`);
  }

  const entryFiles = jsFiles.filter((file) => file.name.startsWith('index-'));
  const chunkFiles = jsFiles.filter((file) => !file.name.startsWith('index-'));

  for (const file of entryFiles) {
    if (file.size > MAX_ENTRY_BYTES) {
      fail(`Entry bundle ${file.name} is ${file.size} bytes; limit is ${MAX_ENTRY_BYTES}`);
    }
  }

  for (const file of chunkFiles) {
    if (file.size > MAX_CHUNK_BYTES) {
      fail(`Chunk ${file.name} is ${file.size} bytes; limit is ${MAX_CHUNK_BYTES}`);
    }
  }

  console.log(
    `[bundle-budget] OK: ${entryFiles.length} entry bundle(s) <= ${MAX_ENTRY_BYTES} bytes and ` +
      `${chunkFiles.length} chunk(s) <= ${MAX_CHUNK_BYTES} bytes`,
  );
} catch (error) {
  fail(`Failed to validate bundle budget: ${error instanceof Error ? error.message : String(error)}`);
}
