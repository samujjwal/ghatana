# YAPPC Web App Development Setup

## Current Status

### ✅ Working
- **Mock API Server**: Running on http://localhost:7003 with mock data for workspaces, projects, and suggestions
- **Workspace Configuration**: Fixed (yappc-api -> @ghatana/yappc-api-app)
- **Dependencies**: Successfully installed via pnpm

### ❌ Issues
- **React Router Dev Server**: Cannot start due to persistent tsconfig error
  - Error: "Failed to load tsconfig for 'src/routes.ts': Tsconfig not found"
  - Root cause: Complex tsconfig.json with rootDir pointing to "../../../../" causing path resolution issues with react-router dev
- **Docker Build**: Fails due to missing infrastructure files
- **TypeScript Errors**: Several files have type errors (devsecops.ts, CanvasToolbar.tsx, collaboration pages)

## Working Development Solution

### Option 1: Mock API Server (Currently Working)

The mock API server provides backend functionality with mock data:

```bash
# From products/yappc/frontend directory
node simple-dev-server.mjs
```

**Available endpoints:**
- `GET /health`
- `GET /api/workspaces`
- `POST /api/workspaces`
- `GET /api/projects`
- `POST /api/projects`
- `GET /api/workspaces/suggest-name`
- `GET /api/projects/suggest-name`

### Option 2: Simple Dev Script

Use the provided simple dev script:

```bash
# From products/yappc/frontend/web directory
./run-dev-simple.sh
```

## Issues Requiring Fix

### 1. tsconfig Configuration (Priority: High)

The web app's tsconfig.json has a complex configuration that prevents react-router dev from working:

```json
{
  "compilerOptions": {
    "rootDir": "../../../../",  // This causes path resolution issues
    "rootDirs": [
      ".",
      "./.react-router/types",
      "../../../../platform/typescript/ds-schema/src",
      "../../../../platform/typescript/ui-builder/src",
      // ... more paths
    ]
  }
}
```

**Suggested fixes:**
1. Simplify the tsconfig.json to use a standard structure without complex rootDir/rootDirs
2. Use a monorepo-aware build tool that can handle the complex workspace structure
3. Split the web app into a simpler standalone package with standard tsconfig

### 2. TypeScript Errors (Priority: Medium)

Several files have TypeScript errors:
- `src/state/devsecops.ts`: Missing type definitions and stub implementations needed
- `src/components/canvas/CanvasToolbar.tsx`: Type errors with Menu props and CanvasMode
- `src/pages/collaboration/TeamDashboardPage.tsx` & `TeamChatPage.tsx`: Import errors with @ghatana/yappc-api-app

### 3. Docker Infrastructure (Priority: Low)

Docker build fails because:
- Missing `products/yappc/infrastructure/docker/docker-compose.yml`
- Missing `products/yappc/infrastructure/docker/.env.template`

## Temporary Workarounds Applied

1. **Workspace Configuration**: Fixed package name from "yappc-api" to "@ghatana/yappc-api-app"
2. **React Router Config**: Converted to JavaScript (react-router.config.js)
3. **tsconfig Simplification**: Removed complex rootDir/rootDirs from main tsconfig.json
4. **File Exclusions**: Excluded problematic files from tsconfig to reduce errors
5. **Stub Types**: Added stub type definitions for devsecops to reduce errors

## Recommended Next Steps

1. **Fix tsconfig Configuration**: This is the main blocker for the web app dev server
   - Consult the team about the intended tsconfig structure
   - Consider using a simpler, standard tsconfig for development
   - May need to restructure the project to avoid complex rootDir setup

2. **Use Docker for Production**: The Dockerfile suggests this is the intended production deployment method
   - Create the missing infrastructure files
   - Test the Docker build process
   - Use Docker for development to bypass local tsconfig issues

3. **Fix TypeScript Errors**: Once the dev server is running, address the remaining type errors
   - Complete the devsecops stub implementations or fix the imports
   - Fix CanvasToolbar type errors
   - Fix collaboration page imports

## Quick Start (Current Working State)

```bash
# 1. Start mock API server
cd /Users/samujjwal/Development/ghatana/products/yappc/frontend
node simple-dev-server.mjs

# 2. Test the API
curl http://localhost:7003/health
curl http://localhost:7003/api/workspaces
```

## Notes

- The mock API server provides a working backend for development and testing
- The web app frontend requires tsconfig configuration fixes to run
- Consider using Docker for a complete development environment
- The project has a complex monorepo structure that may require specialized tooling
