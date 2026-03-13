# Aura API Contracts (GraphQL + REST)

## API Style

- **Public client-facing API:** GraphQL for flexible product, profile, recommendation, and community queries.
- **Internal service-to-service APIs:** REST or gRPC where latency and clear boundaries matter.
- **Brand analytics API:** REST with API key authentication (Phase 4).

---

## Authentication & Authorization

| Concern               | Approach                                                                                                                                                                                    |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Authentication        | JWT issued by the Aura auth service. Every request to the API carries a `Bearer` token in the `Authorization` header.                                                                       |
| Authorization         | Scoped per-user. Users can only access and modify their own data.                                                                                                                           |
| Consent enforcement   | All mutations that process personal data validate a current, in-scope, non-revoked consent record before proceeding. If consent is absent, the mutation returns a `CONSENT_REQUIRED` error. |
| Brand analytics       | API key authentication. Keys are tenant-scoped and grant read-only access to anonymized aggregate data only.                                                                                |
| Internal service auth | Service-to-service calls use short-lived internal tokens (mTLS or shared secret, environment-dependent).                                                                                    |

**Security principles:**

- All endpoints served over HTTPS only.
- Input validation on all parameters at the API boundary.
- PII is never logged in plaintext. Log user IDs, not email addresses or profile details.
- Rate limiting applied per user and per IP at the API gateway.
- CORS restricted to known Aura origins.

---

## GraphQL Schema

```graphql
type Query {
  me: User
  feed(input: FeedInput!): FeedPage!
  product(id: ID!): Product
  compareProducts(ids: [ID!]!): [ProductComparison!]!
  recommendations(input: RecommendationInput!): [Recommendation!]!
  searchProducts(
    query: String!
    filters: ProductFilterInput
  ): ProductSearchResult!
}

type Mutation {
  updateProfile(input: UpdateProfileInput!): UserProfile!
  saveProduct(productId: ID!): SaveResult!
  unsaveProduct(productId: ID!): SaveResult!
  submitFeedback(input: FeedbackInput!): FeedbackResult!
  grantConsent(input: ConsentInput!): Consent!
  revokeConsent(scope: String!): Consent!
  deleteAccount: DeleteAccountResult!
}

type User {
  id: ID!
  profile: UserProfile
  savedProducts: [Product!]!
  consents: [Consent!]!
}

type UserProfile {
  skinType: String
  undertone: String
  skinTone: String
  styleArchetype: String
  allergies: [String!]!
  ethicalPreferences: [String!]!
  profileCompleteness: Float!
}

type Product {
  id: ID!
  name: String!
  brand: Brand!
  category: String!
  subcategory: String
  shades: [ProductShade!]!
  ingredients: [Ingredient!]!
  priceMin: Float
  priceMax: Float
  sources: [ProductSource!]!
}

type ProductShade {
  id: ID!
  name: String!
  undertone: String
  finish: String
  hexCode: String
}

type Ingredient {
  id: ID!
  inciName: String!
  commonName: String
  functions: [String!]!
  riskFlags: [String!]!
}

type Recommendation {
  product: Product!
  shadeSuggestion: ProductShade
  score: Float!
  confidence: Float!
  reasons: [RecommendationReason!]!
  explanation: String!
  trustFlags: [String!]!
}

type RecommendationReason {
  code: RecommendationReasonCode!
  weight: Float
  detail: String
}

enum RecommendationReasonCode {
  SHADE_MATCH
  INGREDIENT_SAFE
  ALLERGEN_ALERT
  DUPLICATE_ACTIVE
  OWNS_SIMILAR
  PRICE_FIT
  ETHICAL_MATCH
  COMMUNITY_MATCH
}

type FeedPage {
  items: [FeedItem!]!
  nextCursor: String
}

type FeedItem {
  type: String!
  recommendation: Recommendation
}

type ProductComparison {
  product: Product!
  compatibilityScore: Float
  reasons: [RecommendationReason!]!
}

type Consent {
  scope: String!
  granted: Boolean!
  grantedAt: String
  revokedAt: String
}

type Brand {
  id: ID!
  name: String!
}

type ProductSource {
  sourceName: String!
  sourceUrl: String
  fetchedAt: String!
}

type SaveResult {
  success: Boolean!
}

type FeedbackResult {
  success: Boolean!
}

type DeleteAccountResult {
  success: Boolean!
}

type ProductSearchResult {
  products: [Product!]!
  total: Int!
}

input FeedInput {
  cursor: String
  limit: Int
}

input RecommendationInput {
  category: String
  priceMax: Float
  ethicalFilters: [String!]
  limit: Int
}

input UpdateProfileInput {
  skinType: String
  undertone: String
  skinTone: String
  styleArchetype: String
  allergies: [String!]
  ethicalPreferences: [String!]
}

input FeedbackInput {
  productId: ID!
  recommendationId: ID
  feedbackType: FeedbackType!
  rating: Int
}

enum FeedbackType {
  CLICK
  SAVE
  DISMISS
  PURCHASE
  RATING
  HELPFUL
  NOT_HELPFUL
}

input ConsentInput {
  scope: String!
  granted: Boolean!
}

input ProductFilterInput {
  category: String
  priceMax: Float
  ethicalFilters: [String!]
}
```

---

## REST Endpoints

### GET /v1/products/{id}

Returns canonical product details.

**Auth:** Bearer token required.

**Response:** Product object including brand, category, shades, ingredients, sources.

---

### POST /v1/recommendations/query

Accepts user context and returns ranked recommendations.

**Auth:** Bearer token required.

**Request body:**

```json
{
  "category": "foundation",
  "priceMax": 60,
  "ethicalFilters": ["cruelty_free"],
  "limit": 20
}
```

**Response:** Array of Recommendation objects, each with score, confidence, reason codes, explanation, and trust flags.

---

### POST /v1/feedback

Captures click, save, dismiss, purchase, or rating feedback.

**Auth:** Bearer token required.

**Request body:**

```json
{
  "productId": "prd_001",
  "recommendationId": "rec_202",
  "feedbackType": "SAVE"
}
```

---

### POST /v1/consents

Creates or updates a user consent.

**Auth:** Bearer token required.

**Request body:**

```json
{
  "scope": "wellness_integration",
  "granted": true
}
```

---

### DELETE /v1/account

Initiates account deletion. All user data is permanently removed per the data retention policy.

**Auth:** Bearer token required. Requires re-authentication (password confirmation or re-login).

---

## Contract Principles

1. **Every recommendation response includes explanations.** Score alone is never returned without reason codes and human-readable text.
2. **Every personally sensitive mutation requires consent validation.** The API returns `CONSENT_REQUIRED` if a valid, in-scope consent record does not exist.
3. **Stable IDs and explicit versioning** in all internal APIs. Breaking changes require a new version prefix (`/v2/`).
4. **Affiliate relationships are disclosed** in the API response. The `sources` field exposes whether any purchase link carries an affiliate relationship.
5. **Error responses follow a consistent structure** with `code`, `message`, and optional `details` field.

---

## Error Response Format

```json
{
  "error": {
    "code": "CONSENT_REQUIRED",
    "message": "User has not granted consent for this operation.",
    "details": {
      "requiredScope": "wellness_integration"
    }
  }
}
```

Common error codes: `UNAUTHORIZED`, `FORBIDDEN`, `CONSENT_REQUIRED`, `NOT_FOUND`, `RATE_LIMITED`, `VALIDATION_ERROR`, `INTERNAL_ERROR`.
