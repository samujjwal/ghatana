# TutorPutor Admin - Content Authoring & Domain Definition Flow

**Date:** December 13, 2025  
**Status:** 📋 Analysis & Specification  
**Target:** Enable admins to author domain content with integrated simulations and visualizations

---

## Executive Summary

This document defines the **Content Authoring Flow** for TutorPutor admins to create domain-specific educational content with inherent simulations and visualizations. The admin must be able to:

1. **Define Domains** - Create/edit domains (Physics, Chemistry, Biology, etc.)
2. **Author Concepts** - Add concepts with learning objectives, prerequisites, and metadata
3. **Embed Simulations** - Integrate simulation manifests directly into concepts
4. **Embed Visualizations** - Include visualization configurations for concepts
5. **Generate Examples** - Create 2-3 complete worked examples per concept with full content stack

---

## Part 1: Current State Analysis

### 1.1 Existing Infrastructure

#### Completed Components ✅

| Component | Location | Status | Purpose |
|-----------|----------|--------|---------|
| **Content Files** | `content/domains/*.json` | Published | Domains: Physics, Chemistry, Biology, Business, Lang Arts |
| **Domain Loader** | `services/tutorputor-domain-loader` | Implemented | Load domain JSON → Database |
| **Simulation Engine** | `services/tutorputor-sim-runtime` | Implemented | Execute simulation manifests |
| **Admin Pages** | `apps/tutorputor-admin/src/pages` | Partial | UI for viewing/managing content |
| **API Gateway** | `apps/api-gateway/src/routes` | Implemented | Endpoints for CRUD operations |
| **Database Schema** | `services/tutorputor-db/prisma` | Implemented | DomainConcept, Module, Simulation models |

#### Content Structure (Current)

```json
{
  "domain": "Physics",
  "levels": {
    "Foundational": {
      "concepts": [
        {
          "id": "phy_F_1",
          "name": "Kinematics: Motion in 1D",
          "description": "...",
          "prerequisites": [],
          "simulation_metadata": {
            "simulation_type": "physics-2D",
            "recommended_interactivity": "high",
            "purpose": "Visualize position, velocity, acceleration graphs",
            "estimated_time": "20 min"
          },
          "pedagogical_metadata": {
            "learning_objectives": [
              "Define position, velocity, acceleration",
              "Solve 1D kinematics problems"
            ],
            "competencies": ["physics reasoning", "problem-solving"],
            "scaffolding_level": "scaffolded"
          }
        }
      ]
    }
  }
}
```

#### Database Models (Current)

```prisma
model DomainConcept {
  id                    String
  domain                String
  name                  String
  description           String
  level                 String           // "Foundational", "Intermediate", etc
  prerequisites         String[]
  simulationType        String?          // NOT POPULATED
  visualizationType     String?          // NOT POPULATED
  learningObjectives    String[]
  keywords              String[]
  createdAt             DateTime
  updatedAt             DateTime
  
  // ❌ Missing: Actual simulation manifest
  // ❌ Missing: Visualization config
  // ❌ Missing: Example content
}

model Module {
  id                    String
  name                  String
  description           String
  conceptId             String?          // FK to DomainConcept
  content               String           // Markdown
  objectives            String[]
  assessments           Assessment[]
  createdAt             DateTime
}

model Simulation {
  id                    String
  manifestId            String
  manifest              Json             // Simulation manifest
  moduleId              String?          // FK to Module
  createdAt             DateTime
}
```

#### Admin Pages (Current)

| Page | Location | Features | Status |
|------|----------|----------|--------|
| **Content** | `ContentPage.tsx` | List domains, search, view coverage | ✅ Working |
| **Domain Detail** | `DomainContentDetailPage.tsx` | Show concepts, coverage analysis | ✅ Working |
| **Templates** | `TemplatesAdminPage.tsx` | View simulation templates | ✅ Working |
| **Content Author** | ❌ **MISSING** | Create/edit domains, concepts | ⚠️ NOT IMPLEMENTED |
| **Simulation Builder** | ❌ **MISSING** | Create/edit simulation manifests | ⚠️ NOT IMPLEMENTED |
| **Visualization Editor** | ❌ **MISSING** | Configure visualizations | ⚠️ NOT IMPLEMENTED |

### 1.2 Gaps Identified

#### Critical Gaps 🔴

1. **No Content Authoring UI**
   - Current pages are READ-ONLY
   - No interface to create/edit domains
   - No interface to create/edit concepts
   - No interface to create/edit simulations

2. **No Simulation Integration**
   - `simulation_metadata` in JSON is DESCRIPTIVE only
   - No actual simulation manifest linked to concepts
   - Simulation models in DB exist but unused

3. **No Visualization Integration**
   - No `visualization_metadata` field in content
   - No visualization configuration saved/served
   - No visualization components integrated with content

4. **No Example Content**
   - Concepts defined but no worked examples
   - No step-by-step solution examples
   - No practice problems with solutions

5. **No Content Editing Workflow**
   - Admin must edit JSON files directly
   - No validation before DB load
   - No conflict resolution for concurrent edits

---

## Part 2: Proposed Architecture

### 2.1 Content Authoring Workflow

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ADMIN FLOW                                   │
│                                                                     │
│  1. CREATE DOMAIN                                                   │
│     └─ Input: Domain name, level structure (Foundational, etc)     │
│     └─ Output: Empty domain ready for concepts                     │
│                                                                     │
│  2. CREATE CONCEPT (for each)                                       │
│     └─ Input:                                                       │
│        • Name, Description                                          │
│        • Prerequisites (select from existing concepts)              │
│        • Learning Objectives (add 3-5)                             │
│        • Audience Tags, Keywords                                    │
│     └─ Output: Concept with metadata                               │
│                                                                     │
│  3. EMBED SIMULATION                                                │
│     └─ Input:                                                       │
│        • Simulation type (interactive_visualization, exercise, etc) │
│        • Select template or build custom manifest                   │
│        • Configure parameters (duration, entities, steps)           │
│     └─ Output: Simulation linked to concept                        │
│                                                                     │
│  4. EMBED VISUALIZATION                                             │
│     └─ Input:                                                       │
│        • Visualization type (2D graph, 3D model, table, etc)        │
│        • Configuration (axes, colors, labels, interactivity)        │
│     └─ Output: Visualization config stored with concept             │
│                                                                     │
│  5. CREATE EXAMPLES (2-3 per concept)                               │
│     └─ Input (per example):                                         │
│        • Title & Problem Statement                                  │
│        • Solution Content (markdown with embedded simulation)       │
│        • Visualization snapshots at key steps                       │
│        • Practice variants                                          │
│     └─ Output: 2-3 worked examples ready for learning path         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 Data Model Extensions

#### New Database Models

```prisma
model DomainAuthor {
  id                    String        @id
  domain                String        @unique
  title                 String
  description           String
  imageUrl              String?
  author                String        // Admin name
  version               String        @default("1.0.0")
  status                String        @default("draft") // draft | published | archived
  createdAt             DateTime      @default(now())
  updatedAt             DateTime      @updatedAt
  publishedAt           DateTime?
  
  concepts              DomainAuthorConcept[]
  
  @@index([status])
}

model DomainAuthorConcept {
  id                    String        @id
  domainId              String
  domain                DomainAuthor  @relation(fields: [domainId], references: [id])
  
  name                  String
  description           String
  level                 String        // Foundational, Intermediate, etc
  orderIndex            Int           @default(0)
  
  // Content metadata
  learningObjectives    String[]
  competencies          String[]
  keywords              String[]
  prerequisites         String[]      // IDs of prerequisite concepts
  
  // Simulation & Visualization
  simulation            SimulationDefinition?
  visualization         VisualizationDefinition?
  
  // Examples
  examples              ContentExample[]
  
  // Status & versioning
  status                String        @default("draft") // draft | review | published
  version               String        @default("1.0.0")
  
  createdAt             DateTime      @default(now())
  updatedAt             DateTime      @updatedAt
  
  @@index([domainId])
  @@index([status])
}

model SimulationDefinition {
  id                    String        @id
  conceptId             String        @unique
  concept               DomainAuthorConcept @relation(fields: [conceptId], references: [id], onDelete: Cascade)
  
  type                  String        // physics-2D, interactive_visualization, exercise, etc
  
  // Manifest JSON
  manifest              Json          // Full SimulationManifest structure
  
  // Metadata
  estimatedTime         Int           // in minutes
  interactivityLevel    String        // low | medium | high
  purpose               String
  
  // Preview & Preview Config
  previewConfig         Json?         // Initial state for preview
  
  status                String        @default("draft")
  version               String        @default("1.0.0")
  
  createdAt             DateTime      @default(now())
  updatedAt             DateTime      @updatedAt
}

model VisualizationDefinition {
  id                    String        @id
  conceptId             String        @unique
  concept               DomainAuthorConcept @relation(fields: [conceptId], references: [id], onDelete: Cascade)
  
  type                  String        // graph-2d | graph-3d | chart | diagram | molecule | etc
  
  // Visualization Config
  config                Json          // {
                                      //   axes: { x: {label, min, max}, y: {...}},
                                      //   colors: {...},
                                      //   interactivity: {...}
                                      // }
  
  // Data spec
  dataSource            String?       // How to populate visualization (simulation, static, dynamic)
  
  status                String        @default("draft")
  version               String        @default("1.0.0")
  
  createdAt             DateTime      @default(now())
  updatedAt             DateTime      @updatedAt
}

model ContentExample {
  id                    String        @id
  conceptId             String
  concept               DomainAuthorConcept @relation(fields: [conceptId], references: [id], onDelete: Cascade)
  
  title                 String
  description           String
  orderIndex            Int           @default(0)
  
  // Content
  problemStatement      String        // Markdown
  solutionContent       String        // Markdown with embedded simulation
  keyLearningPoints     String[]
  
  // Visual assets
  visualizationSteps    VisualizationSnapshot[]
  
  // Practice variants
  practiceVariants      String        // Markdown with 2-3 practice problems
  
  difficulty            String        // easy | intermediate | hard
  estimatedTime         Int           // in minutes
  
  status                String        @default("draft")
  version               String        @default("1.0.0")
  
  createdAt             DateTime      @default(now())
  updatedAt             DateTime      @updatedAt
  
  @@index([conceptId])
}

model VisualizationSnapshot {
  id                    String        @id
  exampleId             String
  example               ContentExample @relation(fields: [exampleId], references: [id], onDelete: Cascade)
  
  stepNumber            Int
  stepDescription       String
  
  // Visualization state at this step
  config                Json          // Same as VisualizationDefinition.config
  data                  Json          // Current data/values for visualization
  
  createdAt             DateTime      @default(now())
  
  @@index([exampleId])
}
```

#### Updated `DomainConcept` Model

```prisma
model DomainConcept {
  id                    String        @id
  domain                String        @indexed
  name                  String
  description           String
  level                 String
  prerequisites         String[]
  
  // Link to author definition (optional)
  authorConceptId       String?       // FK to DomainAuthorConcept
  
  // Simulation & Visualization (populated from author)
  simulationId          String?       // FK to SimulationDefinition
  simulation            Simulation?   @relation(fields: [simulationId], references: [id])
  visualization         VisualizationDefinition?
  
  // Content
  learningObjectives    String[]
  keywords              String[]
  
  // Examples & Practice
  examples              ContentExample[]
  
  createdAt             DateTime      @default(now())
  updatedAt             DateTime      @updatedAt
}
```

### 2.3 API Endpoints for Content Authoring

#### Domain Management

```typescript
// Create domain
POST   /admin/api/v1/content/domains
Body: {
  domain: string;          // e.g., "Physics"
  title: string;
  description: string;
  author: string;
}
Response: { id: string; domain: string; status: "draft" }

// List domains (with status)
GET    /admin/api/v1/content/domains?status=draft
Response: {
  domains: Array<{
    id: string;
    domain: string;
    title: string;
    conceptCount: number;
    status: "draft" | "review" | "published";
    createdAt: string;
  }>
}

// Get domain details
GET    /admin/api/v1/content/domains/:domainId
Response: {
  id: string;
  domain: string;
  title: string;
  description: string;
  author: string;
  status: "draft" | "review" | "published";
  concepts: Array<DomainAuthorConcept>;
  createdAt: string;
  updatedAt: string;
}

// Update domain
PATCH  /admin/api/v1/content/domains/:domainId
Body: Partial<{ title, description, status }>

// Publish domain (finalize + lock)
POST   /admin/api/v1/content/domains/:domainId/publish
Response: { status: "published"; publishedAt: string }

// Delete domain
DELETE /admin/api/v1/content/domains/:domainId
```

#### Concept Management

```typescript
// Create concept
POST   /admin/api/v1/content/domains/:domainId/concepts
Body: {
  name: string;
  description: string;
  level: string;
  learningObjectives: string[];
  prerequisites: string[];  // Concept IDs
  keywords: string[];
}
Response: { id: string; conceptId: string; status: "draft" }

// Update concept
PATCH  /admin/api/v1/content/domains/:domainId/concepts/:conceptId
Body: Partial<{ name, description, level, ... }>

// Delete concept
DELETE /admin/api/v1/content/domains/:domainId/concepts/:conceptId

// List concepts (with simulation & visualization status)
GET    /admin/api/v1/content/domains/:domainId/concepts
Response: {
  concepts: Array<{
    id: string;
    name: string;
    level: string;
    hasSimulation: boolean;
    hasVisualization: boolean;
    exampleCount: number;
    status: string;
  }>
}
```

#### Simulation Management

```typescript
// Create/update simulation for concept
PUT    /admin/api/v1/content/domains/:domainId/concepts/:conceptId/simulation
Body: {
  type: string;              // physics-2D, interactive_visualization, etc
  manifest: SimulationManifest;
  estimatedTime: number;     // minutes
  interactivityLevel: string; // low | medium | high
  purpose: string;
}
Response: { id: string; type: string; status: "draft" }

// Get simulation preview
GET    /admin/api/v1/content/domains/:domainId/concepts/:conceptId/simulation/preview
Response: { 
  id: string;
  type: string;
  manifest: SimulationManifest;
  previewUrl: string;  // URL to preview simulation
}

// Delete simulation
DELETE /admin/api/v1/content/domains/:domainId/concepts/:conceptId/simulation
```

#### Visualization Management

```typescript
// Create/update visualization for concept
PUT    /admin/api/v1/content/domains/:domainId/concepts/:conceptId/visualization
Body: {
  type: string;         // graph-2d | graph-3d | chart | diagram | etc
  config: VisualizationConfig;
  dataSource?: string;  // How to populate visualization
}
Response: { id: string; type: string; status: "draft" }

// Get visualization preview
GET    /admin/api/v1/content/domains/:domainId/concepts/:conceptId/visualization/preview
Response: {
  id: string;
  type: string;
  config: VisualizationConfig;
  previewUrl: string;
}

// Delete visualization
DELETE /admin/api/v1/content/domains/:domainId/concepts/:conceptId/visualization
```

#### Content Examples

```typescript
// Create example for concept
POST   /admin/api/v1/content/domains/:domainId/concepts/:conceptId/examples
Body: {
  title: string;
  description: string;
  problemStatement: string;  // Markdown
  solutionContent: string;   // Markdown with embedded simulation
  difficulty: string;        // easy | intermediate | hard
  estimatedTime: number;     // minutes
}
Response: { id: string; exampleId: string; status: "draft" }

// Update example
PATCH  /admin/api/v1/content/domains/:domainId/concepts/:conceptId/examples/:exampleId
Body: Partial<{ title, description, problemStatement, ... }>

// Add visualization snapshot to example
POST   /admin/api/v1/content/domains/:domainId/concepts/:conceptId/examples/:exampleId/snapshots
Body: {
  stepNumber: number;
  stepDescription: string;
  config: VisualizationConfig;  // Visualization state
  data: Record<string, any>;    // Current data
}
Response: { id: string; snapshotId: string }

// List examples for concept
GET    /admin/api/v1/content/domains/:domainId/concepts/:conceptId/examples
Response: {
  examples: Array<{
    id: string;
    title: string;
    difficulty: string;
    estimatedTime: number;
    snapshotCount: number;
    status: string;
  }>
}

// Delete example
DELETE /admin/api/v1/content/domains/:domainId/concepts/:conceptId/examples/:exampleId
```

---

## Part 3: Required UI Components

### 3.1 Domain Editor UI

**Location:** `apps/tutorputor-admin/src/pages/DomainEditorPage.tsx` (NEW)

```tsx
export function DomainEditorPage() {
  // State:
  // - domainId (param) or null for create
  // - domain object (domain, title, description, author, status)
  // - concepts list
  // - selectedConceptId (for editing)
  
  // Sections:
  // 1. Domain Header (title, description, author, status badge)
  // 2. Concepts List (searchable, with simulation/visualization indicators)
  // 3. Add Concept Button
  // 4. Actions (Save, Publish, Delete)
  
  // Returns: Domain with full concept tree
}
```

### 3.2 Concept Editor UI

**Location:** `apps/tutorputor-admin/src/components/ConceptEditor.tsx` (NEW)

```tsx
export function ConceptEditor({ 
  domainId: string; 
  conceptId?: string; 
  onSave: (concept) => void;
}) {
  // State:
  // - name, description, level
  // - learningObjectives (array with add/remove)
  // - prerequisites (multiselect from domain concepts)
  // - keywords
  
  // Sections:
  // 1. Basic Info (name, description, level dropdown)
  // 2. Learning Objectives (add/edit/delete)
  // 3. Prerequisites (multiselect with suggestions)
  // 4. Keywords (tag input)
  // 5. Simulation Status (badge + link to SimulationEditor)
  // 6. Visualization Status (badge + link to VisualizationEditor)
  // 7. Examples Count (badge + link to ExamplesGallery)
  // 8. Actions (Save, Delete)
  
  // Returns: ConceptEditor dialog/panel
}
```

### 3.3 Simulation Editor UI

**Location:** `apps/tutorputor-admin/src/components/SimulationEditor.tsx` (NEW)

```tsx
export function SimulationEditor({ 
  conceptId: string; 
  simulationType?: string;
  onSave: (simulation) => void;
}) {
  // State:
  // - type (select from: physics-2D, interactive_visualization, exercise, etc)
  // - manifest (JSON editor)
  // - estimatedTime
  // - interactivityLevel
  // - purpose
  
  // Sections:
  // 1. Type Selector (dropdown with template options)
  // 2. Manifest JSON Editor (with syntax highlighting)
  // 3. Manifest Inspector (tree view of manifest structure)
  // 4. Preview (live preview of simulation)
  // 5. Metadata (estimatedTime, interactivityLevel, purpose textarea)
  // 6. Actions (Save, Delete, Preview Full Screen)
  
  // Template library:
  // - Physics 2D (pre-filled manifest for 2D physics)
  // - Molecular Visualizer (pre-filled for molecules)
  // - Interactive Exercise (pre-filled for practice)
  
  // Returns: SimulationEditor modal
}
```

### 3.4 Visualization Editor UI

**Location:** `apps/tutorputor-admin/src/components/VisualizationEditor.tsx` (NEW)

```tsx
export function VisualizationEditor({ 
  conceptId: string; 
  visualizationType?: string;
  onSave: (visualization) => void;
}) {
  // State:
  // - type (select from: graph-2d, graph-3d, chart, diagram, molecule, etc)
  // - config (JSON object with axes, colors, labels, interactivity)
  // - dataSource (how to populate)
  
  // Sections:
  // 1. Type Selector (dropdown)
  // 2. Config Editor (form for axes, colors, labels based on type)
  // 3. Config Inspector (JSON view)
  // 4. Preview (live rendering of visualization)
  // 5. Data Source (dropdown: simulation | static | dynamic)
  // 6. Actions (Save, Delete, Preview Full Screen)
  
  // Type-specific config forms:
  // - Graph 2D: X-axis config, Y-axis config, series config
  // - Chart: Type (line/bar/pie), data mapping
  // - Diagram: Shape definitions, connections, labels
  // - Molecule: Atom positions, bonds, 3D rotation
  
  // Returns: VisualizationEditor modal
}
```

### 3.5 Examples Gallery

**Location:** `apps/tutorputor-admin/src/components/ExamplesGallery.tsx` (NEW)

```tsx
export function ExamplesGallery({ 
  conceptId: string; 
  examples: ContentExample[];
  onAddExample: () => void;
}) {
  // State:
  // - examples list with title, difficulty, estimatedTime
  // - selectedExampleId (for editing)
  
  // Sections:
  // 1. Examples List (cards with difficulty badge, time, snapshot count)
  // 2. Add Example Button
  // 3. Example Detail View (when selected):
  //    - Title, Description, Problem Statement
  //    - Solution Content (markdown preview)
  //    - Visualization Snapshots (carousel)
  //    - Practice Variants (preview)
  // 4. Example Editor (modal):
  //    - All fields above
  //    - Snapshot editor (table of steps with visual preview)
  
  // Returns: Gallery with examples
}
```

### 3.6 Example Editor UI

**Location:** `apps/tutorputor-admin/src/components/ExampleEditor.tsx` (NEW)

```tsx
export function ExampleEditor({
  conceptId: string;
  exampleId?: string;
  onSave: (example) => void;
}) {
  // State:
  // - title, description
  // - problemStatement (markdown textarea)
  // - solutionContent (markdown textarea with simulation embeds)
  // - difficulty, estimatedTime
  // - visualizationSteps (table)
  // - practiceVariants (markdown)
  
  // Sections:
  // 1. Title & Description
  // 2. Problem Statement (markdown editor)
  // 3. Solution Content (markdown editor with `[[simulation:id]]` syntax)
  // 4. Visualization Steps (table: step#, description, config, preview)
  // 5. Practice Variants (markdown editor)
  // 6. Metadata (difficulty dropdown, estimatedTime input)
  // 7. Actions (Save, Delete, Preview)
  
  // Returns: ExampleEditor modal
}
```

---

## Part 4: Implementation Requirements for 2-3 Examples Per Domain

### 4.1 Physics Domain Examples

#### Example 1: "Free Fall Motion" (Foundational)
**Concept:** Kinematics: Motion in 1D

**Content:**
- **Problem Statement:** "A ball is dropped from a building 45m tall. How long does it take to hit the ground? What is its velocity just before impact? (Ignore air resistance)"
- **Learning Objectives:**
  - Apply kinematic equation: h = ½gt²
  - Solve for time and velocity
  - Understand constant acceleration

**Simulation:**
- Type: `physics-2D`
- Entities: Building (static), Ball (falling), Ground (static)
- Steps:
  1. Drop ball (t=0)
  2. Show position vs time graph in real-time
  3. Show velocity vs time graph in real-time
  4. Highlight collision point

**Visualization:**
- Type: `graph-2d` (dual axis)
- Left: Position (m) vs Time (s) - parabolic curve
- Right: Velocity (m/s) vs Time (s) - linear curve
- Interactive: Slider to scrub through time, see both graphs update

**Solution Steps:**
1. Identify knowns: h=45m, g=9.8m/s², v₀=0
2. Use h = ½gt² → t = √(2h/g) = √(2×45/9.8) ≈ 3.03s
3. Use v = v₀ + gt = 0 + 9.8×3.03 ≈ 29.7 m/s
4. Verify with v² = v₀² + 2ah

**Practice Variants:**
- Variant 1: Height = 100m, same calculation
- Variant 2: Ball thrown DOWN at 5 m/s initial velocity
- Variant 3: What height for 2-second fall?

---

#### Example 2: "Projectile Motion" (Intermediate)
**Concept:** Kinematics: Motion in 2D

**Content:**
- **Problem Statement:** "A soccer ball is kicked at 20 m/s at 45° angle. How far does it travel? How long is it in the air? What is the maximum height?"
- **Learning Objectives:**
  - Decompose 2D motion into x and y components
  - Solve projectile motion problems
  - Find range, time of flight, max height

**Simulation:**
- Type: `physics-2D`
- Entities: Ball (moving projectile), Goal (target), Grid (coordinate system)
- Steps:
  1. Launch ball at 45°
  2. Show trajectory with parabolic path
  3. Highlight x-position and y-position separately
  4. Show velocity vectors at key points
  5. Mark landing point

**Visualization:**
- Type: `graph-2d` with interactive 2D trajectory view
- Main: 2D trajectory (x vs y position)
- Insets: x(t), y(t), vx(t), vy(t) graphs
- Interactive: Click on trajectory to see values at that point

**Solution Steps:**
1. Decompose: vₓ = 20cos(45°) = 14.14 m/s, vᵧ = 20sin(45°) = 14.14 m/s
2. Time of flight: t = 2vᵧ/g = 2×14.14/9.8 ≈ 2.89s
3. Range: R = vₓ × t = 14.14 × 2.89 ≈ 40.8m
4. Max height: h = vᵧ²/(2g) = 14.14²/(2×9.8) ≈ 10.2m

**Practice Variants:**
- Variant 1: Launch angle = 30° (different range)
- Variant 2: Launch angle = 60° (same range, different trajectory)
- Variant 3: Hit target at 30m distance at height 5m (solve backwards)

---

#### Example 3: "Simple Harmonic Motion" (Advanced)
**Concept:** Oscillations: Spring-Mass Systems

**Content:**
- **Problem Statement:** "A 0.5 kg mass is attached to a spring with k=100 N/m. It's displaced 0.1m and released. Find amplitude, frequency, period, and maximum velocity."
- **Learning Objectives:**
  - Identify parameters of SHM (A, ω, T, f)
  - Calculate energy (KE, PE, total)
  - Understand phase relationships

**Simulation:**
- Type: `physics-2D`
- Entities: Spring, Mass (oscillating), Equilibrium point, Damping (optional)
- Steps:
  1. Release mass from initial displacement
  2. Show oscillation with velocity/acceleration vectors
  3. Show energy bar chart (KE vs PE)
  4. Complete multiple cycles

**Visualization:**
- Type: `graph-2d` (multi-panel)
- Panel 1: Position vs Time (sine wave)
- Panel 2: Velocity vs Time (cosine wave, 90° offset)
- Panel 3: Energy vs Time (KE and PE trade off)
- Interactive: Slider to vary spring constant k and mass m in real-time

**Solution Steps:**
1. Angular frequency: ω = √(k/m) = √(100/0.5) = √200 ≈ 14.14 rad/s
2. Frequency: f = ω/(2π) ≈ 2.25 Hz
3. Period: T = 1/f ≈ 0.444s
4. Amplitude: A = 0.1m (given as initial displacement)
5. Max velocity: vₘₐₓ = ωA = 14.14 × 0.1 ≈ 1.414 m/s
6. Total energy: E = ½kA² = ½ × 100 × 0.01 = 0.5 J

**Practice Variants:**
- Variant 1: Double the mass (observe period increase)
- Variant 2: Double the spring constant (observe frequency increase)
- Variant 3: Given period and amplitude, solve for k and m

---

### 4.2 Chemistry Domain Examples

#### Example 1: "Balancing Chemical Equations" (Foundational)
**Concept:** Stoichiometry, Moles & Chemical Quantities

**Content:**
- **Problem Statement:** "Balance the combustion of methane: CH₄ + O₂ → CO₂ + H₂O. Then calculate: How many moles of O₂ needed to burn 5 moles of CH₄?"
- **Learning Objectives:**
  - Balance chemical equations by counting atoms
  - Use stoichiometric ratios
  - Solve mole-to-mole conversions

**Simulation:**
- Type: `interactive_visualization`
- Entities: CH₄ molecule (4H atoms + 1C), O₂ molecules, CO₂, H₂O
- Steps:
  1. Show unbalanced equation with atoms highlighted
  2. Allow user to add coefficients
  3. Validate atom counts on each side
  4. Show correctly balanced equation
  5. Highlight stoichiometric ratio (1:2:1:2)

**Visualization:**
- Type: `diagram` (molecular diagram)
- Left side: Reactants (1 CH₄, 2 O₂) with atom counts
- Arrow: Balanced equation coefficients
- Right side: Products (1 CO₂, 2 H₂O) with atom counts
- Interactive: Highlight atoms by type (C, H, O) to verify balance

**Solution Steps:**
1. Count atoms (unbalanced):
   - Reactants: C=1, H=4, O=2
   - Products: C=1, H=2, O=3 ❌
2. Balance H: Need 2 H₂O → need 4 O atoms total
3. Add O₂ coefficient: 2 O₂ gives 4 O atoms ✅
4. Final equation: CH₄ + 2O₂ → CO₂ + 2H₂O
5. Stoichiometric ratio: CH₄ : O₂ = 1 : 2
6. For 5 moles CH₄: O₂ needed = 5 × 2 = 10 moles

**Practice Variants:**
- Variant 1: Balance C₂H₆ + O₂ → CO₂ + H₂O (ethane combustion)
- Variant 2: Balance Fe + O₂ → Fe₂O₃ (iron oxidation)
- Variant 3: Given 10 moles O₂, how much CO₂ produced from 5 moles CH₄?

---

#### Example 2: "Titration: Acid-Base Neutralization" (Intermediate)
**Concept:** Acid-Base Chemistry: Reactions & Neutralization

**Content:**
- **Problem Statement:** "An unknown concentration HCl solution requires 25 mL of 0.1 M NaOH to reach equivalence point. What is the HCl concentration? (Volume of HCl = 50 mL)"
- **Learning Objectives:**
  - Understand acid-base neutralization
  - Use molarity and volume relationships
  - Calculate from titration data

**Simulation:**
- Type: `interactive_visualization`
- Entities: Beaker with acid, burette with base, indicator color change
- Steps:
  1. Start with HCl solution in beaker (yellow)
  2. Add NaOH dropwise from burette
  3. Show color gradient as pH changes
  4. Highlight equivalence point (color change)
  5. Calculate concentration at equivalence point

**Visualization:**
- Type: `graph-2d` (titration curve)
- X-axis: Volume of NaOH added (mL)
- Y-axis: pH
- S-shaped curve showing pH change
- Interactive: Slider to add NaOH volume, see curve update and color change in beaker

**Solution Steps:**
1. At equivalence point: n(H⁺) = n(OH⁻)
2. HCl + NaOH → NaCl + H₂O (1:1 ratio)
3. moles NaOH at equivalence = 0.1 M × 0.025 L = 0.0025 mol
4. moles HCl = 0.0025 mol (1:1 ratio)
5. Concentration of HCl = 0.0025 mol / 0.050 L = 0.05 M

**Practice Variants:**
- Variant 1: Different NaOH concentration (0.2 M) and volume (12.5 mL)
- Variant 2: Weak acid (acetic acid) titration (different curve shape)
- Variant 3: Back-titration problem (excess acid, titrate back with NaOH)

---

#### Example 3: "Equilibrium Constant & Le Chatelier's Principle" (Advanced)
**Concept:** Chemical Equilibrium: Concepts & Calculations

**Content:**
- **Problem Statement:** "For the reaction: N₂ + 3H₂ ⇌ 2NH₃, Kc=0.5 at 400K. If we start with [N₂]=1M, [H₂]=3M, [NH₃]=0, find equilibrium concentrations."
- **Learning Objectives:**
  - Set up ICE tables
  - Calculate equilibrium constant from data
  - Apply Le Chatelier's principle

**Simulation:**
- Type: `interactive_visualization`
- Entities: N₂, H₂, NH₃ molecules
- Steps:
  1. Start with 1 N₂ and 3 H₂
  2. Show forward/reverse reaction rates
  3. Show molecule counts changing over time
  4. Reach equilibrium when rates equal
  5. Show final concentrations

**Visualization:**
- Type: `graph-2d` (reaction progress)
- Left: Concentration vs Time (3 lines: N₂, H₂, NH₃)
- Right: Forward/Reverse rate vs Reaction Progress
- Interactive: Buttons to "add reactant", "add product", "increase temp" → observe Le Chatelier

**Solution Steps:**
1. Set up ICE table:
   ```
   [N₂]    [H₂]    [NH₃]
   I: 1     3       0
   C: -x    -3x    +2x
   E: 1-x   3-3x    2x
   ```
2. Apply Kc expression: Kc = [NH₃]²/([N₂][H₂]³) = 0.5
3. Substitute: 0.5 = (2x)² / ((1-x)(3-3x)³)
4. Solve (using approximation or numerical method): x ≈ 0.143
5. Equilibrium: [N₂]=0.857M, [H₂]=2.571M, [NH₃]=0.286M

**Practice Variants:**
- Variant 1: Different initial concentrations
- Variant 2: Different temperature (different Kc)
- Variant 3: What happens if we double pressure (Le Chatelier shift)?

---

### 4.3 Biology Domain Examples

#### Example 1: "Photosynthesis & Energy Conversion" (Foundational)
**Concept:** Photosynthesis & Cellular Respiration

**Content:**
- **Problem Statement:** "A plant in sunlight consumes 1 mole of CO₂ and produces glucose. Calculate the energy stored and O₂ released. How much glucose must be burned to release that energy?"
- **Learning Objectives:**
  - Understand photosynthetic equation
  - Calculate energy storage
  - Compare with cellular respiration

**Simulation:**
- Type: `interactive_visualization`
- Entities: Chloroplast, CO₂, H₂O, glucose, O₂, light photons
- Steps:
  1. Show light energy entering chloroplast
  2. Show CO₂ and H₂O being consumed
  3. Show glucose and O₂ being produced
  4. Animate electron transport and ATP production

**Visualization:**
- Type: `diagram` (energy flow)
- Top: Sun energy input
- Center: Photosynthesis equation and molar ratios
- Bottom: Products with energy content highlighted
- Right inset: Glucose combustion equation and energy released

**Solution Steps:**
1. Photosynthesis: 6CO₂ + 6H₂O + light → C₆H₁₂O₆ + 6O₂
2. Energy stored in 1 mole glucose ≈ 2,800 kJ
3. From 1 mole CO₂: proportional energy = 2,800/6 ≈ 467 kJ
4. O₂ produced: 1 mole CO₂ → 1 mole O₂ (from stoichiometry 6:6)
5. Cellular respiration reverses this: C₆H₁₂O₆ + 6O₂ → 6CO₂ + 6H₂O + 2,800 kJ
6. Glucose consumed to release 467 kJ = 467/2,800 ≈ 0.167 moles

**Practice Variants:**
- Variant 1: 2 moles CO₂ input (double all outputs)
- Variant 2: Different plant with different efficiency
- Variant 3: Calculate net energy transfer efficiency (energy in light → energy in glucose)

---

#### Example 2: "Genetics: Punnett Squares & Inheritance" (Intermediate)
**Concept:** Basic Genetics: DNA, Genes, and Heredity

**Content:**
- **Problem Statement:** "A pea plant has a hybrid genotype Aa (A=dominant tall, a=recessive short). When two hybrid plants are crossed (Aa × Aa), what are the possible genotypes and phenotypes in F1? What are the ratios?"
- **Learning Objectives:**
  - Use Punnett square to predict offspring
  - Understand dominant/recessive inheritance
  - Calculate phenotypic and genotypic ratios

**Simulation:**
- Type: `interactive_visualization`
- Entities: Parent 1 (Aa), Parent 2 (Aa), Punnett square, offspring plants
- Steps:
  1. Show parent genotypes
  2. Animate gamete formation (A and a)
  3. Fill Punnett square with all combinations
  4. Show F1 genotypes (AA, Aa, Aa, aa)
  5. Color by phenotype (3 tall : 1 short)

**Visualization:**
- Type: `diagram` (Punnett square + phenotypes)
- Left: Punnett square grid
- Center: Genotype counts (1 AA, 2 Aa, 1 aa)
- Right: Plant illustrations showing tall/short phenotypes
- Interactive: Change parent genotypes to see different ratios

**Solution Steps:**
1. Parent gametes: Aa can produce A or a (50% each)
2. Punnett square:
   ```
       A    a
   A  AA   Aa
   a  Aa   aa
   ```
3. Genotypic ratio: 1 AA : 2 Aa : 1 aa (1:2:1)
4. Phenotypic ratio: 3 tall (AA, Aa) : 1 short (aa) (3:1)
5. Probability of tall plant = 75%, short = 25%

**Practice Variants:**
- Variant 1: AA × aa cross (all heterozygous F1)
- Variant 2: Aa × aa (test cross - 1:1 ratio)
- Variant 3: Two traits: AaBb × AaBb (9:3:3:1 ratio in F1)

---

#### Example 3: "DNA Replication & Transcription" (Advanced)
**Concept:** Molecular Biology: Replication, Transcription, Translation

**Content:**
- **Problem Statement:** "A DNA strand is 5'-ATGCGATAG-3'. Show replication to produce two identical daughter DNA molecules. Then transcribe the coding strand to mRNA and translate to amino acids."
- **Learning Objectives:**
  - Explain semi-conservative DNA replication
  - Understand transcription (DNA → mRNA)
  - Understand translation (mRNA → protein)

**Simulation:**
- Type: `physics-2D` or `interactive_visualization`
- Entities: DNA double helix, replication fork, RNA polymerase, ribosome, bases, codons
- Steps:
  1. Show original DNA double helix
  2. Animate unwinding at replication fork
  3. Show DNA polymerase adding complementary bases
  4. Show two identical daughter DNA strands
  5. Switch to transcription: RNA polymerase, mRNA synthesis
  6. Show mRNA with codons
  7. Show ribosome translating to amino acids

**Visualization:**
- Type: `diagram` (3-step visualization)
- Step 1: DNA replication (parent DNA → two daughter DNA)
- Step 2: Transcription (DNA strand → mRNA)
- Step 3: Translation (mRNA codons → amino acid sequence)
- Interactive: Highlight complementary bases, codon-anticodon pairing

**Solution Steps:**
1. **Replication:**
   - Original: 5'-ATGCGATAG-3' / 3'-TACGCTATC-5'
   - Daughter 1: 5'-ATGCGATAG-3' (original top strand)
   - Daughter 2: 3'-TACGCTATC-5' (new bottom strand via complementary)
   - Result: Two identical DNA molecules

2. **Transcription:**
   - Coding strand (template): 3'-TACGCTATC-5'
   - mRNA: 5'-AUGCGAUAG-3' (A↔U, T→A, G↔C, C↔G)

3. **Translation:**
   - mRNA codons: AUG | CGA | UAG
   - Codon meanings: AUG=Methionine (Start), CGA=Arginine, UAG=STOP
   - Amino acid sequence: Met-Arg (stop)

**Practice Variants:**
- Variant 1: Different DNA sequence (different amino acids)
- Variant 2: Point mutation in original DNA (show how it affects final protein)
- Variant 3: Calculate total time for all processes (replication, transcription, translation)

---

## Part 5: Implementation Phases

### Phase 1: Database & API (Week 1)
- [ ] Create all new models in Prisma schema
- [ ] Generate Prisma client
- [ ] Implement API endpoints (domain, concept, simulation, visualization, examples CRUD)
- [ ] Add endpoint validation and error handling
- [ ] Write API tests

### Phase 2: UI Components (Week 2)
- [ ] DomainEditorPage
- [ ] ConceptEditor component
- [ ] SimulationEditor component
- [ ] VisualizationEditor component
- [ ] ExamplesGallery component
- [ ] ExampleEditor component

### Phase 3: Integration & Examples (Week 3)
- [ ] Wire UI to API endpoints
- [ ] Implement simulation previews
- [ ] Implement visualization previews
- [ ] Create Physics examples (3)
- [ ] Create Chemistry examples (3)
- [ ] Create Biology examples (3)

### Phase 4: Testing & Refinement (Week 4)
- [ ] Integration testing
- [ ] User acceptance testing
- [ ] Documentation
- [ ] Deployment

---

## Conclusion

This flow enables TutorPutor admins to comprehensively author educational content with integrated simulations and visualizations. By implementing examples across Physics, Chemistry, and Biology, we demonstrate the system's capability to handle diverse domains with high-quality, interactive learning experiences.

