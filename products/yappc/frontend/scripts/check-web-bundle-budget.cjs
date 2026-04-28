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
const baselinePath = path.join(
  workspaceRoot,
  'performance',
  'baselines',
  'web-bundle-baseline.json'
);

// Absolute thresholds for overall bundle health
const thresholdsKb = {
  totalGzip: 500,
  jsGzip: 350,
  cssGzip: 100,
  largestJsGzip: 200,
};

// Relative growth threshold for CI enforcement (per audit F-Y040)
const GROWTH_THRESHOLD_PERCENT = 10;

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
        growthThresholdPercent: GROWTH_THRESHOLD_PERCENT,
        summary,
      },
      null,
      2
    )
  );
}

function loadBaseline() {
  if (!fs.existsSync(baselinePath)) {
    return null;
  }
  try {
    const content = fs.readFileSync(baselinePath, 'utf-8');
    return JSON.parse(content);
  } catch (error) {
    console.warn(`Failed to load baseline from ${baselinePath}: ${error.message}`);
    return null;
  }
}

function saveBaseline(summary) {
  fs.mkdirSync(path.dirname(baselinePath), { recursive: true });
  fs.writeFileSync(
    baselinePath,
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        summary,
      },
      null,
      2
    )
  );
  console.log(`Baseline saved to ${baselinePath}`);
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

function collectFailures(summary, baseline) {
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

  if (baseline && baseline.summary) {
    const baselineSummary = baseline.summary;
    const mainChunkGrowthPercent =
      ((summary.largestJsGzipKb - baselineSummary.largestJsGzipKb) /
        baselineSummary.largestJsGzipKb) *
      100;

    if (mainChunkGrowthPercent > GROWTH_THRESHOLD_PERCENT) {
      failures.push(
        `Main chunk grew by ${mainChunkGrowthPercent.toFixed(1)}% (baseline: ${formatKb(baselineSummary.largestJsGzipKb)}, current: ${formatKb(summary.largestJsGzipKb)}), exceeds ${GROWTH_THRESHOLD_PERCENT}% threshold. Per audit F-Y040, PRs that grow main chunk by >10% must be reviewed.`
      );
    }
  }

  return failures;
}

function main() {
  if (process.argv.includes('--help')) {
    console.log('Usage: node ../scripts/check-web-bundle-budget.cjs [--report] [--baseline] [--ci]');
    console.log('  --report   Write bundle report to performance/reports/web-bundle-budget.json');
    console.log('  --baseline Save current build as baseline for growth detection');
    console.log('  --ci       Enforce CI checks (fail on >10% main chunk growth)');
    process.exit(0);
  }

  const buildDirectory = findBuildDirectory();
  if (!buildDirectory) {
    console.error('No YAPPC web build output found. Build the web package first.');
    process.exit(1);
  }

  const summary = analyzeBundle(buildDirectory);
  printSummary(summary);

  const shouldSaveBaseline = process.argv.includes('--baseline');
  const isCiMode = process.argv.includes('--ci');

  if (shouldSaveBaseline) {
    saveBaseline(summary);
  }

  if (process.argv.includes('--report')) {
    writeReport(summary);
    console.log(`Bundle report written to ${reportPath}`);
  }

  const baseline = loadBaseline();
  const failures = collectFailures(summary, baseline);
  
  if (failures.length > 0) {
    console.error('Bundle budget check failed:');
    for (const failure of failures) {
      console.error(`- ${failure}`);
    }
    if (isCiMode || !baseline) {
      // In CI mode or if no baseline exists, fail on any failure
      process.exit(1);
    }
    // If baseline exists and not in CI mode, warn but don't fail on growth violations
    const growthFailures = failures.filter(f => f.includes('grew by'));
    if (growthFailures.length > 0 && !isCiMode) {
      console.warn('Warning: Bundle growth detected. Run with --baseline to update baseline.');
    } else {
      process.exit(1);
    }
  }

  console.log('Bundle budget check passed.');
}

main();