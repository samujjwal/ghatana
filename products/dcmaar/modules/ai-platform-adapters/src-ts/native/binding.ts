import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);

const platform = process.platform;
const arch = process.arch;

let cachedBinding: unknown | null = null;
let cachedError: unknown | null = null;

try {
  cachedBinding = loadBinding();
} catch (error) {
  cachedError = error;
}

function candidateFilenames(): string[] {
  const triples = [`${platform}-${arch}`, `${arch}-${platform}`];
  return [
    `agent-napi.${platform}-${arch}.node`,
    `agent-napi.${arch}-${platform}.node`,
    'agent-napi.node',
    ...triples.map(triple => join('npm', `${triple}`, 'agent-napi.node')),
  ];
}

function loadBinding(): unknown {
  const baseDir = dirname(fileURLToPath(import.meta.url));
  const candidates = candidateFilenames().flatMap(filename => [
    join(baseDir, '..', '..', filename),
    join(baseDir, '..', '..', '..', filename),
  ]);

  const tried: string[] = [];
  for (const candidate of candidates) {
    try {
      return require(candidate);
    } catch (_error) {
      tried.push(candidate);
      continue;
    }
  }

  const attempted = Array.from(new Set(tried)).join(', ');
  throw new Error(
    `Failed to load dcmaar agent native binding for ${platform}-${arch}. Tried: ${attempted}`
  );
}

export function getNativeBinding(): unknown | null {
  return cachedBinding;
}

export function getNativeBindingError(): unknown | null {
  return cachedError;
}
