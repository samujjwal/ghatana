# Aura API Contracts (GraphQL + REST)

## API Style

- **Public client-facing API:** GraphQL for flexible product, profile, recommendation, and community queries.
- **Internal service-to-service APIs:** REST or gRPC where latency and clear boundaries matter.
- **Brand analytics API:** REST with API key authentication (Phase 4).

Cross-process async communication referenced by these contracts must publish through AEP, and managed persistence behind these contracts must rely on Data Cloud-managed datasets or approved Data Cloud plugins.

---

## Authentication & Authorization

| Concern               | Approach                                                                                                                                                                                    |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Authentication        | JWT issued by shared auth services. Every request to the API carries a `Bearer` token in the `Authorization` header.                                                                       |
| Authorization         | Scoped per-user. Users can only access and modify their own data.                                                                                                                           |
| Core service data     | Declared profile data, on-platform interactions, recommendation history, safety feedback, and data-rights actions are processed as part of operating the Aura service for the authenticated user. |
| Consent enforcement   | Optional imports and high-sensitivity enrichment validate a current, in-scope, non-revoked consent record before proceeding. If consent is absent, the mutation returns a `CONSENT_REQUIRED` error. |
| Brand analytics       | API key authentication. Keys are tenant-scoped and grant read-only access to anonymized aggregate data only.                                                                                |
| Internal service auth | Service-to-service calls use shared security patterns with short-lived internal tokens (mTLS or shared secret, environment-dependent).                                                      |

**Security principles:**

- All endpoints served over HTTPS only.
- Input validation on all parameters at the API boundary.
- PII is never logged in plaintext. Log user IDs, not email addresses or profile details.
- Rate limiting applied per user and per IP at the API gateway.
- CORS restricted to known Aura origins.

---

## GraphQL Schema

```graphql
scalar JSON

type Query {
  me: User
  feed(input: FeedInput!): FeedPage!
  product(id: ID!): Product
  compareProducts(ids: [ID!]!): [ProductComparison!]!
  recommendations(input: RecommendationInput!): [Recommendation!]!
  recommendationHistory(input: RecommendationHistoryInput): RecommendationHistoryPage!
  searchProducts(
    query: String!
    filters: ProductFilterInput
  ): ProductSearchResult!
}

type Mutation {
  updateProfile(input: UpdateProfileInput!): UserProfile!
  overrideProfileAttribute(input: OverrideProfileAttributeInput!): UserProfile!
  deleteProfileAttribute(key: String!, origin: ProfileDataOrigin!): UserProfile!
  saveProduct(productId: ID!): SaveResult!
  unsaveProduct(productId: ID!): SaveResult!
  submitFeedback(input: FeedbackInput!): FeedbackResult!
  submitRecommendationOutcome(input: RecommendationOutcomeInput!): OutcomeResult!
  requestDataExport: DataExportRequest!
  grantConsent(input: ConsentInput!): Consent!
  revokeConsent(scope: ConsentScope!): Consent!
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
  skinConcerns: [String!]!
  styleArchetype: String
  secondaryStyleArchetype: String
  allergies: [String!]!
  ethicalPreferences: [String!]!
  spendingPreference: SpendingPreference
  declaredAttributes: [ProfileAttribute!]!
  inferredAttributes: [ProfileAttribute!]!
  importedAttributes: [ProfileAttribute!]!
  profileCompleteness: Float!
}

type ProfileAttribute {
  key: String!
  value: JSON!
  origin: ProfileDataOrigin!
  confidence: Float
  sourceScope: ConsentScope
  updatedAt: String!
}

type SpendingPreference {
  min: Float
  max: Float
  currency: String
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
  canonicalDepth: Int
  undertone: String
  finish: String
  coverage: String
  hexCode: String
  mappingConfidence: Float
}

type Ingredient {
  id: ID!
  inciName: String!
  commonName: String
  functions: [String!]!
  riskFlags: [String!]!
}

type Recommendation {
  id: ID!
  product: Product!
  shadeSuggestion: ProductShade
  score: Float!
  confidence: Float!
  confidenceLabel: String!
  reasons: [RecommendationReason!]!
  explanation: String!
  trustFlags: [RecommendationTrustFlag!]!
  evidence: [RecommendationEvidence!]!
  servedAt: String!
}

type RecommendationReason {
  code: RecommendationReasonCode!
  weight: Float
  detail: String
}

type RecommendationEvidence {
  label: String!
  value: String!
  sourceType: String
  sourceRef: String
}

enum RecommendationReasonCode {
  SHADE_MATCH
  INGREDIENT_SAFE
  ALLERGEN_ALERT
  IRRITANT_RISK
  DUPLICATE_ACTIVE
  OWNS_SIMILAR
  PRICE_FIT
  ETHICAL_MATCH
  COMMUNITY_MATCH
  COMMUNITY_FLAG
}

enum RecommendationTrustFlag {
  LOW_CONFIDENCE
  LOW_SHADE_CONFIDENCE
  STALE_PRICE
  PARTIAL_INGREDIENTS
  COMMUNITY_CAUTION
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
  trustFlags: [RecommendationTrustFlag!]!
}

type Consent {
  scope: ConsentScope!
  granted: Boolean!
  grantedAt: String
  revokedAt: String
}

type Brand {
  id: ID!
  name: String!
}

type ProductSource {
  merchantName: String
  sourceName: String!
  sourceUrl: String
  affiliateUrl: String
  price: Float
  currency: String
  availabilityStatus: String
  isAffiliate: Boolean!
  freshnessScore: Float
  lastVerifiedAt: String
  fetchedAt: String!
}

type SaveResult {
  success: Boolean!
}

type FeedbackResult {
  success: Boolean!
}

type OutcomeResult {
  success: Boolean!
}

type DataExportRequest {
  id: ID!
  status: String!
  requestedAt: String!
  completedAt: String
}

type DeleteAccountResult {
  success: Boolean!
}

type ProductSearchResult {
  products: [Product!]!
  total: Int!
}

type RecommendationHistoryPage {
  items: [Recommendation!]!
  nextCursor: String
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
  skinConcerns: [String!]
  styleArchetype: String
  secondaryStyleArchetype: String
  allergies: [String!]
  ethicalPreferences: [String!]
  spendingPreference: SpendingPreferenceInput
}

input OverrideProfileAttributeInput {
  key: String!
  value: JSON!
  origin: ProfileDataOrigin!
}

input FeedbackInput {
  productId: ID!
  recommendationId: ID
  feedbackType: FeedbackType!
  rating: Int
}

enum FeedbackType {
  VIEW
  CLICK
  SAVE
  DISMISS
  PURCHASE
  RATING
  HELPFUL
  NOT_HELPFUL
}

input ConsentInput {
  scope: ConsentScope!
}

input ProductFilterInput {
  category: String
  priceMax: Float
  ethicalFilters: [String!]
}

input RecommendationOutcomeInput {
  productId: ID!
  recommendationId: ID
  outcomeType: RecommendationOutcomeType!
  shadeFeedback: ShadeFeedback
  notes: String
}

input ShadeFeedback {
  matchedShadeId: ID
  result: ShadeFeedbackResult!
}

input SpendingPreferenceInput {
  min: Float
  max: Float
  currency: String
}

input RecommendationHistoryInput {
  cursor: String
  limit: Int
}

enum ConsentScope {
  PURCHASE_HISTORY_IMPORT
  EMAIL_RECEIPT_IMPORT
  WEARABLE_BIO_SIGNALS
  SELFIE_SHADE_ANALYSIS
  PUBLIC_PROFILE_SHARING
  COMMUNITY_CONTRIBUTION
}

enum ProfileDataOrigin {
  DECLARED
  INFERRED
  IMPORTED
}

enum RecommendationOutcomeType {
  PURCHASE_CONFIRMED
  RETURN_REPORTED
  ADVERSE_REACTION_REPORTED
  SHADE_FEEDBACK
}

enum ShadeFeedbackResult {
  MATCHED
  TOO_LIGHT
  TOO_DARK
  WRONG_UNDERTONE
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

**Response:** Array of Recommendation objects, each with score, confidence, reason codes, evidence, explanation, and trust flags.

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

### POST /v1/recommendations/outcomes

Captures post-purchase and post-use outcomes used for quality, safety, and return-reduction measurement.

**Auth:** Bearer token required.

**Request body:**

```json
{
  "productId": "prd_001",
  "recommendationId": "rec_202",
  "outcomeType": "ADVERSE_REACTION_REPORTED",
  "notes": "Caused stinging after two uses"
}
```

---

### POST /v1/consents

Creates or updates a user consent.

**Auth:** Bearer token required.

**Request body:**

```json
{
  "scope": "WEARABLE_BIO_SIGNALS"
}
```

---

### POST /v1/data-export

Requests a full export of the user's declared, inferred, imported, interaction, and recommendation history data.

**Auth:** Bearer token required.

---

### DELETE /v1/account

Initiates account deletion. All user data is permanently removed per the data retention policy.

**Auth:** Bearer token required. Requires re-authentication (password confirmation or re-login).

---

## Contract Principles

1. **Every recommendation response includes explanations.** Score alone is never returned without reason codes and human-readable text.
2. **Every recommendation response includes confidence, trust flags, and evidence.** The client can explain uncertainty and data quality without hidden logic.
3. **Core service operations are not blocked by optional-integration consent.** Profile updates, saves, on-platform feedback, outcome reporting, export, and deletion operate for authenticated users.
4. **Optional imports and high-sensitivity enrichment require consent validation.** The API returns `CONSENT_REQUIRED` if a valid, in-scope consent record does not exist.
5. **Stable IDs and explicit versioning** in all internal APIs. Breaking changes require a new version prefix (`/v2/`).
6. **Affiliate relationships are disclosed** in the API response. Merchant-level source fields expose whether any purchase link carries an affiliate relationship.
7. **Error responses follow a consistent structure** with `code`, `message`, and optional `details` field.

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
