# P3-3: Artifact Pre-Population at Phase Entry

**Status:** Documented for Implementation

## Overview

AI generates draft artifacts using project context when entering a new lifecycle phase.

## Implementation Requirements

1. Hook into phase transition events
2. Call AIService with project context and phase requirements
3. Generate draft artifacts (documents, checklists, templates)
4. Save to project workspace
5. Allow user to accept, modify, or regenerate drafts

## Architecture

### Phase Transition Hook

Location: `frontend/web/src/services/canvas/lifecycle/PhaseGateService.ts`

Add a hook that triggers when a phase transition is approved:

```typescript
interface PhaseTransitionHook {
  onPhaseTransition: (transition: PhaseTransition) => Promise<void>;
}
```

### Artifact Generation Service

Create new service: `frontend/web/src/services/artifacts/ArtifactPrepopulationService.ts`

```typescript
class ArtifactPrepopulationService {
  async generateDraftArtifacts(
    projectId: string,
    targetPhase: LifecyclePhase,
    projectContext: ProjectContext
  ): Promise<ArtifactDraft[]>;
  
  async saveDraftArtifacts(
    projectId: string,
    artifacts: ArtifactDraft[]
  ): Promise<void>;
  
  async acceptArtifact(
    projectId: string,
    artifactId: string
  ): Promise<void>;
  
  async regenerateArtifact(
    projectId: string,
    artifactId: string
  ): Promise<ArtifactDraft>;
}
```

### AI Prompt Templates

Create phase-specific prompt templates in `frontend/web/src/prompts/artifact-prompts.ts`:

```typescript
const ARTIFACT_PROMPTS = {
  INTENT: "Generate project charter document based on...",
  SHAPE: "Generate architecture diagram and domain model based on...",
  VALIDATE: "Generate validation checklist for...",
  // ... other phases
};
```

## Implementation Steps

### Step 1: Create ArtifactPrepopulationService

- Implement AI integration for artifact generation
- Add prompt templates for each phase
- Implement draft artifact data structures
- Add save/accept/regenerate methods

### Step 2: Hook into PhaseGateService

- Add post-transition hook
- Call prepopulation service on successful transition
- Handle errors gracefully (don't block transition)

### Step 3: Create UI for Draft Artifacts

- Show draft artifacts in phase header
- Add accept/modify/regenerate actions
- Show artifact status (draft, accepted, rejected)

### Step 4: Add User Preferences

- Allow users to disable auto-generation
- Allow users to customize artifact templates
- Track which artifacts were auto-generated vs manual

## Estimated Effort

12-16 hours total:
- Service implementation: 4-6 hours
- Phase transition hook: 2 hours
- UI components: 4-6 hours
- Testing: 2 hours

## Dependencies

- AIService (already exists)
- PhaseGateService (needs modification)
- Artifact storage (YappcArtifactRepository)
- Project context builder (may need enhancement)

## Risks

- AI generation may be slow - implement async background generation
- Artifacts may not match user expectations - allow easy modification
- Cost of AI generation - add feature flag and user preference

## Future Enhancements

- Learn from user feedback to improve prompts
- Cache common artifact templates
- Support custom artifact templates per organization
- Version control for artifacts
