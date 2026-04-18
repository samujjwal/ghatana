# TutorPutor API Documentation

## Overview

This directory contains the API documentation for TutorPutor, an AI-powered educational platform for STEAM learning. The API provides endpoints for modules, assessments, content generation, learning, users, payments, and AI tutoring.

## Documentation Files

- **openapi.yaml** - OpenAPI 3.0 specification for the TutorPutor API
- **README.md** - This file

## Viewing the Documentation

### Using Swagger UI

1. Install dependencies:
```bash
npm install -g @redocly/cli
```

2. Start the Swagger UI server:
```bash
redocly preview-docs openapi.yaml
```

3. Open your browser to `http://localhost:8080`

### Using Redoc

1. Install Redoc:
```bash
npm install -g @redocly/cli
```

2. Build the static documentation:
```bash
redocly build-docs openapi.yaml --output api-docs.html
```

3. Open `api-docs.html` in your browser

## API Endpoints

### Authentication (`/api/v1/auth`)
- `POST /api/v1/auth/login` - User login
- `POST /api/v1/auth/register` - User registration
- `POST /api/v1/auth/refresh` - Refresh JWT token
- `POST /api/v1/auth/logout` - User logout

### Modules (`/api/v1/modules`)
- `GET /api/v1/modules` - List all modules with filtering
- `GET /api/v1/modules/{moduleId}` - Get module details
- `POST /api/v1/modules` - Create new module (admin)
- `PUT /api/v1/modules/{moduleId}` - Update module (admin)
- `DELETE /api/v1/modules/{moduleId}` - Delete module (admin)

### Assessments (`/api/v1/assessments`)
- `GET /api/v1/assessments` - List assessments with status filtering
- `GET /api/v1/assessments/{id}` - Get assessment details
- `POST /api/v1/assessments/{id}/attempts` - Start assessment attempt
- `POST /api/v1/assessments/attempts/{attemptId}/submit` - Submit attempt with CBM scoring
- `GET /api/v1/assessments/attempts/mine` - Get user's attempt history

### Content Generation (`/api/v1/content`)
- `POST /api/v1/content/generate` - Generate learning content
- `GET /api/v1/content/jobs/{jobId}` - Get generation job status
- `POST /api/v1/content/claims` - Generate knowledge claims
- `POST /api/v1/content/examples` - Generate examples
- `POST /api/v1/content/simulations` - Generate simulations

### Learning (`/api/v1/learning`)
- `GET /api/v1/learning/enrollments` - List user enrollments
- `POST /api/v1/learning/modules/{moduleId}/enroll` - Enroll in module
- `GET /api/v1/learning/progress` - Get learning progress
- `POST /api/v1/learning/progress` - Update learning progress

### Users (`/api/v1/users`)
- `GET /api/v1/users/me` - Get current user profile
- `PUT /api/v1/users/me` - Update user profile
- `GET /api/v1/users/{userId}` - Get user details (admin)
- `GET /api/v1/users/{userId}/analytics` - Get user analytics

### Payments (`/api/v1/payments`)
- `POST /api/v1/payments/create-intent` - Create payment intent
- `POST /api/v1/payments/webhook` - Stripe webhook handler
- `GET /api/v1/payments/subscriptions` - List subscriptions
- `POST /api/v1/payments/subscriptions/cancel` - Cancel subscription

### AI Tutoring (`/api/v1/ai`)
- `POST /api/v1/ai/tutor/query` - AI tutor query with context
- `POST /api/v1/ai/simulation/parse-intent` - Parse simulation intent
- `POST /api/v1/ai/simulation/explain` - Explain simulation
- `POST /api/v1/ai/content/draft` - Generate learning unit draft
- `POST /api/v1/ai/content/query` - Parse content query
- `GET /api/v1/ai/health` - AI service health check
- `GET /api/v1/ai/cache/stats` - AI cache statistics

### Analytics (`/api/v1/analytics`)
- `GET /api/v1/analytics/summary` - Analytics summary
- `GET /api/v1/analytics/usage-trends` - Usage trends
- `GET /api/v1/analytics/at-risk-students` - At-risk students

### Health (`/health`)
- `GET /health` - Health check endpoint
- `GET /health/ready` - Readiness check
- `GET /health/live` - Liveness check

## Authentication

Most endpoints require authentication via JWT tokens. Include the token in the Authorization header:

```
Authorization: Bearer <your-jwt-token>
```

### Token Refresh

JWT tokens expire after 1 hour. Use the refresh endpoint to obtain a new token before expiration.

## Rate Limiting

API requests are rate-limited to 100 requests per minute per user. Rate limit headers are included in responses:

- `X-RateLimit-Limit`: Request limit per window
- `X-RateLimit-Remaining`: Remaining requests in current window
- `X-RateLimit-Reset`: Unix timestamp when window resets

## Error Handling

The API uses standard HTTP status codes:

- `200` - Success
- `201` - Created
- `204` - No Content
- `400` - Bad Request
- `401` - Unauthorized
- `403` - Forbidden
- `404` - Not Found
- `409` - Conflict
- `422` - Unprocessable Entity
- `429` - Too Many Requests
- `500` - Internal Server Error
- `503` - Service Unavailable

Error responses follow this format:

```json
{
  "error": "Error type",
  "message": "Detailed error message",
  "statusCode": 400,
  "details": {}
}
```

## Webhooks

TutorPutor supports webhooks for:

- Payment events (Stripe)
- Content generation completion
- Assessment submission
- User enrollment changes

Configure webhooks via the admin dashboard or API.

## SDKs and Libraries

- **JavaScript/TypeScript**: `@tutorputor/sdk` (npm)
- **Python**: `tutorputor-python` (PyPI)
- **Java**: `com.ghatana.tutorputor.client` (Maven)

## API Testing

Use the provided Postman collection or OpenAPI specification for testing.

## Versioning

Current API version: v1

Base URL: `https://api.tutorputor.com/api/v1`

Versioning strategy: URL-based versioning. Deprecated endpoints will be supported for at least 6 months after deprecation notice.

## Support

For API support, contact: api-support@tutorputor.com
