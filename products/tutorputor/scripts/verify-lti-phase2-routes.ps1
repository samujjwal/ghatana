$ErrorActionPreference = "Stop"

Set-Location "$PSScriptRoot\..\services\tutorputor-platform"
corepack pnpm exec vitest run src/modules/integration/lti/routes.test.ts
