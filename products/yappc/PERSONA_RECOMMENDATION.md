# Persona Recommendation System

## Overview

The YAPPC lifecycle API supports AI-backed persona recommendation with fallback to phase-definition heuristics. The system recommends the most appropriate persona for a given project phase based on project context, artifacts, and phase requirements.

## API Endpoint

**POST** `/personas/derive`

**Request Body:**
```json
{
  "projectId": "project-123",
  "phase": "INTENT",
  "useAI": true
}
```

**Response (AI-backed):**
```json
{
  "persona": "Product Manager",
  "confidence": 0.85,
  "reasoning": "AI recommendation based on project context",
  "phase": "INTENT",
  "source": "AI",
  "artifactCount": 3
}
```

**Response (Fallback):**
```json
{
  "persona": "Product Owner",
  "confidence": 0.75,
  "reasoning": "Intent phase: Define the problem and strategic intent",
  "phase": "INTENT",
  "source": "phase_definition",
  "artifactCount": 3,
  "approvedCount": 2
}
```

## Prompt Template

The AI service uses the following prompt template for persona recommendation:

```
Given a project in the {phaseName} phase, recommend the most appropriate persona from this list: {personaList}.

Project: {projectName}
Description: {projectDescription}
Phase: {phaseName} - {phaseDescription}
Key Artifacts ({artifactCount}): {artifactSummary}

Respond with JSON format:
{
  "persona": "recommended persona name",
  "reasoning": "brief explanation",
  "confidence": 0.0-1.0
}
```

## Fallback Behavior

When `useAI` is `false` or the AI service is unavailable, the system falls back to phase-definition heuristics:

1. Uses the first persona from the phase definition's canonical persona list
2. Calculates confidence based on artifact completion rate
3. Base confidence: 0.75
4. Completion bonus: (approvedCount / artifactCount) * 0.2

## Phase Personas

| Phase | Personas |
|-------|----------|
| INTENT | Product Owner, Product Manager |
| SHAPE | Architect, Tech Lead |
| VALIDATE | QA Engineer, Test Lead |
| GENERATE | Developer, Engineer |
| RUN | DevOps Engineer, SRE |
| OBSERVE | SRE, Operations |
| IMPROVE | Product Manager, All |

## Configuration

- Set `useAI: false` in the request body to disable AI recommendation
- The AI service is called via the existing `AIService.sendCopilotMessage` method
- Session ID format: `persona-{projectId}-{phase}`

## Integration Notes

- The persona recommendation endpoint can be called during task creation or phase transitions
- The recommended persona can be used to assign tasks or suggest team composition
- The confidence score indicates the strength of the recommendation
- The source field indicates whether the recommendation came from AI or phase definition
