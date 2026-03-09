# DCMaar – Guardian App – Known Issues & Troubleshooting

## 1. Known Issues

- Coordination issues across apps, backend, and libs can cause partial or inconsistent deployments.

## 2. Troubleshooting

- When Guardian behavior is inconsistent, check:
  - Which components were built and deployed (dashboard, backend, libs).
  - Compatibility and dependency versions across workspaces.

## 3. Common Issues and Remedies

### 3.1 Browser extension: "You do not have permission to use blocking webRequest listeners"

- Symptom: service worker logs show a runtime.lastError indicating `webRequestBlocking` is not allowed under MV3.
- Cause: an extension attempted to register a blocking `webRequest.onBeforeRequest` listener or the manifest requests `webRequestBlocking` permission but the extension is not force-installed by enterprise policy.
- Fix: Move blocking behavior to `declarativeNetRequest` dynamic rules (DNR) and remove `webRequestBlocking` from `manifest.json`. Rebuild and reload the extension.

### 3.2 Service worker errors: "ReferenceError: window is not defined"

- Symptom: service worker stack traces referencing `window` usage (setInterval, document, etc.).
- Cause: code written for a page context executed in the service worker context (service workers don't have `window` or DOM APIs).
- Fix: Use `chrome.alarms` for periodic tasks or `globalThis.setInterval` guarded by `typeof window !== 'undefined'` and prefer the alarms API in MV3 service worker.

### 3.3 Popup CSP / inline script blocked

- Symptom: Console shows content security policy error complaining about inline script execution.
- Cause: Manifest V3 CSP disallows inline scripts in extension pages. Debug helpers added inline in `index.html` trigger the policy.
- Fix: Move fallback/debug logic into an external module and load it via `<script type="module" src="./popup-fallback.js"></script>` so CSP (script-src 'self') allows it.

### 3.4 DeclarativeNetRequest (DNR) dynamic rule limits

- Symptom: Not all blocking rules apply, or updateDynamicRules fails with quota errors.
- Cause: Each browser has per-extension dynamic rule limits (e.g., Chrome limit ~30k total rules, lower on some platforms). Excessive per-domain rules can hit limits.
- Fix: Consolidate rules, use patterns or regex where supported, and prefer fewer, broader rules. Log and monitor rule counts.

### 3.5 Database connection, migration and schema mismatch

- Symptom: Backend fails to start or migrations error out.
- Cause: Environment variables misconfigured, migrations not applied, or incompatible schema versions.
- Fix: Ensure `.env` values are correct, run migration commands in a staging environment first, check migration logs in `apps/backend/logs` or container logs.

### 3.6 Build / dependency failures (pnpm)

- Symptom: `pnpm build` fails with missing modules or types.
- Cause: Workspace dependencies not installed, or pnpm lockfile out of date.
- Fix: Run `pnpm install` at repo root, ensure node version matches `engines` in `package.json`, and run `pnpm -w install` if needed. If CI differs, check CI Node version.

## 4. Diagnostic Commands (useful)

```bash
# Show extension manifest and permissions
jq .permissions apps/guardian/apps/browser-extension/dist/chrome/manifest.json

# Tail service worker logs (Chrome => Extensions => Inspect views)

# Container logs
docker compose -f docker-compose.yml logs --follow backend

# Check rule counts (pseudo-example)
# inspect the extension runtime or log the number of activeRuleIds maintained by the background service
```

If you encounter a new issue, add a short reproducible description and the steps above under `docs/usage/KNOWN_ISSUES_TROUBLESHOOTING.md` so the team can triage and update fixes.

This document is self-contained and lists common issues and mitigations for the Guardian product workspace.
