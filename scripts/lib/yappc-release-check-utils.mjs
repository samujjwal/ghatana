import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

const ignoredDirectories = new Set(['.git', '.gradle', 'build', 'dist', 'node_modules', '.turbo', 'coverage']);

export function toRepoPath(absolutePath) {
  return path.relative(repoRoot, absolutePath).replaceAll(path.sep, '/');
}

export function fileExists(relativePath) {
  return existsSync(path.join(repoRoot, relativePath));
}

export function readText(relativePath) {
  return fileExists(relativePath) ? readFileSync(path.join(repoRoot, relativePath), 'utf8') : '';
}

export function readJson(relativePath) {
  return JSON.parse(readText(relativePath));
}

export function walkFiles(relativeDir, predicate = () => true) {
  const start = path.join(repoRoot, relativeDir);
  if (!existsSync(start)) return [];
  const files = [];
  const stack = [start];
  while (stack.length > 0) {
    const current = stack.pop();
    for (const entry of readdirSync(current, { withFileTypes: true })) {
      if (ignoredDirectories.has(entry.name)) continue;
      const child = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(child);
      } else {
        const relativeChild = toRepoPath(child);
        if (predicate(relativeChild)) {
          files.push(relativeChild);
        }
      }
    }
  }
  return files.sort();
}

export function currentGitSha(root = repoRoot) {
  try {
    return execFileSync('git', ['rev-parse', 'HEAD'], {
      cwd: root,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
  } catch {
    return 'unknown';
  }
}

export function createChecker({ checkId, evidencePath }) {
  const checks = [];
  const violations = [];

  function record(name, pass, details = {}) {
    checks.push({ name, pass, ...details });
    if (!pass) {
      violations.push(`${name}${details.message ? `: ${details.message}` : ''}`);
    }
  }

  function requireFile(relativePath, name = relativePath) {
    record(name, fileExists(relativePath), {
      path: relativePath,
      message: `missing ${relativePath}`,
    });
  }

  function requireIncludes(relativePath, token, name = `${relativePath} includes ${token}`) {
    const source = readText(relativePath);
    record(name, source.includes(token), {
      path: relativePath,
      token,
      message: `${relativePath} must include ${token}`,
    });
  }

  function requireAnyFileIncludes(relativeDir, token, name = `${relativeDir} includes ${token}`) {
    const matches = walkFiles(relativeDir, (file) => /\.(java|ts|tsx|js|mjs|yaml|yml|json|md|sql)$/.test(file))
      .filter((relativePath) => readText(relativePath).includes(token));
    record(name, matches.length > 0, {
      token,
      matches: matches.slice(0, 20),
      message: `${relativeDir} must include ${token}`,
    });
    return matches;
  }

  function finish(extra = {}) {
    const evidence = {
      checkId,
      generatedAt: new Date().toISOString(),
      pass: violations.length === 0,
      evidenceRun: {
        generatedBy: `scripts/${path.basename(process.argv[1] ?? checkId)}`,
        command: `node ${toRepoPath(process.argv[1] ?? '')}`.trim(),
        commit: currentGitSha(),
      },
      summary: {
        checkCount: checks.length,
        passedChecks: checks.filter((check) => check.pass).length,
        violationCount: violations.length,
      },
      checks,
      violations,
      ...extra,
    };

    if (evidencePath) {
      const absolutePath = path.join(repoRoot, evidencePath);
      mkdirSync(path.dirname(absolutePath), { recursive: true });
      writeFileSync(absolutePath, `${JSON.stringify(evidence, null, 2)}\n`);
    }

    if (violations.length > 0) {
      console.error(`${checkId} failed:`);
      for (const violation of violations) {
        console.error(`- ${violation}`);
      }
      process.exit(1);
    }

    console.log(`${checkId} passed.`);
    if (evidencePath) {
      console.log(`Evidence written to ${evidencePath}.`);
    }
  }

  return {
    checks,
    violations,
    record,
    requireFile,
    requireIncludes,
    requireAnyFileIncludes,
    finish,
  };
}
