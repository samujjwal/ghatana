# Aura Complete Database Schema (Postgres + Prisma)

## Core Modeling Principles

- Separate declared, inferred, and imported user data.
- Normalize products, ingredients, shades, brands, and sources.
- Preserve recommendation auditability.
- Track consent per optional integration and per high-sensitivity processing category.
- Do not gate core service operations behind optional-integration consent.

## Prisma Schema Proposal

```prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

enum ProfileDataOrigin {
  DECLARED
  INFERRED
  IMPORTED
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

enum FeedbackEventType {
  VIEW
  CLICK
  SAVE
  DISMISS
  PURCHASE
  HELPFUL
  NOT_HELPFUL
  RETURN_REPORTED
  ADVERSE_REACTION_REPORTED
  SHADE_FEEDBACK
}

model User {
  id              String              @id @default(cuid())
  email           String?             @unique
  createdAt       DateTime            @default(now())
  updatedAt       DateTime            @updatedAt
  profile         UserProfile?
  consents        Consent[]
  dataExports     DataExportRequest[]
  recommendations Recommendation[]
  feedbackEvents  FeedbackEvent[]
  ownedProducts   UserOwnedProduct[]
}

model UserProfile {
  id                String             @id @default(cuid())
  userId            String             @unique
  skinType          String?
  undertone         String?
  skinTone          String?
  skinConcerns      Json?
  styleArchetype    String?
  secondaryStyleArchetype String?
  colorPreferences  Json?
  ethicalPrefs      Json?
  allergies         Json?
  healthGoals       Json?
  spendingPreference Json?
  user              User               @relation(fields: [userId], references: [id], onDelete: Cascade)
  attributes        UserProfileAttribute[]
}

model UserProfileAttribute {
  id          String            @id @default(cuid())
  profileId   String
  key         String
  value       Json
  origin      ProfileDataOrigin
  sourceScope String?
  confidence  Float?
  createdAt   DateTime          @default(now())
  updatedAt   DateTime          @updatedAt
  profile     UserProfile       @relation(fields: [profileId], references: [id], onDelete: Cascade)

  @@index([profileId, key])
}

model Brand {
  id          String    @id @default(cuid())
  name        String    @unique
  metadata    Json?
  products    Product[]
}

model Product {
  id              String              @id @default(cuid())
  externalRef     String?             @unique
  brandId         String
  name            String
  category        String
  subcategory     String?
  description     String?
  priceMin        Decimal?            @db.Decimal(10,2)
  priceMax        Decimal?            @db.Decimal(10,2)
  currency        String?
  metadata        Json?
  brand           Brand               @relation(fields: [brandId], references: [id])
  ingredients     ProductIngredient[]
  shades          ProductShade[]
  sources         ProductSource[]
  recommendations Recommendation[]
  reviews         Review[]
  ownedByUsers    UserOwnedProduct[]
}

model Ingredient {
  id              String              @id @default(cuid())
  inciName        String              @unique
  commonName      String?
  functions       Json?
  riskFlags       Json?
  metadata        Json?
  productLinks    ProductIngredient[]
}

model ProductIngredient {
  productId       String
  ingredientId    String
  position        Int?
  concentration   String?
  product         Product             @relation(fields: [productId], references: [id], onDelete: Cascade)
  ingredient      Ingredient          @relation(fields: [ingredientId], references: [id], onDelete: Cascade)

  @@id([productId, ingredientId])
}

model ProductShade {
  id              String              @id @default(cuid())
  productId       String
  name            String
  hexCode         String?
  canonicalDepth  Int?
  undertone       String?
  finish          String?
  coverage        String?
  mappingConfidence Float?
  ontologyVersion String?
  metadata        Json?
  product         Product             @relation(fields: [productId], references: [id], onDelete: Cascade)
}

model ProductSource {
  id              String              @id @default(cuid())
  productId       String
  sourceType      String
  sourceName      String
  merchantName    String?
  sourceUrl       String?
  affiliateUrl    String?
  price           Decimal?            @db.Decimal(10,2)
  currency        String?
  availabilityStatus String?
  isAffiliate     Boolean             @default(false)
  freshnessScore  Float?
  lastVerifiedAt  DateTime?
  payload         Json?
  fetchedAt       DateTime            @default(now())
  product         Product             @relation(fields: [productId], references: [id], onDelete: Cascade)

  @@index([productId, sourceType])
}

model Review {
  id              String              @id @default(cuid())
  productId       String
  sourceType      String
  authorHash      String?
  rating          Float?
  title           String?
  body            String?
  sentimentScore  Float?
  metadata        Json?
  createdAt       DateTime?
  product         Product             @relation(fields: [productId], references: [id], onDelete: Cascade)
}

model Consent {
  id              String              @id @default(cuid())
  userId          String
  scope           String
  category        String?
  granted         Boolean
  grantedAt       DateTime            @default(now())
  revokedAt       DateTime?
  policyVersion   String?
  metadata        Json?
  user            User                @relation(fields: [userId], references: [id], onDelete: Cascade)

  @@index([userId, scope])
}

model Recommendation {
  id              String                     @id @default(cuid())
  userId          String
  productId       String
  score           Float
  confidence      Float
  trustFlags      RecommendationTrustFlag[]
  explanation     Json
  evidence        Json?
  profileSnapshot Json
  requestContext  Json?
  modelVersion    String
  createdAt       DateTime                   @default(now())
  servedAt        DateTime?
  user            User                       @relation(fields: [userId], references: [id], onDelete: Cascade)
  product         Product                    @relation(fields: [productId], references: [id], onDelete: Cascade)
  reasons         RecommendationReason[]
}

model RecommendationReason {
  id                String                    @id @default(cuid())
  recommendationId  String
  code              RecommendationReasonCode
  weight            Float?
  details           Json?
  recommendation    Recommendation            @relation(fields: [recommendationId], references: [id], onDelete: Cascade)

  @@index([recommendationId, code])
}

model FeedbackEvent {
  id              String              @id @default(cuid())
  userId          String
  productId       String?
  recommendationId String?
  eventType       FeedbackEventType
  value           String?
  decisionLatencyMs Int?
  metadata        Json?
  createdAt       DateTime            @default(now())
  user            User                @relation(fields: [userId], references: [id], onDelete: Cascade)

  @@index([userId, eventType, createdAt])
}

model DataExportRequest {
  id              String              @id @default(cuid())
  userId          String
  status          String
  requestedAt     DateTime            @default(now())
  completedAt     DateTime?
  format          String              @default("json")
  artifactUrl     String?
  user            User                @relation(fields: [userId], references: [id], onDelete: Cascade)

  @@index([userId, requestedAt])
}

model UserOwnedProduct {
  userId          String
  productId       String
  source          String
  acquiredAt      DateTime?
  metadata        Json?
  user            User                @relation(fields: [userId], references: [id], onDelete: Cascade)
  product         Product             @relation(fields: [productId], references: [id], onDelete: Cascade)

  @@id([userId, productId])
}
```

## Notes

- **Receipt import and wearable data:** Keep in separate bounded contexts if compliance or
  sensitivity requirements increase. Use separate Prisma schemas or databases with explicit
  cross-context access policies.

---

## Phase 2+ Extension: pgvector Embedding Tables

When semantic retrieval becomes core to the recommendation pipeline, add pgvector-backed embedding
tables. These use the `Unsupported("vector(1536)")` type in Prisma (raw PostgreSQL `vector` type
from the `pgvector` extension).

```prisma
// Enable pgvector extension via a migration:
// CREATE EXTENSION IF NOT EXISTS vector;

model ProductEmbedding {
  id            String   @id @default(cuid())
  productId     String   @unique
  embedding     Unsupported("vector(1536)")
  modelVersion  String                         // e.g. "text-embedding-3-small-v1"
  updatedAt     DateTime @updatedAt
  product       Product  @relation(fields: [productId], references: [id], onDelete: Cascade)

  @@index([productId])
}

model IngredientEmbedding {
  id            String     @id @default(cuid())
  ingredientId  String     @unique
  embedding     Unsupported("vector(1536)")
  modelVersion  String
  updatedAt     DateTime   @updatedAt
  ingredient    Ingredient @relation(fields: [ingredientId], references: [id], onDelete: Cascade)

  @@index([ingredientId])
}

model UserPreferenceEmbedding {
  id            String   @id @default(cuid())
  userId        String   @unique
  embedding     Unsupported("vector(1536)")
  modelVersion  String
  updatedAt     DateTime @updatedAt
  user          User     @relation(fields: [userId], references: [id], onDelete: Cascade)

  @@index([userId])
}
```

### Usage Pattern

Raw SQL queries are required for vector operations; Prisma does not natively support vector
operators yet. Issue queries using `db.$queryRaw`:

```sql
-- Find top-20 products semantically similar to a given product
SELECT p.id, p.name, 1 - (pe.embedding <=> $1::vector) AS similarity
FROM "ProductEmbedding" pe
JOIN "Product" p ON p.id = pe."productId"
ORDER BY pe.embedding <=> $1::vector
LIMIT 20;

-- Find products compatible with a user's preference vector
SELECT p.id, p.name, 1 - (pe.embedding <=> $1::vector) AS similarity
FROM "ProductEmbedding" pe
JOIN "Product" p ON p.id = pe."productId"
ORDER BY pe.embedding <=> $1::vector
LIMIT 50;
```

### Scaling Consideration

At > 1 M product embeddings, switch from pgvector's exact search to the HNSW index for
sub-millisecond approximate nearest-neighbor (ANN) retrieval:

```sql
CREATE INDEX ON "ProductEmbedding" USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);
```

At > 10 M records or when multi-tenant isolation at the vector layer is required, migrate to a
dedicated vector database (Pinecone, Weaviate, or Qdrant).
