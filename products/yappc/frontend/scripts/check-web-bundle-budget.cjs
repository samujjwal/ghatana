#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const workspaceRoot = path.resolve(__dirname, '..');
const webRoot = path.join(workspaceRoot, 'web');
const reportPath = path.join(
  workspaceRoot,
  'performance',
  'reports',
  'web-bundle-budget.json'
);

const thresholdsKb = {
  totalGzip: 500,
  jsGzip: 350,
  cssGzip: 100,
  largestJsGzip: 200,
};

const buildCandidates = [
  path.join(webRoot, 'build', 'client', 'assets'),
  path.join(webRoot, 'build', 'client'),
  path.join(webRoot, 'dist', 'assets'),
  path.join(webRoot, 'dist'),
  path.join(workspaceRoot, 'apps', 'web', 'dist', 'assets'),
  path.join(workspaceRoot, 'apps', 'web', 'dist'),
];

function formatKb(value) {
  return `${value.toFixed(2)} KB`;
}

function toKb(bytes) {
  return bytes / 1024;
}

function gzipSizeBytes(filePath) {
  const buffer = fs.readFileSync(filePath);
  return zlib.gzipSync(buffer).length;
}

function findBuildDirectory() {
  return buildCandidates.find((candidate) => fs.existsSync(candidate)) ?? null;
}

function collectFiles(directory) {
  const entries = fs.readdirSync(directory, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectFiles(entryPath));
      continue;
    }
    files.push(entryPath);
  }

  return files;
}

function analyzeBundle(directory) {
  const files = collectFiles(directory).filter((filePath) => {
    const extension = path.extname(filePath);
    return extension === '.js' || extension === '.css';
  });

  const summary = {
    buildDirectory: directory,
    fileCount: files.length,
    totalGzipKb: 0,
    jsGzipKb: 0,
    cssGzipKb: 0,
    largestJsFile: null,
    largestJsGzipKb: 0,
    files: [],
  };

  for (const filePath of files) {
    const extension = path.extname(filePath);
    const gzipKb = toKb(gzipSizeBytes(filePath));
    const relativePath = path.relative(webRoot, filePath);

    summary.totalGzipKb += gzipKb;
    if (extension === '.js') {
      summary.jsGzipKb += gzipKb;
      if (gzipKb > summary.largestJsGzipKb) {
        summary.largestJsGzipKb = gzipKb;
        summary.largestJsFile = relativePath;
      }
    }
    if (extension === '.css') {
      summary.cssGzipKb += gzipKb;
    }

    summary.files.push({
      path: relativePath,
      gzipKb,
    });
  }

  summary.files.sort((left, right) => right.gzipKb - left.gzipKb);

  return summary;
}

function writeReport(summary) {
  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(
    reportPath,
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        thresholdsKb,
        summary,
      },
      null,
      2
    )
  );
}

function printSummary(summary) {
  console.log('YAPPC Web Bundle Budget');
  console.log(`Build directory: ${summary.buildDirectory}`);
  console.log(`Files analyzed: ${summary.fileCount}`);
  console.log(`Total gzip size: ${formatKb(summary.totalGzipKb)}`);
  console.log(`JavaScript gzip size: ${formatKb(summary.jsGzipKb)}`);
  console.log(`CSS gzip size: ${formatKb(summary.cssGzipKb)}`);
  if (summary.largestJsFile) {
    console.log(
      `Largest JS chunk: ${summary.largestJsFile} (${formatKb(summary.largestJsGzipKb)})`
    );
  }
}

function collectFailures(summary) {
  const failures = [];

  if (summary.totalGzipKb > thresholdsKb.totalGzip) {
    failures.push(
      `Total gzip size ${formatKb(summary.totalGzipKb)} exceeds ${formatKb(thresholdsKb.totalGzip)}`
    );
  }
  if (summary.jsGzipKb > thresholdsKb.jsGzip) {
    failures.push(
      `JavaScript gzip size ${formatKb(summary.jsGzipKb)} exceeds ${formatKb(thresholdsKb.jsGzip)}`
    );
  }
  if (summary.cssGzipKb > thresholdsKb.cssGzip) {
    failures.push(
      `CSS gzip size ${formatKb(summary.cssGzipKb)} exceeds ${formatKb(thresholdsKb.cssGzip)}`
    );
  }
  if (summary.largestJsGzipKb > thresholdsKb.largestJsGzip) {
    failures.push(
      `Largest JS chunk ${formatKb(summary.largestJsGzipKb)} exceeds ${formatKb(thresholdsKb.largestJsGzip)}`
    );
  }

  return failures;
}

function main() {
  if (process.argv.includes('--help')) {
    console.log('Usage: node ../scripts/check-web-bundle-budget.cjs [--report]');
    process.exit(0);
  }

  const buildDirectory = findBuildDirectory();
  if (!buildDirectory) {
    console.error('No YAPPC web build output found. Build the web package first.');
    process.exit(1);
  }

  const summary = analyzeBundle(buildDirectory);
  printSummary(summary);

  if (process.argv.includes('--report')) {
    writeReport(summary);
    console.log(`Bundle report written to ${reportPath}`);
  }

  const failures = collectFailures(summary);
  if (failures.length > 0) {
    console.error('Bundle budget exceeded:');
    for (const failure of failures) {
      console.error(`- ${failure}`);
    }
    process.exit(1);
  }

  console.log('Bundle budget check passed.');
}

main();