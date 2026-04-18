# TutorPutor API Documentation

## Overview

This directory contains the API documentation for TutorPutor, an AI-powered educational platform for STEAM learning.

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

### Authentication (`/auth`)
- `POST /auth/login` - User login
- `POST /auth/register` - User registration

### Modules (`/modules`)
- `GET /modules` - List all modules
- `GET /modules/{moduleId}` - Get module details

### AI (`/ai`)
- `POST /ai/tutor/query` - AI tutor query
- `GET /ai/health` - AI service health check
- `GET /ai/cache/stats` - AI cache statistics

### Analytics (`/analytics`)
- `GET /analytics/summary` - Analytics summary

### Health (`/health`)
- `GET /health` - Health check endpoint

## Authentication

Most endpoints require authentication via JWT tokens. Include the token in the Authorization header:

```
Authorization: Bearer <your-jwt-token>
```

## Rate Limiting

API requests are rate-limited to 100 requests per minute per user.

## Error Handling

The API uses standard HTTP status codes:

- `200` - Success
- `201` - Created
- `400` - Bad Request
- `401` - Unauthorized
- `404` - Not Found
- `500` - Internal Server Error

Error responses follow this format:

```json
{
  "error": "Error type",
  "message": "Detailed error message",
  "statusCode": 400
}
```

## Versioning

Current API version: v1

Base URL: `https://api.tutorputor.com/api/v1`

## Support

For API support, contact: api-support@tutorputor.com
