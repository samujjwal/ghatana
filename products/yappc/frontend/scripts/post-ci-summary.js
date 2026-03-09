#!/usr/bin/env node
const fs = require('fs');

// Simple CI summary poster: reads artifact files or environment variables and posts a PR comment summarizing failing checks.
// Usage in CI: node scripts/post-ci-summary.js

async function main() {
  const eventPath = process.env.GITHUB_EVENT_PATH;
  const githubToken = process.env.GITHUB_TOKEN;
  const repo = process.env.GITHUB_REPOSITORY;
  if (!eventPath || !fs.existsSync(eventPath) || !githubToken || !repo) {
    console.log(
      'Local mode or insufficient CI context - printing summary from available files.'
    );
    printLocalSummary();
    process.exit(0);
  }
  const ev = JSON.parse(fs.readFileSync(eventPath, 'utf8'));
  const pr = ev.pull_request;
  if (!pr || !pr.number) {
    console.log('No PR context; skipping posting summary.');
    process.exit(0);
  }
  const [owner, repoName] = repo.split('/');
  const fetch = global.fetch || require('node-fetch');

  const parts = [];
  if (fs.existsSync('.jscpd-report.html'))
    parts.push('- Duplication report: attached');
  if (fs.existsSync('depcruise-report.txt'))
    parts.push('- Dependency boundary report: attached');
  if (fs.existsSync('depcruise.svg'))
    parts.push('- Dependency graph: attached');

  if (parts.length === 0) parts.push('- No detailed reports generated.');

  const body = `CI Quality Summary for this PR:\n\n${parts.join('\n')}\n\nPlease inspect the attached artifacts and fix any issues.`;

  const url = `https://api.github.com/repos/${owner}/${repoName}/issues/${pr.number}/comments`;
  try {
    await fetch(url, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${githubToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ body }),
    });
    console.log('Posted CI summary comment to PR.');
  } catch (err) {
    console.error('Failed to post CI summary comment to PR:', err);
  }
}

function printLocalSummary() {
  if (fs.existsSync('.jscpd-report.html'))
    console.log('Duplication report available: .jscpd-report.html');
  if (fs.existsSync('depcruise-report.txt'))
    console.log('Dependency report: depcruise-report.txt');
  if (fs.existsSync('depcruise.svg'))
    console.log('Dependency graph: depcruise.svg');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
