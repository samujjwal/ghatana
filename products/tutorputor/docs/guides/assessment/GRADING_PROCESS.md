# Grading Process Documentation

**Last Updated:** 2026-04-17  
**Version:** 1.0

---

## Overview

TutorPutor implements a comprehensive AI-assisted grading system that combines automated grading with human oversight for quality assurance. The system handles multiple assessment types including multiple-choice, simulation interactions, and open-ended responses.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Grading Layer                           │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐  ┌──────────────────┐                │
│  │  Multiple Choice │  │  Simulation      │                │
│  │  Grading         │  │  Grading         │                │
│  └────────┬─────────┘  └────────┬─────────┘                │
│           │                     │                             │
│           └──────────┬──────────┘                             │
│                      ▼                                        │
│           ┌──────────────────┐                               │
│           │  AI Grading       │                               │
│           │  (Open-Ended)    │                               │
│           └────────┬─────────┘                               │
│                    │                                        │
│                    ▼                                        │
│           ┌──────────────────┐                               │
│           │  Teacher Review   │                               │
│           │  Workflow         │                               │
│           └────────┬─────────┘                               │
│                    │                                        │
│                    ▼                                        │
│           ┌──────────────────┐                               │
│           │  Quality          │                               │
│           │  Monitoring      │                               │
│           └──────────────────┘                               │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 1. Multiple Choice Grading

### Purpose
Automatic grading for multiple-choice questions with immediate feedback.

### Implementation
- **Location:** `services/tutorputor-platform/src/modules/learning/assessment-service.ts`
- **Method:** Exact match validation against correct choice IDs
- **Scoring:** Binary (0% or 100%) per item

### Algorithm
```typescript
const correctIds = choices.filter((choice) => choice.isCorrect).map((choice) => choice.id);
const isCorrect = response.selectedChoiceIds.length === 1 && correctIds.includes(response.selectedChoiceIds[0]);
const scorePercent = isCorrect ? 100 : 0;
```

### Feedback
- **Correct:** "Correct selection."
- **Incorrect:** "Review the concept and try again."

---

## 2. Simulation Grading

### Purpose
Grade simulation interactions based on parameter exploration, prediction accuracy, and explanation quality.

### Implementation
- **Location:** `services/tutorputor-platform/src/modules/assessment/simulation-integration/service.ts`
- **Components:** Parameter coverage (40%), Prediction accuracy (35%), Explanation quality (25%)

### Scoring Formula
```typescript
overall = matchedParameters * 0.4
        + predictionScore * 0.35
        + explanationScore * 0.25
scorePercent = Math.round(overall * 100)
```

### Question Types
- **Prediction:** Predict outcomes before parameter changes
- **Parameter Identification:** Identify relevant parameters
- **Process Explanation:** Explain causal relationships observed

### Domain-Specific Rubrics
- **Physics:** Systematic exploration, cause-effect reasoning, mathematical awareness, scientific communication
- **Chemistry:** Molecular reasoning, equilibrium understanding, experimental design, safety awareness
- **Biology:** Systems thinking, scale navigation, evolutionary reasoning, data interpretation

### Feedback
- **Strengths:** Identified when scores are high in specific areas
- **Improvements:** Specific suggestions for low-scoring areas
- **Needs Review:** Flagged when score < 65%

---

## 3. AI Grading for Open-Ended Questions

### Purpose
AI-powered grading for short answer, essay, and free-response questions.

### Implementation
- **Location:** `services/tutorputor-platform/src/modules/assessment/ai-grading/AIGradingService.ts`
- **Method:** Semantic similarity and rubric-based evaluation
- **Confidence Threshold:** 0.7 (below threshold requires teacher review)

### Process
1. Send question prompt, student response, rubric, and model answer to AI client
2. Receive score, confidence, strengths, improvements, and comments
3. If confidence < 0.7, flag for teacher review
4. Store result with metadata

### Usage
```typescript
const service = new AIGradingService();
const result = await service.gradeOpenEndedResponse({
  tenantId,
  assessmentId,
  itemId,
  questionPrompt,
  studentResponse,
  rubric: {
    criteria: [
      { name: 'Accuracy', description: 'Correctness of content', maxPoints: 5 },
      { name: 'Clarity', description: 'Clarity of expression', maxPoints: 5 },
    ],
  },
  modelAnswer: 'Expected answer...',
});
```

### Output
```typescript
{
  itemId: string;
  scorePercent: number;
  earnedPoints: number;
  maxPoints: number;
  confidence: number;
  needsReview: boolean;
  feedback: {
    strengths: string[];
    improvements: string[];
    comments: string;
    rubricScores?: Array<{...}>;
  };
  metadata: {
    modelUsed: string;
    processingTimeMs: number;
    timestamp: string;
  };
}
```

---

## 4. Teacher Review Workflow

### Purpose
Human oversight for AI-graded responses and flagged assessments.

### Implementation
- **Location:** `services/tutorputor-platform/src/modules/assessment/teacher-review/TeacherReviewService.ts`
- **States:** pending → assigned → in_progress → completed/rejected

### Workflow
1. **Task Creation:** AI grading with low confidence or manual flag creates review task
2. **Assignment:** System assigns to available teacher or teacher claims task
3. **Review:** Teacher reviews response and provides score/feedback
4. **Decision:** Teacher approves (uses reviewed score) or rejects (returns to student)

### Priority Levels
- **High:** Critical assessments, low confidence (< 0.5)
- **Medium:** Standard flagged responses
- **Low:** Optional review, borderline cases

### Usage
```typescript
const service = new TeacherReviewService(prisma);

// Create review task
await service.createReviewTask({
  tenantId,
  assessmentId,
  attemptId,
  itemId,
  studentId,
  reason: 'Low AI confidence',
  priority: 'high',
});

// Assign to teacher
await service.assignReviewTask(taskId, teacherId);

// Submit review
await service.submitReview({
  taskId,
  teacherId,
  approved: true,
  reviewedScore: 95,
  reviewedFeedback: 'Excellent response',
});
```

---

## 5. Quality Monitoring

### Purpose
Monitor grading quality across AI and teacher grading to ensure consistency and fairness.

### Implementation
- **Location:** `services/tutorputor-platform/src/modules/assessment/quality-monitoring/GradingQualityMonitoringService.ts`

### Metrics Tracked
- **AI Grading Accuracy:** Proxy based on score distribution
- **Teacher-AI Agreement:** Correlation between AI and teacher scores
- **Grading Consistency:** Variance in scores for similar responses
- **Bias Score:** Detection of scoring anomalies across groups
- **Average Confidence:** Mean confidence of AI grading
- **Needs Review Rate:** Percentage of responses requiring review
- **Average Processing Time:** Performance metric

### Alert Thresholds
- **Accuracy:** < 0.8 triggers alert
- **Agreement:** < 0.85 triggers alert
- **Consistency:** < 0.9 triggers alert
- **Bias:** > 0.1 triggers alert

### Dashboard
```typescript
const service = new GradingQualityMonitoringService(prisma);
const dashboard = await service.getQualityDashboard(tenantId);

// Returns:
{
  metrics: QualityMetrics;
  alerts: QualityAlert[];
  trends: Array<{ date, accuracy, agreement }>;
}
```

---

## 6. IRT Calibration

### Purpose
Item Response Theory calibration for adaptive assessment.

### Implementation
- **Location:** `services/tutorputor-platform/src/modules/assessment/irt/service.ts`

### Parameters
- **Discrimination:** How well the item distinguishes between ability levels
- **Difficulty:** Ability level at which 50% of respondents answer correctly
- **Guessing:** Probability of correct answer by guessing

### Difficulty Mapping
- **INTRO:** -0.75
- **INTERMEDIATE:** 0
- **ADVANCED:** 0.85

### Usage
```typescript
const irtService = new IRTCalibrationService();
const irt = irtService.calibrateForDifficulty('INTERMEDIATE', 'understand');
// Returns: { discrimination: 1.0, difficulty: 0, guessing: 0.25 }
```

---

## 7. Misconception Detection

### Purpose
Identify common misconceptions from student responses.

### Implementation
- **Location:** `services/tutorputor-platform/src/modules/assessment/misconceptions/`

### Process
1. Analyze response patterns
2. Match against misconception database
3. Record knowledge gaps in learner profile
4. Trigger remediation recommendations

---

## Performance Metrics

### Key Indicators
- Average grading time per response
- AI grading accuracy (compared to teacher review)
- Teacher-AI agreement rate
- Needs review rate
- Average review completion time

### Monitoring
- Prometheus metrics for all grading services
- Quality dashboard for real-time monitoring
- Alert system for quality degradation

---

## Best Practices

1. **Always provide fallbacks** - AI grading fails gracefully to teacher review
2. **Respect teacher authority** - Teachers can override AI scores
3. **Monitor for bias** - Regularly audit grading patterns across groups
4. **Maintain audit trails** - All grading decisions are logged
5. **Protect student privacy** - Anonymize data in quality monitoring

---

## Future Enhancements

- Reinforcement learning for AI grading improvement
- Cross-teacher calibration for consistency
- Real-time grading during assessment (for immediate feedback)
- Multilingual grading support
- Plagiarism detection integration

---

**Maintained By:** TutorPutor Engineering Team  
**Contact:** See team documentation for ownership
