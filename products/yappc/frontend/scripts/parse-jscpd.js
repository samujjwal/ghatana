const fs = require('fs');
const path = require('path');

const REPORT = path.resolve(
  __dirname,
  '../.jscpd-report.html/html/jscpd-report.json'
);
const OUT_JSON = path.resolve(__dirname, '../.jscpd-top-duplicates.json');

function loadReport() {
  if (!fs.existsSync(REPORT)) {
    console.error('Report not found:', REPORT);
    process.exit(2);
  }
  const raw = fs.readFileSync(REPORT, 'utf8');
  return JSON.parse(raw);
}

function deriveSuggestion(a, b) {
  const aPath = a.name || '';
  const bPath = b.name || '';
  if (aPath.includes('/libs/diagram') && bPath.includes('/libs/diagram')) {
    return 'Create a shared base node / render helper in libs/diagram (e.g. BaseNode) to consolidate repeated markup and styles.';
  }
  if (aPath.includes('/libs/ui') && bPath.includes('/libs/ui')) {
    return 'Extract shared UI helpers/styles/hooks into libs/ui (e.g. shared styles, useA11y hook, helper components).';
  }
  if (aPath.includes('/libs/ui') && bPath.includes('/apps/')) {
    return 'Move shared UI logic into libs/ui so apps import the centralized implementation.';
  }
  if (
    aPath.includes('/apps/mobile-cap') &&
    bPath.includes('/apps/mobile-cap')
  ) {
    return 'Extract repeating mobile UI/service logic into libs or a mobile-shared package to avoid duplication.';
  }
  if (
    aPath.includes('/libs/ui/src/test') ||
    bPath.includes('/libs/ui/src/test') ||
    aPath.includes('/libs/mocks') ||
    bPath.includes('/libs/mocks')
  ) {
    return 'Centralize test utilities (mocks, setup) into a single test utils file under libs/ui/src/test or libs/mocks and import that.';
  }
  // fallback
  return 'Consider extracting into a shared util/component in an appropriate lib (libs/ui, libs/diagram, or libs/mocks) and replace duplicates with imports.';
}

function summarize(report, limit = 12) {
  const duplicates = report.duplicates || [];
  const sorted = duplicates.slice().sort((a, b) => {
    if ((b.lines || 0) !== (a.lines || 0))
      return (b.lines || 0) - (a.lines || 0);
    return (b.tokens || 0) - (a.tokens || 0);
  });

  const top = sorted.slice(0, limit).map((d, i) => {
    const first = d.firstFile || {};
    const second = d.secondFile || {};
    return {
      rank: i + 1,
      format: d.format,
      lines: d.lines || 0,
      tokens: d.tokens || 0,
      fragmentPreview: (d.fragment || '').slice(0, 240).replace(/\n/g, '\\n'),
      first: {
        file: first.name || '<unknown>',
        start: first.start || null,
        end: first.end || null,
        startLine: first.startLoc ? first.startLoc.line : null,
        endLine: first.endLoc ? first.endLoc.line : null,
      },
      second: {
        file: second.name || '<unknown>',
        start: second.start || null,
        end: second.end || null,
        startLine: second.startLoc ? second.startLoc.line : null,
        endLine: second.endLoc ? second.endLoc.line : null,
      },
      suggestion: deriveSuggestion(first, second),
    };
  });

  return top;
}

function printSummary(list) {
  console.log(`\nTop ${  list.length  } duplicate groups (prioritized):\n`);
  list.forEach((item) => {
    console.log(`#${item.rank} — ${item.lines} lines — ${item.format}`);
    console.log(`Files:`);
    console.log(
      `  1) ${item.first.file}:${item.first.startLine}-${item.first.endLine}`
    );
    console.log(
      `  2) ${item.second.file}:${item.second.startLine}-${item.second.endLine}`
    );
    console.log(`Suggestion: ${item.suggestion}`);
    console.log('Preview:', item.fragmentPreview);
    console.log('---');
  });
}

function main() {
  const report = loadReport();
  const arg = parseInt(process.argv[2], 10);
  const limit = Number.isInteger(arg) && arg > 0 ? arg : 12;
  const top = summarize(report, limit);
  fs.writeFileSync(OUT_JSON, JSON.stringify(top, null, 2));
  printSummary(top);
  console.log('\nWrote JSON summary to', OUT_JSON);
}

main();
