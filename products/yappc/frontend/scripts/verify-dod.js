#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

async function main() {
  const eventPath = process.env.GITHUB_EVENT_PATH || process.argv[2];
  const githubToken = process.env.GITHUB_TOKEN;
  const repo = process.env.GITHUB_REPOSITORY;

  if (!eventPath || !fs.existsSync(eventPath)) {
    console.log(
      'GITHUB_EVENT_PATH not found — running in local mode. Checking latest commit message.'
    );
    const message = require('child_process')
      .execSync('git log -1 --pretty=%B')
      .toString();
    checkPRBodyLocal(message);
    process.exit(0);
  }

  const ev = JSON.parse(fs.readFileSync(eventPath, 'utf8'));
  const pr = ev.pull_request;
  const body =
    pr && pr.body
      ? pr.body
      : ev.comment && ev.comment.body
        ? ev.comment.body
        : '';

  // If no PR context, fallback to body check
  if (!pr || !pr.number) {
    checkPRBodyLocal(body);
    return;
  }

  // Fetch changed files for the PR
  const changedFiles = [];
  if (githubToken && repo) {
    const fetch = global.fetch || require('node-fetch');
    const [owner, repoName] = repo.split('/');
    const prNumber = pr.number;
    const perPage = 100;
    let page = 1;
    while (true) {
      const url = `https://api.github.com/repos/${owner}/${repoName}/pulls/${prNumber}/files?per_page=${perPage}&page=${page}`;
      const res = await fetch(url, {
        headers: {
          Authorization: `Bearer ${githubToken}`,
          'User-Agent': 'yappc-ci',
        },
      });
      if (!res.ok) {
        console.error('Failed to fetch PR files', res.status, await res.text());
        break;
      }
      const files = await res.json();
      if (!files || files.length === 0) break;
      changedFiles.push(...files.map((f) => f.filename));
      if (files.length < perPage) break;
      page++;
    }
  }

  // Determine components changed that require DoD
  const componentPaths = changedFiles.filter(
    (p) =>
      p.startsWith('apps/web/src/components/') ||
      p.startsWith('libs/ui/') ||
      p.startsWith('apps/web/src/routes')
  );

  // Strict DoD parsing: look for a UI Component DoD checklist section and require all boxes checked
  const hasDoDReference =
    body &&
    (body.includes('docs/ui-component-dod-checklist.md') ||
      /UI Component DoD/i.test(body) ||
      /UI Component DoD/i.test(body));

  const checklist = parseChecklistFromBody(body);
  const checklistPresent = checklist.length > 0;
  const allChecked = checklistPresent
    ? checklist.every((item) => item.checked)
    : false;

  if (componentPaths.length === 0) {
    console.log('No UI components changed. Skipping DoD enforcement.');
    process.exit(0);
  }

  // If checklist present, require all boxes checked
  if (checklistPresent) {
    if (allChecked) {
      console.log('DoD verification passed: all checklist items checked.');
      // remove label if present
      await removeNeedsDodLabelIfExists(githubToken, repo, pr.number);
      process.exit(0);
    }
    console.error(
      'DoD verification failed: not all checklist items are checked.'
    );
  }

  // Fallback: if no checklist but PR body references the DoD docs text, treat as pass
  if (!checklistPresent && hasDoDReference) {
    console.log('DoD verification passed (document reference found).');
    await removeNeedsDodLabelIfExists(githubToken, repo, pr.number);
    process.exit(0);
  }

  // Post a comment on the PR to request DoD details (if possible)
  if (githubToken && repo) {
    try {
      const fetch = global.fetch || require('node-fetch');
      const [owner, repoName] = repo.split('/');
      const prNumber = pr.number;
      const url = `https://api.github.com/repos/${owner}/${repoName}/issues/${prNumber}/comments`;
      const message = `:warning: DoD verification failed for UI components modified in this PR.\n\nFiles changed:\n${componentPaths.map((p) => `- ${p}`).join('\n')}\n\nPlease include a filled UI Component DoD checklist (see docs/ui-component-dod-checklist.md) in the PR description or a short excerpt showing the items are satisfied.\n`;
      await fetch(url, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${githubToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ body: message }),
      });

      // Try to add a label to the PR to make it visible (create the label if it doesn't exist)
      const labelsUrl = `https://api.github.com/repos/${owner}/${repoName}/issues/${prNumber}/labels`;
      const labelName = 'needs-dod';
      try {
        const res = await fetch(labelsUrl, {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${githubToken}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify([labelName]),
        });
        if (!res.ok) {
          // If adding label failed (maybe label doesn't exist), try to create the label then add
          const createLabelUrl = `https://api.github.com/repos/${owner}/${repoName}/labels`;
          const createRes = await fetch(createLabelUrl, {
            method: 'POST',
            headers: {
              Authorization: `Bearer ${githubToken}`,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
              name: labelName,
              color: 'ffcc00',
              description: 'PR requires DoD details',
            }),
          });
          if (createRes.ok) {
            await fetch(labelsUrl, {
              method: 'POST',
              headers: {
                Authorization: `Bearer ${githubToken}`,
                'Content-Type': 'application/json',
              },
              body: JSON.stringify([labelName]),
            });
          }
        }
      } catch (err) {
        console.error('Failed to add label to PR:', err);
      }

      console.error(
        'DoD verification failed: PR body does not reference the UI Component DoD checklist. A comment was posted on the PR and label added (if possible).'
      );
    } catch (err) {
      console.error('Failed to post comment to PR:', err);
    }
  } else {
    console.error(
      '\nDoD verification failed: PR body does not reference the UI Component DoD checklist.'
    );
  }
  process.exit(2);
}

function checkPRBodyLocal(bodyText) {
  const checklist = parseChecklistFromBody(bodyText);
  if (checklist.length > 0 && checklist.every((i) => i.checked)) {
    console.log(
      'DoD verification passed (local): all checklist items checked.'
    );
    process.exit(0);
  }
  const requiredA = 'docs/ui-component-dod-checklist.md';
  const requiredB = 'UI Component DoD';
  if (
    bodyText &&
    (bodyText.includes(requiredA) || /UI Component DoD/i.test(bodyText))
  ) {
    console.log('DoD verification passed (local): document reference found.');
    process.exit(0);
  }
  console.error(
    '\nDoD verification failed: PR body does not reference the UI Component DoD checklist or checklist items are unchecked.'
  );
  console.error(
    'Please include a filled DoD checklist or a link to docs/ui-component-dod-checklist.md in the PR description.'
  );
  process.exit(2);
}

function parseChecklistFromBody(bodyText) {
  if (!bodyText) return [];
  const lines = bodyText.split(/\r?\n/);
  // find start index: heading 'UI Component DoD' or the docs link
  let start = -1;
  for (let i = 0; i < lines.length; i++) {
    if (
      /^#+\s*UI Component DoD/i.test(lines[i]) ||
      /docs\/ui-component-dod-checklist\.md/i.test(lines[i])
    ) {
      start = i;
      break;
    }
  }
  // if not found, try entire body
  const scanFrom = start >= 0 ? start : 0;
  const checklist = [];
  for (let i = scanFrom; i < lines.length; i++) {
    const m = lines[i].match(/^[-*]\s*\[([ xX])\]\s*(.+)$/);
    if (m) {
      checklist.push({
        checked: m[1].toLowerCase() === 'x',
        text: m[2].trim(),
      });
    } else if (start >= 0 && /^#{1,6}\s+/.test(lines[i])) {
      // reached next heading after starting section
      break;
    }
  }
  return checklist;
}

async function removeNeedsDodLabelIfExists(githubToken, repo, prNumber) {
  if (!githubToken || !repo) return;
  const fetch = global.fetch || require('node-fetch');
  const [owner, repoName] = repo.split('/');
  const labelName = 'needs-dod';
  try {
    // attempt to delete label from issue
    const url = `https://api.github.com/repos/${owner}/${repoName}/issues/${prNumber}/labels/${encodeURIComponent(labelName)}`;
    const res = await fetch(url, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${githubToken}` },
    });
    if (res.status === 204) {
      console.log('Removed label needs-dod from PR.');
    }
  } catch (err) {
    console.error('Failed to remove label from PR:', err);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(2);
});
