# Device Health Extension

## Setup

### Initial Installation

```bash
# Install dependencies
pnpm install

# Note: Playwright browsers are automatically installed
# System dependencies (optional) may be needed for E2E tests
```

### Playwright System Dependencies (Optional)

The `postinstall` script automatically installs Playwright browsers **without system dependencies** to avoid requiring `sudo` during `pnpm install`.

If you encounter errors during E2E tests about missing libraries (e.g., `libnss3`, `libgtk-3-0`), install system dependencies once:

**Linux:**

```bash
sudo npx playwright install-deps chromium firefox msedge
```

**macOS:**

```bash
# Usually not needed - macOS has required libraries
npx playwright install chromium firefox msedge
```

**Windows:**

```powershell
# Usually not needed - Windows has required libraries
npx playwright install chromium firefox msedge
```

**Alternative Setup Script:**

```bash
pnpm run setup:playwright
```

This only needs to be done once per system and is optional for most development workflows.

## Why This Approach?

1. **Cross-platform**: Works on Linux, macOS, and Windows without platform-specific code
2. **No sudo required**: Avoids blocking `pnpm install` with password prompts
3. **CI/CD friendly**: Automated installs work without interaction
4. **Developer friendly**: Most developers can run tests without additional setup
5. **Optional dependencies**: System libraries only needed when actual tests fail

## Development

```bash
# Run in development mode
pnpm run dev

# Build for all browsers
pnpm run build

# Run tests
pnpm run test

# Run E2E tests
pnpm run test:e2e:chrome
```

## Troubleshooting

### E2E Tests Fail with Missing Libraries

**Error:**

```
Error: browserType.launch: Host system is missing dependencies to run browsers
```

**Solution:**

```bash
sudo npx playwright install-deps chromium firefox msedge
```

### Browsers Not Found

**Error:**

```
Error: Executable doesn't exist at <path>
```

**Solution:**

```bash
pnpm run setup:playwright
```
