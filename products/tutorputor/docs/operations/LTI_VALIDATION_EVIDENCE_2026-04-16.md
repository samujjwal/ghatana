# LTI Validation Evidence

Date: 2026-04-16
Scope: integration/lti route boundary validation and behavior checks

## Checks

- [ ] Invalid deep-linking payload rejected with 400
- [ ] Invalid platform registration payload rejected with 400
- [ ] Grade passback required fields enforced
- [ ] Platform id param validation for admin routes

## Test Command

corepack pnpm vitest run src/modules/integration/lti/routes.test.ts

## Result

- Status:
- Evidence log:

## Notes

