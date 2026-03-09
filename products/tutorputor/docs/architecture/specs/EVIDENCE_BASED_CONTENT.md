# Evidence-Based Learning Content: Current State & Enhancement Plan

**Date**: January 4, 2026  
**Status**: 📊 Analysis Complete - Implementation Needed  
**Priority**: 🔴 High - Core Learning Experience Quality

---

## Executive Summary

The tutorputor platform has a solid foundation for evidence-based learning with a well-designed data model linking **Claims → Evidence → Tasks**. However, the current AI generation workflow has critical gaps:

### ✅ What Works Well
- **Strong Data Model**: `LearningExperience` → `LearningClaim` → `LearningEvidence` → `ExperienceTask`
- **Separate Content Examples**: `ContentExample` model exists for concepts
- **AI Generation Framework**: `ai-engine.ts` with structured prompts for claims, evidence, and tasks
- **Task Types**: Supports `prediction`, `simulation`, `explanation`, `construction`
- **Grade Adaptation**: Built-in grade-level specificity (K-2, 3-5, 6-8, 9-12, UG, Grad)

### ❌ Critical Gaps
1. **No Automatic Example Generation**: Claims are generated but not automatically backed by concrete examples
2. **Missing Simulation-Claim Linkage**: Simulations exist separately but aren't auto-generated per claim
3. **No Animation Generation**: Animation generation endpoint exists but isn't integrated into claim workflow
4. **Incomplete Task Generation**: Tasks are generated but don't specify which require simulations/animations
5. **No Evidence Type Mapping**: Evidence types don't explicitly map to examples/simulations/animations

---

## Current Architecture

### Data Model (Prisma Schema)

```
LearningExperience
├── claims: LearningClaim[]
│   ├── claimRef: "C1", "C2", etc.
│   ├── text: "The learner can..."
│   ├── bloomLevel: REMEMBER | UNDERSTAND | APPLY | ANALYZE | EVALUATE | CREATE
│   └── orderIndex: number
│
├── evidences: LearningEvidence[]
│   ├── evidenceRef: "E1", "E2", etc.
│   ├── claimRef: "C1" (links to claim)
│   ├── type: "prediction_vs_outcome" | "parameter_targeting" | "explanation_quality" | "construction_artifact"
│   ├── description: string
│   └── observables: Json
│
├── experienceTasks: ExperienceTask[]
│   ├── taskRef: "T1", "T2", etc.
│   ├── type: "prediction" | "simulation" | "explanation" | "construction"
│   ├── claimRef: "C1" (links to claim)
│   ├── evidenceRef: "E1" (links to evidence)
│   ├── prompt: string
│   └── config: Json (SimulationConfig | PredictionConfig | etc.)
│
└── simulationManifestId?: string (single simulation for entire experience)
```

### Separate Content Example Model

```
DomainAuthorConcept
└── examples: ContentExample[]
    ├── title: string
    ├── description: string
    ├── problemStatement: string
    ├── solutionContent: string
    ├── keyLearningPoints: Json
    ├── difficulty: "BEGINNER" | "INTERMEDIATE" | "ADVANCED"
    └── snapshots: VisualizationSnapshot[]
```

**Problem**: `ContentExample` is linked to `DomainAuthorConcept`, not to `LearningClaim`. This means examples exist separately from the claim-evidence-task workflow.

---

## AI Generation Workflow (Current)

### Content Studio Service Flow

```typescript
// services/tutorputor-content-studio/src/service.ts

1. generateClaims(experience, gradeAdaptation)
   → Returns: LearningClaim[]
   → Creates: Claims in database
   
2. generateEvidence(experience, gradeAdaptation)
   → Returns: LearningEvidence[]
   → Creates: Evidence records linked to claims
   
3. generateTasks(experience, gradeAdaptation)
   → Returns: ExperienceTask[]
   → Creates: Tasks linked to claims and evidence
```

### AI Engine Prompts

**Claims Generation** (`ai-engine.ts:66-99`):
- Generates 3-5 learning claims
- Uses Bloom's taxonomy
- Grade-appropriate language
- **Missing**: No instruction to identify which claims need examples/simulations/animations

**Evidence Generation** (`ai-engine.ts:101-138`):
- Generates 1-2 evidence definitions per claim
- Evidence types: `prediction_vs_outcome`, `parameter_targeting`, `explanation_quality`, `construction_artifact`
- **Missing**: No explicit mapping to content delivery methods (example, simulation, animation)

**Task Generation** (`ai-engine.ts:140-179`):
- Generates 1-2 tasks per claim
- Task types: `prediction`, `simulation`, `explanation`, `construction`
- **Partial**: Tasks can be type `simulation` but no automatic simulation manifest generation

---

## Gap Analysis

### Gap 1: Claims Not Backed by Examples

**Current State**:
```json
{
  "claims": [
    {
      "id": "C1",
      "text": "The learner can explain Newton's First Law",
      "bloom": "understand"
    }
  ]
}
```

**Desired State**:
```json
{
  "claims": [
    {
      "id": "C1",
      "text": "The learner can explain Newton's First Law",
      "bloom": "understand",
      "examples": [
        {
          "id": "EX1",
          "type": "real_world",
          "title": "Car Braking Example",
          "description": "When a car brakes suddenly, passengers continue moving forward",
          "content": "..."
        },
        {
          "id": "EX2",
          "type": "simulation",
          "title": "Interactive Inertia Demo",
          "simulationRef": "SIM1"
        }
      ]
    }
  ]
}
```

### Gap 2: No Automatic Simulation Generation Per Claim

**Current State**:
- One `simulationManifestId` per entire `LearningExperience`
- Simulations created separately in Simulation Builder
- No automatic linkage during content generation

**Desired State**:
- Each claim that requires hands-on learning gets a dedicated simulation
- Simulations auto-generated during claim creation
- Tasks of type `simulation` automatically reference the correct simulation manifest

### Gap 3: Missing Animation Integration

**Current State**:
- Animation generation endpoint exists: `/api/v1/animations/generate`
- Not called during content generation workflow
- No data model for storing animations

**Desired State**:
- Visual concepts identified during claim generation
- Animations auto-generated for each visual concept
- Animations linked to specific claims
- Tasks can reference animations for visual learning

### Gap 4: Evidence Types Don't Map to Content Types

**Current State**:
```typescript
type EvidenceType = 
  | 'prediction_vs_outcome'      // Could use simulation
  | 'parameter_targeting'        // Could use simulation
  | 'explanation_quality'        // Could use example
  | 'construction_artifact'      // Could use simulation or animation
```

**Problem**: Evidence types describe *what to measure* but not *how to deliver content*.

**Desired State**: Add explicit content delivery mapping:
```typescript
interface LearningEvidence {
  type: EvidenceType;
  contentDelivery: {
    method: 'example' | 'simulation' | 'animation' | 'video' | 'text';
    interactive: boolean;
    estimatedMinutes: number;
  };
}
```

---

## Recommended Enhancements

### Enhancement 1: Extend Claim Generation to Include Content Recommendations

**Modify**: `services/tutorputor-content-studio/src/ai-engine.ts`

```typescript
// Enhanced prompt for claim generation
const ENHANCED_CLAIM_PROMPT = `
Generate learning claims and specify the best content delivery method for each:

For each claim, determine:
1. Does this claim need concrete examples? (yes/no)
2. Does this claim need interactive simulation? (yes/no)
3. Does this claim need visual animation? (yes/no)
4. What type of examples work best? (real_world, problem_solving, analogy, case_study)

Return JSON:
{
  "claims": [
    {
      "id": "C1",
      "text": "...",
      "bloom": "understand",
      "contentNeeds": {
        "examples": {
          "required": true,
          "types": ["real_world", "analogy"],
          "count": 2
        },
        "simulation": {
          "required": true,
          "interactionType": "parameter_exploration",
          "complexity": "medium"
        },
        "animation": {
          "required": false
        }
      }
    }
  ]
}
`;
```

### Enhancement 2: Auto-Generate Examples for Each Claim

**New Function**: `generateExamplesForClaim()`

```typescript
async function generateExamplesForClaim(
  claim: LearningClaim,
  experience: LearningExperience,
  gradeAdaptation: GradeAdaptation
): Promise<ClaimExample[]> {
  
  const prompt = `
  Generate ${claim.contentNeeds.examples.count} concrete examples for this learning claim:
  
  Claim: ${claim.text}
  Grade Level: ${gradeAdaptation.gradeRange}
  Example Types Needed: ${claim.contentNeeds.examples.types.join(', ')}
  
  For each example, provide:
  1. Title (engaging, grade-appropriate)
  2. Description (2-3 sentences)
  3. Problem statement (if applicable)
  4. Solution/explanation
  5. Key learning points (3-5 bullet points)
  6. Real-world connection
  
  Return JSON array of examples.
  `;
  
  // Call AI service
  const result = await aiService.generate(prompt);
  
  // Store examples linked to claim
  return result.examples.map(ex => ({
    claimRef: claim.claimRef,
    ...ex
  }));
}
```

### Enhancement 3: Auto-Generate Simulations for Claims

**New Function**: `generateSimulationForClaim()`

```typescript
async function generateSimulationForClaim(
  claim: LearningClaim,
  experience: LearningExperience,
  gradeAdaptation: GradeAdaptation
): Promise<SimulationManifest> {
  
  if (!claim.contentNeeds.simulation.required) {
    return null;
  }
  
  const prompt = `
  Create a simulation manifest for this learning claim:
  
  Claim: ${claim.text}
  Domain: ${experience.domain}
  Grade Level: ${gradeAdaptation.gradeRange}
  Interaction Type: ${claim.contentNeeds.simulation.interactionType}
  
  The simulation should:
  1. Allow learners to explore the concept hands-on
  2. Provide immediate visual feedback
  3. Include adjustable parameters
  4. Show cause-and-effect relationships
  5. Be completable in 5-10 minutes
  
  Return simulation manifest in NL-SIM format.
  `;
  
  // Call simulation generation service
  const manifest = await simAuthorService.generateFromNL(prompt);
  
  // Link simulation to claim
  await prisma.claimSimulation.create({
    data: {
      claimRef: claim.claimRef,
      experienceId: experience.id,
      simulationManifestId: manifest.id
    }
  });
  
  return manifest;
}
```

### Enhancement 4: Integrate Animation Generation

**New Function**: `generateAnimationForClaim()`

```typescript
async function generateAnimationForClaim(
  claim: LearningClaim,
  experience: LearningExperience
): Promise<Animation> {
  
  if (!claim.contentNeeds.animation.required) {
    return null;
  }
  
  const prompt = `
  Create an educational animation for this concept:
  
  Claim: ${claim.text}
  Domain: ${experience.domain}
  
  Animation should:
  1. Visualize the key concept in 30-60 seconds
  2. Use clear, simple visuals
  3. Include step-by-step progression
  4. Support pause/play/replay
  5. Be accessible (alt text, captions)
  
  Return animation specification with keyframes and timeline.
  `;
  
  const animation = await animationService.generate(prompt);
  
  // Store animation linked to claim
  await prisma.claimAnimation.create({
    data: {
      claimRef: claim.claimRef,
      experienceId: experience.id,
      animationId: animation.id,
      config: animation.config
    }
  });
  
  return animation;
}
```

### Enhancement 5: Update Data Model

**New Prisma Models**:

```prisma
model ClaimExample {
  id           String             @id @default(cuid())
  experienceId String
  experience   LearningExperience @relation(fields: [experienceId], references: [id], onDelete: Cascade)
  claimRef     String
  
  type         String // "real_world" | "problem_solving" | "analogy" | "case_study"
  title        String
  description  String
  problemStatement String?
  solutionContent  String
  keyLearningPoints Json // string[]
  realWorldConnection String?
  
  orderIndex   Int
  createdAt    DateTime @default(now())
  updatedAt    DateTime @updatedAt
  
  @@index([experienceId, claimRef])
}

model ClaimSimulation {
  id                   String             @id @default(cuid())
  experienceId         String
  experience           LearningExperience @relation(fields: [experienceId], references: [id], onDelete: Cascade)
  claimRef             String
  
  simulationManifestId String
  simulationManifest   SimulationManifest @relation(fields: [simulationManifestId], references: [id])
  
  interactionType      String // "parameter_exploration" | "prediction" | "construction"
  estimatedMinutes     Int @default(10)
  
  createdAt            DateTime @default(now())
  
  @@unique([experienceId, claimRef])
  @@index([experienceId])
}

model ClaimAnimation {
  id           String             @id @default(cuid())
  experienceId String
  experience   LearningExperience @relation(fields: [experienceId], references: [id], onDelete: Cascade)
  claimRef     String
  
  animationId  String @unique
  title        String
  description  String
  duration     Int // seconds
  config       Json // Animation keyframes and timeline
  
  createdAt    DateTime @default(now())
  
  @@unique([experienceId, claimRef])
  @@index([experienceId])
}
```

---

## Enhanced Workflow

### New Content Generation Flow

```typescript
// services/tutorputor-content-studio/src/service.ts

async function generateLearningExperience(request: GenerateExperienceRequest) {
  
  // 1. Generate claims with content needs analysis
  const claims = await aiEngine.generateClaimsWithContentNeeds(
    request.experience,
    request.gradeAdaptation
  );
  
  // 2. For each claim, generate supporting content in parallel
  const contentPromises = claims.map(async (claim) => {
    const [examples, simulation, animation] = await Promise.all([
      
      // Generate examples if needed
      claim.contentNeeds.examples.required
        ? generateExamplesForClaim(claim, request.experience, request.gradeAdaptation)
        : [],
      
      // Generate simulation if needed
      claim.contentNeeds.simulation.required
        ? generateSimulationForClaim(claim, request.experience, request.gradeAdaptation)
        : null,
      
      // Generate animation if needed
      claim.contentNeeds.animation.required
        ? generateAnimationForClaim(claim, request.experience)
        : null
    ]);
    
    return {
      claim,
      examples,
      simulation,
      animation
    };
  });
  
  const claimContent = await Promise.all(contentPromises);
  
  // 3. Generate evidence based on available content
  const evidence = await aiEngine.generateEvidence(
    request.experience,
    claimContent, // Pass content for context
    request.gradeAdaptation
  );
  
  // 4. Generate tasks that reference simulations/animations
  const tasks = await aiEngine.generateTasks(
    request.experience,
    claimContent, // Pass content for task design
    request.gradeAdaptation
  );
  
  // 5. Return complete learning experience
  return {
    experience: request.experience,
    claims: claimContent,
    evidence,
    tasks
  };
}
```

---

## Implementation Roadmap

### Phase 1: Data Model Enhancement (Week 1)
- [ ] Add `ClaimExample`, `ClaimSimulation`, `ClaimAnimation` models to schema
- [ ] Create migration
- [ ] Update TypeScript types in contracts
- [ ] Seed database with example data

### Phase 2: AI Prompt Enhancement (Week 1-2)
- [ ] Update `generateClaims` prompt to include content needs analysis
- [ ] Add `generateExamplesForClaim` function
- [ ] Test with various domains (Physics, Math, Biology)
- [ ] Validate output quality

### Phase 3: Simulation Integration (Week 2-3)
- [ ] Implement `generateSimulationForClaim` function
- [ ] Connect to existing `sim-author` service
- [ ] Handle simulation generation failures gracefully
- [ ] Add simulation preview in UI

### Phase 4: Animation Integration (Week 3-4)
- [ ] Implement `generateAnimationForClaim` function
- [ ] Create animation rendering component
- [ ] Add animation controls (play, pause, replay, speed)
- [ ] Ensure accessibility (captions, alt text)

### Phase 5: Workflow Integration (Week 4)
- [ ] Update `ContentStudioService.generateLearningExperience()`
- [ ] Implement parallel content generation
- [ ] Add progress indicators in UI
- [ ] Handle partial failures (some content generated, some failed)

### Phase 6: Testing & Refinement (Week 5)
- [ ] End-to-end testing with real educators
- [ ] Measure content generation quality
- [ ] Optimize AI prompts based on feedback
- [ ] Performance optimization (caching, parallel processing)

---

## Success Metrics

### Quality Metrics
- **100% Claim Coverage**: Every claim has at least one example
- **Simulation Appropriateness**: 80%+ of claims requiring hands-on learning have simulations
- **Animation Clarity**: 90%+ of visual concepts have animations
- **Grade Appropriateness**: 95%+ of generated content matches grade level

### Performance Metrics
- **Generation Time**: < 2 minutes for complete experience (3-5 claims)
- **Success Rate**: 95%+ of generations complete without errors
- **Educator Satisfaction**: 4.5/5 rating on content quality

### Usage Metrics
- **Adoption Rate**: 80%+ of educators use AI-generated content
- **Refinement Rate**: < 20% of generated content requires manual editing
- **Learner Engagement**: 30%+ increase in simulation interaction time

---

## Risks & Mitigation

### Risk 1: AI Generation Quality
**Risk**: Generated examples/simulations may be inaccurate or inappropriate  
**Mitigation**: 
- Implement guardrails (accuracy, harmlessness, authority checks)
- Require educator review before publishing
- Collect feedback and continuously improve prompts

### Risk 2: Performance/Cost
**Risk**: Generating multiple pieces of content per claim is expensive  
**Mitigation**:
- Use caching for similar claims
- Implement progressive generation (examples first, simulations on demand)
- Use cheaper models for drafts, expensive models for final content

### Risk 3: Simulation Complexity
**Risk**: Auto-generated simulations may be too simple or too complex  
**Mitigation**:
- Start with template-based simulations
- Gradually increase complexity based on domain
- Allow educators to refine simulation parameters

---

## Conclusion

The tutorputor platform has excellent foundations for evidence-based learning, but the current AI generation workflow doesn't ensure that **every claim is backed by concrete examples, simulations, or animations**. 

By implementing the enhancements outlined in this document, we can achieve:

1. **Automatic Content Richness**: Every claim gets appropriate supporting content
2. **Implicit AI Usage**: Educators describe what they want, AI handles the details
3. **Evidence-Based Design**: All content explicitly supports measurable learning outcomes
4. **Grade-Appropriate Delivery**: Content automatically adapts to learner level

This will transform content authoring from a manual, time-consuming process into a fast, AI-assisted workflow that produces high-quality, evidence-based learning experiences.
