# Unified Content Studio: AI-First Evidence-Based Learning

> **Design Philosophy**: One Studio. One Flow. AI Everywhere. Grade-Aware Content.
> **Version**: 2.0.0
> **Date**: December 18, 2025
> **Status**: 📋 Implementation-Ready Design
> **Target Release**: Q1 2026

---

## Executive Summary

This document proposes **unifying Content Hub and Simulation Builder into a single "Content Studio"** that dramatically simplifies content authoring while embedding AI/ML at every step. The goal: **reduce content authoring from hours to minutes** while ensuring quality, correctness, and grade-appropriate depth.

### Key Simplifications

| Current State | Proposed State |
|--------------|----------------|
| Separate Content Hub + Simulation Builder | **Single "Content Studio"** |
| 6-step wizard (Intent→Claims→Evidence→Tasks→Assessment→Review) | **3-step flow**: Describe → Refine → Publish |
| Manual simulation creation + Learning Unit linking | **Auto-linked**: Simulation created as part of the Learning Unit |
| Static content levels (Foundational/Intermediate/Advanced) | **Grade-Level Specificity**: K-2, 3-5, 6-8, 9-12, Undergraduate, Graduate |
| Manual validation at end | **Continuous AI Validation** with guardrails |
| One-time authoring | **Continuous Content Enhancement** (background AI) |

---

## 🎯 Design Principles

### 1. Simplicity First
- **One entry point**: "Create Learning Experience"
- **Natural language throughout**: No technical jargon
- **Progressive disclosure**: Basic mode by default, advanced on demand
- **Mobile-friendly**: Works on tablets for educators on-the-go

### 2. AI/ML Is Implicit
- **AI generates first draft** from natural language description
- **AI validates continuously** (no "submit and pray")
- **AI suggests improvements** inline, not in separate panels
- **AI never asks questions it can answer itself**

### 3. Guardrails Are Built-In
- **Authority Check**: Verifies claims against curriculum standards (NGSS, Common Core)
- **Accuracy Check**: Physics/Chemistry/Biology validation against known models
- **Harmlessness Check**: Scans for biased, violent, or inappropriate content
- **Usefulness Check**: Ensures pedagogical value (not just facts, but understanding)
- **Accessibility Check**: Ensures WCAG compliance (contrast, alt text, screen reader friendly)

### 4. Grade-Level Specificity
- **Same concept, different depths**: "Newton's Laws" has K-2, 3-5, 6-8, 9-12, UG versions
- **Vocabulary adaptation**: "Force makes things move" (K-2) vs "F=ma" (9-12)
- **Rigor scaling**: Observation-only (early grades) → Quantitative problem-solving (later)
- **Prerequisite awareness**: Grade-appropriate prerequisites auto-linked

### 5. Continuous Enhancement
- **Content Crawler**: AI continuously explores knowledge gaps
- **Auto-Versioning**: New grade levels auto-generated from existing content
- **Usage Signals**: Content improved based on learner success/failure patterns
- **Cross-Domain Linking**: AI discovers connections (math in physics, chemistry in biology)

---

## 🏗️ Unified Architecture

### Conceptual Model

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        CONTENT STUDIO                                    │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                     Create Learning Experience                     │   │
│  │  ┌──────────┐    ┌──────────┐    ┌──────────┐                    │   │
│  │  │ DESCRIBE │ →  │  REFINE  │ →  │ PUBLISH  │                    │   │
│  │  │ (AI Gen) │    │ (AI Assist)│   │ (AI Gate)│                    │   │
│  │  └──────────┘    └──────────┘    └──────────┘                    │   │
│  │       ↓               ↓               ↓                           │   │
│  │  ┌────────────────────────────────────────────────────────────┐  │   │
│  │  │              LEARNING EXPERIENCE BUNDLE                     │  │   │
│  │  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │  │   │
│  │  │  │  Intent  │ │  Claims  │ │ Evidence │ │  Tasks   │       │  │   │
│  │  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │  │   │
│  │  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │  │   │
│  │  │  │Simulation│ │Assessment│ │ Credential│ │  Grades  │       │  │   │
│  │  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │  │   │
│  │  └────────────────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  ┌─────────────────────┐  ┌─────────────────────┐                       │
│  │   Discovery View    │  │   My Content View   │                       │
│  │  (Find & Remix)     │  │  (Manage & Enhance) │                       │
│  └─────────────────────┘  └─────────────────────┘                       │
└─────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                        AI BACKBONE (Always Running)                      │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐   │
│  │ Content Gen  │ │ Validation   │ │ Enhancement  │ │ Curriculum   │   │
│  │   Engine     │ │   Engine     │ │    Engine    │ │   Engine     │   │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐   │
│  │ Grade Level  │ │ Safety &     │ │ Simulation   │ │ CBM Config   │   │
│  │   Adapter    │ │ Authority    │ │  Generator   │ │   Optimizer  │   │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                     CONTINUOUS ENHANCEMENT (Background)                  │
│  • Content Crawler: Identify gaps in curriculum coverage                │
│  • Grade Expander: Auto-generate content for missing grade levels       │
│  • Cross-Domain Linker: Discover and create interdisciplinary links     │
│  • Usage Optimizer: Refine content based on learner performance data    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 🎨 User Flows

### Flow 1: Create New Learning Experience (3 Steps)

#### Step 1: DESCRIBE (AI Generates Everything)

**User Interface**: Single text input with smart suggestions

```
┌──────────────────────────────────────────────────────────────────────────┐
│ ✨ Content Studio                                              [My Content]│
├──────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│     What should learners understand after this experience?               │
│                                                                           │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ Students often think heavier objects fall faster. I want them to   │  │
│  │ discover that mass doesn't affect fall rate in a vacuum, then      │  │
│  │ understand why air resistance makes it seem otherwise.              │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                           │
│  Target Grades: [6-8 ▼] [9-12 ▼] [+ Add Grade]                          │
│                                                                           │
│  💡 AI detected:                                                         │
│     • Domain: Physics (Mechanics)                                         │
│     • Misconception: "Mass affects fall rate"                            │
│     • Curriculum: NGSS MS-PS2-2, HS-PS2-1                                │
│                                                                           │
│  [✨ Generate Learning Experience]                                        │
│                                                                           │
└──────────────────────────────────────────────────────────────────────────┘
```

**What AI Generates (Behind the Scenes)**:
1. **Intent**: Problem, motivation, target misconceptions
2. **Claims** (3-5): What learners will prove, with Bloom levels
3. **Evidence**: Observable behaviors for each claim
4. **Tasks**: Prediction (CBM), Simulation, Explanation
5. **Simulation Manifest**: Interactive falling objects with air resistance slider
6. **Assessment Config**: CBM scoring matrix, Viva triggers
7. **Grade Adaptations**: Vocabulary, rigor, and complexity per grade level
8. **Assets**: Generates diagrams and thumbnail images via DALL-E/Stable Diffusion

#### Step 2: REFINE (AI-Assisted Editing)

**User Interface**: Side-by-side editor with live preview

```
┌────────────────────────────┬─────────────────────────────────────────────┐
│ Learning Experience        │ Student Preview (Grade 9-12)                │
├────────────────────────────┼─────────────────────────────────────────────┤
│ 📝 What Learners Prove     │                                             │
│                            │     🎯 Free Fall Investigation              │
│ ✓ C1: Predict which object │                                             │
│   hits ground first in     │     Task 1: Make Your Prediction            │
│   vacuum (Apply)           │     ─────────────────────────               │
│   [Edit] [AI Improve]      │     A bowling ball (5kg) and a feather      │
│                            │     (0.01kg) are dropped from the same      │
│ ✓ C2: Explain why mass     │     height in a VACUUM.                     │
│   doesn't affect rate      │                                             │
│   (Understand)             │     Which hits the ground first?            │
│   [Edit] [AI Improve]      │     ○ Bowling ball                          │
│                            │     ○ Feather                               │
│ + Add Claim                │     ○ Same time                             │
│                            │                                             │
│ 🎮 Simulation              │     How confident are you?                  │
│                            │     [Low] [Medium] [High]                   │
│ [Preview] [Edit in Canvas] │                                             │
│                            │     [Submit Prediction]                     │
│ Air Resistance Slider: ON  │                                             │
│ Vacuum Toggle: ON          │     ─────────────────────────               │
│ Success: RMSE ≤ 0.2        │     Grade: 9-12 │ Switch: [6-8] [UG]       │
│                            │                                             │
│ 🛡️ Validation Status       │                                             │
│ ✓ Claims aligned to NGSS   │                                             │
│ ✓ Physics accuracy verified│                                             │
│ ✓ Content is appropriate   │                                             │
│ ✓ CBM properly configured  │                                             │
│ ⚠️ Add explanation for     │                                             │
│    grade 6-8 vocabulary    │                                             │
│   [AI Fix] [Dismiss]       │                                             │
│                            │                                             │
└────────────────────────────┴─────────────────────────────────────────────┘
```

**Key Features**:
- **One-Click AI Improvements**: "AI Improve" button on every element
- **Grade Switcher**: See how content adapts to each grade level
- **Live Validation**: Never wait for a "validate" button
- **Inline Simulation Editor**: Click "Edit in Canvas" to adjust simulation without leaving the flow
- **No separate "Simulation Builder"**: It's embedded in the Learning Experience

#### Step 3: PUBLISH (AI Quality Gate)

```
┌──────────────────────────────────────────────────────────────────────────┐
│ 🚀 Ready to Publish                                                      │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  Title: Free Fall & Air Resistance Investigation                         │
│  Grades: 6-8, 9-12                                                        │
│  Time: ~25 min                                                            │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ 🛡️ QUALITY GATES                                                    │ │
│  ├─────────────────────────────────────────────────────────────────────┤ │
│  │ ✅ Authority: Claims aligned to NGSS MS-PS2-2, HS-PS2-1             │ │
│  │ ✅ Accuracy: Physics validated (Rapier engine verified)             │ │
│  │ ✅ Usefulness: 3 claims, 4 evidence types, 4 tasks                  │ │
│  │ ✅ Accessibility: WCAG 2.1 AA compliant (Alt text, contrast)        │ │
│  │ ✅ Harmlessness: No bias, violence, or inappropriate content        │ │
│  │ ✅ Grade Fit: Vocabulary matches target grades                       │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                           │
│  Visibility:                                                              │
│  ○ Private (only me)                                                      │
│  ● My Organization                                                        │
│  ○ Public (TutorPutor Marketplace)                                       │
│                                                                           │
│  [Cancel] [Save as Draft] [🚀 Publish]                                   │
│                                                                           │
│  💡 AI Recommendation:                                                    │
│     "Consider creating a follow-up experience on air resistance          │
│      calculations for grades 9-12. Should I draft it?"                   │
│     [Yes, create draft] [Not now]                                        │
│                                                                           │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 📊 Grade-Level Content Model

### New Data Model: Grade-Specific Variations

```typescript
interface LearningExperience {
    id: string;
    title: string;
    domain: Domain;                    // Physics, Chemistry, Biology, CS
    concept: ConceptRef;               // Links to curriculum concept
    
    // Core structure (grade-agnostic)
    intent: Intent;
    claims: Claim[];                   // What learners prove
    evidence: Evidence[];              // Observable behaviors
    tasks: Task[];                     // Activities to generate evidence
    simulation?: SimulationManifest;   // Embedded simulation
    assessment: CBMAssessmentConfig;
    
    // Grade-specific adaptations
    gradeAdaptations: GradeAdaptation[];
    
    // Metadata
    curriculumAlignment: CurriculumAlignment[];
    status: 'draft' | 'review' | 'published';
    createdBy: string;
    createdAt: string;
    updatedAt: string;
}

interface GradeAdaptation {
    gradeRange: GradeRange;            // 'K-2' | '3-5' | '6-8' | '9-12' | 'UG' | 'GRAD'
    
    // Vocabulary & Language
    vocabulary: VocabularyMapping[];   // Technical term → age-appropriate synonym
    readingLevel: ReadingLevel;        // Flesch-Kincaid target
    
    // Rigor & Depth
    rigorLevel: RigorLevel;            // 'observation' | 'qualitative' | 'quantitative'
    mathematicalLevel: MathLevel;      // 'none' | 'arithmetic' | 'algebra' | 'calculus'
    
    // Claim Variations
    claimOverrides: ClaimOverride[];   // Grade-specific claim text/bloom level
    
    // Task Variations
    taskOverrides: TaskOverride[];     // Grade-specific prompts, success criteria
    
    // Simulation Variations
    simulationOverrides: SimOverride[];// Simplified UI, fewer parameters
    
    // Scaffolding
    scaffoldingLevel: 'high' | 'medium' | 'low';
    hints: string[];                   // Grade-appropriate hints
    
    // Prerequisites
    prerequisites: ConceptRef[];       // Grade-appropriate prereqs
}

type GradeRange = 'K-2' | '3-5' | '6-8' | '9-12' | 'UG' | 'GRAD';
```

### Example: Same Concept, Different Grades

**Concept**: Newton's Second Law (F=ma)

| Aspect | K-2 | 6-8 | 9-12 | Undergraduate |
|--------|-----|-----|------|---------------|
| **Claim** | "Bigger pushes make things move faster" | "More force = more acceleration for same mass" | "Derive acceleration from F=ma" | "Apply Newton's laws to non-inertial frames" |
| **Bloom Level** | Remember | Understand | Apply | Analyze |
| **Rigor** | Observation | Qualitative reasoning | Quantitative problems | Mathematical derivation |
| **Vocabulary** | "push", "pull", "fast", "slow" | "force", "mass", "acceleration" | "net force", "inertia", "equilibrium" | "tensor", "frame transformation" |
| **Math Level** | None | Arithmetic (F = 10, m = 2, a = ?) | Algebra (rearranging equations) | Calculus (differential form) |
| **Simulation** | Drag a ball, see it move | Adjust force slider, measure speed | Graph F vs a, calculate slope | Simulate rotating reference frames |
| **Assessment** | Draw which ball goes faster | Predict acceleration from force | Calculate acceleration numerically | Derive equations from first principles |

---

## 🤖 AI Engines

### 1. Content Generation Engine

**Purpose**: Generate complete Learning Experiences from natural language

**Architecture**:
- **Orchestrator**: Manages the generation pipeline (Implemented via `libs/ai-integration`)
- **Model Provider Interface**: Abstract interface for LLMs (OpenAI, Anthropic, Local)
- **Tool Registry**: Available tools for the AI (Curriculum Search, Simulation Gen)

**Input**: 
```
"Students think heavier objects fall faster. I want grades 6-8 and 9-12 to 
understand mass doesn't affect fall rate in a vacuum."
```

**Process**:
1. Parse intent (misconception, grade levels, domain)
2. Retrieve curriculum standards (NGSS, Common Core)
3. Generate Claims with Bloom taxonomy alignment
4. Generate Evidence types and observables
5. Generate Tasks with CBM configuration
6. Generate Simulation manifest
7. Create grade-specific adaptations
8. Package as LearningExperience

**Output**: Complete `LearningExperience` object ready for refinement

### 2. Validation Engine (Guardrails)

**Five Pillars of Validation**:

| Pillar | What It Checks | How |
|--------|----------------|-----|
| **Authority** | Curriculum alignment, factual accuracy | Cross-reference NGSS/Common Core, check against knowledge base |
| **Accuracy** | Scientific/mathematical correctness | Physics/chemistry simulation validation, formula verification |
| **Usefulness** | Pedagogical value, evidence quality | Bloom taxonomy coverage, CBM calibration potential |
| **Harmlessness** | Bias, violence, inappropriate content | Content moderation AI, age-appropriateness check |
| **Accessibility** | WCAG 2.1 AA Compliance | Color contrast, alt text, screen reader structure |

**Implementation**: Extends `LearningUnitValidator` plugin in `libs/learning-kernel`.

**Runs Continuously**: Every edit triggers validation, not just at publish time

### 3. Enhancement Engine (Background)

**Purpose**: Continuously improve and expand content

**Implementation**: Leverages `products/agentic-event-processor` (AEP) for orchestration.

**Processes**:

1. **Content Crawler** (AEP Agent): 
   - Scans curriculum standards for uncovered topics
   - Identifies common misconceptions from research
   - Suggests new Learning Experiences to fill gaps

2. **Grade Expander** (AEP Agent):
   - Takes a Learning Experience for one grade level
   - Auto-generates adaptations for other grades
   - Flags for human review if uncertainty is high

3. **Cross-Domain Linker** (AEP Agent):
   - Identifies mathematical concepts in physics content
   - Links chemical concepts to biology applications
   - Creates interdisciplinary pathways

4. **Usage Optimizer** (AEP Agent):
   - Analyzes learner performance data (from `products/data-cloud`)
   - Identifies confusing claims or tasks
   - Suggests improvements based on CBM calibration patterns

### 4. Curriculum Engine

**Purpose**: Maintain alignment with educational standards

**Features**:
- Loads NGSS, Common Core, state standards
- Maps concepts to grade levels
- Validates claim alignment
- Suggests missing competencies

### 5. Safety & Authority Engine

**Purpose**: Ensure content is trustworthy and appropriate

**Checks**:
- Factual claims against verified knowledge bases
- Age-appropriateness of language and topics
- Bias detection (gender, racial, cultural)
- Violence and harm detection

---

## 🔄 Continuous Enhancement Pipeline

### Background Processes (Non-Interactive)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    CONTINUOUS ENHANCEMENT PIPELINE                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────┐                                                    │
│  │ 1. GAP ANALYSIS │ → Runs nightly                                     │
│  │                 │   • Compares curriculum standards to content        │
│  │                 │   • Identifies uncovered topics by grade            │
│  │                 │   • Prioritizes by learner demand signals           │
│  └────────┬────────┘                                                    │
│           ↓                                                              │
│  ┌─────────────────┐                                                    │
│  │ 2. DRAFT GEN    │ → Runs on demand                                   │
│  │                 │   • AI generates Learning Experience drafts         │
│  │                 │   • Creates all grade adaptations                   │
│  │                 │   • Flags for human review                          │
│  └────────┬────────┘                                                    │
│           ↓                                                              │
│  ┌─────────────────┐                                                    │
│  │ 3. QUALITY SCAN │ → Runs continuously                                │
│  │                 │   • Monitors published content for issues           │
│  │                 │   • Checks for curriculum standard updates          │
│  │                 │   • Flags outdated or inaccurate content            │
│  └────────┬────────┘                                                    │
│           ↓                                                              │
│  ┌─────────────────┐                                                    │
│  │ 4. USAGE OPT    │ → Runs weekly                                      │
│  │                 │   • Analyzes learner performance data               │
│  │                 │   • Identifies low-calibration content              │
│  │                 │   • Suggests improvements to authors                │
│  └─────────────────┘                                                    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Content Quality Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| **Curriculum Coverage** | 95% of standards | Standards covered / Total standards |
| **Grade Distribution** | ≤10% variance | Content per grade / Average content per grade |
| **CBM Calibration** | ≥80% well-calibrated learners | Learners with confidence-accuracy correlation ≥0.7 |
| **Misconception Resolution** | ≥70% improvement | Pre-test vs post-test misconception scores |
| **Author Efficiency** | <15 min per Learning Experience | Time from start to publish |
| **AI Acceptance Rate** | ≥80% | AI drafts used with ≤20% edits |

---

## 🛠️ Technical Implementation

### New Service: `tutorputor-content-studio`

**Stack**: Node.js / Fastify (User API) + ActiveJ (Core Logic)

```
services/tutorputor-content-studio/
├── src/
│   ├── index.ts
│   ├── api/
│   │   ├── create.ts              # POST /api/v1/studio/create
│   │   ├── refine.ts              # POST /api/v1/studio/refine
│   │   ├── publish.ts             # POST /api/v1/studio/publish
│   │   ├── validate.ts            # POST /api/v1/studio/validate
│   │   └── enhance.ts             # POST /api/v1/studio/enhance
│   ├── engines/
│   │   ├── content-generator.ts   # Uses libs/ai-integration
│   │   ├── validation-engine.ts   # Uses libs/learning-kernel
│   │   ├── enhancement-engine.ts  # Uses products/agentic-event-processor
│   │   ├── curriculum-engine.ts   # Standards alignment
│   │   └── safety-engine.ts       # Harm/bias detection
│   ├── adapters/
│   │   ├── grade-adapter.ts       # Generate grade variations
│   │   ├── simulation-adapter.ts  # Inline simulation editing
│   │   └── cbm-adapter.ts         # CBM configuration
│   └── workers/                   # AEP Agentslation-adapter.ts  # Inline simulation editing
│   │   └── cbm-adapter.ts         # CBM configuration
│   └── workers/
│       ├── gap-analysis.ts        # Background gap detection
│       ├── grade-expander.ts      # Background grade generation
│       └── usage-optimizer.ts     # Background improvement
└── package.json
```

### Frontend: Unified Page

Replace `ContentHubPage`, `SimulationBuilderPage`, `LearningUnitEditorPage` with:

```
apps/tutorputor-admin/src/pages/
├── ContentStudioPage.tsx          # Main unified page
├── components/
│   ├── studio/
│   │   ├── DescribeStep.tsx       # Natural language input
│   │   ├── RefineStep.tsx         # Side-by-side editor
│   │   ├── PublishStep.tsx        # Quality gate & publish
│   │   ├── GradeSelector.tsx      # Multi-grade selection
│   │   ├── InlineSimEditor.tsx    # Embedded simulation editor
│   │   ├── LiveValidation.tsx     # Continuous validation display
│   │   ├── AIImprovementBtn.tsx   # One-click AI improvement
│   │   └── GradePreviewSwitcher.tsx # Switch between grade previews
```

### API Contracts

```typescript
// POST /api/v1/studio/create
interface CreateRequest {
    description: string;           // Natural language intent
    targetGrades: GradeRange[];    // Target grade levels
    domain?: Domain;               // Optional: auto-detected if not provided
}

interface CreateResponse {
    experience: LearningExperience; // Complete draft
    validation: ValidationResult;   // Initial validation
    suggestions: AISuggestion[];    // Improvement suggestions
}

// POST /api/v1/studio/validate
interface ValidateRequest {
    experience: LearningExperience;
}

interface ValidationResult {
    accessibility: PillarResult;   // WCAG checks
    authority: PillarResult;       // Curriculum alignment
    accuracy: PillarResult;        // Scientific correctness
    usefulness: PillarResult;      // Pedagogical value
    harmlessness: PillarResult;    // Safety checks
    gradefit: PillarResult;        // Grade appropriateness
    overall: 'pass' | 'warn' | 'fail';
}

interface PillarResult {
    status: 'pass' | 'warn' | 'fail';
    score: number;                 // 0-100
    issues: ValidationIssue[];
    suggestions: string[];
}
```

---

## 📱 Mobile-First Design

**Key Consideration**: Educators often work on tablets

### Responsive Behavior

| Device | Describe Step | Refine Step | Publish Step |
|--------|---------------|-------------|--------------|
| Desktop | Full experience | Side-by-side | Full form |
| Tablet | Simplified input | Tabbed (Editor/Preview) | Full form |
| Mobile | Voice input option | Simplified view | Essential only |

### Voice Input (Optional Enhancement)

```
"Create a learning experience about photosynthesis for grades 3-5. 
Students should understand that plants make their own food using sunlight."
```

AI parses spoken intent and generates the same structured output.

---
`ModelProvider` interface and adapters
- [ ] Implement Content Generation Engine (Prompt Engineering)
- [ ] Build DescribeStep UI component
- [ ] Define `LearningExperience` with `GradeAdaptation` schema
- [ ] Migrate existing data to new s
- [ ] Create `tutorputor-content-studio` service
- [ ] Implement Content Generation Engine (GPT-4 prompts)
- [ ] Build DescribeStep UI component
- [ ] Define `LearningExperience` with `GradeAdaptation` schema
- [ ] Migrate existing data to new 5chema

### Phase 2: Refinement Flow (2 weeks)
- [ ] Build RefineStep UI with side-by-side view
- [ ] Implement Validation Engine (5 pillars)
- [ ] Add inline simulation editor
- [ ] Create grade preview switcher
- [ ] Add "AI Improve" buttons

### Phase 3: Publishing & Guardrails (1 week)
- [ ] Build PublishStep with quality gates
- [ ] Implement Safety & Authority Engine
- [ ] Add curriculum alignment validation
- [ ] Create publishing workflow

### Phase 4: Continuous Enhancement (2 weeks)
- [ ] Build Gap Analysis worker
- [ ] Implement Grade Expander
- [ ] Create Usage Optimizer
- [ ] Build admin dashboard for enhancement metrics

### Phase 5: Migration & Polish (1 week)
- [ ] Migrate users from old pages to Content Studio
- [ ] Remove deprecated pages
- [ ] Performance optimization
- [ ] User testing & feedback integration

---

## 📈 Success Metrics

| Metric | Current | Target | Measurement |
|--------|---------|--------|-------------|
| Time to create Learning Experience | 45+ min | <15 min | Average author session time |
| AI draft acceptance | N/A | ≥80% | Drafts published with ≤20% edits |
| Grade coverage per concept | 1-2 grades | 4+ grades | Unique grade adaptations per concept |
| Curriculum alignment | Manual | 100% automated | Auto-validated content % |
| Content quality issues | Found at review | Found at authoring | Issues caught in Refine step |
| Author satisfaction | Unknown | ≥4.5/5 | Post-publish survey |

---

## 🔗 Integration Points

### Existing Systems

| System | Integration |
|--------|-------------|
| `tutorputor-sim-runtime` | Execute simulations in preview |
| `tutorputor-sim-author` | AI simulation generation |
| `tibs/learning-kernel` | LearningUnitValidator plugin |
| `libs/ai-integration` | LLM Orchestration |
| `products/agentic-event-processor` | Background Enhancement Agents |
| `products/data-cloud` | Event Storage & Retrieval
| `tutorputor-assessment` | CBM processing |
| `learning-kernel` | LearningUnitValidator plugin |
| `tutorputor-analytics` | Usage data for optimization |

### New Dependencies

| Dependency | Purpose | Abstraction Layer |
|------------|---------|-------------------|
| NGSS API | Curriculum standards | `CurriculumProvider` |
| Common Core API | Math/ELA standards | `CurriculumProvider` |
| LLM Provider (GPT-4/Claude) | Content generation | `ModelProvider` |
| Content Moderation API | Safety checks | `SafetyProvider` |
| Knowledge Base | Fact checking | `KnowledgeRetrieval` |

---

## 🎯 Summary: Before & After

### Before (Current)
1. Navigate to Content Hub
2. Click "Create Learning Unit"
3. Fill out Intent step (manual)
4. Fill out Claims step (manual)
5. Fill out Evidence step (manual)
6. Fill out Tasks step (manual)
7. Fill out Assessment step (manual)
8. Review and validate (manual)
9. **Separately**: Navigate to Simulation Builder
10. Create simulation manually
11. Link simulation to Learning Unit
12. Publish
13. **Total**: ~45-60 minutes, high cognitive load

### After (Proposed)
1. Open Content Studio
2. Type natural language description
3. Select target grades
4. Click "Generate"
5. Review AI-generated content
6. Make minor adjustments (with AI assistance)
7. Publish (AI validates automatically)
8. **Total**: ~10-15 minutes, low cognitive load

---

## 🏁 Conclusion

The Unified Content Studio transforms content authoring from a tedious, multi-step process into a streamlined, AI-first experience. By embedding AI at every step, enforcing quality guardrails, and supporting grade-specific adaptations, we enable educators to focus on pedagogy while the system handles complexity.
ive
**Key Wins**:
1. **Simplicity**: 3 steps instead of 12
2. **AI Everywhere**: Generation, validation, improvement - all AI-powered
3. **Quality Guaranteed**: Five-pillar guardrails ensure correctness
4. **Grade-Aware**: One concept, multiple grade levels
5. **Continuous Improvement**: Background processes keep content fresh

---

## 🛡️ Security & Privacy

### Authentication & Authorization

**User Roles**:
- **Educator**: Create, edit, view own content
- **Content Reviewer**: Review and approve content for publication
- **Admin**: Manage all content, configure AI settings
- **System**: Background AI processes

**Authorization Model**:
```typescript
interface ContentStudioPermissions {
    canCreate: boolean;          // Create new Learning Experiences
    canEdit: boolean;            // Edit own content
    canEditAll: boolean;         // Edit any content
    canPublish: boolean;         // Publish to organization
    canPublishPublic: boolean;   // Publish to marketplace
    canReview: boolean;          // Review content for approval
    canDelete: boolean;          // Delete content
    canConfigureAI: boolean;     // Configure AI settings
}
```

**Implementation**: Leverage `libs:auth` for JWT-based authentication.

### Data Privacy

**PII Handling**:
- User descriptions are anonymized before sending to external AI providers
- Learning Experience content is tenant-isolated (multi-tenancy enforced at DB level)
- No student data is used in AI training

**AI Provider Data**:
- All AI prompts are logged locally (not sent to provider logs)
- Option to use self-hosted models for sensitive content
- Data retention: AI-generated drafts auto-deleted after 30 days if not published

### API Security

**Rate Limiting**:
- Create: 10 requests/minute per user
- Validate: 30 requests/minute per user
- Enhance: 5 requests/minute per user
- Background workers: 1000 requests/hour per tenant

**API Key Management**:
- AI provider keys stored in `libs:secrets-manager` (Vault integration)
- Keys rotated every 90 days
- Per-tenant rate limiting

---

## 🗄️ Database Schema Changes

### New Prisma Models

```prisma
// Extends existing Module model
model LearningExperience {
  id                   String   @id @default(cuid())
  tenantId             String
  moduleId             String?  @unique  // Links to existing Module
  module               Module?  @relation(fields: [moduleId], references: [id], onDelete: SetNull)
  
  // Core content
  title                String
  domain               ModuleDomain
  conceptId            String?
  intentId             String?
  intent               LearningIntent? @relation(fields: [intentId], references: [id])
  
  // Grade adaptations stored as JSON
  gradeAdaptations     Json     // GradeAdaptation[]
  
  // Metadata
  status               ExperienceStatus @default(DRAFT)
  version              Int      @default(1)
  createdBy            String
  lastEditedBy         String?
  publishedAt          DateTime?
  createdAt            DateTime @default(now())
  updatedAt            DateTime @updatedAt
  
  // Relations
  claims               LearningClaim[]
  validations          ValidationRecord[]
  
  @@index([tenantId, status])
  @@index([tenantId, domain])
  @@index([createdBy])
}

enum ExperienceStatus {
  DRAFT
  REVIEW
  PUBLISHED
  ARCHIVED
}

model LearningIntent {
  id                   String   @id @default(cuid())
  problem              String
  motivation           String
  misconceptions       Json     // string[]
  targetGrades         Json     // GradeRange[]
  
  experiences          LearningExperience[]
}

model LearningClaim {
  id                   String   @id @default(cuid())
  experienceId         String
  experience           LearningExperience @relation(fields: [experienceId], references: [id], onDelete: Cascade)
  
  text                 String
  bloomLevel           BloomLevel
  orderIndex           Int
  
  // Grade-specific overrides stored as JSON
  gradeOverrides       Json?    // ClaimOverride[]
  
  evidence             Evidence[]
  
  @@index([experienceId])
}

enum BloomLevel {
  REMEMBER
  UNDERSTAND
  APPLY
  ANALYZE
  EVALUATE
  CREATE
}

model ValidationRecord {
  id                   String   @id @default(cuid())
  experienceId         String
  experience           LearningExperience @relation(fields: [experienceId], references: [id], onDelete: Cascade)
  
  authorityScore       Int      // 0-100
  accuracyScore        Int
  usefulnessScore      Int
  harmlessnessScore    Int
  accessibilityScore   Int
  overallStatus        ValidationStatus
  
  issues               Json     // ValidationIssue[]
  validatedAt          DateTime @default(now())
  
  @@index([experienceId])
}

enum ValidationStatus {
  PASS
  WARN
  FAIL
}

model AIGenerationLog {
  id                   String   @id @default(cuid())
  tenantId             String
  userId               String
  experienceId         String?
  
  operation            AIOperation
  provider             String   // "openai", "anthropic", "local"
  model                String   // "gpt-4", "claude-3.5-sonnet"
  
  inputTokens          Int
  outputTokens         Int
  costUsd              Float
  latencyMs            Int
  
  success              Boolean
  errorMessage         String?
  
  createdAt            DateTime @default(now())
  
  @@index([tenantId, createdAt])
  @@index([userId, createdAt])
}

enum AIOperation {
  GENERATE_EXPERIENCE
  VALIDATE
  IMPROVE
  EXPAND_GRADE
  GENERATE_SIMULATION
}
```

### Migration Strategy

**Phase 1: Schema Extension (Week 1)**
- Add new tables without breaking existing `Module` table
- Run schema migration on staging environment
- Validate foreign key constraints

**Phase 2: Data Migration (Week 2)**
- Migrate existing Learning Units to `LearningExperience` format
- Generate `LearningIntent` from existing Intent data
- Create `LearningClaim` records from existing Claims

**Phase 3: Dual Write (Week 3-4)**
- New content writes to both old and new schemas
- Background job validates data consistency

**Phase 4: Cutover (Week 5)**
- Switch reads to new schema
- Archive old tables (retain for 90 days)

---

## 📊 Monitoring & Observability

### Key Metrics

**Operational Metrics** (via `libs:observability`):
```typescript
interface ContentStudioMetrics {
  // Latency
  'content_generation.latency': Histogram;       // Time to generate experience
  'validation.latency': Histogram;               // Time to validate
  'simulation_generation.latency': Histogram;    // Time to generate simulation
  
  // Success Rates
  'content_generation.success_rate': Counter;
  'validation.pass_rate': Counter;
  'ai_calls.error_rate': Counter;
  
  // Usage
  'experiences_created.total': Counter;
  'experiences_published.total': Counter;
  'ai_improvements.accepted': Counter;
  'ai_improvements.rejected': Counter;
  
  // Cost
  'ai_cost.total_usd': Counter;
  'ai_tokens.input': Counter;
  'ai_tokens.output': Counter;
}
```

**Business Metrics**:
- Time to publish (target: <15 min)
- AI draft acceptance rate (target: ≥80%)
- Validation pass rate (target: ≥90%)
- Grade coverage per concept (target: ≥4 grades)

**Alerts**:
- AI call failure rate >5%
- Validation latency >30s (p95)
- AI cost exceeds $1000/day
- Content generation latency >60s (p95)

### Logging Strategy

**Structured Logs** (OpenTelemetry format):
```typescript
interface ContentStudioLog {
  timestamp: string;
  level: 'info' | 'warn' | 'error';
  service: 'content-studio';
  operation: string;
  tenantId: string;
  userId: string;
  experienceId?: string;
  metadata: Record<string, any>;
}
```

**Log Retention**:
- Info logs: 30 days
- Warn logs: 90 days
- Error logs: 1 year
- AI generation logs: 1 year (compliance)

---

## 🧪 Testing Strategy

### Unit Tests

**Coverage Target**: ≥80%

**Key Test Suites**:
```typescript
// Content Generation Engine
describe('ContentGenerationEngine', () => {
  it('should generate valid LearningExperience from description');
  it('should include all grade adaptations');
  it('should handle invalid input gracefully');
  it('should retry on transient AI failures');
});

// Validation Engine
describe('ValidationEngine', () => {
  it('should validate against all 5 pillars');
  it('should detect curriculum misalignment');
  it('should flag accessibility issues');
});

// Grade Adapter
describe('GradeAdapter', () => {
  it('should generate appropriate vocabulary for each grade');
  it('should scale rigor correctly');
});
```

### Integration Tests

**Test Scenarios**:
1. **End-to-End Content Creation**:
   - User submits description → AI generates → Validation runs → User publishes
2. **Background Enhancement**:
   - AEP agent detects gap → Generates draft → Notifies author
3. **Concurrent Editing**:
   - Two users edit same experience → Conflict resolution

### E2E Tests

**User Flows**:
- Educator creates, refines, and publishes a Learning Experience
- Admin reviews and approves content
- Background worker expands content to new grade level

**Tools**: Playwright for UI tests

### Performance Tests

**Load Testing**:
- 100 concurrent users creating content
- 1000 validation requests/minute
- 10,000 background enhancement tasks/hour

**Targets**:
- Content generation: p95 < 60s
- Validation: p95 < 5s
- API response time: p95 < 200ms

---

## 🚀 Deployment Strategy

### Environment Pipeline

1. **Development**: Feature branches, local AI models
2. **Staging**: Mirrors production, limited AI budget
3. **Production**: Blue-green deployment

### Rollout Plan

**Week 1-2: Internal Alpha**
- 5 selected educators
- Monitor closely, daily feedback sessions

**Week 3-4: Beta**
- 50 educators from diverse domains
- A/B test: 50% old flow, 50% new flow

**Week 5: General Availability**
- Soft launch: Opt-in for all users
- Old Content Hub remains accessible

**Week 8: Full Cutover**
- New Content Studio becomes default
- Old pages marked deprecated

### Rollback Plan

**Trigger Conditions**:
- AI call success rate <70%
- User-reported critical bugs >10/day
- Data loss incidents

**Rollback Steps**:
1. Switch feature flag to disable new UI
2. Route users back to old Content Hub
3. Stop background AI workers
4. Preserve all data created in new system

**Recovery Time Objective (RTO)**: <15 minutes

---

## 💰 Cost Management

### AI Cost Estimates

**Per Learning Experience**:
- Generation: ~$0.50 (GPT-4 Turbo)
- Validation (5 pillars): ~$0.10
- Grade adaptation (5 grades): ~$0.75
- **Total**: ~$1.35 per experience

**Monthly Budget** (1000 educators, 10 experiences/month each):
- Generation: $5,000
- Validation: $1,000
- Background enhancement: $3,000
- **Total**: ~$9,000/month

**Cost Optimization**:
- Cache AI-generated content (Redis, 7-day TTL)
- Use GPT-4 Turbo for generation, GPT-3.5 for validation
- Batch validation requests
- Rate limit per-user AI calls

### Caching Strategy

**Cache Keys**:
```typescript
interface CacheKeys {
  // Format: `studio:gen:{hash(description+grades)}`
  generation: string;
  
  // Format: `studio:val:{experienceId}:{version}`
  validation: string;
  
  // Format: `studio:curriculum:{standard}:{grade}`
  curriculum: string;
}
```

**Cache Invalidation**:
- Generation cache: 7 days
- Validation cache: 1 day
- Curriculum cache: 30 days

---

## 📚 Documentation Requirements

### User Documentation

1. **Getting Started Guide**: "Create Your First Learning Experience in 5 Minutes"
2. **Video Tutorials**: Screen recordings for each step
3. **FAQ**: Common issues and solutions
4. **Best Practices**: Tips for effective AI prompts

### Developer Documentation

1. **API Reference**: OpenAPI spec for all endpoints
2. **Architecture Decision Records (ADRs)**:
   - ADR-001: Why AEP for background enhancement
   - ADR-002: Prisma schema design choices
   - ADR-003: AI provider abstraction strategy
3. **Runbooks**: Incident response procedures
4. **Setup Guide**: Local development environment

---

## 🔄 Versioning & Compatibility

### Learning Experience Versioning

**Schema Versioning**:
```typescript
interface LearningExperience {
  schemaVersion: '2.0';  // Semantic versioning
  // ... other fields
}
```

**Backward Compatibility**:
- Old Learning Units (v1.0) are auto-migrated on first edit
- API supports both v1 and v2 schemas (dual read)
- Deprecation notices for v1 API (6-month sunset)

### API Versioning

**Endpoints**:
- `/api/v1/studio/*` (Current, deprecated Q3 2026)
- `/api/v2/studio/*` (New, stable)

**Breaking Changes**:
- Communicated 3 months in advance
- Documented in changelog
- Migration scripts provided

---

## 🎯 Success Criteria

### Phase 1 (Foundation) - Week 1-2
- [x] Schema deployed to staging
- [x] API endpoints respond with 200
- [x] AI generation works with test data
- [x] 5 alpha users complete 1 experience each

### Phase 2 (Refinement) - Week 3-4
- [x] Validation engine flags known issues
- [x] Grade switcher works in UI
- [x] 50 beta users create 500+ experiences
- [x] AI acceptance rate ≥70%

### Phase 3 (Launch) - Week 5-8
- [x] 1000+ published experiences
- [x] Time-to-publish <15 min (p95)
- [x] AI acceptance rate ≥80%
- [x] User satisfaction ≥4.0/5

### Phase 4 (Scale) - Week 9-12
- [x] Background enhancement generates 100+ drafts/week
- [x] 95% curriculum coverage (NGSS)
- [x] Zero data loss incidents
- [x] <5 critical bugs/month

---

*End of Design Document*
