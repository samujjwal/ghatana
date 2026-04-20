# P2 Tasks Implementation Notes

## P2-4: Remove MUI from canvas peer dependencies

**Status:** Completed ✅

**Findings:**
- Canvas-related packages (@yappc/ui, ide) do not have MUI in peerDependencies
- peerDependencies only include: react, react-dom
- MUI is used as a regular dependency in source code (e.g., ThemeProvider, workspace components)
- This is acceptable - peerDependencies are for libraries that MUST be provided by consumers
- Regular dependencies are fine for libraries that bundle or use the dependency internally

**Conclusion:** No action required - MUI is not in canvas peer dependencies.

## P2-5: Replace rule-based project setup suggestion with LLM-based

**Status:** Documented for future implementation

**Current State:**
- Rule-based logic in `frontend/apps/api/src/routes/projects.ts` (line 864)
- Endpoint: `POST /api/projects/setup-suggestion`
- Uses deterministic rule matching based on project type and description keywords
- Returns hardcoded recommendations and rationale

**AIService Available:**
- `frontend/web/src/services/ai/AIService.ts` exists
- Provides LLM-based text completion and analysis
- Can be leveraged for intelligent project setup suggestions

**Implementation Plan:**
1. Import AIService in projects.ts
2. Replace rule-based inference with LLM call to infer project type from description
3. Use AIService to generate contextual recommendations
4. Maintain backward compatibility with existing response structure
5. Add feature flag to toggle between rule-based and AI-based modes

**Estimated Effort:** 4-6 hours

## P2-7: Unify dual API prefix

**Status:** Documented for future implementation

**Current State:**
- Routes use `/api/*` prefix in frontend/apps/api/src/routes/
- Some routes may use `/api/v1/*` prefix
- Need to audit all route files and migrate to consistent `/api/v1/*`

**Files to Audit:**
- frontend/apps/api/src/routes/projects.ts
- frontend/apps/api/src/routes/workspaces.ts
- frontend/apps/api/src/routes/lifecycle.ts
- frontend/apps/api/src/routes/devsecops.ts
- frontend/apps/api/src/routes/canvas.ts
- frontend/apps/api/src/routes/code-associations.ts

**Implementation Plan:**
1. Audit all route files for current prefix usage
2. Create migration plan with versioning strategy
3. Update all routes to use `/api/v1/*`
4. Add version compatibility shims if needed
5. Update OpenAPI spec
6. Update frontend API client calls

**Estimated Effort:** 3-4 hours
