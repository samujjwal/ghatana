# TutorPutor Admin - Content Authoring Quick Reference

**Created:** December 13, 2025

---

## 📋 Quick Links

- **Full Specification:** `ADMIN_CONTENT_AUTHORING_FLOW.md` (80+ pages)
- **Current Status:** `ADMIN_COMPLETE_IMPLEMENTATION.md`
- **Domain Content:** `/content/domains/*.json`
- **Admin App:** `apps/tutorputor-admin/src/pages/*.tsx`

---

## 🎯 Core Concept

Enable admins to author educational content with **inherent simulations and visualizations**:

```
Domain → Concepts → Simulation Manifest + Visualization Config → 2-3 Examples
```

---

## 🗂️ New Database Models

```
DomainAuthor
  ├─ name: "Physics"
  ├─ concepts: DomainAuthorConcept[]
  │  └─ DomainAuthorConcept
  │     ├─ simulation: SimulationDefinition (JSON manifest)
  │     ├─ visualization: VisualizationDefinition (config)
  │     └─ examples: ContentExample[]
  │        └─ ContentExample
  │           └─ snapshots: VisualizationSnapshot[] (step-by-step visualization state)
```

---

## 🔌 API Endpoints Summary

### Domains
```
POST   /admin/api/v1/content/domains
GET    /admin/api/v1/content/domains?status=draft
GET    /admin/api/v1/content/domains/:domainId
PATCH  /admin/api/v1/content/domains/:domainId
POST   /admin/api/v1/content/domains/:domainId/publish
DELETE /admin/api/v1/content/domains/:domainId
```

### Concepts
```
POST   /admin/api/v1/content/domains/:domainId/concepts
PATCH  /admin/api/v1/content/domains/:domainId/concepts/:conceptId
DELETE /admin/api/v1/content/domains/:domainId/concepts/:conceptId
GET    /admin/api/v1/content/domains/:domainId/concepts
```

### Simulations
```
PUT    /admin/api/v1/content/domains/:domainId/concepts/:conceptId/simulation
GET    /admin/api/v1/content/domains/:domainId/concepts/:conceptId/simulation/preview
DELETE /admin/api/v1/content/domains/:domainId/concepts/:conceptId/simulation
```

### Visualizations
```
PUT    /admin/api/v1/content/domains/:domainId/concepts/:conceptId/visualization
GET    /admin/api/v1/content/domains/:domainId/concepts/:conceptId/visualization/preview
DELETE /admin/api/v1/content/domains/:domainId/concepts/:conceptId/visualization
```

### Examples
```
POST   /admin/api/v1/content/domains/:domainId/concepts/:conceptId/examples
PATCH  /admin/api/v1/content/domains/:domainId/concepts/:conceptId/examples/:exampleId
DELETE /admin/api/v1/content/domains/:domainId/concepts/:conceptId/examples/:exampleId
GET    /admin/api/v1/content/domains/:domainId/concepts/:conceptId/examples
POST   /admin/api/v1/content/domains/:domainId/concepts/:conceptId/examples/:exampleId/snapshots
```

---

## 🎨 UI Components (New)

| Component | Location | Purpose |
|-----------|----------|---------|
| **DomainEditorPage** | `pages/DomainEditorPage.tsx` | Create/edit domains with concepts list |
| **ConceptEditor** | `components/ConceptEditor.tsx` | Edit concept name, description, objectives, prerequisites |
| **SimulationEditor** | `components/SimulationEditor.tsx` | Create/edit simulation manifest with preview |
| **VisualizationEditor** | `components/VisualizationEditor.tsx` | Create/edit visualization config with preview |
| **ExamplesGallery** | `components/ExamplesGallery.tsx` | List and manage examples |
| **ExampleEditor** | `components/ExampleEditor.tsx` | Edit problem, solution, snapshots, variants |

---

## 📚 Example Structure (Per Domain)

### Physics

| # | Concept | Level | Simulation Type | Visualization | Examples Count |
|---|---------|-------|-----------------|---|--|
| 1 | Kinematics: Motion in 1D | Foundational | `physics-2D` | position/velocity graphs | 1 (Free Fall) |
| 2 | Kinematics: Motion in 2D | Intermediate | `physics-2D` | 2D trajectory + x(t), y(t) graphs | 1 (Projectile Motion) |
| 3 | Oscillations: Spring-Mass | Advanced | `physics-2D` | position, velocity, energy vs time | 1 (Simple Harmonic Motion) |

### Chemistry

| # | Concept | Level | Simulation Type | Visualization | Examples Count |
|---|---------|-------|-----------------|---|--|
| 1 | Stoichiometry & Moles | Foundational | `interactive_visualization` | molecular diagram with atom counts | 1 (Balancing Equations) |
| 2 | Acid-Base Chemistry | Intermediate | `interactive_visualization` | titration curve + color change beaker | 1 (Titration) |
| 3 | Chemical Equilibrium | Advanced | `interactive_visualization` | concentration vs time + forward/reverse rates | 1 (Equilibrium Constant) |

### Biology

| # | Concept | Level | Simulation Type | Visualization | Examples Count |
|---|---------|-------|-----------------|---|--|
| 1 | Photosynthesis & Respiration | Foundational | `interactive_visualization` | energy flow diagram | 1 (Energy Conversion) |
| 2 | Basic Genetics | Intermediate | `interactive_visualization` | Punnett square + phenotype illustrations | 1 (Inheritance) |
| 3 | Molecular Biology | Advanced | `physics-2D` or `interactive_visualization` | DNA replication, transcription, translation | 1 (DNA Replication) |

---

## 💾 Example Content Structure

```json
{
  "title": "Free Fall Motion",
  "description": "A ball dropped from 45m building",
  "problemStatement": "A ball is dropped from 45m. Time to hit ground? Final velocity?",
  
  "simulation": {
    "type": "physics-2D",
    "manifest": { ... },  // Full SimulationManifest
    "estimatedTime": 15,
    "purpose": "Visualize falling ball with position/velocity graphs"
  },
  
  "visualization": {
    "type": "graph-2d",
    "config": {
      "panels": [
        { "title": "Position vs Time", "xAxis": "t(s)", "yAxis": "h(m)" },
        { "title": "Velocity vs Time", "xAxis": "t(s)", "yAxis": "v(m/s)" }
      ]
    }
  },
  
  "solutionSteps": [
    "1. Identify knowns: h=45m, g=9.8m/s², v₀=0",
    "2. Use h = ½gt² → t = √(2h/g) ≈ 3.03s",
    "3. Use v = v₀ + gt ≈ 29.7 m/s",
    "4. Verify with v² = v₀² + 2ah"
  ],
  
  "visualizationSnapshots": [
    {
      "step": 1,
      "description": "Ball released at t=0",
      "visualization": { ... }
    },
    {
      "step": 2,
      "description": "Ball at t=1.5s (midway)",
      "visualization": { ... }
    },
    {
      "step": 3,
      "description": "Ball hits ground at t≈3.03s",
      "visualization": { ... }
    }
  ],
  
  "practiceVariants": [
    "Variant 1: Height = 100m",
    "Variant 2: Ball thrown DOWN at 5 m/s",
    "Variant 3: What height for 2-second fall?"
  ]
}
```

---

## 🚀 Implementation Roadmap

### Phase 1: Database & API (Week 1)
- [ ] Add new Prisma models
- [ ] Implement 18 API endpoints
- [ ] Add validation & error handling
- [ ] Write API tests

### Phase 2: UI Components (Week 2)
- [ ] Implement 6 React components
- [ ] Wire to API endpoints
- [ ] Add live previews (simulation, visualization)
- [ ] Test form validation

### Phase 3: Examples (Week 3)
- [ ] Physics: 3 examples (Free Fall, Projectile, SHM)
- [ ] Chemistry: 3 examples (Equations, Titration, Equilibrium)
- [ ] Biology: 3 examples (Photosynthesis, Genetics, DNA)
- [ ] Create simulation manifests & visualizations

### Phase 4: Polish & Deploy (Week 4)
- [ ] Integration testing
- [ ] Documentation
- [ ] Demo & feedback
- [ ] Production deployment

---

## 📖 File Locations

### Documentation
```
products/tutorputor/docs/
├─ ADMIN_CONTENT_AUTHORING_FLOW.md (THIS - Full Spec)
├─ ADMIN_COMPLETE_IMPLEMENTATION.md (Current Status)
└─ DOMAIN_CONTENT_INTEGRATION_PLAN.md (Phase 3 details)
```

### Content Files
```
products/tutorputor/content/domains/
├─ physics.json (existing - foundational content)
├─ chemistry.json (existing)
├─ biology.json (existing)
└─ [others]
```

### Admin App
```
products/tutorputor/apps/tutorputor-admin/src/
├─ pages/
│  ├─ ContentPage.tsx (existing - read-only)
│  ├─ DomainContentDetailPage.tsx (existing - read-only)
│  ├─ DomainEditorPage.tsx (NEW)
│  └─ [others]
└─ components/
   ├─ ConceptEditor.tsx (NEW)
   ├─ SimulationEditor.tsx (NEW)
   ├─ VisualizationEditor.tsx (NEW)
   ├─ ExamplesGallery.tsx (NEW)
   └─ ExampleEditor.tsx (NEW)
```

### Database
```
products/tutorputor/services/tutorputor-db/prisma/
├─ schema.prisma (update with new models)
└─ [migrations]
```

### API Gateway
```
products/tutorputor/apps/api-gateway/src/routes/
├─ admin.ts (add new endpoints)
└─ [existing routes]
```

---

## 🎓 Learning Outcomes per Example

### Physics

**Free Fall:**
- Define position, velocity, acceleration
- Apply kinematic equation h = ½gt²
- Understand constant acceleration
- Read and interpret graphs

**Projectile Motion:**
- Decompose 2D motion into components
- Solve range, time of flight, max height
- Understand parabolic trajectories
- Apply superposition of motion

**Simple Harmonic Motion:**
- Identify SHM parameters (A, ω, T, f)
- Calculate energy (KE, PE, total)
- Understand phase relationships
- Connect to real-world springs

### Chemistry

**Balancing Equations:**
- Count atoms systematically
- Balance by inspection
- Use stoichiometric ratios
- Solve mole-to-mole conversions

**Titration:**
- Understand acid-base neutralization
- Calculate molarity & volume relationships
- Interpret titration curves
- Determine equivalence point

**Equilibrium:**
- Set up ICE tables
- Calculate equilibrium constant
- Apply Le Chatelier's principle
- Predict system shifts

### Biology

**Photosynthesis:**
- Understand energy conversion
- Learn reactants/products
- Compare with cellular respiration
- Calculate energy efficiency

**Genetics:**
- Use Punnett squares
- Predict offspring genotypes/phenotypes
- Calculate probability ratios
- Understand inheritance patterns

**DNA Replication:**
- Explain semi-conservative replication
- Understand transcription process
- Understand translation process
- Connect molecular to phenotypic changes

---

## 🔧 Technical Stack

- **Backend:** Fastify (Node.js) with Prisma ORM
- **Database:** PostgreSQL with libsql adapter
- **Frontend:** React 19 with React Router 7, TanStack Query
- **Styling:** Tailwind CSS
- **Simulations:** tutorputor-sim-runtime (kernel-based)
- **UI Components:** @ghatana/ui + custom components

---

## ✅ Success Criteria

1. ✅ Database models support full content authoring workflow
2. ✅ API endpoints enable CRUD for all content types
3. ✅ UI components provide intuitive editing experience
4. ✅ Live previews show simulations and visualizations
5. ✅ 9 complete examples demonstrate system capability
6. ✅ Examples cover 3 domains × 3 difficulty levels
7. ✅ Each example includes problem, solution, visualization, practice
8. ✅ System supports collaboration (multi-admin editing)

---

## 📞 Next Steps

1. Review full specification in `ADMIN_CONTENT_AUTHORING_FLOW.md`
2. Create Prisma models and migrations
3. Implement API endpoints with validation
4. Build UI components with live previews
5. Create 9 examples with complete content
6. Integration testing and refinement

---

**Author:** AI Agent  
**Version:** 1.0.0  
**Status:** 📋 Specification Complete, Ready for Implementation

