$ErrorActionPreference = "Stop"

Set-Location "$PSScriptRoot\..\services\tutorputor-platform"
corepack pnpm exec vitest run `
  src/modules/content/__tests__/routes.test.ts `
  src/modules/content/studio/__tests__/routes.test.ts
