# Knowledge Base API Documentation

**Last Updated:** April 28, 2026  
**Status:** Implemented with actual database queries

---

## Overview

The Knowledge Base API provides fact verification, concept search, and curriculum alignment capabilities for the TutorPutor platform. It serves as the backbone for content validation and educational content fact-checking.

---

## Endpoints

### POST `/api/knowledge-base/verify-fact`

Verifies a factual claim against the knowledge base.

**Request Body:**
```typescript
{
  claim: string;          // The claim to verify
  domain: string;         // Domain (e.g., "math", "science")
  context?: {
    gradeRange?: string;
    subject?: string;
    relatedConcepts?: string[];
  };
}
```

**Response:**
```typescript
{
  success: boolean;
  data?: {
    isVerified: boolean;
    confidence: number;
    sources: Array<{
      id: string;
      title: string;
      excerpt: string;
    }>;
    explanation: string;
  };
  error?: string;
}
```

---

### GET `/api/knowledge-base/search-concepts`

Searches for concepts related to a query.

**Query Parameters:**
- `query`: Search query string
- `domain`: Domain to search within

**Response:**
```typescript
{
  success: boolean;
  data?: {
    concepts: Array<{
      id: string;
      name: string;
      domain: string;
      confidence: number;
    }>;
  };
  error?: string;
}
```

---

### POST `/api/knowledge-base/validate-content`

Validates educational content against curriculum standards.

**Request Body:**
```typescript
{
  content: string;
  contentType: "claim" | "example" | "explanation" | "task";
  domain: string;
  gradeRange: string;
  context?: {
    learningObjectives?: string[];
    prerequisites?: string[];
  };
}
```

**Response:**
```typescript
{
  success: boolean;
  data?: {
    isValid: boolean;
    issues: string[];
    suggestions: string[];
    alignmentScore: number;
  };
  error?: string;
}
```

---

### GET `/api/knowledge-base/curriculum-alignment`

Checks curriculum alignment for a concept.

**Query Parameters:**
- `concept`: Concept identifier
- `domain`: Domain
- `gradeRange`: Grade level

**Response:**
```typescript
{
  success: boolean;
  data?: {
    aligned: boolean;
    alignmentScore: number;
    relatedObjectives: Array<{
      id: string;
      objective: string;
    }>;
    prerequisites: string[];
  };
  error?: string;
}
```

---

### POST `/api/knowledge-base/batch-verify-facts`

Batch verifies multiple claims.

**Request Body:**
```typescript
{
  claims: Array<{
    claim: string;
    domain: string;
  }>;
}
```

**Response:**
```typescript
{
  success: boolean;
  data?: {
    results: Array<{
      claim: string;
      isVerified: boolean;
      confidence: number;
    }>;
  };
  error?: string;
}
```

---

### GET `/api/knowledge-base/stats`

Returns statistics about the knowledge base.

**Response:**
```typescript
{
  success: boolean;
  data?: {
    totalConcepts: number;           // Count of unique concepts from LearnerMastery
    totalSources: number;            // Count of evidence bundles
    averageConfidence: number;      // Average confidence across evidence bundles
    lastUpdated: Date;             // Timestamp of most recent evidence bundle
    domains: string[];             // List of unique domains from modules
  };
  error?: string;
}
```

**Implementation Notes:**
- `totalConcepts`: Queries `LearnerMastery` with distinct `conceptId`
- `totalSources`: Counts `EvidenceBundleMetadata` records
- `averageConfidence`: Calculates from `bundleConfidence` of evidence bundles
- `domains`: Extracts from `Module.domain` with distinct values
- `lastUpdated`: Gets most recent `generatedAt` from evidence bundles

---

### GET `/api/knowledge-base/health`

Health check endpoint.

**Response:**
```typescript
{
  success: boolean;
  status: "healthy";
  service: "Knowledge Base Integration";
  timestamp: Date;
}
```

---

## Data Sources

The Knowledge Base API queries the following database tables:

1. **LearnerMastery** - Concept mastery data for learners
2. **EvidenceBundleMetadata** - Evidence bundle information with confidence scores
3. **Module** - Module information including domains
4. **ModuleContentBlock** - Content blocks with payload data
5. **ModuleLearningObjective** - Learning objectives with labels

---

## Current Limitations

1. **Semantic Search**: Currently uses text similarity (Jaccard index) rather than vector similarity
2. **Classroom Filtering**: Not implemented (LearnerProfile lacks classroomId field)
3. **External Knowledge Sources**: No integration with external APIs (Wikipedia, etc.)
4. **Real-time Updates**: Statistics are calculated on-demand, not cached

---

## Future Enhancements

1. Implement vector-based semantic search using embeddings
2. Add external knowledge source integration (OpenAlex, Crossref, etc.)
3. Implement classroom-level filtering with proper schema changes
4. Add caching layer for statistics endpoint
5. Implement fact-checking against trusted sources
