# Mastery System Pilot Adoption Guide

## Overview

This guide provides step-by-step instructions for adopting the mastery system in a pilot flow for either YAPPC or Data Cloud runtime.

## Prerequisites

- Java 21 toolchain
- ActiveJ promise-based async model
- Data Cloud EntityRepository for persistence
- MasteryRegistry, LearningDeltaRepository, and related components configured

## Step 1: Configure Mastery Registry

```java
// In your runtime composition module
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.repository.MasteryTransitionRepository;
import com.ghatana.agent.mastery.repository.MasteryEvidenceRepository;
import com.ghatana.agent.mastery.transition.MasteryTransitionPolicy;

EntityRepository entityRepository = // your existing EntityRepository
MasteryTransitionPolicy transitionPolicy = // your transition policy

MasteryRegistry masteryRegistry = new MasteryRegistry(
    entityRepository,
    new MasteryTransitionRepository(entityRepository),
    new MasteryEvidenceRepository(entityRepository),
    transitionPolicy
);
```

## Step 2: Integrate with Agent Dispatcher

Replace or wrap your existing AgentDispatcher with GovernedAgentDispatcher:

```java
import com.ghatana.agent.runtime.safety.GovernedAgentDispatcher;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.runtime.mode.ModeSelector;

// Wrap your existing dispatcher
AgentDispatcher baseDispatcher = // your existing dispatcher
MasteryRegistry masteryRegistry = // configured in Step 1
ModeSelector modeSelector = // your mode selector
MemoryRetriever memoryRetriever = // optional memory retriever

AgentDispatcher governedDispatcher = new GovernedAgentDispatcher(
    baseDispatcher,
    masteryRegistry,
    modeSelector,
    memoryRetriever,
    // ... other dependencies
);
```

## Step 3: Configure Skill ID Derivation

Ensure skillId is available in AgentContext for mastery-bound dispatch:

```java
// In your agent execution flow
AgentContext ctx = AgentContext.builder()
    .addConfig("skillId", "gaa.code-generation") // or derive from capability manifest
    .addConfig("agentId", agentId)
    .addConfig("tenantId", tenantId)
    .build();
```

## Step 4: Enable Learning Delta Collection

Configure LearningEngine to capture learning deltas:

```java
import com.ghatana.agent.learning.LearningEngine;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.learning.LearningContract;

LearningDeltaRepository deltaRepository = // your delta repository
LearningContract learningContract = // your learning contract

LearningEngine learningEngine = new LearningEngine(
    deltaRepository,
    learningContract,
    // ... other dependencies
);
```

## Step 5: Configure Promotion Engine

Set up promotion evaluation and governance:

```java
import com.ghatana.agent.promotion.PromotionEngine;
import com.ghatana.agent.promotion.DefaultPromotionEngine;
import com.ghatana.agent.promotion.DefaultPromotionGovernancePolicy;
import com.ghatana.agent.promotion.PromotionGovernancePolicy;

PromotionGovernancePolicy governancePolicy = new DefaultPromotionGovernancePolicy();

PromotionEngine promotionEngine = new DefaultPromotionEngine(
    masteryRegistry,
    deltaRepository,
    governancePolicy,
    // ... other dependencies
);
```

## Step 6: Load Canonical Skill Manifests

Initialize skill manifests for your domain:

```java
import com.ghatana.agent.skill.CanonicalSkillManifests;
import com.ghatana.agent.skill.SkillManifestRepository;

// Load canonical manifests
List<SkillManifest> manifests = CanonicalSkillManifests.all();

// Or create custom manifests
SkillManifest customSkill = new SkillManifest(
    "my-domain.custom-skill",
    "Custom Skill",
    "Description",
    "my-domain",
    "custom",
    "1.0.0",
    // ... evaluation packs, promotion criteria, etc.
);
```

## Step 7: Configure Obsolescence Detection

Enable obsolescence signal ingestion:

```java
import com.ghatana.agent.obsolescence.ObsolescenceDetector;
import com.ghatana.agent.obsolescence.ObsolescenceSignalRepository;

ObsolescenceSignalRepository signalRepository = 
    new ObsolescenceSignalRepository(entityRepository);

ObsolescenceDetector obsolescenceDetector = new ObsolescenceDetector(
    masteryRegistry,
    signalRepository,
    // ... other dependencies
);
```

## Step 8: Add Governance API Endpoints

Expose mastery and learning endpoints via your HTTP layer:

```java
import com.ghatana.agent.mastery.MasteryController;

MasteryController masteryController = new MasteryController(
    masteryRegistry,
    approvalService,
    obsolescenceDetector,
    deltaRepository,
    promotionEngine
);

// Register routes
router.add("/api/v1/mastery/*", masteryController);
```

## Step 9: Enable UI Integration

Configure the mastery UI components:

```typescript
import { MasteryPage } from './pages/MasteryPage';

// In your app routing
<Route path="/mastery" element={<MasteryPage tenantId={currentTenant} />} />
```

## Step 10: Monitor and Iterate

Set up observability for mastery operations:

- Track mastery state transitions
- Monitor promotion queue length
- Alert on obsolescence signal detection
- Track learning delta approval rates

## Pilot Success Criteria

- [ ] Mastery registry successfully stores and retrieves items
- [ ] GovernedAgentDispatcher enforces mastery-based execution gates
- [ ] Learning deltas are captured and can be promoted
- [ ] Promotion workflow with governance approvals works end-to-end
- [ ] Obsolescence signals trigger appropriate state transitions
- [ ] UI displays mastery state and allows management
- [ ] Performance impact is acceptable (< 50ms overhead per dispatch)

## Rollback Plan

If issues arise during pilot:

1. Disable GovernedAgentDispatcher by using base dispatcher directly
2. Disable learning delta collection
3. Disable promotion engine
4. Clear mastery items from persistence if needed

## Next Steps

After successful pilot:

1. Expand to additional skills/domains
2. Add custom skill manifests for domain-specific capabilities
3. Configure tenant-specific governance policies
4. Add regression gates to CI (see P5.3)
5. Scale to production with appropriate resource allocation
