CI enforcement helpers

- `scripts/verify-dod.js` — simple verifier run during PR checks to ensure PR body references the DoD checklist. It exits non-zero if not present.
- `.jscpd.json` — configuration for jscpd duplication detection.
- `.dependency-cruiser.json` — rules for import boundaries; adjust for project layout.

Placeholders and artifacts uploaded by the workflow:
- `.jscpd-report.html` — duplication report
- `depcruise-report.txt` — dependency cruiser text output

Adjust thresholds in `.jscpd.json` and `.size-limit.json` according to project requirements.
