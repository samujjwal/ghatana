# Tutorputor API Documentation

This document provides comprehensive API documentation for the Tutorputor platform.

## Table of Contents

1. [Authentication](#authentication)
2. [Modules API](#modules-api)
3. [Assessments API](#assessments-api)
4. [Content Generation API](#content-generation-api)
5. [Learning API](#learning-api)
6. [Users API](#users-api)
7. [Payments API](#payments-api)
8. [Error Responses](#error-responses)
9. [Rate Limiting](#rate-limiting)
10. [Webhooks](#webhooks)

## Base URL

```
Development: http://localhost:3000/api/v1
Staging: https://staging.api.tutorputor.com/api/v1
Production: https://api.tutorputor.com/api/v1
```

## Authentication

### Register

Register a new user account.

```
POST /auth/register
```

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Response (201):**
```json
{
  "id": "usr_123456",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

### Login

Authenticate a user and receive a JWT token.

```
POST /auth/login
```

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900,
  "user": {
    "id": "usr_123456",
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe"
  }
}
```

### Refresh Token

Refresh an expired access token.

```
POST /auth/refresh
```

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900
}
```

### Logout

Invalidate the current session.

```
POST /auth/logout
```

**Headers:**
```
Authorization: Bearer <token>
```

**Response (204):** No content

## Modules API

### List Modules

Get a paginated list of modules.

```
GET /modules
```

**Query Parameters:**
- `page` (integer, default: 1)
- `limit` (integer, default: 20)
- `domain` (string, optional)
- `difficulty` (string, optional)
- `status` (string, optional)

**Response (200):**
```json
{
  "data": [
    {
      "id": "mod_123456",
      "title": "Introduction to Python",
      "slug": "introduction-to-python",
      "domain": "programming",
      "difficulty": "beginner",
      "status": "published",
      "createdAt": "2024-01-01T00:00:00Z",
      "updatedAt": "2024-01-01T00:00:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 100,
    "totalPages": 5
  }
}
```

### Get Module

Get details of a specific module.

```
GET /modules/:id
```

**Response (200):**
```json
{
  "id": "mod_123456",
  "title": "Introduction to Python",
  "slug": "introduction-to-python",
  "description": "Learn the basics of Python programming",
  "domain": "programming",
  "difficulty": "beginner",
  "status": "published",
  "estimatedDuration": 3600,
  "learningObjectives": [
    "Understand Python syntax",
    "Write basic programs",
    "Use data structures"
  ],
  "prerequisites": [],
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z"
}
```

### Create Module

Create a new module (admin only).

```
POST /modules
```

**Headers:**
```
Authorization: Bearer <token>
```

**Request Body:**
```json
{
  "title": "Introduction to Python",
  "slug": "introduction-to-python",
  "description": "Learn the basics of Python programming",
  "domain": "programming",
  "difficulty": "beginner",
  "estimatedDuration": 3600,
  "learningObjectives": [
    "Understand Python syntax",
    "Write basic programs",
    "Use data structures"
  ]
}
```

**Response (201):**
```json
{
  "id": "mod_123456",
  "title": "Introduction to Python",
  "slug": "introduction-to-python",
  "status": "draft",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

### Update Module

Update an existing module (admin only).

```
PUT /modules/:id
```

**Headers:**
```
Authorization: Bearer <token>
```

**Request Body:**
```json
{
  "title": "Introduction to Python (Updated)",
  "status": "published"
}
```

**Response (200):**
```json
{
  "id": "mod_123456",
  "title": "Introduction to Python (Updated)",
  "slug": "introduction-to-python",
  "status": "published",
  "updatedAt": "2024-01-02T00:00:00Z"
}
```

### Delete Module

Delete a module (admin only).

```
DELETE /modules/:id
```

**Headers:**
```
Authorization: Bearer <token>
```

**Response (204):** No content

## Assessments API

### List Assessments

Get assessments for a module.

```
GET /modules/:moduleId/assessments
```

**Response (200):**
```json
{
  "data": [
    {
      "id": "asm_123456",
      "moduleId": "mod_123456",
      "title": "Python Basics Quiz",
      "type": "quiz",
      "status": "published",
      "passingScore": 80,
      "attemptsAllowed": 3,
      "timeLimitMinutes": 30
    }
  ]
}
```

### Get Assessment

Get details of a specific assessment.

```
GET /assessments/:id
```

**Response (200):**
```json
{
  "id": "asm_123456",
  "moduleId": "mod_123456",
  "title": "Python Basics Quiz",
  "type": "quiz",
  "status": "published",
  "passingScore": 80,
  "attemptsAllowed": 3,
  "timeLimitMinutes": 30,
  "items": [
    {
      "id": "itm_123456",
      "orderIndex": 1,
      "itemType": "multiple_choice",
      "prompt": "What is the correct way to print in Python?",
      "choices": [
        { "id": "a", "text": "print('Hello')" },
        { "id": "b", "text": "echo 'Hello'" },
        { "id": "c", "text": "console.log('Hello')" }
      ],
      "modelAnswer": "a",
      "points": 1
    }
  ]
}
```

### Create Assessment Attempt

Start an assessment attempt.

```
POST /assessments/:id/attempts
```

**Headers:**
```
Authorization: Bearer <token>
```

**Response (201):**
```json
{
  "id": "att_123456",
  "assessmentId": "asm_123456",
  "status": "in_progress",
  "startedAt": "2024-01-01T00:00:00Z",
  "timeLimitMinutes": 30
}
```

### Submit Assessment

Submit assessment responses.

```
PUT /assessments/:id/attempts/:attemptId
```

**Headers:**
```
Authorization: Bearer <token>
```

**Request Body:**
```json
{
  "responses": [
    {
      "itemId": "itm_123456",
      "response": "a"
    }
  ]
}
```

**Response (200):**
```json
{
  "id": "att_123456",
  "status": "completed",
  "scorePercent": 100,
  "passed": true,
  "feedback": "Excellent work!",
  "submittedAt": "2024-01-01T00:30:00Z",
  "gradedAt": "2024-01-01T00:30:05Z"
}
```

## Content Generation API

### Generate Content

Request content generation (async).

```
POST /content/generate
```

**Headers:**
```
Authorization: Bearer <token>
```

**Request Body:**
```json
{
  "moduleId": "mod_123456",
  "contentType": "lesson",
  "topic": "Variables and Data Types",
  "learningObjectives": [
    "Understand variables",
    "Learn data types"
  ],
  "options": {
    "difficulty": "beginner",
    "language": "python",
    "includeExamples": true
  }
}
```

**Response (202):**
```json
{
  "jobId": "job_123456",
  "status": "queued",
  "estimatedDuration": 60
}
```

### Get Generation Status

Check content generation status.

```
GET /content/status/:jobId
```

**Response (200):**
```json
{
  "jobId": "job_123456",
  "status": "completed",
  "progress": 100,
  "result": {
    "id": "cnt_123456",
    "content": "Generated content here...",
    "metadata": {
      "wordCount": 500,
      "readingTime": 120
    }
  }
}
```

## Learning API

### Enroll in Module

Enroll a user in a module.

```
POST /learning/modules/:moduleId/enroll
```

**Headers:**
```
Authorization: Bearer <token>
```

**Response (201):**
```json
{
  "id": "enr_123456",
  "moduleId": "mod_123456",
  "userId": "usr_123456",
  "status": "in_progress",
  "progressPercent": 0,
  "startedAt": "2024-01-01T00:00:00Z"
}
```

### Get Learning Progress

Get learning progress for a user.

```
GET /learning/progress
```

**Headers:**
```
Authorization: Bearer <token>
```

**Response (200):**
```json
{
  "enrollments": [
    {
      "moduleId": "mod_123456",
      "status": "in_progress",
      "progressPercent": 45,
      "timeSpentSeconds": 1800,
      "startedAt": "2024-01-01T00:00:00Z",
      "lastAccessedAt": "2024-01-02T12:00:00Z"
    }
  ],
  "summary": {
    "totalEnrollments": 5,
    "completedModules": 2,
    "inProgressModules": 3,
    "totalTimeSpentSeconds": 36000
  }
}
```

### Record Learning Event

Record a learning event (e.g., lesson completion).

```
POST /learning/events
```

**Headers:**
```
Authorization: Bearer <token>
```

**Request Body:**
```json
{
  "moduleId": "mod_123456",
  "eventType": "lesson_completed",
  "payload": {
    "lessonId": "lsn_123456",
    "durationSeconds": 300
  }
}
```

**Response (201):**
```json
{
  "id": "evt_123456",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

## Users API

### Get User Profile

Get the current user's profile.

```
GET /users/me
```

**Headers:**
```
Authorization: Bearer <token>
```

**Response (200):**
```json
{
  "id": "usr_123456",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "avatar": "https://example.com/avatar.jpg",
  "preferences": {
    "language": "en",
    "timezone": "UTC"
  },
  "createdAt": "2024-01-01T00:00:00Z"
}
```

### Update User Profile

Update the current user's profile.

```
PUT /users/me
```

**Headers:**
```
Authorization: Bearer <token>
```

**Request Body:**
```json
{
  "firstName": "Jane",
  "lastName": "Smith"
}
```

**Response (200):**
```json
{
  "id": "usr_123456",
  "firstName": "Jane",
  "lastName": "Smith",
  "updatedAt": "2024-01-02T00:00:00Z"
}
```

## Payments API

### Create Payment Intent

Create a payment intent for a subscription or purchase.

```
POST /payments/create-intent
```

**Headers:**
```
Authorization: Bearer <token>
```

**Request Body:**
```json
{
  "amount": 1999,
  "currency": "usd",
  "type": "subscription",
  "subscriptionPlan": "pro"
}
```

**Response (200):**
```json
{
  "clientSecret": "pi_123456_secret_abc123",
  "amount": 1999,
  "currency": "usd"
}
```

### Confirm Payment

Confirm a payment after client-side processing.

```
POST /payments/confirm
```

**Headers:**
```
Authorization: Bearer <token>
```

**Request Body:**
```json
{
  "paymentIntentId": "pi_123456"
}
```

**Response (200):**
```json
{
  "status": "succeeded",
  "amount": 1999,
  "currency": "usd",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

## Error Responses

All errors follow a consistent format:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request parameters",
    "details": [
      {
        "field": "email",
        "message": "Invalid email format"
      }
    ]
  }
}
```

### Common Error Codes

- `VALIDATION_ERROR`: Invalid request parameters
- `UNAUTHORIZED`: Missing or invalid authentication
- `FORBIDDEN`: Insufficient permissions
- `NOT_FOUND`: Resource not found
- `CONFLICT`: Resource conflict (e.g., duplicate email)
- `RATE_LIMIT_EXCEEDED`: Too many requests
- `INTERNAL_ERROR`: Server error

### HTTP Status Codes

- `200 OK`: Request succeeded
- `201 Created`: Resource created
- `204 No Content`: Request succeeded, no content returned
- `400 Bad Request`: Invalid request
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `409 Conflict`: Resource conflict
- `429 Too Many Requests`: Rate limit exceeded
- `500 Internal Server Error`: Server error

## Rate Limiting

The API implements rate limiting to prevent abuse:

- **Anonymous requests**: 100 requests per hour
- **Authenticated requests**: 1000 requests per hour
- **Premium users**: 5000 requests per hour

Rate limit headers are included in responses:

```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1609459200
```

## Webhooks

### Stripe Webhook

Handle Stripe payment events.

```
POST /webhooks/stripe
```

**Headers:**
```
Stripe-Signature: t=123456,v1=abc123
```

**Request Body:** Stripe event payload

**Response (200):** Webhook received

### Supported Stripe Events

- `payment_intent.succeeded`
- `payment_intent.failed`
- `customer.subscription.created`
- `customer.subscription.updated`
- `customer.subscription.deleted`
- `invoice.payment_succeeded`
- `invoice.payment_failed`

## SDKs and Libraries

### JavaScript/TypeScript

```bash
npm install @tutorputor/sdk
```

```typescript
import { TutorputorClient } from '@tutorputor/sdk';

const client = new TutorputorClient({
  apiKey: 'your-api-key',
  baseUrl: 'https://api.tutorputor.com/api/v1'
});

// Get modules
const modules = await client.modules.list();

// Create enrollment
const enrollment = await client.learning.enroll('mod_123456');
```

### Python

```bash
pip install tutorputor-sdk
```

```python
from tutorputor import TutorputorClient

client = TutorputorClient(
    api_key='your-api-key',
    base_url='https://api.tutorputor.com/api/v1'
)

# Get modules
modules = client.modules.list()

# Create enrollment
enrollment = client.learning.enroll('mod_123456')
```

## Testing the API

### Using cURL

```bash
# Login
curl -X POST https://api.tutorputor.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password"}'

# Get modules
curl https://api.tutorputor.com/api/v1/modules \
  -H "Authorization: Bearer <token>"
```

### Using Postman

Import the Postman collection from:
`docs/api/tutorputor-api-collection.json`

## API Versioning

The API uses URL versioning: `/api/v1/`

- **v1**: Current stable version
- **v2**: Beta features (when available)

Breaking changes will increment the version number.

## Support

For API support:
- Documentation: https://docs.tutorputor.com/api
- Status Page: https://status.tutorputor.com
- Email: api-support@tutorputor.com
- Slack: #api-support
