# AEP UI Accessibility Audit 2026-04-15

## Scope

This audit covers the current AEP UI route surface implemented in `products/aep/ui` as of 2026-04-15.

Automated audit coverage now includes:

- `/login`
- `/operate`
- `/operate/reviews`
- `/build/pipelines`
- `/build/pipelines/new`
- `/build/patterns`
- `/learn/episodes`
- `/learn/memory`
- `/govern`
- `/catalog/agents`
- `/catalog/workflows`

## Automated Checks

The Playwright suite in `ui/e2e/a11y.spec.ts` performs three checks per route:

1. axe-core scan for WCAG 2.1 A/AA critical and serious violations
2. Structural accessibility rules: landmarks, region structure, labels, button names, image alt text, and color contrast
3. Keyboard-entry smoke check to confirm a focusable control is reachable with keyboard navigation

Protected routes are evaluated with seeded AEP auth state so the tests exercise the real routed UI shell instead of the login redirect.

The audit harness also suppresses the Vite development error overlay during Playwright execution so failed backend proxy requests do not create false-positive accessibility violations unrelated to the rendered AEP UI route under test.

## Execution Command

Run the Chromium accessibility slice with:

```bash
corepack pnpm --dir /home/samujjwal/Developments/ghatana/products/aep/ui exec playwright test e2e/a11y.spec.ts --project=chromium
```

Run all configured Playwright browser projects with:

```bash
corepack pnpm --dir /home/samujjwal/Developments/ghatana/products/aep/ui test:e2e:a11y
```

## Results

- Automated route coverage: expanded from the pipeline-builder-only audit to the current canonical AEP route matrix plus the login page.
- Severity gate: the suite now fails on any critical or serious WCAG 2.1 A/AA violation on the audited routes.
- Structural gate: the suite now fails on landmark, labeling, button-name, image-alt, or color-contrast violations on the audited routes.
- Command validation on 2026-04-15: `corepack pnpm --dir /home/samujjwal/Developments/ghatana/products/aep/ui exec playwright test e2e/a11y.spec.ts --project=chromium` completed with `33 passed`.

The Chromium pass above provides the current automated evidence for route-wide accessibility regression coverage. Cross-browser Playwright execution remains available via `pnpm test:e2e:a11y` when broader sign-off is needed.

## Remaining Manual Validation

The following still require manual sign-off and are not fully replaceable with automated browser tooling:

- Screen reader pass with VoiceOver and/or NVDA on the highest-traffic routes
- Manual review of focus order quality beyond the first-entry keyboard smoke checks
- Manual confirmation of accessible names for dynamic canvas interactions that may not be fully asserted by axe-core alone

## Readiness Position

This audit closes the automation gap for route-wide A11y regression detection in AEP UI.

It does not by itself certify full WCAG 2.1 AA compliance until the manual screen-reader pass is completed and signed off.