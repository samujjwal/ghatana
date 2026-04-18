# Personalization Strategy Documentation

**Last Updated:** 2026-04-17  
**Version:** 1.0

---

## Overview

TutorPutor implements a comprehensive AI-powered personalization system that adapts learning experiences to individual learners through multiple interconnected components:

1. **Learning Style Detection** - Identifies learner preferences (visual, auditory, kinesthetic, reading)
2. **Content Recommendation Engine** - Ranks and suggests relevant learning assets
3. **Adaptive Difficulty Adjustment** - Real-time content adaptation based on struggle patterns
4. **Personalized Learning Pathways** - AI-generated learning sequences
5. **A/B Testing Integration** - Continuous optimization of personalization strategies

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Personalization Layer                    │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐  ┌──────────────────┐                │
│  │  Learning Style  │  │  Recommendation  │                │
│  │    Detection     │  │     Engine       │                │
│  └────────┬─────────┘  └────────┬─────────┘                │
│           │                     │                             │
│           └──────────┬──────────┘                             │
│                      ▼                                        │
│           ┌──────────────────┐                               │
│           │  Adaptive        │                               │
│           │  Difficulty      │                               │
│           │  Adjustment      │                               │
│           └────────┬─────────┘                               │
│                    │                                        │
│                    ▼                                        │
│           ┌──────────────────┐                               │
│           │  Learning        │                               │
│           │  Pathways        │                               │
│           │  Service         │                               │
│           └────────┬─────────┘                               │
│                    │                                        │
│                    ▼                                        │
│           ┌──────────────────┐                               │
│           │  A/B Testing     │                               │
│           │  Integration     │                               │
│           └──────────────────┘                               │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 1. Learning Style Detection

### Purpose
Identify learner preferences to deliver content in the most effective modality.

### Implementation
- **Location:** `services/tutorputor-platform/src/modules/adaptation/session-engine.ts`
- **Modalities:** VISUAL, AUDITORY, KINESTHETIC, READING, MIXED
- **Storage:** Learner profile snapshots

### Detection Method
Learning style is inferred from:
- Content interaction patterns
- Modality selection in adapted content
- Time spent on different content types
- Feedback on modality preferences

### Usage
```typescript
const snapshot = await learnerProfileService.getPersonalizationSnapshot(tenantId, userId);
const preferredModality = snapshot.preferredModality; // "VISUAL" | "AUDITORY" | etc.
```

---

## 2. Content Recommendation Engine

### Purpose
Rank and suggest relevant learning assets based on learner context and goals.

### Implementation
- **Location:** `services/tutorputor-platform/src/modules/content/recommendation/recommendation-engine.ts`
- **Factors:** Edge weight, confidence, quality score, difficulty fit, pathway boost, goal matching

### Scoring Algorithm
```typescript
score = edgeWeight * 0.35
       + confidence * 0.15
       + qualityScore * 0.20
       + difficultyFit * 0.20
       + pathwayBoost
       + goalBoost
```

### Difficulty Fitting
- **Beginner learners:** Prefer elementary/beginner content
- **Intermediate learners:** Prefer intermediate content
- **Advanced learners:** Prefer advanced/expert content

### Usage
```typescript
const engine = new RecommendationEngine(prisma);
const recommendations = await engine.getRecommendations(
  { assetId, tenantId, limit: 10 },
  { userProgress, completedAssets, currentPathway, learningGoals }
);
```

---

## 3. Adaptive Difficulty Adjustment

### Purpose
Real-time content adaptation based on learner struggle patterns.

### Implementation
- **Location:** `services/tutorputor-platform/src/modules/adaptation/session-engine.ts`
- **Struggle Patterns:** REPEATED_ERRORS, DISENGAGEMENT, EXCESSIVE_HINTS, RAPID_GUESSING

### Detection Thresholds
- **REPEATED_ERRORS:** 3+ consecutive incorrect answers
- **DISENGAGEMENT:** 3+ minutes of inactivity
- **EXCESSIVE_HINTS:** 40%+ hint rate over 3+ events
- **RAPID_GUESSING:** 3+ rapid incorrect answers (<4s each)

### Adaptation Responses
| Trigger | Response |
|---------|----------|
| REPEATED_ERRORS | Lower difficulty, provide worked examples |
| EXCESSIVE_HINTS | Switch to scaffolded guidance |
| DISENGAGEMENT | Switch to preferred modality |
| RAPID_GUESSING | Require explanation before next attempt |

### Usage
```typescript
const engine = new SessionAdaptationEngine(learnerProfileService, variationService, redis);
const decision = await engine.processEvent({
  tenantId, userId, sessionId, assetId,
  eventType: 'ANSWER_SUBMITTED',
  correct: false,
  responseLatencyMs: 2500
});
```

---

## 4. Personalized Learning Pathways

### Purpose
Generate AI-driven learning sequences tailored to learner goals and level.

### Implementation
- **Location:** `services/tutorputor-platform/src/modules/learning/pathways-service.ts`
- **Learner Levels:** beginner, intermediate, advanced

### Learner Level Computation
Based on:
- Number of completed modules
- Average assessment score
- Proportion of advanced-difficulty modules completed

**Thresholds:**
- **Advanced:** 8+ modules, 75%+ avg score, 30%+ advanced ratio
- **Intermediate:** 3+ modules, 55%+ avg score
- **Beginner:** Default

### Pathway Generation
1. Compute learner level
2. Call AI client with goal and level
3. Map AI nodes to database modules via search
4. Create learning path with ordered nodes
5. Fallback to heuristic scoring if AI fails

### Usage
```typescript
const pathwaysService = createPathwaysService(prisma);
const pathway = await pathwaysService.generatePathway({
  tenantId, userId, goal: 'Learn Python',
  constraints: { maxModules: 10, maxDurationMinutes: 300 }
});
```

---

## 5. A/B Testing Integration

### Purpose
Continuously optimize personalization strategies through experimentation.

### Implementation
- **Location:** `services/tutorputor-platform/src/modules/personalization/PersonalizationABTestingService.ts`
- **Strategies:** adaptive_difficulty, learning_style_based, pathway_optimized, collaborative_filtering, hybrid

### Experiment Types
- Algorithm variant comparison
- Difficulty strategy testing
- Modality preference validation
- Pathway optimization

### Metrics Tracked
- Engagement score (60% weight)
- Completion rate (40% weight)
- Mastery score
- Feedback score
- Time spent

### Statistical Analysis
- p-value calculation
- Effect size measurement
- Confidence intervals
- Statistical power estimation
- Auto-promotion of winners

### Usage
```typescript
const service = new PersonalizationABTestingService(abTestingService);

// Create experiment
const experiment = await service.createPersonalizationExperiment({
  tenantId, strategy: 'adaptive_difficulty',
  controlStrategy: 'adaptive_difficulty',
  treatmentStrategy: 'learning_style_based'
});

// Assign variant
const { variant, strategy } = await service.assignPersonalizationVariant(
  tenantId, experiment.id, userId
);

// Record outcome
await service.recordPersonalizationOutcome(tenantId, experiment.id, userId, {
  engagementScore: 0.85,
  completionRate: 0.92,
  masteryScore: 88,
  timeSpentMinutes: 45
});

// Evaluate and promote
const results = await service.evaluateActivePersonalizationExperiments(tenantId, {
  minSampleSize: 20,
  autoPromote: true,
  maxPValue: 0.05,
  minRelativeImprovement: 0.05
});
```

---

## Java Integration

### Outcome-Aware Recommendation Service
- **Location:** `services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/recommendation/OutcomeAwareRecommendationService.java`
- **Purpose:** Heavy recomputation of recommendation edges based on outcome signals
- **Complementary to:** TypeScript lightweight scoring

### Architecture Pattern
- **TypeScript:** Real-time adaptation, lightweight scoring, session-level decisions
- **Java:** Batch processing, heavy recomputation, edge reweighting

---

## Performance Metrics

### Key Indicators
- Recommendation click-through rate (CTR)
- Completion rate by difficulty level
- Time to mastery
- Engagement score distribution
- Adaptation trigger frequency

### Monitoring
- Prometheus metrics for all personalization services
- Redis state management for session adaptation
- Database persistence for pathway and experiment data

---

## Best Practices

1. **Always provide fallbacks** - AI services may fail; have heuristic fallbacks
2. **Respect learner agency** - Allow users to override personalization
3. **Monitor for bias** - Regularly audit recommendations for fairness
4. **A/B test changes** - Never deploy personalization changes without testing
5. **Protect privacy** - Aggregate personalization data, avoid PII in logs

---

## Future Enhancements

- Reinforcement learning for continuous improvement
- Cross-learner collaborative filtering
- Knowledge graph-based recommendations
- Multimodal content generation
- Real-time skill assessment

---

**Maintained By:** TutorPutor Engineering Team  
**Contact:** See team documentation for ownership
