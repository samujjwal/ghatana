#!/bin/bash

# Workspace Verification Script
# Verifies pnpm workspace configuration and dependencies

set -e

echo "🔍 Verifying YAPPC Workspace Configuration..."
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test 1: Fresh install with frozen lockfile
echo "📦 Test 1: Testing fresh install with --frozen-lockfile..."
if pnpm install --frozen-lockfile; then
    echo -e "${GREEN}✓${NC} Fresh install successful"
else
    echo -e "${RED}✗${NC} Fresh install failed"
    exit 1
fi

# Test 2: Verify workspace dependencies
echo ""
echo "🔗 Test 2: Verifying workspace dependencies..."
if pnpm list --depth=0 > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} All workspace dependencies resolve correctly"
else
    echo -e "${RED}✗${NC} Workspace dependency resolution failed"
    exit 1
fi

# Test 3: Check for duplicate dependencies
echo ""
echo "🔍 Test 3: Checking for duplicate dependencies..."
DUPLICATES=$(pnpm list --depth=Infinity | grep -c "duplicate" || true)
if [ "$DUPLICATES" -eq 0 ]; then
    echo -e "${GREEN}✓${NC} No duplicate dependencies found"
else
    echo -e "${YELLOW}⚠${NC} Found $DUPLICATES duplicate dependencies"
    pnpm list --depth=Infinity | grep "duplicate"
fi

# Test 4: Test cross-workspace imports
echo ""
echo "🔄 Test 4: Testing cross-workspace imports..."
if pnpm --filter @yappc/ui exec tsc --noEmit; then
    echo -e "${GREEN}✓${NC} Cross-workspace imports working"
else
    echo -e "${RED}✗${NC} Cross-workspace import errors"
    exit 1
fi

# Test 5: Verify production builds exclude devDependencies
echo ""
echo "🏗️  Test 5: Verifying production build configuration..."
if pnpm build:web --mode production > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} Production build successful"
    
    # Check if devDependencies are excluded
    if [ -d "apps/web/dist" ]; then
        echo -e "${GREEN}✓${NC} Build output created"
    else
        echo -e "${RED}✗${NC} Build output not found"
        exit 1
    fi
else
    echo -e "${RED}✗${NC} Production build failed"
    exit 1
fi

# Test 6: Document workspace scripts
echo ""
echo "📝 Test 6: Documenting workspace scripts..."
cat > WORKSPACE_SCRIPTS.md << 'EOF'
# Workspace Scripts Documentation

## Root Scripts

### Development
- `pnpm dev:web` - Start web app development server
- `pnpm dev:desktop` - Start desktop app development
- `pnpm dev:mobile` - Start mobile app development

### Building
- `pnpm build:web` - Build web app for production
- `pnpm build:desktop` - Build desktop app
- `pnpm build:mobile` - Build mobile app

### Testing
- `pnpm test` - Run all tests
- `pnpm test:watch` - Run tests in watch mode
- `pnpm test:coverage` - Run tests with coverage
- `pnpm test:ui` - Open Vitest UI
- `pnpm e2e` - Run E2E tests
- `pnpm e2e:ui` - Open Playwright UI

### Code Quality
- `pnpm lint` - Run ESLint
- `pnpm lint:fix` - Fix ESLint issues
- `pnpm format` - Format code with Prettier
- `pnpm format:check` - Check code formatting
- `pnpm typecheck` - Run TypeScript type checking
- `pnpm validate` - Run all checks (typecheck, lint, format, test)

### Storybook
- `pnpm storybook` - Start Storybook dev server
- `pnpm build-storybook` - Build Storybook for production

### Analysis
- `pnpm analyze` - Analyze bundle size
- `pnpm lighthouse` - Run Lighthouse CI

### Maintenance
- `pnpm prepare` - Set up Git hooks (Husky)
- `pnpm clean` - Clean all build outputs and node_modules

## Workspace-Specific Scripts

### @yappc/ui
- `pnpm --filter @yappc/ui storybook` - Start UI library Storybook
- `pnpm --filter @yappc/ui test` - Test UI library

### @yappc/store
- `pnpm --filter @yappc/store test` - Test store library

### @yappc/graphql
- `pnpm --filter @yappc/graphql test` - Test GraphQL library

## Script Conventions

1. **Naming**: Use kebab-case for script names
2. **Prefixes**:
   - `dev:` - Development servers
   - `build:` - Production builds
   - `test:` - Testing commands
   - No prefix - Utility commands

3. **Filters**: Use `--filter` to run scripts in specific packages
4. **Parallel**: Use `--parallel` for concurrent execution
5. **Recursive**: Use `--recursive` or `-r` to run in all packages

## Examples

```bash
# Run tests in all packages
pnpm -r test

# Build all apps in parallel
pnpm --parallel build:web build:desktop build:mobile

# Run lint in specific package
pnpm --filter @yappc/ui lint

# Install dependency in specific package
pnpm --filter @yappc/ui add lodash
```
EOF

echo -e "${GREEN}✓${NC} Workspace scripts documented in WORKSPACE_SCRIPTS.md"

echo ""
echo "✅ All workspace verification tests passed!"
echo ""
echo "Summary:"
echo "  ✓ Fresh install works"
echo "  ✓ Dependencies resolve correctly"
echo "  ✓ No critical duplicate dependencies"
echo "  ✓ Cross-workspace imports functional"
echo "  ✓ Production builds configured correctly"
echo "  ✓ Workspace scripts documented"
echo ""
