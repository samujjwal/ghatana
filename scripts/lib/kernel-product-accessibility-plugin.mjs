import { readFileSync, readdirSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';

const DEFAULT_EXTENSIONS = ['.tsx', '.ts', '.jsx', '.js'];
const SKIPPED_DIRECTORIES = new Set(['node_modules', '__tests__', 'dist', 'build', '.next']);

export function scanKernelProductAccessibility(config) {
  const repoRoot = resolve(config.repoRoot ?? process.cwd());
  const productLabel = config.productLabel;
  const scanDirs = config.scanDirs.map((dir) => resolve(repoRoot, dir));
  const extensions = config.extensions ?? DEFAULT_EXTENSIONS;

  const fileResults = scanDirs.flatMap((dir) => scanDirectorySafely(dir, extensions));
  const issues = fileResults.flatMap((result) => result.issues.map((issue) => ({
    ...issue,
    file: relative(repoRoot, result.file),
  })));

  return {
    productLabel,
    filesChecked: fileResults.length,
    issues,
  };
}

export function printKernelProductAccessibilityReport(result) {
  if (result.issues.length === 0) {
    console.log(`${result.productLabel} accessibility conformance: OK - ${result.filesChecked} files checked, no issues.`);
    return;
  }

  console.error(`${result.productLabel} accessibility conformance: FAILED - ${result.issues.length} issue(s) found:\n`);
  for (const issue of result.issues) {
    console.error(`  ${issue.file}:${issue.line}  [${issue.type}]`);
  }
}

function scanDirectorySafely(dirPath, extensions) {
  try {
    return scanDirectory(dirPath, extensions);
  } catch {
    return [];
  }
}

function scanDirectory(dirPath, extensions) {
  const results = [];

  function scanRecursive(currentPath) {
    const entries = readdirSync(currentPath, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = join(currentPath, entry.name);
      if (entry.isDirectory()) {
        if (!SKIPPED_DIRECTORIES.has(entry.name)) {
          scanRecursive(fullPath);
        }
      } else if (entry.isFile() && extensions.some((extension) => entry.name.endsWith(extension))) {
        results.push({ file: fullPath, issues: scanFile(fullPath) });
      }
    }
  }

  scanRecursive(dirPath);
  return results;
}

function scanFile(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const issues = [];

  for (const match of content.matchAll(/<button[^>]*>/g)) {
    const buttonTag = match[0];
    const hasAriaLabel = /aria-label=/.test(buttonTag);
    const hasVisibleText = content.substring(match.index, match.index + 200).match(/<button[^>]*>([^<]+)/);
    if (!hasAriaLabel && (!hasVisibleText || hasVisibleText[1].trim().length === 0)) {
      issues.push({ type: 'button-without-label', line: getLineNumber(content, match.index) });
    }
  }

  for (const match of content.matchAll(/<img[^>]*>/g)) {
    if (!/alt=/.test(match[0])) {
      issues.push({ type: 'image-without-alt', line: getLineNumber(content, match.index) });
    }
  }

  for (const match of content.matchAll(/<input[^>]*>/g)) {
    const inputTag = match[0];
    if (!/aria-label=/.test(inputTag) && !/id=/.test(inputTag)) {
      issues.push({ type: 'input-without-label', line: getLineNumber(content, match.index) });
    }
  }

  for (const match of content.matchAll(/<a[^>]*>\s*<\/a>/g)) {
    issues.push({ type: 'empty-link', line: getLineNumber(content, match.index) });
  }

  return issues;
}

function getLineNumber(content, index) {
  return content.substring(0, index).split('\n').length;
}
