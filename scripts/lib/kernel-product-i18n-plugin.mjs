import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';

const DEFAULT_EXTENSIONS = ['.tsx', '.ts'];
const DEFAULT_EXCLUDE_PATTERNS = [
  /__tests__/,
  /\.test\./,
  /\.spec\./,
  /__mocks__/,
];

const RAW_ENGLISH_PATTERNS = [
  {
    pattern: />\s*([A-Z][a-z]+(?:\s+[a-zA-Z]+){2,}[.…!?]?)\s*</g,
    description: 'Raw English JSX text node',
    captureGroup: 1,
  },
  {
    pattern: /\b(?:title|label|placeholder|aria-label|accessibilityLabel)\s*=\s*"([A-Z][a-zA-Z\s,.']{10,})"/g,
    description: 'Raw English string in user-visible JSX attribute',
    captureGroup: 1,
  },
  {
    pattern: /Alert\.alert\(\s*'([A-Z][a-zA-Z\s]{6,})'/g,
    description: 'Raw English string in Alert.alert title',
    captureGroup: 1,
  },
  {
    pattern: /<(?:Text|Button|Pressable)[^>]*>\s*([A-Z][a-z]+(?:\s+[a-zA-Z]+){1,})\s*<\/(?:Text|Button|Pressable)>/g,
    description: 'Raw English string inside Text/Button element',
    captureGroup: 1,
  },
];

const ALLOW_REGEXES = [
  /t\('/,
  /\{.*\}/,
  /className|id=|data-/,
  /^\s*\/\//,
];

export function scanKernelProductI18nConformance(config) {
  const repoRoot = resolve(config.repoRoot ?? process.cwd());
  const productLabel = config.productLabel;
  const scanDirs = config.scanDirs.map((dir) => resolve(repoRoot, dir));
  const extensions = config.extensions ?? DEFAULT_EXTENSIONS;
  const excludePatterns = config.excludePatterns ?? DEFAULT_EXCLUDE_PATTERNS;
  const allowRegexes = [...ALLOW_REGEXES, ...(config.allowRegexes ?? [])];

  const files = scanDirs
    .flatMap((dir) => walkFilesSafely(dir, extensions))
    .filter((file) => !excludePatterns.some((pattern) => pattern.test(file)));
  const violations = files.flatMap((file) => scanI18nFile(file, allowRegexes));

  return {
    productLabel,
    filesChecked: files.length,
    violations: violations.map((violation) => ({
      ...violation,
      file: relative(repoRoot, violation.file),
    })),
  };
}

export function printKernelProductI18nReport(result) {
  if (result.violations.length === 0) {
    console.log(`${result.productLabel} i18n conformance: OK - ${result.filesChecked} files checked, no violations.`);
    return;
  }

  console.error(`${result.productLabel} i18n conformance: FAILED - ${result.violations.length} violation(s) found:\n`);
  for (const violation of result.violations) {
    console.error(`  ${violation.file}:${violation.line}  [${violation.description}]`);
    console.error(`    -> "${violation.text}"`);
  }
}

function walkFilesSafely(dir, extensions) {
  try {
    return walkFiles(dir, extensions);
  } catch {
    return [];
  }
}

function walkFiles(dir, extensions) {
  const results = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory() && !['node_modules', 'build', '.gradle', 'dist'].includes(entry)) {
      results.push(...walkFiles(full, extensions));
    } else if (stat.isFile() && extensions.some((extension) => full.endsWith(extension))) {
      results.push(full);
    }
  }
  return results;
}

function scanI18nFile(filePath, allowRegexes) {
  const content = readFileSync(filePath, 'utf-8');
  const lines = content.split('\n');
  const violations = [];

  for (const { pattern, description, captureGroup } of RAW_ENGLISH_PATTERNS) {
    pattern.lastIndex = 0;
    let match;
    while ((match = pattern.exec(content)) !== null) {
      const matchedText = match[captureGroup];
      const lineNumber = content.slice(0, match.index).split('\n').length;
      const lineContent = lines[lineNumber - 1] ?? '';
      if (allowRegexes.some((regex) => regex.test(lineContent))) {
        continue;
      }
      violations.push({
        file: filePath,
        line: lineNumber,
        description,
        text: matchedText.trim().slice(0, 80),
      });
    }
  }

  return violations;
}
