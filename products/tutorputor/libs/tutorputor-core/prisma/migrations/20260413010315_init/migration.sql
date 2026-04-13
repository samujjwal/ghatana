-- CreateEnum
CREATE TYPE "ModuleDomain" AS ENUM ('MATH', 'SCIENCE', 'TECH');

-- CreateEnum
CREATE TYPE "ModuleDifficulty" AS ENUM ('INTRO', 'INTERMEDIATE', 'ADVANCED');

-- CreateEnum
CREATE TYPE "ModuleStatus" AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED');

-- CreateEnum
CREATE TYPE "EnrollmentStatus" AS ENUM ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED');

-- CreateEnum
CREATE TYPE "AssessmentType" AS ENUM ('QUIZ', 'PROJECT', 'SIMULATION');

-- CreateEnum
CREATE TYPE "AssessmentStatus" AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED');

-- CreateEnum
CREATE TYPE "AssessmentAttemptStatus" AS ENUM ('IN_PROGRESS', 'SUBMITTED', 'GRADED', 'EXPIRED');

-- CreateEnum
CREATE TYPE "LearnerDifficultyPreference" AS ENUM ('BEGINNER', 'EASY', 'MEDIUM', 'HARD', 'EXPERT');

-- CreateEnum
CREATE TYPE "LearnerModalityPreference" AS ENUM ('VISUAL', 'AUDITORY', 'KINESTHETIC', 'READING', 'MIXED');

-- CreateEnum
CREATE TYPE "LearnerPacingPreference" AS ENUM ('SELF_PACED', 'GUIDED', 'ADAPTIVE', 'INTENSIVE');

-- CreateEnum
CREATE TYPE "KnowledgeGapSeverity" AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');

-- CreateEnum
CREATE TYPE "KnowledgeGapDetectionMethod" AS ENUM ('ASSESSMENT', 'PREREQUISITE_CHECK', 'ADAPTIVE_ANALYSIS', 'LEARNER_REPORTED', 'AI_PREDICTION');

-- CreateEnum
CREATE TYPE "EvidenceSourceType" AS ENUM ('OPENSTAX', 'KHAN_ACADEMY', 'WIKIPEDIA', 'PEER_REVIEWED_JOURNAL', 'TEXTBOOK', 'CURRICULUM_STANDARD', 'DOMAIN_EXPERT', 'SIMULATION_RESULT', 'CALCULATION');

-- CreateEnum
CREATE TYPE "SupportKind" AS ENUM ('SUPPORTS', 'CONTRADICTS', 'NEUTRAL', 'PARTIALLY_SUPPORTS');

-- CreateEnum
CREATE TYPE "EvidenceFreshnessStatus" AS ENUM ('CURRENT', 'STALE', 'EXPIRED', 'UNKNOWN');

-- CreateEnum
CREATE TYPE "EvidenceVerificationState" AS ENUM ('UNVERIFIED', 'VERIFIED', 'DISPUTED', 'FAILED_VERIFICATION');

-- CreateEnum
CREATE TYPE "LearnerPathwayStatus" AS ENUM ('DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED', 'ABANDONED');

-- CreateEnum
CREATE TYPE "ListingStatus" AS ENUM ('DRAFT', 'ACTIVE', 'ARCHIVED');

-- CreateEnum
CREATE TYPE "ListingVisibility" AS ENUM ('PUBLIC', 'PRIVATE');

-- CreateEnum
CREATE TYPE "PathwayStatus" AS ENUM ('ACTIVE', 'COMPLETED', 'PAUSED');

-- CreateEnum
CREATE TYPE "ThreadStatus" AS ENUM ('OPEN', 'RESOLVED', 'CLOSED');

-- CreateEnum
CREATE TYPE "HelpRequestStatus" AS ENUM ('PENDING', 'ANSWERED', 'ESCALATED');

-- CreateEnum
CREATE TYPE "CheckoutStatus" AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED');

-- CreateEnum
CREATE TYPE "SubscriptionTier" AS ENUM ('FREE', 'STARTER', 'PROFESSIONAL', 'ENTERPRISE');

-- CreateEnum
CREATE TYPE "SubscriptionStatus" AS ENUM ('ACTIVE', 'PAST_DUE', 'CANCELED', 'INCOMPLETE', 'INCOMPLETE_EXPIRED', 'TRIALING', 'UNPAID');

-- CreateEnum
CREATE TYPE "BillingInterval" AS ENUM ('MONTHLY', 'QUARTERLY', 'ANNUAL');

-- CreateEnum
CREATE TYPE "StudyGroupVisibility" AS ENUM ('PUBLIC', 'PRIVATE', 'CLASSROOM_ONLY');

-- CreateEnum
CREATE TYPE "StudyGroupStatus" AS ENUM ('ACTIVE', 'ARCHIVED', 'SUSPENDED');

-- CreateEnum
CREATE TYPE "StudyGroupRole" AS ENUM ('OWNER', 'ADMIN', 'MODERATOR', 'MEMBER');

-- CreateEnum
CREATE TYPE "JoinRequestStatus" AS ENUM ('PENDING', 'APPROVED', 'REJECTED');

-- CreateEnum
CREATE TYPE "InviteStatus" AS ENUM ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED');

-- CreateEnum
CREATE TYPE "StudySessionType" AS ENUM ('DISCUSSION', 'REVIEW', 'QUIZ_PRACTICE', 'VIDEO_CALL', 'COLLABORATIVE');

-- CreateEnum
CREATE TYPE "StudySessionStatus" AS ENUM ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');

-- CreateEnum
CREATE TYPE "RsvpStatus" AS ENUM ('ATTENDING', 'MAYBE', 'NOT_ATTENDING');

-- CreateEnum
CREATE TYPE "ForumScope" AS ENUM ('GLOBAL', 'STUDY_GROUP', 'CLASSROOM', 'MODULE');

-- CreateEnum
CREATE TYPE "ForumStatus" AS ENUM ('ACTIVE', 'ARCHIVED', 'LOCKED');

-- CreateEnum
CREATE TYPE "TopicStatus" AS ENUM ('DRAFT', 'PENDING', 'PUBLISHED', 'HIDDEN', 'DELETED');

-- CreateEnum
CREATE TYPE "ReactionType" AS ENUM ('LIKE', 'HELPFUL', 'INSIGHTFUL', 'QUESTION', 'CELEBRATE');

-- CreateEnum
CREATE TYPE "TutorStatus" AS ENUM ('ACTIVE', 'PAUSED', 'INACTIVE');

-- CreateEnum
CREATE TYPE "TutoringRequestStatus" AS ENUM ('OPEN', 'MATCHED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'EXPIRED');

-- CreateEnum
CREATE TYPE "TutoringSessionStatus" AS ENUM ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'NO_SHOW', 'CANCELLED');

-- CreateEnum
CREATE TYPE "ChatRoomType" AS ENUM ('DIRECT', 'STUDY_GROUP', 'CLASSROOM', 'TUTORING', 'SUPPORT');

-- CreateEnum
CREATE TYPE "ChatMessageType" AS ENUM ('TEXT', 'IMAGE', 'FILE', 'CODE', 'MATH', 'QUIZ_SHARE', 'SYSTEM');

-- CreateEnum
CREATE TYPE "SocialActivityType" AS ENUM ('JOINED_GROUP', 'CREATED_TOPIC', 'REPLIED_TOPIC', 'LIKED_POST', 'SCHEDULED_SESSION', 'COMPLETED_SESSION', 'SHARED_NOTE', 'EARNED_BADGE', 'HELPED_PEER');

-- CreateEnum
CREATE TYPE "SocialNotificationType" AS ENUM ('GROUP_INVITE', 'GROUP_JOIN_REQUEST', 'GROUP_MESSAGE', 'TOPIC_REPLY', 'POST_MENTION', 'POST_REACTION', 'TUTORING_REQUEST', 'SESSION_REMINDER', 'REVIEW_RECEIVED');

-- CreateEnum
CREATE TYPE "VRLabCategory" AS ENUM ('CHEMISTRY', 'PHYSICS', 'BIOLOGY', 'ASTRONOMY', 'ENGINEERING', 'ANATOMY', 'GEOLOGY', 'HISTORY', 'ART', 'MATHEMATICS');

-- CreateEnum
CREATE TYPE "VRLabDifficulty" AS ENUM ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT');

-- CreateEnum
CREATE TYPE "VRSessionStatus" AS ENUM ('INITIALIZING', 'LOADING', 'ACTIVE', 'PAUSED', 'COMPLETED', 'FAILED');

-- CreateEnum
CREATE TYPE "VRMultiplayerStatus" AS ENUM ('LOBBY', 'ACTIVE', 'ENDED');

-- CreateEnum
CREATE TYPE "CurriculumLevel" AS ENUM ('FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED', 'RESEARCH');

-- CreateEnum
CREATE TYPE "SimulationDomain" AS ENUM ('CS_DISCRETE', 'PHYSICS', 'CHEMISTRY', 'BIOLOGY', 'MEDICINE', 'ECONOMICS', 'ENGINEERING', 'MATHEMATICS');

-- CreateEnum
CREATE TYPE "ContentStatus" AS ENUM ('PUBLISHED', 'DRAFT');

-- CreateEnum
CREATE TYPE "SimulationTemplateDifficulty" AS ENUM ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT');

-- CreateEnum
CREATE TYPE "SimulationTemplateLicense" AS ENUM ('FREE', 'CC_BY', 'CC_BY_SA', 'CC_BY_NC', 'PROPRIETARY');

-- CreateEnum
CREATE TYPE "SimulationTemplateStatus" AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED');

-- CreateEnum
CREATE TYPE "IdentityProviderType" AS ENUM ('OIDC', 'SAML');

-- CreateEnum
CREATE TYPE "SsoProviderStatus" AS ENUM ('ACTIVE', 'INACTIVE', 'ERROR', 'PENDING_VERIFICATION');

-- CreateEnum
CREATE TYPE "AssetType" AS ENUM ('MODEL_3D', 'TEXTURE', 'AUDIO', 'VIDEO', 'DOCUMENT', 'IMAGE', 'OTHER');

-- CreateEnum
CREATE TYPE "AssetStatus" AS ENUM ('UPLOADING', 'PROCESSING', 'READY', 'FAILED', 'ARCHIVED');

-- CreateEnum
CREATE TYPE "ExperienceStatus" AS ENUM ('DRAFT', 'REVIEW', 'PUBLISHED', 'ARCHIVED');

-- CreateEnum
CREATE TYPE "ContentStudioBloomLevel" AS ENUM ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE', 'EVALUATE', 'CREATE');

-- CreateEnum
CREATE TYPE "ValidationStatus" AS ENUM ('PASS', 'WARN', 'FAIL');

-- CreateEnum
CREATE TYPE "AIOperation" AS ENUM ('GENERATE_EXPERIENCE', 'VALIDATE', 'IMPROVE', 'EXPAND_GRADE', 'GENERATE_SIMULATION', 'GENERATE_ASSET');

-- CreateEnum
CREATE TYPE "GradeRange" AS ENUM ('K_2', 'GRADE_3_5', 'GRADE_6_8', 'GRADE_9_12', 'UNDERGRADUATE', 'GRADUATE');

-- CreateEnum
CREATE TYPE "RigorLevel" AS ENUM ('OBSERVATION', 'QUALITATIVE', 'QUANTITATIVE');

-- CreateEnum
CREATE TYPE "MathLevel" AS ENUM ('NONE', 'ARITHMETIC', 'ALGEBRA', 'CALCULUS');

-- CreateEnum
CREATE TYPE "ScaffoldingLevel" AS ENUM ('HIGH', 'MEDIUM', 'LOW');

-- CreateEnum
CREATE TYPE "AuthorType" AS ENUM ('HUMAN', 'AI_ASSISTED', 'AI_GENERATED');

-- CreateEnum
CREATE TYPE "ExperienceEventType" AS ENUM ('CREATED', 'UPDATED', 'VALIDATED', 'PUBLISHED', 'UNPUBLISHED', 'ARCHIVED', 'CONTENT_CHANGED', 'CLAIMS_GENERATED', 'GRADE_ADAPTED', 'REFINED', 'REVIEW_SUBMITTED', 'REVIEW_DECISION', 'SIMULATION_LINKED', 'SIMULATION_UNLINKED', 'ANALYTICS_VIEWED', 'ANALYTICS_UPDATED');

-- CreateEnum
CREATE TYPE "SimulationManifestStatus" AS ENUM ('DRAFT', 'REVIEW', 'ACTIVE', 'DEPRECATED');

-- CreateEnum
CREATE TYPE "ReviewerRole" AS ENUM ('SME', 'PEDAGOGY', 'SAFETY', 'ADMIN');

-- CreateEnum
CREATE TYPE "ReviewDecisionType" AS ENUM ('APPROVE', 'REJECT', 'REQUEST_CHANGES', 'ESCALATE');

-- CreateEnum
CREATE TYPE "ContentAssetType" AS ENUM ('EXPLAINER', 'MODULE', 'EXAMPLE_SET', 'SIMULATION', 'ANIMATION', 'ASSESSMENT', 'PATHWAY', 'REFERENCE_PACK');

-- CreateEnum
CREATE TYPE "ContentAssetStatus" AS ENUM ('DRAFT', 'VALIDATING', 'REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED');

-- CreateEnum
CREATE TYPE "ContentBlockType" AS ENUM ('TEXT_EXPLAINER', 'WORKED_EXAMPLE', 'DATA_TABLE', 'VISUAL_SEQUENCE', 'SIMULATION_ENTRY', 'ANIMATION_ENTRY', 'QUESTION_SET', 'TASK', 'REFLECTION', 'HINT', 'TUTOR_PROMPT', 'EVIDENCE_CAPTURE');

-- CreateEnum
CREATE TYPE "ArtifactManifestType" AS ENUM ('WORKED_EXAMPLE', 'SIMULATION', 'ANIMATION', 'ASSESSMENT');

-- CreateEnum
CREATE TYPE "ChunkSource" AS ENUM ('BLOCK', 'CLAIM', 'MANIFEST', 'METADATA');

-- CreateEnum
CREATE TYPE "EmbeddingStatus" AS ENUM ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'STALE');

-- CreateEnum
CREATE TYPE "RecommendationEdgeType" AS ENUM ('PREREQUISITE', 'FOLLOW_UP', 'RELATED', 'ALTERNATIVE', 'DEEPER_DIVE');

-- CreateEnum
CREATE TYPE "RecommendationSource" AS ENUM ('RULE_BASED', 'SEMANTIC', 'OUTCOME_AWARE', 'MANUAL');

-- CreateEnum
CREATE TYPE "GenerationRequestStatus" AS ENUM ('DRAFT', 'PLANNING', 'PLANNED', 'EXECUTING', 'COMPLETED', 'FAILED', 'CANCELLED');

-- CreateEnum
CREATE TYPE "GenerationJobStatus" AS ENUM ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED');

-- CreateEnum
CREATE TYPE "GenerationJobType" AS ENUM ('CLAIM', 'EXPLAINER', 'WORKED_EXAMPLE', 'SIMULATION', 'ANIMATION', 'ASSESSMENT', 'EVALUATION');

-- CreateEnum
CREATE TYPE "ReviewPath" AS ENUM ('AUTO_PUBLISH', 'HUMAN_REVIEW', 'EXPERT_REVIEW');

-- CreateEnum
CREATE TYPE "RiskLevel" AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');

-- CreateEnum
CREATE TYPE "EvaluationStatus" AS ENUM ('PENDING', 'RUNNING', 'PASSED', 'FAILED', 'SKIPPED');

-- CreateEnum
CREATE TYPE "PublishRecommendation" AS ENUM ('AUTO_PUBLISH', 'MANUAL_REVIEW', 'BLOCK');

-- CreateEnum
CREATE TYPE "GenerationReviewDecisionStatus" AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'REGENERATION_REQUESTED');

-- CreateEnum
CREATE TYPE "ExplorerEventType" AS ENUM ('IMPRESSION', 'CLICK', 'QUERY_REFORMULATION', 'ASSET_START', 'ASSET_COMPLETE', 'NEXT_STEP_SELECT', 'RANKING_FEEDBACK');

-- CreateEnum
CREATE TYPE "RegenerationTrigger" AS ENUM ('POOR_DISCOVERY_PERFORMANCE', 'POOR_LEARNING_OUTCOMES', 'MISCONCEPTION_PATTERN', 'STALE_CURRICULUM', 'SAFETY_CONCERN', 'LOW_EVALUATION_SCORE', 'MANUAL_FLAGGED');

-- CreateEnum
CREATE TYPE "RegenerationCandidateStatus" AS ENUM ('OPEN', 'QUEUED', 'IN_PROGRESS', 'RESOLVED', 'DISMISSED');

-- CreateTable
CREATE TABLE "Module" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "domain" "ModuleDomain" NOT NULL,
    "difficulty" "ModuleDifficulty" NOT NULL,
    "estimatedTimeMinutes" INTEGER NOT NULL,
    "status" "ModuleStatus" NOT NULL DEFAULT 'PUBLISHED',
    "description" TEXT NOT NULL,
    "version" INTEGER NOT NULL DEFAULT 1,
    "authorId" TEXT,
    "updatedBy" TEXT,
    "publishedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Module_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ModuleTag" (
    "id" SERIAL NOT NULL,
    "moduleId" TEXT NOT NULL,
    "label" TEXT NOT NULL,

    CONSTRAINT "ModuleTag_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ModuleLearningObjective" (
    "id" SERIAL NOT NULL,
    "moduleId" TEXT NOT NULL,
    "label" TEXT NOT NULL,
    "taxonomyLevel" TEXT NOT NULL,

    CONSTRAINT "ModuleLearningObjective_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ModuleContentBlock" (
    "id" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "orderIndex" INTEGER NOT NULL,
    "blockType" TEXT NOT NULL,
    "payload" JSONB NOT NULL,

    CONSTRAINT "ModuleContentBlock_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ModulePrerequisite" (
    "moduleId" TEXT NOT NULL,
    "prerequisiteModuleId" TEXT NOT NULL,

    CONSTRAINT "ModulePrerequisite_pkey" PRIMARY KEY ("moduleId","prerequisiteModuleId")
);

-- CreateTable
CREATE TABLE "ModuleRevision" (
    "id" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "version" INTEGER NOT NULL,
    "snapshot" JSONB NOT NULL,
    "createdBy" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ModuleRevision_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Enrollment" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "status" "EnrollmentStatus" NOT NULL DEFAULT 'NOT_STARTED',
    "progressPercent" INTEGER NOT NULL DEFAULT 0,
    "startedAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "timeSpentSeconds" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Enrollment_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Assessment" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "type" "AssessmentType" NOT NULL DEFAULT 'QUIZ',
    "status" "AssessmentStatus" NOT NULL DEFAULT 'DRAFT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "passingScore" INTEGER NOT NULL DEFAULT 80,
    "attemptsAllowed" INTEGER,
    "timeLimitMinutes" INTEGER,
    "createdBy" TEXT NOT NULL,
    "updatedBy" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Assessment_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AssessmentObjective" (
    "id" TEXT NOT NULL,
    "assessmentId" TEXT NOT NULL,
    "label" TEXT NOT NULL,
    "taxonomyLevel" TEXT NOT NULL,

    CONSTRAINT "AssessmentObjective_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AssessmentItem" (
    "id" TEXT NOT NULL,
    "assessmentId" TEXT NOT NULL,
    "orderIndex" INTEGER NOT NULL,
    "itemType" TEXT NOT NULL,
    "prompt" TEXT NOT NULL,
    "stimulus" TEXT,
    "choices" JSONB,
    "modelAnswer" TEXT,
    "rubric" TEXT,
    "points" INTEGER NOT NULL DEFAULT 10,
    "metadata" JSONB,

    CONSTRAINT "AssessmentItem_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AssessmentAttempt" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "assessmentId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "status" "AssessmentAttemptStatus" NOT NULL DEFAULT 'IN_PROGRESS',
    "responses" JSONB,
    "scorePercent" INTEGER,
    "feedback" JSONB,
    "startedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "submittedAt" TIMESTAMP(3),
    "gradedAt" TIMESTAMP(3),
    "timeSpentSeconds" INTEGER,

    CONSTRAINT "AssessmentAttempt_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AssessmentDraft" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "createdBy" TEXT NOT NULL,
    "payload" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AssessmentDraft_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "LearningEvent" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "moduleId" TEXT,
    "eventType" TEXT NOT NULL,
    "payload" JSONB,
    "timestamp" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "LearningEvent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "MarketplaceListing" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "creatorId" TEXT NOT NULL,
    "priceCents" INTEGER NOT NULL DEFAULT 0,
    "status" "ListingStatus" NOT NULL DEFAULT 'DRAFT',
    "visibility" "ListingVisibility" NOT NULL DEFAULT 'PUBLIC',
    "publishedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "MarketplaceListing_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "LearningPath" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "goal" TEXT NOT NULL,
    "status" "PathwayStatus" NOT NULL DEFAULT 'ACTIVE',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "LearningPath_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "LearningPathNode" (
    "id" TEXT NOT NULL,
    "pathId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "orderIndex" INTEGER NOT NULL,
    "isOptional" BOOLEAN NOT NULL DEFAULT false,
    "completedAt" TIMESTAMP(3),

    CONSTRAINT "LearningPathNode_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Classroom" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "teacherId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Classroom_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ClassroomStudent" (
    "id" TEXT NOT NULL,
    "classroomId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "displayName" TEXT NOT NULL,
    "email" TEXT,
    "enrolledAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ClassroomStudent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ClassroomAssignment" (
    "id" TEXT NOT NULL,
    "classroomId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "assignedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "dueAt" TIMESTAMP(3),

    CONSTRAINT "ClassroomAssignment_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Thread" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT,
    "title" TEXT NOT NULL,
    "status" "ThreadStatus" NOT NULL DEFAULT 'OPEN',
    "authorId" TEXT NOT NULL,
    "authorName" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "resolvedAt" TIMESTAMP(3),

    CONSTRAINT "Thread_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Post" (
    "id" TEXT NOT NULL,
    "threadId" TEXT NOT NULL,
    "authorId" TEXT NOT NULL,
    "authorName" TEXT NOT NULL,
    "content" TEXT NOT NULL,
    "isAnswer" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Post_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "HelpRequest" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "question" TEXT NOT NULL,
    "status" "HelpRequestStatus" NOT NULL DEFAULT 'PENDING',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "answeredAt" TIMESTAMP(3),

    CONSTRAINT "HelpRequest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CheckoutSession" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "listingId" TEXT NOT NULL,
    "amountCents" INTEGER NOT NULL,
    "status" "CheckoutStatus" NOT NULL DEFAULT 'PENDING',
    "paymentUrl" TEXT,
    "successUrl" TEXT,
    "cancelUrl" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "completedAt" TIMESTAMP(3),

    CONSTRAINT "CheckoutSession_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Purchase" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "listingId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "amountCents" INTEGER NOT NULL,
    "purchasedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Purchase_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "StripeCustomer" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "stripeCustomerId" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "name" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "StripeCustomer_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Subscription" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "stripeCustomerId" TEXT NOT NULL,
    "stripeSubscriptionId" TEXT NOT NULL,
    "stripePriceId" TEXT NOT NULL,
    "tier" "SubscriptionTier" NOT NULL,
    "status" "SubscriptionStatus" NOT NULL,
    "billingInterval" "BillingInterval" NOT NULL,
    "currentPeriodStart" TIMESTAMP(3) NOT NULL,
    "currentPeriodEnd" TIMESTAMP(3) NOT NULL,
    "cancelAtPeriodEnd" BOOLEAN NOT NULL DEFAULT false,
    "canceledAt" TIMESTAMP(3),
    "trialStart" TIMESTAMP(3),
    "trialEnd" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Subscription_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PaymentMethod" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "stripeCustomerId" TEXT NOT NULL,
    "stripePaymentMethodId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "isDefault" BOOLEAN NOT NULL DEFAULT false,
    "lastFour" TEXT,
    "brand" TEXT,
    "expMonth" INTEGER,
    "expYear" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PaymentMethod_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Invoice" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "stripeCustomerId" TEXT NOT NULL,
    "subscriptionId" TEXT,
    "stripeInvoiceId" TEXT NOT NULL,
    "number" TEXT NOT NULL,
    "status" TEXT NOT NULL,
    "currency" TEXT NOT NULL,
    "subtotalCents" INTEGER NOT NULL,
    "taxCents" INTEGER NOT NULL,
    "totalCents" INTEGER NOT NULL,
    "amountPaidCents" INTEGER NOT NULL,
    "amountDueCents" INTEGER NOT NULL,
    "dueDate" TIMESTAMP(3) NOT NULL,
    "paidAt" TIMESTAMP(3),
    "hostedInvoiceUrl" TEXT,
    "invoicePdfUrl" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Invoice_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Transaction" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "invoiceId" TEXT,
    "paymentMethodId" TEXT,
    "stripePaymentIntentId" TEXT,
    "stripeChargeId" TEXT,
    "type" TEXT NOT NULL,
    "status" TEXT NOT NULL,
    "amountCents" INTEGER NOT NULL,
    "currency" TEXT NOT NULL,
    "failureReason" TEXT,
    "receiptUrl" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "processedAt" TIMESTAMP(3),

    CONSTRAINT "Transaction_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "WebhookEvent" (
    "id" TEXT NOT NULL,
    "stripeEventId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "data" JSONB NOT NULL,
    "processed" BOOLEAN NOT NULL DEFAULT false,
    "processedAt" TIMESTAMP(3),
    "error" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "WebhookEvent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "UsageSnapshot" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "subscriptionId" TEXT NOT NULL,
    "periodStart" TIMESTAMP(3) NOT NULL,
    "periodEnd" TIMESTAMP(3) NOT NULL,
    "users" INTEGER NOT NULL DEFAULT 0,
    "modules" INTEGER NOT NULL DEFAULT 0,
    "storageGB" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "classrooms" INTEGER NOT NULL DEFAULT 0,
    "vrSessions" INTEGER NOT NULL DEFAULT 0,
    "capturedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "UsageSnapshot_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "LTIPlatform" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "platformName" TEXT NOT NULL,
    "issuer" TEXT NOT NULL,
    "clientId" TEXT NOT NULL,
    "jwksUrl" TEXT NOT NULL,
    "authUrl" TEXT NOT NULL,
    "tokenUrl" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "LTIPlatform_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "StudyGroup" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "coverImageUrl" TEXT,
    "createdBy" TEXT NOT NULL,
    "visibility" "StudyGroupVisibility" NOT NULL DEFAULT 'PUBLIC',
    "maxMembers" INTEGER NOT NULL DEFAULT 50,
    "requireApproval" BOOLEAN NOT NULL DEFAULT false,
    "allowGuestView" BOOLEAN NOT NULL DEFAULT false,
    "subjects" TEXT NOT NULL,
    "modules" TEXT,
    "memberCount" INTEGER NOT NULL DEFAULT 1,
    "status" "StudyGroupStatus" NOT NULL DEFAULT 'ACTIVE',
    "lastActivityAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "archivedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "StudyGroup_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "StudyGroupMember" (
    "id" TEXT NOT NULL,
    "groupId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "role" "StudyGroupRole" NOT NULL DEFAULT 'MEMBER',
    "invitedBy" TEXT,
    "messagesCount" INTEGER NOT NULL DEFAULT 0,
    "notificationsEnabled" BOOLEAN NOT NULL DEFAULT true,
    "mutedUntil" TIMESTAMP(3),
    "lastActiveAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "joinedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "StudyGroupMember_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "StudyGroupJoinRequest" (
    "id" TEXT NOT NULL,
    "groupId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "message" TEXT,
    "status" "JoinRequestStatus" NOT NULL DEFAULT 'PENDING',
    "reviewedBy" TEXT,
    "rejectionReason" TEXT,
    "reviewedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "StudyGroupJoinRequest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "StudyGroupInvite" (
    "id" TEXT NOT NULL,
    "groupId" TEXT NOT NULL,
    "invitedEmail" TEXT NOT NULL,
    "invitedBy" TEXT NOT NULL,
    "status" "InviteStatus" NOT NULL DEFAULT 'PENDING',
    "acceptedAt" TIMESTAMP(3),
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "StudyGroupInvite_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "StudySession" (
    "id" TEXT NOT NULL,
    "groupId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "createdBy" TEXT NOT NULL,
    "scheduledAt" TIMESTAMP(3) NOT NULL,
    "duration" INTEGER NOT NULL,
    "timezone" TEXT NOT NULL DEFAULT 'UTC',
    "type" "StudySessionType" NOT NULL DEFAULT 'DISCUSSION',
    "meetingUrl" TEXT,
    "maxParticipants" INTEGER,
    "rsvpDeadline" TIMESTAMP(3),
    "moduleId" TEXT,
    "lessonIds" TEXT,
    "agenda" TEXT,
    "attachments" TEXT,
    "status" "StudySessionStatus" NOT NULL DEFAULT 'SCHEDULED',
    "startedAt" TIMESTAMP(3),
    "endedAt" TIMESTAMP(3),
    "notes" TEXT,
    "recordingUrl" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "StudySession_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SessionRsvp" (
    "id" TEXT NOT NULL,
    "sessionId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "status" "RsvpStatus" NOT NULL,
    "note" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SessionRsvp_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Forum" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "iconUrl" TEXT,
    "scope" "ForumScope" NOT NULL DEFAULT 'GLOBAL',
    "scopeId" TEXT,
    "allowAnonymousPosts" BOOLEAN NOT NULL DEFAULT false,
    "requireModeration" BOOLEAN NOT NULL DEFAULT false,
    "allowAttachments" BOOLEAN NOT NULL DEFAULT true,
    "allowPolls" BOOLEAN NOT NULL DEFAULT true,
    "categories" TEXT,
    "topicCount" INTEGER NOT NULL DEFAULT 0,
    "postCount" INTEGER NOT NULL DEFAULT 0,
    "lastPostAt" TIMESTAMP(3),
    "status" "ForumStatus" NOT NULL DEFAULT 'ACTIVE',
    "studyGroupId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Forum_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ForumTopic" (
    "id" TEXT NOT NULL,
    "forumId" TEXT NOT NULL,
    "categoryId" TEXT,
    "title" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "authorId" TEXT NOT NULL,
    "authorName" TEXT NOT NULL,
    "content" TEXT NOT NULL,
    "contentFormat" TEXT NOT NULL DEFAULT 'markdown',
    "attachments" TEXT,
    "viewCount" INTEGER NOT NULL DEFAULT 0,
    "replyCount" INTEGER NOT NULL DEFAULT 0,
    "likeCount" INTEGER NOT NULL DEFAULT 0,
    "isPinned" BOOLEAN NOT NULL DEFAULT false,
    "isLocked" BOOLEAN NOT NULL DEFAULT false,
    "isAnswered" BOOLEAN NOT NULL DEFAULT false,
    "answerId" TEXT,
    "status" "TopicStatus" NOT NULL DEFAULT 'PUBLISHED',
    "moderatedBy" TEXT,
    "moderatedAt" TIMESTAMP(3),
    "moderationNote" TEXT,
    "lastReplyAt" TIMESTAMP(3),
    "lastReplyBy" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ForumTopic_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ForumPost" (
    "id" TEXT NOT NULL,
    "topicId" TEXT NOT NULL,
    "authorId" TEXT NOT NULL,
    "authorName" TEXT NOT NULL,
    "isAnonymous" BOOLEAN NOT NULL DEFAULT false,
    "content" TEXT NOT NULL,
    "contentFormat" TEXT NOT NULL DEFAULT 'markdown',
    "attachments" TEXT,
    "parentId" TEXT,
    "depth" INTEGER NOT NULL DEFAULT 0,
    "likeCount" INTEGER NOT NULL DEFAULT 0,
    "isAcceptedAnswer" BOOLEAN NOT NULL DEFAULT false,
    "status" "TopicStatus" NOT NULL DEFAULT 'PUBLISHED',
    "moderatedBy" TEXT,
    "moderatedAt" TIMESTAMP(3),
    "isEdited" BOOLEAN NOT NULL DEFAULT false,
    "editedAt" TIMESTAMP(3),
    "editHistory" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ForumPost_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PostReaction" (
    "id" TEXT NOT NULL,
    "postId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "type" "ReactionType" NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PostReaction_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "TutorProfile" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "displayName" TEXT NOT NULL,
    "bio" TEXT NOT NULL,
    "avatarUrl" TEXT,
    "subjects" TEXT NOT NULL,
    "modules" TEXT,
    "qualifications" TEXT,
    "isAvailable" BOOLEAN NOT NULL DEFAULT true,
    "availabilitySchedule" TEXT,
    "timezone" TEXT NOT NULL DEFAULT 'UTC',
    "responseTime" TEXT NOT NULL DEFAULT 'Usually within 2 hours',
    "sessionTypes" TEXT NOT NULL,
    "maxSessionsPerWeek" INTEGER NOT NULL DEFAULT 5,
    "pricePerHour" INTEGER,
    "rating" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "reviewCount" INTEGER NOT NULL DEFAULT 0,
    "sessionsCompleted" INTEGER NOT NULL DEFAULT 0,
    "totalHelpedStudents" INTEGER NOT NULL DEFAULT 0,
    "status" "TutorStatus" NOT NULL DEFAULT 'ACTIVE',
    "verifiedAt" TIMESTAMP(3),
    "verifiedBy" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "TutorProfile_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "TutoringRequest" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "studentId" TEXT NOT NULL,
    "tutorId" TEXT,
    "subject" TEXT NOT NULL,
    "moduleId" TEXT,
    "lessonId" TEXT,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "attachments" TEXT,
    "preferredTypes" TEXT NOT NULL,
    "preferredTime" TIMESTAMP(3),
    "estimatedDuration" INTEGER NOT NULL DEFAULT 60,
    "urgency" TEXT NOT NULL DEFAULT 'medium',
    "status" "TutoringRequestStatus" NOT NULL DEFAULT 'OPEN',
    "acceptedAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "cancelledAt" TIMESTAMP(3),
    "cancellationReason" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "TutoringRequest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "TutoringSession" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "requestId" TEXT NOT NULL,
    "studentId" TEXT NOT NULL,
    "tutorId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "scheduledAt" TIMESTAMP(3) NOT NULL,
    "duration" INTEGER NOT NULL,
    "meetingUrl" TEXT,
    "moduleId" TEXT,
    "lessonId" TEXT,
    "notes" TEXT,
    "sharedResources" TEXT,
    "status" "TutoringSessionStatus" NOT NULL DEFAULT 'SCHEDULED',
    "startedAt" TIMESTAMP(3),
    "endedAt" TIMESTAMP(3),
    "actualDuration" INTEGER,
    "recordingUrl" TEXT,
    "transcriptUrl" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "TutoringSession_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "TutoringReview" (
    "id" TEXT NOT NULL,
    "sessionId" TEXT NOT NULL,
    "tutorId" TEXT NOT NULL,
    "reviewerId" TEXT NOT NULL,
    "rating" INTEGER NOT NULL,
    "helpfulness" INTEGER NOT NULL,
    "communication" INTEGER NOT NULL,
    "knowledge" INTEGER NOT NULL,
    "comment" TEXT,
    "privateNote" TEXT,
    "response" TEXT,
    "respondedAt" TIMESTAMP(3),
    "isVisible" BOOLEAN NOT NULL DEFAULT true,
    "moderatedBy" TEXT,
    "moderatedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "TutoringReview_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ChatRoom" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "type" "ChatRoomType" NOT NULL,
    "name" TEXT,
    "studyGroupId" TEXT,
    "tutoringSessionId" TEXT,
    "participants" TEXT NOT NULL,
    "maxParticipants" INTEGER,
    "isEncrypted" BOOLEAN NOT NULL DEFAULT false,
    "retentionDays" INTEGER NOT NULL DEFAULT 90,
    "messageCount" INTEGER NOT NULL DEFAULT 0,
    "lastMessageAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ChatRoom_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ChatMessage" (
    "id" TEXT NOT NULL,
    "roomId" TEXT NOT NULL,
    "senderId" TEXT NOT NULL,
    "senderName" TEXT NOT NULL,
    "type" "ChatMessageType" NOT NULL DEFAULT 'TEXT',
    "content" TEXT NOT NULL,
    "metadata" TEXT,
    "attachments" TEXT,
    "replyToId" TEXT,
    "reactions" TEXT,
    "status" TEXT NOT NULL DEFAULT 'sent',
    "editedAt" TIMESTAMP(3),
    "deletedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ChatMessage_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ChatReadReceipt" (
    "id" TEXT NOT NULL,
    "roomId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "lastReadMessageId" TEXT NOT NULL,
    "lastReadAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ChatReadReceipt_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SharedNote" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "createdBy" TEXT NOT NULL,
    "studyGroupId" TEXT,
    "title" TEXT NOT NULL,
    "content" TEXT NOT NULL,
    "version" INTEGER NOT NULL DEFAULT 1,
    "allowEditing" BOOLEAN NOT NULL DEFAULT false,
    "allowComments" BOOLEAN NOT NULL DEFAULT true,
    "moduleId" TEXT,
    "lessonId" TEXT,
    "sharedWith" TEXT,
    "lastEditedBy" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "SharedNote_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "NoteComment" (
    "id" TEXT NOT NULL,
    "noteId" TEXT NOT NULL,
    "authorId" TEXT NOT NULL,
    "anchorStart" INTEGER,
    "anchorEnd" INTEGER,
    "content" TEXT NOT NULL,
    "parentId" TEXT,
    "isResolved" BOOLEAN NOT NULL DEFAULT false,
    "resolvedBy" TEXT,
    "resolvedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "NoteComment_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CollaborativeWhiteboard" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "studyGroupId" TEXT,
    "sessionId" TEXT,
    "name" TEXT NOT NULL,
    "canvasState" TEXT NOT NULL,
    "activeUsers" TEXT,
    "allowAnonymous" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CollaborativeWhiteboard_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SocialActivity" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "actorId" TEXT NOT NULL,
    "actorName" TEXT NOT NULL,
    "actorAvatarUrl" TEXT,
    "type" "SocialActivityType" NOT NULL,
    "targetType" TEXT NOT NULL,
    "targetId" TEXT NOT NULL,
    "targetTitle" TEXT NOT NULL,
    "studyGroupId" TEXT,
    "classroomId" TEXT,
    "metadata" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SocialActivity_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SocialNotification" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "type" "SocialNotificationType" NOT NULL,
    "title" TEXT NOT NULL,
    "body" TEXT NOT NULL,
    "iconUrl" TEXT,
    "actionUrl" TEXT,
    "actorId" TEXT,
    "actorName" TEXT,
    "targetType" TEXT,
    "targetId" TEXT,
    "isRead" BOOLEAN NOT NULL DEFAULT false,
    "readAt" TIMESTAMP(3),
    "emailSent" BOOLEAN NOT NULL DEFAULT false,
    "pushSent" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SocialNotification_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "NotificationPreference" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "emailEnabled" BOOLEAN NOT NULL DEFAULT true,
    "pushEnabled" BOOLEAN NOT NULL DEFAULT true,
    "preferences" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "NotificationPreference_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "VRLab" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "category" "VRLabCategory" NOT NULL,
    "difficulty" "VRLabDifficulty" NOT NULL,
    "thumbnailUrl" TEXT NOT NULL,
    "previewVideoUrl" TEXT,
    "estimatedDuration" INTEGER NOT NULL,
    "requiredDevices" TEXT NOT NULL,
    "minRequirements" TEXT NOT NULL,
    "completionRate" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "averageRating" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "totalSessions" INTEGER NOT NULL DEFAULT 0,
    "memberCount" INTEGER NOT NULL DEFAULT 0,
    "isPublished" BOOLEAN NOT NULL DEFAULT false,
    "tags" TEXT NOT NULL,
    "prerequisites" TEXT,
    "createdBy" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "VRLab_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "VRScene" (
    "id" TEXT NOT NULL,
    "labId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "order" INTEGER NOT NULL,
    "environmentUrl" TEXT NOT NULL,
    "skyboxUrl" TEXT,
    "lightingPreset" TEXT NOT NULL,
    "spawnPoints" TEXT NOT NULL,
    "ambientSoundUrl" TEXT,
    "narrationUrl" TEXT,
    "estimatedDuration" INTEGER NOT NULL,

    CONSTRAINT "VRScene_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "VRInteractable" (
    "id" TEXT NOT NULL,
    "sceneId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "position" TEXT NOT NULL,
    "rotation" TEXT NOT NULL,
    "scale" TEXT NOT NULL,
    "modelUrl" TEXT NOT NULL,
    "materialOverrides" TEXT,
    "allowedInteractions" TEXT NOT NULL,
    "interactionRange" DOUBLE PRECISION NOT NULL,
    "behavior" TEXT NOT NULL,
    "tooltip" TEXT,
    "audioFeedbackUrl" TEXT,

    CONSTRAINT "VRInteractable_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "VRLabObjective" (
    "id" TEXT NOT NULL,
    "labId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "order" INTEGER NOT NULL,
    "type" TEXT NOT NULL,
    "criteria" TEXT NOT NULL,
    "hints" TEXT NOT NULL,
    "points" INTEGER NOT NULL,
    "isOptional" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "VRLabObjective_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "VRSession" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "labId" TEXT NOT NULL,
    "status" "VRSessionStatus" NOT NULL,
    "currentSceneId" TEXT NOT NULL,
    "deviceType" TEXT NOT NULL,
    "deviceInfo" TEXT NOT NULL,
    "progress" TEXT NOT NULL,
    "startedAt" TIMESTAMP(3) NOT NULL,
    "lastActiveAt" TIMESTAMP(3) NOT NULL,
    "endedAt" TIMESTAMP(3),
    "totalDuration" INTEGER NOT NULL DEFAULT 0,
    "performanceMetrics" TEXT NOT NULL,

    CONSTRAINT "VRSession_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "VRMultiplayerSession" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "labId" TEXT NOT NULL,
    "hostUserId" TEXT NOT NULL,
    "maxParticipants" INTEGER NOT NULL,
    "voiceChatEnabled" BOOLEAN NOT NULL DEFAULT true,
    "spatialAudioEnabled" BOOLEAN NOT NULL DEFAULT true,
    "participants" TEXT NOT NULL,
    "status" "VRMultiplayerStatus" NOT NULL DEFAULT 'LOBBY',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "VRMultiplayerSession_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "VRAsset" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "url" TEXT NOT NULL,
    "size" INTEGER NOT NULL,
    "format" TEXT NOT NULL,
    "s3Key" TEXT NOT NULL,
    "thumbnailUrl" TEXT,
    "tags" TEXT NOT NULL,
    "isPublic" BOOLEAN NOT NULL DEFAULT false,
    "status" TEXT NOT NULL DEFAULT 'ready',
    "createdBy" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "VRAsset_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "VRAnalyticsEvent" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "eventType" TEXT NOT NULL,
    "labId" TEXT,
    "sessionId" TEXT,
    "metadata" TEXT NOT NULL,
    "timestamp" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "VRAnalyticsEvent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DomainConcept" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "externalId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "domain" "SimulationDomain" NOT NULL,
    "level" "CurriculumLevel" NOT NULL,
    "keywords" TEXT NOT NULL,
    "audienceTags" TEXT NOT NULL,
    "simulationMetadata" JSONB NOT NULL,
    "learningObjectMetadata" JSONB NOT NULL,
    "pedagogicalMetadata" JSONB NOT NULL,
    "crossDomainLinks" JSONB NOT NULL,
    "status" "ContentStatus" NOT NULL DEFAULT 'DRAFT',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "DomainConcept_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ConceptPrerequisite" (
    "conceptId" TEXT NOT NULL,
    "prerequisiteId" TEXT NOT NULL,

    CONSTRAINT "ConceptPrerequisite_pkey" PRIMARY KEY ("conceptId","prerequisiteId")
);

-- CreateTable
CREATE TABLE "ConceptModuleMapping" (
    "id" TEXT NOT NULL,
    "conceptId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "simulationManifestIds" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ConceptModuleMapping_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SimulationManifest" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "domain" "SimulationDomain" NOT NULL,
    "version" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "moduleId" TEXT,
    "manifest" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "SimulationManifest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SimulationTemplate" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "domain" "SimulationDomain" NOT NULL,
    "difficulty" "SimulationTemplateDifficulty" NOT NULL,
    "tags" TEXT NOT NULL,
    "thumbnailUrl" TEXT,
    "license" "SimulationTemplateLicense" NOT NULL DEFAULT 'FREE',
    "isPremium" BOOLEAN NOT NULL DEFAULT false,
    "isVerified" BOOLEAN NOT NULL DEFAULT false,
    "version" TEXT NOT NULL,
    "authorId" TEXT NOT NULL,
    "authorName" TEXT,
    "authorAvatarUrl" TEXT,
    "organization" TEXT,
    "statsViews" INTEGER NOT NULL DEFAULT 0,
    "statsUses" INTEGER NOT NULL DEFAULT 0,
    "statsFavorites" INTEGER NOT NULL DEFAULT 0,
    "statsRating" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "statsRatingCount" INTEGER NOT NULL DEFAULT 0,
    "statsCompletionRate" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "statsAvgTimeMinutes" INTEGER NOT NULL DEFAULT 0,
    "status" "SimulationTemplateStatus" NOT NULL DEFAULT 'DRAFT',
    "publishedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "conceptId" TEXT,
    "moduleId" TEXT,
    "manifestId" TEXT,

    CONSTRAINT "SimulationTemplate_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Curriculum" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "domain" "SimulationDomain" NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "version" TEXT NOT NULL DEFAULT '1.0.0',
    "isPublished" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Curriculum_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "tenants" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "subdomain" TEXT NOT NULL,
    "adminEmail" TEXT,
    "subscriptionTier" "SubscriptionTier" NOT NULL DEFAULT 'FREE',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "tenants_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "TenantSettings" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "allowPublicRegistration" BOOLEAN NOT NULL DEFAULT false,
    "requireEmailVerification" BOOLEAN NOT NULL DEFAULT true,
    "defaultUserRole" TEXT NOT NULL DEFAULT 'student',
    "maxUsersPerClassroom" INTEGER NOT NULL DEFAULT 50,
    "enabledFeatures" TEXT NOT NULL DEFAULT '[]',
    "enabledDomainPacks" TEXT NOT NULL DEFAULT '[]',
    "simulationQuotas" TEXT NOT NULL DEFAULT '{}',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "TenantSettings_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "User" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "displayName" TEXT NOT NULL,
    "role" TEXT NOT NULL DEFAULT 'student',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "User_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "LearnerProfile" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "preferredDifficulty" "LearnerDifficultyPreference" NOT NULL DEFAULT 'MEDIUM',
    "preferredModality" "LearnerModalityPreference" NOT NULL DEFAULT 'MIXED',
    "preferredPacing" "LearnerPacingPreference" NOT NULL DEFAULT 'ADAPTIVE',
    "preferredSessionMinutes" INTEGER NOT NULL DEFAULT 30,
    "notificationFrequency" TEXT NOT NULL DEFAULT 'daily',
    "visualLearningScore" DOUBLE PRECISION NOT NULL DEFAULT 0.25,
    "auditoryLearningScore" DOUBLE PRECISION NOT NULL DEFAULT 0.25,
    "kinestheticLearningScore" DOUBLE PRECISION NOT NULL DEFAULT 0.25,
    "readingLearningScore" DOUBLE PRECISION NOT NULL DEFAULT 0.25,
    "avgSessionMinutes" DOUBLE PRECISION NOT NULL DEFAULT 30,
    "preferredTimeOfDay" TEXT,
    "streakDays" INTEGER NOT NULL DEFAULT 0,
    "lastActiveAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "LearnerProfile_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "LearnerMastery" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "conceptId" TEXT NOT NULL,
    "masteryProbability" DOUBLE PRECISION NOT NULL DEFAULT 0.2,
    "confidenceScore" DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    "attempts" INTEGER NOT NULL DEFAULT 0,
    "correctAttempts" INTEGER NOT NULL DEFAULT 0,
    "incorrectAttempts" INTEGER NOT NULL DEFAULT 0,
    "hintsUsed" INTEGER NOT NULL DEFAULT 0,
    "totalTimeSeconds" INTEGER NOT NULL DEFAULT 0,
    "lastObservedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "nextReviewAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "LearnerMastery_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "KnowledgeGap" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "conceptId" TEXT NOT NULL,
    "prerequisiteId" TEXT NOT NULL,
    "severity" "KnowledgeGapSeverity" NOT NULL DEFAULT 'MEDIUM',
    "detectedBy" "KnowledgeGapDetectionMethod" NOT NULL DEFAULT 'ADAPTIVE_ANALYSIS',
    "status" TEXT NOT NULL DEFAULT 'OPEN',
    "evidence" JSONB,
    "detectionCount" INTEGER NOT NULL DEFAULT 1,
    "lastDetectedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "resolvedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "KnowledgeGap_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PreferenceChange" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "changedBy" TEXT NOT NULL DEFAULT 'user',
    "reason" TEXT,
    "changes" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PreferenceChange_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "LearningPathway" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "profileId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "goal" TEXT NOT NULL,
    "status" "LearnerPathwayStatus" NOT NULL DEFAULT 'ACTIVE',
    "recommendedModules" JSONB NOT NULL DEFAULT '[]',
    "currentModuleId" TEXT,
    "progressPercent" INTEGER NOT NULL DEFAULT 0,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "LearningPathway_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "IdentityProvider" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "type" TEXT NOT NULL DEFAULT 'oidc',
    "displayName" TEXT NOT NULL,
    "discoveryEndpoint" TEXT NOT NULL,
    "clientId" TEXT NOT NULL,
    "clientSecret" TEXT,
    "allowedDomains" TEXT NOT NULL DEFAULT '[]',
    "enabled" BOOLEAN NOT NULL DEFAULT false,
    "status" TEXT NOT NULL DEFAULT 'pending_verification',
    "roleMapping" TEXT,
    "lastSuccessfulAuthAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "IdentityProvider_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SsoUserLink" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "providerId" TEXT NOT NULL,
    "externalId" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "displayName" TEXT,
    "avatarUrl" TEXT,
    "lastClaims" TEXT,
    "linkedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastLoginAt" TIMESTAMP(3),

    CONSTRAINT "SsoUserLink_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DataExportRequest" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "requestedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "estimatedCompletionAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "downloadUrl" TEXT,
    "expiresAt" TIMESTAMP(3),

    CONSTRAINT "DataExportRequest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DataDeletionRequest" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'scheduled',
    "requestedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "scheduledDeletionAt" TIMESTAMP(3) NOT NULL,
    "completedAt" TIMESTAMP(3),
    "retentionDays" INTEGER NOT NULL DEFAULT 30,

    CONSTRAINT "DataDeletionRequest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DeletionVerification" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "token" TEXT NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "DeletionVerification_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AuditLog" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "actorId" TEXT NOT NULL,
    "action" TEXT NOT NULL,
    "resourceType" TEXT NOT NULL,
    "resourceId" TEXT NOT NULL,
    "outcome" TEXT NOT NULL DEFAULT 'success',
    "timestamp" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "ipAddress" TEXT,
    "userAgent" TEXT,
    "metadata" TEXT NOT NULL DEFAULT '{}',

    CONSTRAINT "AuditLog_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ClassroomMember" (
    "id" TEXT NOT NULL,
    "classroomId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "role" TEXT NOT NULL DEFAULT 'student',
    "joinedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ClassroomMember_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "LearningPathEnrollment" (
    "id" TEXT NOT NULL,
    "pathId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "currentNodeIndex" INTEGER NOT NULL DEFAULT 0,
    "startedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "completedAt" TIMESTAMP(3),

    CONSTRAINT "LearningPathEnrollment_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DomainAuthor" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "domain" "SimulationDomain" NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "author" TEXT NOT NULL,
    "status" "ContentStatus" NOT NULL DEFAULT 'DRAFT',
    "publishedAt" TIMESTAMP(3),
    "version" INTEGER NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "DomainAuthor_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DomainAuthorConcept" (
    "id" TEXT NOT NULL,
    "domainId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "level" "CurriculumLevel" NOT NULL,
    "orderIndex" INTEGER NOT NULL DEFAULT 0,
    "learningObjectives" TEXT NOT NULL,
    "prerequisites" TEXT NOT NULL,
    "competencies" TEXT NOT NULL,
    "keywords" TEXT NOT NULL,
    "status" "ContentStatus" NOT NULL DEFAULT 'DRAFT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "DomainAuthorConcept_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SimulationDefinition" (
    "id" TEXT NOT NULL,
    "conceptId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "manifest" JSONB NOT NULL,
    "estimatedTimeMinutes" INTEGER NOT NULL DEFAULT 15,
    "interactivityLevel" TEXT NOT NULL DEFAULT 'medium',
    "purpose" TEXT NOT NULL,
    "previewConfig" JSONB,
    "status" "ContentStatus" NOT NULL DEFAULT 'DRAFT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "SimulationDefinition_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "VisualizationDefinition" (
    "id" TEXT NOT NULL,
    "conceptId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "config" JSONB NOT NULL,
    "dataSource" TEXT NOT NULL DEFAULT 'simulation',
    "status" "ContentStatus" NOT NULL DEFAULT 'DRAFT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "VisualizationDefinition_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ContentExample" (
    "id" TEXT NOT NULL,
    "conceptId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "problemStatement" TEXT NOT NULL,
    "solutionContent" TEXT NOT NULL,
    "keyLearningPoints" TEXT NOT NULL,
    "difficulty" "CurriculumLevel" NOT NULL,
    "estimatedTimeMinutes" INTEGER NOT NULL DEFAULT 20,
    "orderIndex" INTEGER NOT NULL DEFAULT 0,
    "status" "ContentStatus" NOT NULL DEFAULT 'DRAFT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ContentExample_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "VisualizationSnapshot" (
    "id" TEXT NOT NULL,
    "exampleId" TEXT NOT NULL,
    "stepNumber" INTEGER NOT NULL,
    "stepDescription" TEXT NOT NULL,
    "config" JSONB NOT NULL,
    "data" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "VisualizationSnapshot_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Asset" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "filename" TEXT NOT NULL,
    "displayName" TEXT,
    "fileType" "AssetType" NOT NULL,
    "mimeType" TEXT NOT NULL,
    "fileSize" INTEGER NOT NULL,
    "url" TEXT NOT NULL,
    "thumbnailUrl" TEXT,
    "metadata" JSONB,
    "tags" TEXT,
    "uploadedBy" TEXT NOT NULL,
    "status" "AssetStatus" NOT NULL DEFAULT 'UPLOADING',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Asset_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "LearningUnit" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "domain" TEXT NOT NULL,
    "level" TEXT NOT NULL,
    "status" TEXT NOT NULL,
    "version" INTEGER NOT NULL DEFAULT 1,
    "intent" JSONB NOT NULL,
    "claims" JSONB NOT NULL,
    "evidence" JSONB NOT NULL,
    "tasks" JSONB NOT NULL,
    "artifacts" JSONB NOT NULL,
    "assessment" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "createdBy" TEXT NOT NULL,

    CONSTRAINT "LearningUnit_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "LearningExperience" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT,
    "title" TEXT NOT NULL,
    "domain" "ModuleDomain" NOT NULL,
    "conceptId" TEXT,
    "intentProblem" TEXT NOT NULL,
    "intentMotivation" TEXT NOT NULL,
    "intentMisconceptions" JSONB NOT NULL,
    "targetGrades" JSONB NOT NULL,
    "curriculumAlignment" JSONB,
    "gradeAdaptations" JSONB NOT NULL,
    "simulationManifestId" TEXT,
    "simulationVersion" TEXT,
    "assessmentConfig" JSONB NOT NULL,
    "status" "ExperienceStatus" NOT NULL DEFAULT 'DRAFT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "estimatedTimeMinutes" INTEGER NOT NULL DEFAULT 30,
    "createdBy" TEXT NOT NULL,
    "lastEditedBy" TEXT,
    "publishedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "promptHash" TEXT,
    "riskLevel" "RiskLevel" NOT NULL DEFAULT 'LOW',
    "confidenceScore" DOUBLE PRECISION,

    CONSTRAINT "LearningExperience_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "LearningClaim" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "text" TEXT NOT NULL,
    "bloomLevel" "ContentStudioBloomLevel" NOT NULL,
    "orderIndex" INTEGER NOT NULL,
    "prerequisites" JSONB,
    "gradeOverrides" JSONB,
    "contentNeeds" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "LearningClaim_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "LearningEvidence" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "evidenceRef" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "sourceType" "EvidenceSourceType" NOT NULL,
    "sourceUrl" TEXT,
    "sourceTitle" TEXT NOT NULL,
    "sourcePublisher" TEXT,
    "sourcePublicationDate" TIMESTAMP(3),
    "excerpt" TEXT,
    "structuredFact" JSONB,
    "type" TEXT,
    "description" TEXT,
    "observables" JSONB,
    "supportKind" "SupportKind" NOT NULL DEFAULT 'SUPPORTS',
    "credibilityScore" REAL,
    "retrievedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "freshnessStatus" "EvidenceFreshnessStatus" NOT NULL DEFAULT 'UNKNOWN',
    "verificationState" "EvidenceVerificationState" NOT NULL DEFAULT 'UNVERIFIED',
    "contradictionNotes" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "LearningEvidence_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "EvidenceBundleMetadata" (
    "id" TEXT NOT NULL,
    "bundleConfidence" DOUBLE PRECISION NOT NULL,
    "coverageScore" DOUBLE PRECISION NOT NULL,
    "contradictionDetected" BOOLEAN NOT NULL DEFAULT false,
    "freshnessOverall" "EvidenceFreshnessStatus" NOT NULL DEFAULT 'UNKNOWN',
    "evidenceCount" INTEGER NOT NULL DEFAULT 0,
    "primarySourceTypes" TEXT[],
    "generatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "regeneratedAt" TIMESTAMP(3),
    "generationJobId" TEXT,
    "claimRef" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "bundleCache" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "EvidenceBundleMetadata_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ExperienceTask" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "taskRef" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "evidenceRef" TEXT NOT NULL,
    "prompt" TEXT NOT NULL,
    "orderIndex" INTEGER NOT NULL,
    "config" JSONB NOT NULL,
    "gradeOverrides" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ExperienceTask_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ValidationRecord" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "authorityScore" INTEGER NOT NULL,
    "accuracyScore" INTEGER NOT NULL,
    "usefulnessScore" INTEGER NOT NULL,
    "harmlessnessScore" INTEGER NOT NULL,
    "accessibilityScore" INTEGER NOT NULL,
    "gradefitScore" INTEGER NOT NULL,
    "overallStatus" "ValidationStatus" NOT NULL,
    "issues" JSONB NOT NULL,
    "suggestions" JSONB,
    "validatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ValidationRecord_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AIGenerationLog" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "experienceId" TEXT,
    "operation" "AIOperation" NOT NULL,
    "provider" TEXT NOT NULL,
    "model" TEXT NOT NULL,
    "modelVersion" TEXT,
    "inputTokens" INTEGER NOT NULL,
    "outputTokens" INTEGER NOT NULL,
    "costUsd" DOUBLE PRECISION NOT NULL,
    "latencyMs" INTEGER NOT NULL,
    "success" BOOLEAN NOT NULL,
    "errorMessage" TEXT,
    "promptHash" TEXT,
    "requestHash" TEXT,
    "guardrailsVersion" TEXT,
    "riskLevel" "RiskLevel" NOT NULL DEFAULT 'LOW',
    "confidenceScore" DOUBLE PRECISION,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AIGenerationLog_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ExperienceRevision" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "version" INTEGER NOT NULL,
    "diff" JSONB NOT NULL,
    "authorType" "AuthorType" NOT NULL,
    "authorId" TEXT NOT NULL,
    "promptHash" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ExperienceRevision_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ExperienceEvent" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "eventType" "ExperienceEventType" NOT NULL,
    "actorId" TEXT NOT NULL,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ExperienceEvent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SimulationManifestVersion" (
    "id" TEXT NOT NULL,
    "manifestId" TEXT NOT NULL,
    "version" TEXT NOT NULL,
    "manifestJson" JSONB NOT NULL,
    "safetyConstraints" JSONB NOT NULL,
    "pedagogicalTags" JSONB NOT NULL,
    "instrumentationPlan" JSONB NOT NULL,
    "status" "SimulationManifestStatus" NOT NULL DEFAULT 'DRAFT',
    "riskLevel" "RiskLevel" NOT NULL DEFAULT 'LOW',
    "confidenceScore" DOUBLE PRECISION,
    "createdBy" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SimulationManifestVersion_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SimulationManifestExtension" (
    "id" TEXT NOT NULL,
    "metadata" JSONB,

    CONSTRAINT "SimulationManifestExtension_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SimulationLinkAudit" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "simulationManifestId" TEXT NOT NULL,
    "action" TEXT NOT NULL,
    "beforeVersion" TEXT,
    "afterVersion" TEXT,
    "actorId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SimulationLinkAudit_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AIPromptCache" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "rawIntentHash" TEXT NOT NULL,
    "flowType" TEXT NOT NULL,
    "refinedPrompt" TEXT NOT NULL,
    "guardrailsVersion" TEXT NOT NULL,
    "domain" TEXT,
    "gradeRange" TEXT,
    "riskLevel" "RiskLevel" NOT NULL DEFAULT 'LOW',
    "aiOutputIds" JSONB NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AIPromptCache_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ValidationRecordExtended" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "inputHash" TEXT NOT NULL,
    "validatorsVersion" TEXT NOT NULL,
    "authorityScore" INTEGER NOT NULL,
    "accuracyScore" INTEGER NOT NULL,
    "usefulnessScore" INTEGER NOT NULL,
    "harmlessnessScore" INTEGER NOT NULL,
    "accessibilityScore" INTEGER NOT NULL,
    "gradefitScore" INTEGER NOT NULL,
    "overallStatus" "ValidationStatus" NOT NULL,
    "checks" JSONB NOT NULL,
    "issues" JSONB NOT NULL,
    "suggestions" JSONB,
    "simulationHealthReport" JSONB,
    "validatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ValidationRecordExtended_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ReviewQueue" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "queuedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "priority" INTEGER NOT NULL DEFAULT 0,
    "assignedTo" TEXT,
    "assignedAt" TIMESTAMP(3),
    "riskLevel" "RiskLevel" NOT NULL,
    "triggerReason" TEXT NOT NULL,
    "metadata" JSONB,

    CONSTRAINT "ReviewQueue_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ReviewDecision" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "reviewerId" TEXT NOT NULL,
    "reviewerRole" "ReviewerRole" NOT NULL,
    "decision" "ReviewDecisionType" NOT NULL,
    "rationale" TEXT NOT NULL,
    "changes" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ReviewDecision_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ExperienceAnalytics" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "viewCount" INTEGER NOT NULL DEFAULT 0,
    "completionCount" INTEGER NOT NULL DEFAULT 0,
    "completionRate" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "avgTimeMinutes" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "dropOffRate" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "simulationStarts" INTEGER NOT NULL DEFAULT 0,
    "simulationAborts" INTEGER NOT NULL DEFAULT 0,
    "simulationErrors" INTEGER NOT NULL DEFAULT 0,
    "trends7d" JSONB,
    "trends28d" JSONB,
    "hasEngagementDrift" BOOLEAN NOT NULL DEFAULT false,
    "hasQualityIssues" BOOLEAN NOT NULL DEFAULT false,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ExperienceAnalytics_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ExperienceAutoRefinement" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "analyticsSnapshot" JSONB NOT NULL,
    "triggerReason" TEXT NOT NULL,
    "candidateExperienceId" TEXT,
    "appliedGuardrails" JSONB NOT NULL,
    "aiCompletionId" TEXT,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "reviewedBy" TEXT,
    "reviewedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ExperienceAutoRefinement_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ClaimExample" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "manifestId" TEXT NOT NULL,
    "manifestVersion" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "content" JSONB NOT NULL,
    "exampleFamily" TEXT NOT NULL DEFAULT 'worked-solution',
    "type" TEXT NOT NULL,
    "difficulty" TEXT NOT NULL DEFAULT 'INTERMEDIATE',
    "orderIndex" INTEGER NOT NULL,
    "validationStatus" TEXT NOT NULL DEFAULT 'pending',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ClaimExample_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ClaimSimulation" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "simulationManifestId" TEXT NOT NULL,
    "interactionType" TEXT NOT NULL,
    "goal" TEXT NOT NULL,
    "successCriteria" JSONB NOT NULL,
    "estimatedMinutes" INTEGER NOT NULL DEFAULT 10,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ClaimSimulation_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ClaimAnimation" (
    "id" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "claimRef" TEXT NOT NULL,
    "manifestId" TEXT NOT NULL,
    "manifestVersion" TEXT NOT NULL,
    "variantKey" TEXT NOT NULL DEFAULT 'primary',
    "isPrimary" BOOLEAN NOT NULL DEFAULT true,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "duration" INTEGER NOT NULL,
    "config" JSONB NOT NULL,
    "validationStatus" TEXT NOT NULL DEFAULT 'pending',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ClaimAnimation_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ABExperiment" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "controlVersion" INTEGER NOT NULL,
    "treatmentVersion" INTEGER NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'draft',
    "controlSampleSize" INTEGER NOT NULL DEFAULT 0,
    "treatmentSampleSize" INTEGER NOT NULL DEFAULT 0,
    "controlMetrics" JSONB NOT NULL DEFAULT '{}',
    "treatmentMetrics" JSONB NOT NULL DEFAULT '{}',
    "pValue" DOUBLE PRECISION,
    "confidenceLower" DOUBLE PRECISION,
    "confidenceUpper" DOUBLE PRECISION,
    "effectSize" DOUBLE PRECISION,
    "winner" TEXT,
    "statisticalPower" DOUBLE PRECISION,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "startedAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "notes" TEXT,
    "priority" INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT "ABExperiment_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ABExperimentAssignment" (
    "id" TEXT NOT NULL,
    "experimentId" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "variant" TEXT NOT NULL,
    "assignedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastSeenAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "metadata" JSONB,

    CONSTRAINT "ABExperimentAssignment_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ABExperimentObservation" (
    "id" TEXT NOT NULL,
    "experimentId" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "variant" TEXT NOT NULL,
    "sessionId" TEXT,
    "assetId" TEXT,
    "metricValue" DOUBLE PRECISION NOT NULL,
    "completed" BOOLEAN,
    "masteryScore" DOUBLE PRECISION,
    "feedbackScore" DOUBLE PRECISION,
    "metadata" JSONB,
    "observedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ABExperimentObservation_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DriftSignal" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "signalType" TEXT NOT NULL,
    "severity" TEXT NOT NULL,
    "metric" TEXT NOT NULL,
    "value" DOUBLE PRECISION NOT NULL,
    "threshold" DOUBLE PRECISION NOT NULL,
    "recommendation" TEXT NOT NULL,
    "confidence" DOUBLE PRECISION,
    "context" JSONB,
    "status" TEXT NOT NULL DEFAULT 'detected',
    "detectedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "acknowledgedAt" TIMESTAMP(3),
    "addressedAt" TIMESTAMP(3),
    "autoRefinementId" TEXT,

    CONSTRAINT "DriftSignal_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RegenerationInsight" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "category" TEXT NOT NULL,
    "issue" TEXT NOT NULL,
    "suggestedAction" TEXT NOT NULL,
    "priority" INTEGER NOT NULL,
    "evidence" JSONB NOT NULL,
    "confidence" DOUBLE PRECISION,
    "status" TEXT NOT NULL DEFAULT 'identified',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "autoRefinementId" TEXT,

    CONSTRAINT "RegenerationInsight_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AutoRevisionConfig" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "driftCheckIntervalHours" INTEGER NOT NULL DEFAULT 6,
    "maxConcurrentRegenerations" INTEGER NOT NULL DEFAULT 3,
    "minSampleSize" INTEGER NOT NULL DEFAULT 100,
    "significanceThreshold" DOUBLE PRECISION NOT NULL DEFAULT 0.05,
    "completionRateThreshold" DOUBLE PRECISION NOT NULL DEFAULT 0.6,
    "abortRateThreshold" DOUBLE PRECISION NOT NULL DEFAULT 0.3,
    "averageTimeSpentThreshold" DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    "masteryRateThreshold" DOUBLE PRECISION NOT NULL DEFAULT 0.7,
    "feedbackScoreThreshold" DOUBLE PRECISION NOT NULL DEFAULT 3.0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "updatedBy" TEXT,
    "notes" TEXT,

    CONSTRAINT "AutoRevisionConfig_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AutoRevisionMetrics" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "date" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "driftSignalsDetected" INTEGER NOT NULL DEFAULT 0,
    "experiencesMonitored" INTEGER NOT NULL DEFAULT 0,
    "highSeveritySignals" INTEGER NOT NULL DEFAULT 0,
    "regenerationsQueued" INTEGER NOT NULL DEFAULT 0,
    "regenerationsCompleted" INTEGER NOT NULL DEFAULT 0,
    "regenerationsFailed" INTEGER NOT NULL DEFAULT 0,
    "averageProcessingTimeMinutes" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "abExperimentsCreated" INTEGER NOT NULL DEFAULT 0,
    "abExperimentsCompleted" INTEGER NOT NULL DEFAULT 0,
    "averageTestDurationHours" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "improvementRate" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "averageEngagementImprovement" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "averageCompletionImprovement" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "averageMasteryImprovement" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "queueLength" INTEGER NOT NULL DEFAULT 0,
    "processingJobs" INTEGER NOT NULL DEFAULT 0,
    "failedJobs24h" INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT "AutoRevisionMetrics_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Badge" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "icon" TEXT NOT NULL,
    "category" TEXT NOT NULL DEFAULT 'LEARNING',
    "criteria" TEXT NOT NULL,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "points" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Badge_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "BadgeEarned" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "badgeId" TEXT NOT NULL,
    "earnedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "BadgeEarned_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "UserPoints" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "totalPoints" INTEGER NOT NULL DEFAULT 0,
    "level" INTEGER NOT NULL DEFAULT 1,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "UserPoints_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DeviceToken" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "token" TEXT NOT NULL,
    "platform" TEXT NOT NULL,
    "endpoint" TEXT,
    "p256dhKey" TEXT,
    "authKey" TEXT,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "lastSeen" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "DeviceToken_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "KernelPlugin" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "pluginId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "version" TEXT NOT NULL,
    "description" TEXT,
    "author" TEXT,
    "kernelType" TEXT NOT NULL,
    "capabilities" TEXT NOT NULL DEFAULT '[]',
    "dependencies" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "KernelPlugin_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AutomationRule" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "experienceId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "trigger" TEXT NOT NULL,
    "action" TEXT NOT NULL,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "AutomationRule_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ContentAsset" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "assetType" "ContentAssetType" NOT NULL,
    "domain" "ModuleDomain" NOT NULL,
    "conceptId" TEXT,
    "status" "ContentAssetStatus" NOT NULL DEFAULT 'DRAFT',
    "currentVersion" INTEGER NOT NULL DEFAULT 1,
    "qualityScore" DOUBLE PRECISION,
    "reviewState" TEXT,
    "semanticIndexStatus" TEXT,
    "recommendationStatus" TEXT,
    "searchableText" TEXT,
    "tags" JSONB,
    "targetGrades" JSONB NOT NULL,
    "difficultyLevel" TEXT,
    "authorId" TEXT NOT NULL,
    "lastEditedBy" TEXT,
    "publishedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "promptHash" TEXT,
    "riskLevel" "RiskLevel" NOT NULL DEFAULT 'LOW',
    "confidenceScore" DOUBLE PRECISION,
    "legacyModuleId" TEXT,
    "legacyExperienceId" TEXT,

    CONSTRAINT "ContentAsset_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ContentAssetRevision" (
    "id" TEXT NOT NULL,
    "assetId" TEXT NOT NULL,
    "version" INTEGER NOT NULL,
    "changeNote" TEXT,
    "changeDiff" JSONB,
    "snapshot" JSONB NOT NULL,
    "qualityScore" DOUBLE PRECISION,
    "validationId" TEXT,
    "createdBy" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ContentAssetRevision_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ContentBlock" (
    "id" TEXT NOT NULL,
    "assetId" TEXT NOT NULL,
    "blockRef" TEXT NOT NULL,
    "blockType" "ContentBlockType" NOT NULL,
    "orderIndex" INTEGER NOT NULL,
    "title" TEXT,
    "payload" JSONB NOT NULL,
    "claimRefs" JSONB,
    "evidenceRefs" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ContentBlock_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ArtifactManifest" (
    "id" TEXT NOT NULL,
    "assetId" TEXT NOT NULL,
    "manifestType" "ArtifactManifestType" NOT NULL,
    "version" TEXT NOT NULL,
    "claimRef" TEXT,
    "manifest" JSONB NOT NULL,
    "schema" TEXT,
    "isValid" BOOLEAN NOT NULL DEFAULT false,
    "validationErrors" JSONB,
    "generatedBy" TEXT,
    "generationId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ArtifactManifest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SemanticChunk" (
    "id" TEXT NOT NULL,
    "assetId" TEXT NOT NULL,
    "chunkRef" TEXT NOT NULL,
    "source" "ChunkSource" NOT NULL,
    "sourceRef" TEXT NOT NULL,
    "sequenceIdx" INTEGER NOT NULL,
    "text" TEXT NOT NULL,
    "tokenCount" INTEGER NOT NULL,
    "contentHash" TEXT NOT NULL,
    "embeddingStatus" "EmbeddingStatus" NOT NULL DEFAULT 'PENDING',
    "domain" TEXT,
    "claimRefs" JSONB,
    "tags" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "SemanticChunk_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "EmbeddingVector" (
    "id" TEXT NOT NULL,
    "chunkId" TEXT NOT NULL,
    "vector" BYTEA NOT NULL,
    "dimensions" INTEGER NOT NULL,
    "model" TEXT NOT NULL,
    "generatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "generationMs" INTEGER,
    "jobId" TEXT,

    CONSTRAINT "EmbeddingVector_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RecommendationEdge" (
    "id" TEXT NOT NULL,
    "sourceAssetId" TEXT NOT NULL,
    "targetAssetId" TEXT NOT NULL,
    "edgeType" "RecommendationEdgeType" NOT NULL,
    "source" "RecommendationSource" NOT NULL DEFAULT 'RULE_BASED',
    "weight" DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    "confidence" DOUBLE PRECISION,
    "reason" TEXT,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "RecommendationEdge_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "GenerationRequest" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "domain" TEXT NOT NULL,
    "conceptId" TEXT,
    "targetGrades" JSONB,
    "requestedBy" TEXT NOT NULL,
    "requestConfig" JSONB,
    "status" "GenerationRequestStatus" NOT NULL DEFAULT 'DRAFT',
    "plannedAssets" JSONB,
    "artifactNeeds" JSONB,
    "riskLevel" "RiskLevel" NOT NULL DEFAULT 'LOW',
    "riskFactors" JSONB,
    "reviewPath" "ReviewPath" NOT NULL DEFAULT 'HUMAN_REVIEW',
    "estimatedCost" JSONB,
    "routingDecision" JSONB,
    "totalJobs" INTEGER NOT NULL DEFAULT 0,
    "completedJobs" INTEGER NOT NULL DEFAULT 0,
    "failedJobs" INTEGER NOT NULL DEFAULT 0,
    "plannedAt" TIMESTAMP(3),
    "startedAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "GenerationRequest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "GenerationJob" (
    "id" TEXT NOT NULL,
    "requestId" TEXT NOT NULL,
    "jobType" "GenerationJobType" NOT NULL,
    "targetRef" TEXT,
    "inputPrompt" TEXT,
    "parameters" JSONB,
    "status" "GenerationJobStatus" NOT NULL DEFAULT 'PENDING',
    "progress" INTEGER NOT NULL DEFAULT 0,
    "outputAssetId" TEXT,
    "outputData" JSONB,
    "diagnostics" JSONB,
    "errorMessage" TEXT,
    "retryCount" INTEGER NOT NULL DEFAULT 0,
    "maxRetries" INTEGER NOT NULL DEFAULT 3,
    "startedAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "GenerationJob_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "EvaluationRecord" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "assetId" TEXT,
    "generationJobId" TEXT,
    "generationRequestId" TEXT,
    "coherenceScore" DOUBLE PRECISION,
    "completenessScore" DOUBLE PRECISION,
    "safetyScore" DOUBLE PRECISION,
    "accessibilityScore" DOUBLE PRECISION,
    "manifestValidityScore" DOUBLE PRECISION,
    "overallScore" DOUBLE PRECISION,
    "status" "EvaluationStatus" NOT NULL DEFAULT 'PENDING',
    "recommendation" "PublishRecommendation" NOT NULL DEFAULT 'MANUAL_REVIEW',
    "issues" JSONB,
    "diagnostics" JSONB,
    "errorMessage" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "EvaluationRecord_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "GenerationReviewDecision" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "requestId" TEXT NOT NULL,
    "status" "GenerationReviewDecisionStatus" NOT NULL DEFAULT 'PENDING',
    "reviewedBy" TEXT,
    "decisionNote" TEXT,
    "regenerateJobIds" JSONB,
    "reviewedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "GenerationReviewDecision_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ExplorerEvent" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT,
    "sessionId" TEXT,
    "eventType" "ExplorerEventType" NOT NULL,
    "query" TEXT,
    "assetId" TEXT,
    "assetType" TEXT,
    "position" INTEGER,
    "score" DOUBLE PRECISION,
    "feedbackLabel" TEXT,
    "feedbackScore" DOUBLE PRECISION,
    "metadata" JSONB,
    "occurredAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ExplorerEvent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RegenerationCandidate" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "assetId" TEXT NOT NULL,
    "assetType" TEXT,
    "trigger" "RegenerationTrigger" NOT NULL,
    "severity" "RiskLevel" NOT NULL DEFAULT 'MEDIUM',
    "reason" TEXT NOT NULL,
    "evidence" JSONB,
    "priority" INTEGER NOT NULL DEFAULT 50,
    "status" "RegenerationCandidateStatus" NOT NULL DEFAULT 'OPEN',
    "generationRequestId" TEXT,
    "resolvedBy" TEXT,
    "resolvedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "RegenerationCandidate_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Module_tenantId_domain_idx" ON "Module"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "Module_tenantId_status_idx" ON "Module"("tenantId", "status");

-- CreateIndex
CREATE UNIQUE INDEX "Module_tenantId_slug_key" ON "Module"("tenantId", "slug");

-- CreateIndex
CREATE UNIQUE INDEX "ModuleTag_moduleId_label_key" ON "ModuleTag"("moduleId", "label");

-- CreateIndex
CREATE INDEX "ModuleRevision_moduleId_version_idx" ON "ModuleRevision"("moduleId", "version");

-- CreateIndex
CREATE INDEX "Enrollment_tenantId_userId_idx" ON "Enrollment"("tenantId", "userId");

-- CreateIndex
CREATE UNIQUE INDEX "Enrollment_tenantId_userId_moduleId_key" ON "Enrollment"("tenantId", "userId", "moduleId");

-- CreateIndex
CREATE INDEX "Assessment_tenantId_moduleId_idx" ON "Assessment"("tenantId", "moduleId");

-- CreateIndex
CREATE INDEX "Assessment_tenantId_status_idx" ON "Assessment"("tenantId", "status");

-- CreateIndex
CREATE INDEX "AssessmentAttempt_tenantId_userId_idx" ON "AssessmentAttempt"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "AssessmentAttempt_tenantId_assessmentId_idx" ON "AssessmentAttempt"("tenantId", "assessmentId");

-- CreateIndex
CREATE INDEX "AssessmentDraft_tenantId_moduleId_idx" ON "AssessmentDraft"("tenantId", "moduleId");

-- CreateIndex
CREATE INDEX "AssessmentDraft_tenantId_createdBy_idx" ON "AssessmentDraft"("tenantId", "createdBy");

-- CreateIndex
CREATE INDEX "LearningEvent_tenantId_eventType_idx" ON "LearningEvent"("tenantId", "eventType");

-- CreateIndex
CREATE INDEX "LearningEvent_tenantId_userId_idx" ON "LearningEvent"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "MarketplaceListing_tenantId_status_idx" ON "MarketplaceListing"("tenantId", "status");

-- CreateIndex
CREATE INDEX "MarketplaceListing_tenantId_moduleId_idx" ON "MarketplaceListing"("tenantId", "moduleId");

-- CreateIndex
CREATE INDEX "MarketplaceListing_tenantId_creatorId_idx" ON "MarketplaceListing"("tenantId", "creatorId");

-- CreateIndex
CREATE INDEX "LearningPath_tenantId_userId_idx" ON "LearningPath"("tenantId", "userId");

-- CreateIndex
CREATE UNIQUE INDEX "LearningPath_tenantId_userId_status_key" ON "LearningPath"("tenantId", "userId", "status");

-- CreateIndex
CREATE INDEX "LearningPathNode_pathId_idx" ON "LearningPathNode"("pathId");

-- CreateIndex
CREATE UNIQUE INDEX "LearningPathNode_pathId_moduleId_key" ON "LearningPathNode"("pathId", "moduleId");

-- CreateIndex
CREATE INDEX "Classroom_tenantId_teacherId_idx" ON "Classroom"("tenantId", "teacherId");

-- CreateIndex
CREATE INDEX "ClassroomStudent_classroomId_idx" ON "ClassroomStudent"("classroomId");

-- CreateIndex
CREATE UNIQUE INDEX "ClassroomStudent_classroomId_userId_key" ON "ClassroomStudent"("classroomId", "userId");

-- CreateIndex
CREATE INDEX "ClassroomAssignment_classroomId_idx" ON "ClassroomAssignment"("classroomId");

-- CreateIndex
CREATE UNIQUE INDEX "ClassroomAssignment_classroomId_moduleId_key" ON "ClassroomAssignment"("classroomId", "moduleId");

-- CreateIndex
CREATE INDEX "Thread_tenantId_moduleId_idx" ON "Thread"("tenantId", "moduleId");

-- CreateIndex
CREATE INDEX "Thread_tenantId_status_idx" ON "Thread"("tenantId", "status");

-- CreateIndex
CREATE INDEX "Thread_tenantId_authorId_idx" ON "Thread"("tenantId", "authorId");

-- CreateIndex
CREATE INDEX "Post_threadId_idx" ON "Post"("threadId");

-- CreateIndex
CREATE INDEX "HelpRequest_tenantId_status_idx" ON "HelpRequest"("tenantId", "status");

-- CreateIndex
CREATE INDEX "HelpRequest_tenantId_userId_idx" ON "HelpRequest"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "CheckoutSession_tenantId_userId_idx" ON "CheckoutSession"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "CheckoutSession_tenantId_status_idx" ON "CheckoutSession"("tenantId", "status");

-- CreateIndex
CREATE INDEX "Purchase_tenantId_userId_idx" ON "Purchase"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "Purchase_tenantId_moduleId_idx" ON "Purchase"("tenantId", "moduleId");

-- CreateIndex
CREATE UNIQUE INDEX "Purchase_tenantId_userId_moduleId_key" ON "Purchase"("tenantId", "userId", "moduleId");

-- CreateIndex
CREATE UNIQUE INDEX "StripeCustomer_tenantId_key" ON "StripeCustomer"("tenantId");

-- CreateIndex
CREATE UNIQUE INDEX "StripeCustomer_stripeCustomerId_key" ON "StripeCustomer"("stripeCustomerId");

-- CreateIndex
CREATE INDEX "StripeCustomer_tenantId_idx" ON "StripeCustomer"("tenantId");

-- CreateIndex
CREATE UNIQUE INDEX "Subscription_stripeSubscriptionId_key" ON "Subscription"("stripeSubscriptionId");

-- CreateIndex
CREATE INDEX "Subscription_tenantId_idx" ON "Subscription"("tenantId");

-- CreateIndex
CREATE INDEX "Subscription_stripeCustomerId_idx" ON "Subscription"("stripeCustomerId");

-- CreateIndex
CREATE INDEX "Subscription_status_idx" ON "Subscription"("status");

-- CreateIndex
CREATE UNIQUE INDEX "PaymentMethod_stripePaymentMethodId_key" ON "PaymentMethod"("stripePaymentMethodId");

-- CreateIndex
CREATE INDEX "PaymentMethod_tenantId_idx" ON "PaymentMethod"("tenantId");

-- CreateIndex
CREATE INDEX "PaymentMethod_stripeCustomerId_idx" ON "PaymentMethod"("stripeCustomerId");

-- CreateIndex
CREATE UNIQUE INDEX "Invoice_stripeInvoiceId_key" ON "Invoice"("stripeInvoiceId");

-- CreateIndex
CREATE INDEX "Invoice_tenantId_idx" ON "Invoice"("tenantId");

-- CreateIndex
CREATE INDEX "Invoice_stripeCustomerId_idx" ON "Invoice"("stripeCustomerId");

-- CreateIndex
CREATE INDEX "Invoice_subscriptionId_idx" ON "Invoice"("subscriptionId");

-- CreateIndex
CREATE INDEX "Invoice_status_idx" ON "Invoice"("status");

-- CreateIndex
CREATE INDEX "Transaction_tenantId_idx" ON "Transaction"("tenantId");

-- CreateIndex
CREATE INDEX "Transaction_invoiceId_idx" ON "Transaction"("invoiceId");

-- CreateIndex
CREATE INDEX "Transaction_status_idx" ON "Transaction"("status");

-- CreateIndex
CREATE UNIQUE INDEX "WebhookEvent_stripeEventId_key" ON "WebhookEvent"("stripeEventId");

-- CreateIndex
CREATE INDEX "WebhookEvent_processed_idx" ON "WebhookEvent"("processed");

-- CreateIndex
CREATE INDEX "WebhookEvent_type_idx" ON "WebhookEvent"("type");

-- CreateIndex
CREATE INDEX "UsageSnapshot_tenantId_idx" ON "UsageSnapshot"("tenantId");

-- CreateIndex
CREATE INDEX "UsageSnapshot_subscriptionId_idx" ON "UsageSnapshot"("subscriptionId");

-- CreateIndex
CREATE INDEX "UsageSnapshot_periodStart_periodEnd_idx" ON "UsageSnapshot"("periodStart", "periodEnd");

-- CreateIndex
CREATE INDEX "LTIPlatform_tenantId_idx" ON "LTIPlatform"("tenantId");

-- CreateIndex
CREATE UNIQUE INDEX "LTIPlatform_tenantId_issuer_key" ON "LTIPlatform"("tenantId", "issuer");

-- CreateIndex
CREATE INDEX "StudyGroup_tenantId_status_idx" ON "StudyGroup"("tenantId", "status");

-- CreateIndex
CREATE INDEX "StudyGroup_tenantId_visibility_idx" ON "StudyGroup"("tenantId", "visibility");

-- CreateIndex
CREATE INDEX "StudyGroup_tenantId_createdBy_idx" ON "StudyGroup"("tenantId", "createdBy");

-- CreateIndex
CREATE INDEX "StudyGroupMember_userId_idx" ON "StudyGroupMember"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "StudyGroupMember_groupId_userId_key" ON "StudyGroupMember"("groupId", "userId");

-- CreateIndex
CREATE INDEX "StudyGroupJoinRequest_groupId_status_idx" ON "StudyGroupJoinRequest"("groupId", "status");

-- CreateIndex
CREATE UNIQUE INDEX "StudyGroupJoinRequest_groupId_userId_status_key" ON "StudyGroupJoinRequest"("groupId", "userId", "status");

-- CreateIndex
CREATE INDEX "StudyGroupInvite_invitedEmail_status_idx" ON "StudyGroupInvite"("invitedEmail", "status");

-- CreateIndex
CREATE UNIQUE INDEX "StudyGroupInvite_groupId_invitedEmail_key" ON "StudyGroupInvite"("groupId", "invitedEmail");

-- CreateIndex
CREATE INDEX "StudySession_groupId_scheduledAt_idx" ON "StudySession"("groupId", "scheduledAt");

-- CreateIndex
CREATE INDEX "StudySession_groupId_status_idx" ON "StudySession"("groupId", "status");

-- CreateIndex
CREATE UNIQUE INDEX "SessionRsvp_sessionId_userId_key" ON "SessionRsvp"("sessionId", "userId");

-- CreateIndex
CREATE UNIQUE INDEX "Forum_studyGroupId_key" ON "Forum"("studyGroupId");

-- CreateIndex
CREATE INDEX "Forum_tenantId_scope_idx" ON "Forum"("tenantId", "scope");

-- CreateIndex
CREATE INDEX "Forum_tenantId_scopeId_idx" ON "Forum"("tenantId", "scopeId");

-- CreateIndex
CREATE INDEX "ForumTopic_forumId_isPinned_idx" ON "ForumTopic"("forumId", "isPinned");

-- CreateIndex
CREATE INDEX "ForumTopic_forumId_status_idx" ON "ForumTopic"("forumId", "status");

-- CreateIndex
CREATE INDEX "ForumTopic_authorId_idx" ON "ForumTopic"("authorId");

-- CreateIndex
CREATE UNIQUE INDEX "ForumTopic_forumId_slug_key" ON "ForumTopic"("forumId", "slug");

-- CreateIndex
CREATE INDEX "ForumPost_topicId_createdAt_idx" ON "ForumPost"("topicId", "createdAt");

-- CreateIndex
CREATE INDEX "ForumPost_topicId_parentId_idx" ON "ForumPost"("topicId", "parentId");

-- CreateIndex
CREATE INDEX "ForumPost_authorId_idx" ON "ForumPost"("authorId");

-- CreateIndex
CREATE INDEX "PostReaction_postId_idx" ON "PostReaction"("postId");

-- CreateIndex
CREATE UNIQUE INDEX "PostReaction_postId_userId_type_key" ON "PostReaction"("postId", "userId", "type");

-- CreateIndex
CREATE INDEX "TutorProfile_tenantId_status_idx" ON "TutorProfile"("tenantId", "status");

-- CreateIndex
CREATE INDEX "TutorProfile_tenantId_isAvailable_idx" ON "TutorProfile"("tenantId", "isAvailable");

-- CreateIndex
CREATE UNIQUE INDEX "TutorProfile_tenantId_userId_key" ON "TutorProfile"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "TutoringRequest_tenantId_status_idx" ON "TutoringRequest"("tenantId", "status");

-- CreateIndex
CREATE INDEX "TutoringRequest_tenantId_studentId_idx" ON "TutoringRequest"("tenantId", "studentId");

-- CreateIndex
CREATE INDEX "TutoringRequest_tenantId_tutorId_idx" ON "TutoringRequest"("tenantId", "tutorId");

-- CreateIndex
CREATE INDEX "TutoringSession_tenantId_status_idx" ON "TutoringSession"("tenantId", "status");

-- CreateIndex
CREATE INDEX "TutoringSession_tenantId_studentId_idx" ON "TutoringSession"("tenantId", "studentId");

-- CreateIndex
CREATE INDEX "TutoringSession_tenantId_tutorId_idx" ON "TutoringSession"("tenantId", "tutorId");

-- CreateIndex
CREATE INDEX "TutoringSession_scheduledAt_idx" ON "TutoringSession"("scheduledAt");

-- CreateIndex
CREATE INDEX "TutoringReview_tutorId_isVisible_idx" ON "TutoringReview"("tutorId", "isVisible");

-- CreateIndex
CREATE UNIQUE INDEX "TutoringReview_sessionId_reviewerId_key" ON "TutoringReview"("sessionId", "reviewerId");

-- CreateIndex
CREATE UNIQUE INDEX "ChatRoom_studyGroupId_key" ON "ChatRoom"("studyGroupId");

-- CreateIndex
CREATE UNIQUE INDEX "ChatRoom_tutoringSessionId_key" ON "ChatRoom"("tutoringSessionId");

-- CreateIndex
CREATE INDEX "ChatRoom_tenantId_type_idx" ON "ChatRoom"("tenantId", "type");

-- CreateIndex
CREATE INDEX "ChatRoom_tenantId_studyGroupId_idx" ON "ChatRoom"("tenantId", "studyGroupId");

-- CreateIndex
CREATE INDEX "ChatMessage_roomId_createdAt_idx" ON "ChatMessage"("roomId", "createdAt");

-- CreateIndex
CREATE INDEX "ChatMessage_roomId_senderId_idx" ON "ChatMessage"("roomId", "senderId");

-- CreateIndex
CREATE UNIQUE INDEX "ChatReadReceipt_roomId_userId_key" ON "ChatReadReceipt"("roomId", "userId");

-- CreateIndex
CREATE INDEX "SharedNote_tenantId_createdBy_idx" ON "SharedNote"("tenantId", "createdBy");

-- CreateIndex
CREATE INDEX "SharedNote_tenantId_studyGroupId_idx" ON "SharedNote"("tenantId", "studyGroupId");

-- CreateIndex
CREATE INDEX "NoteComment_noteId_isResolved_idx" ON "NoteComment"("noteId", "isResolved");

-- CreateIndex
CREATE INDEX "CollaborativeWhiteboard_tenantId_studyGroupId_idx" ON "CollaborativeWhiteboard"("tenantId", "studyGroupId");

-- CreateIndex
CREATE INDEX "SocialActivity_tenantId_createdAt_idx" ON "SocialActivity"("tenantId", "createdAt");

-- CreateIndex
CREATE INDEX "SocialActivity_tenantId_actorId_idx" ON "SocialActivity"("tenantId", "actorId");

-- CreateIndex
CREATE INDEX "SocialActivity_tenantId_studyGroupId_idx" ON "SocialActivity"("tenantId", "studyGroupId");

-- CreateIndex
CREATE INDEX "SocialNotification_tenantId_userId_isRead_idx" ON "SocialNotification"("tenantId", "userId", "isRead");

-- CreateIndex
CREATE INDEX "SocialNotification_tenantId_userId_createdAt_idx" ON "SocialNotification"("tenantId", "userId", "createdAt");

-- CreateIndex
CREATE UNIQUE INDEX "NotificationPreference_tenantId_userId_key" ON "NotificationPreference"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "VRLab_tenantId_category_idx" ON "VRLab"("tenantId", "category");

-- CreateIndex
CREATE INDEX "VRLab_tenantId_isPublished_idx" ON "VRLab"("tenantId", "isPublished");

-- CreateIndex
CREATE INDEX "VRLab_tenantId_difficulty_idx" ON "VRLab"("tenantId", "difficulty");

-- CreateIndex
CREATE UNIQUE INDEX "VRLab_tenantId_slug_key" ON "VRLab"("tenantId", "slug");

-- CreateIndex
CREATE INDEX "VRScene_labId_order_idx" ON "VRScene"("labId", "order");

-- CreateIndex
CREATE INDEX "VRInteractable_sceneId_idx" ON "VRInteractable"("sceneId");

-- CreateIndex
CREATE INDEX "VRLabObjective_labId_order_idx" ON "VRLabObjective"("labId", "order");

-- CreateIndex
CREATE INDEX "VRSession_tenantId_userId_idx" ON "VRSession"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "VRSession_tenantId_labId_idx" ON "VRSession"("tenantId", "labId");

-- CreateIndex
CREATE INDEX "VRSession_userId_status_idx" ON "VRSession"("userId", "status");

-- CreateIndex
CREATE INDEX "VRMultiplayerSession_tenantId_status_idx" ON "VRMultiplayerSession"("tenantId", "status");

-- CreateIndex
CREATE INDEX "VRMultiplayerSession_labId_status_idx" ON "VRMultiplayerSession"("labId", "status");

-- CreateIndex
CREATE INDEX "VRAsset_tenantId_type_idx" ON "VRAsset"("tenantId", "type");

-- CreateIndex
CREATE INDEX "VRAsset_tenantId_isPublic_idx" ON "VRAsset"("tenantId", "isPublic");

-- CreateIndex
CREATE INDEX "VRAnalyticsEvent_tenantId_eventType_idx" ON "VRAnalyticsEvent"("tenantId", "eventType");

-- CreateIndex
CREATE INDEX "VRAnalyticsEvent_tenantId_labId_idx" ON "VRAnalyticsEvent"("tenantId", "labId");

-- CreateIndex
CREATE INDEX "VRAnalyticsEvent_tenantId_timestamp_idx" ON "VRAnalyticsEvent"("tenantId", "timestamp");

-- CreateIndex
CREATE INDEX "DomainConcept_tenantId_domain_idx" ON "DomainConcept"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "DomainConcept_tenantId_level_idx" ON "DomainConcept"("tenantId", "level");

-- CreateIndex
CREATE INDEX "DomainConcept_tenantId_status_idx" ON "DomainConcept"("tenantId", "status");

-- CreateIndex
CREATE UNIQUE INDEX "DomainConcept_tenantId_externalId_key" ON "DomainConcept"("tenantId", "externalId");

-- CreateIndex
CREATE UNIQUE INDEX "ConceptModuleMapping_conceptId_key" ON "ConceptModuleMapping"("conceptId");

-- CreateIndex
CREATE INDEX "ConceptModuleMapping_moduleId_idx" ON "ConceptModuleMapping"("moduleId");

-- CreateIndex
CREATE INDEX "SimulationManifest_tenantId_domain_idx" ON "SimulationManifest"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "SimulationManifest_tenantId_moduleId_idx" ON "SimulationManifest"("tenantId", "moduleId");

-- CreateIndex
CREATE INDEX "SimulationTemplate_tenantId_domain_idx" ON "SimulationTemplate"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "SimulationTemplate_tenantId_difficulty_idx" ON "SimulationTemplate"("tenantId", "difficulty");

-- CreateIndex
CREATE INDEX "SimulationTemplate_tenantId_isPremium_idx" ON "SimulationTemplate"("tenantId", "isPremium");

-- CreateIndex
CREATE INDEX "SimulationTemplate_tenantId_isVerified_idx" ON "SimulationTemplate"("tenantId", "isVerified");

-- CreateIndex
CREATE INDEX "SimulationTemplate_tenantId_status_idx" ON "SimulationTemplate"("tenantId", "status");

-- CreateIndex
CREATE UNIQUE INDEX "SimulationTemplate_tenantId_slug_key" ON "SimulationTemplate"("tenantId", "slug");

-- CreateIndex
CREATE INDEX "Curriculum_tenantId_isPublished_idx" ON "Curriculum"("tenantId", "isPublished");

-- CreateIndex
CREATE UNIQUE INDEX "Curriculum_tenantId_domain_key" ON "Curriculum"("tenantId", "domain");

-- CreateIndex
CREATE UNIQUE INDEX "tenants_subdomain_key" ON "tenants"("subdomain");

-- CreateIndex
CREATE INDEX "tenants_subdomain_idx" ON "tenants"("subdomain");

-- CreateIndex
CREATE UNIQUE INDEX "TenantSettings_tenantId_key" ON "TenantSettings"("tenantId");

-- CreateIndex
CREATE INDEX "User_tenantId_role_idx" ON "User"("tenantId", "role");

-- CreateIndex
CREATE UNIQUE INDEX "User_tenantId_email_key" ON "User"("tenantId", "email");

-- CreateIndex
CREATE UNIQUE INDEX "LearnerProfile_userId_key" ON "LearnerProfile"("userId");

-- CreateIndex
CREATE INDEX "LearnerProfile_tenantId_userId_idx" ON "LearnerProfile"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "LearnerProfile_tenantId_updatedAt_idx" ON "LearnerProfile"("tenantId", "updatedAt");

-- CreateIndex
CREATE INDEX "LearnerMastery_tenantId_conceptId_idx" ON "LearnerMastery"("tenantId", "conceptId");

-- CreateIndex
CREATE INDEX "LearnerMastery_tenantId_masteryProbability_idx" ON "LearnerMastery"("tenantId", "masteryProbability");

-- CreateIndex
CREATE UNIQUE INDEX "LearnerMastery_profileId_conceptId_key" ON "LearnerMastery"("profileId", "conceptId");

-- CreateIndex
CREATE INDEX "KnowledgeGap_tenantId_conceptId_idx" ON "KnowledgeGap"("tenantId", "conceptId");

-- CreateIndex
CREATE INDEX "KnowledgeGap_tenantId_severity_idx" ON "KnowledgeGap"("tenantId", "severity");

-- CreateIndex
CREATE UNIQUE INDEX "KnowledgeGap_profileId_conceptId_prerequisiteId_status_key" ON "KnowledgeGap"("profileId", "conceptId", "prerequisiteId", "status");

-- CreateIndex
CREATE INDEX "PreferenceChange_tenantId_profileId_idx" ON "PreferenceChange"("tenantId", "profileId");

-- CreateIndex
CREATE INDEX "PreferenceChange_tenantId_createdAt_idx" ON "PreferenceChange"("tenantId", "createdAt");

-- CreateIndex
CREATE INDEX "LearningPathway_tenantId_profileId_idx" ON "LearningPathway"("tenantId", "profileId");

-- CreateIndex
CREATE INDEX "LearningPathway_tenantId_status_idx" ON "LearningPathway"("tenantId", "status");

-- CreateIndex
CREATE INDEX "IdentityProvider_tenantId_enabled_idx" ON "IdentityProvider"("tenantId", "enabled");

-- CreateIndex
CREATE INDEX "SsoUserLink_userId_idx" ON "SsoUserLink"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "SsoUserLink_providerId_externalId_key" ON "SsoUserLink"("providerId", "externalId");

-- CreateIndex
CREATE INDEX "DataExportRequest_tenantId_status_idx" ON "DataExportRequest"("tenantId", "status");

-- CreateIndex
CREATE INDEX "DataExportRequest_tenantId_userId_idx" ON "DataExportRequest"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "DataDeletionRequest_tenantId_status_idx" ON "DataDeletionRequest"("tenantId", "status");

-- CreateIndex
CREATE INDEX "DataDeletionRequest_tenantId_userId_idx" ON "DataDeletionRequest"("tenantId", "userId");

-- CreateIndex
CREATE UNIQUE INDEX "DeletionVerification_token_key" ON "DeletionVerification"("token");

-- CreateIndex
CREATE INDEX "DeletionVerification_userId_idx" ON "DeletionVerification"("userId");

-- CreateIndex
CREATE INDEX "AuditLog_tenantId_action_idx" ON "AuditLog"("tenantId", "action");

-- CreateIndex
CREATE INDEX "AuditLog_tenantId_resourceType_idx" ON "AuditLog"("tenantId", "resourceType");

-- CreateIndex
CREATE INDEX "AuditLog_tenantId_actorId_idx" ON "AuditLog"("tenantId", "actorId");

-- CreateIndex
CREATE INDEX "AuditLog_tenantId_timestamp_idx" ON "AuditLog"("tenantId", "timestamp");

-- CreateIndex
CREATE INDEX "ClassroomMember_classroomId_idx" ON "ClassroomMember"("classroomId");

-- CreateIndex
CREATE INDEX "ClassroomMember_userId_idx" ON "ClassroomMember"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "ClassroomMember_classroomId_userId_key" ON "ClassroomMember"("classroomId", "userId");

-- CreateIndex
CREATE INDEX "LearningPathEnrollment_tenantId_userId_idx" ON "LearningPathEnrollment"("tenantId", "userId");

-- CreateIndex
CREATE UNIQUE INDEX "LearningPathEnrollment_pathId_userId_key" ON "LearningPathEnrollment"("pathId", "userId");

-- CreateIndex
CREATE INDEX "DomainAuthor_tenantId_status_idx" ON "DomainAuthor"("tenantId", "status");

-- CreateIndex
CREATE UNIQUE INDEX "DomainAuthor_tenantId_domain_key" ON "DomainAuthor"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "DomainAuthorConcept_domainId_level_idx" ON "DomainAuthorConcept"("domainId", "level");

-- CreateIndex
CREATE UNIQUE INDEX "DomainAuthorConcept_domainId_name_key" ON "DomainAuthorConcept"("domainId", "name");

-- CreateIndex
CREATE UNIQUE INDEX "SimulationDefinition_conceptId_key" ON "SimulationDefinition"("conceptId");

-- CreateIndex
CREATE INDEX "SimulationDefinition_conceptId_idx" ON "SimulationDefinition"("conceptId");

-- CreateIndex
CREATE UNIQUE INDEX "VisualizationDefinition_conceptId_key" ON "VisualizationDefinition"("conceptId");

-- CreateIndex
CREATE INDEX "VisualizationDefinition_conceptId_idx" ON "VisualizationDefinition"("conceptId");

-- CreateIndex
CREATE INDEX "ContentExample_conceptId_difficulty_idx" ON "ContentExample"("conceptId", "difficulty");

-- CreateIndex
CREATE UNIQUE INDEX "ContentExample_conceptId_title_key" ON "ContentExample"("conceptId", "title");

-- CreateIndex
CREATE INDEX "VisualizationSnapshot_exampleId_idx" ON "VisualizationSnapshot"("exampleId");

-- CreateIndex
CREATE UNIQUE INDEX "VisualizationSnapshot_exampleId_stepNumber_key" ON "VisualizationSnapshot"("exampleId", "stepNumber");

-- CreateIndex
CREATE INDEX "Asset_tenantId_fileType_idx" ON "Asset"("tenantId", "fileType");

-- CreateIndex
CREATE INDEX "Asset_tenantId_status_idx" ON "Asset"("tenantId", "status");

-- CreateIndex
CREATE INDEX "Asset_uploadedBy_idx" ON "Asset"("uploadedBy");

-- CreateIndex
CREATE INDEX "LearningUnit_tenantId_domain_idx" ON "LearningUnit"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "LearningUnit_tenantId_status_idx" ON "LearningUnit"("tenantId", "status");

-- CreateIndex
CREATE UNIQUE INDEX "LearningExperience_moduleId_key" ON "LearningExperience"("moduleId");

-- CreateIndex
CREATE INDEX "LearningExperience_tenantId_status_idx" ON "LearningExperience"("tenantId", "status");

-- CreateIndex
CREATE INDEX "LearningExperience_tenantId_domain_idx" ON "LearningExperience"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "LearningExperience_createdBy_idx" ON "LearningExperience"("createdBy");

-- CreateIndex
CREATE INDEX "LearningExperience_tenantId_riskLevel_idx" ON "LearningExperience"("tenantId", "riskLevel");

-- CreateIndex
CREATE INDEX "LearningExperience_promptHash_idx" ON "LearningExperience"("promptHash");

-- CreateIndex
CREATE INDEX "LearningClaim_experienceId_idx" ON "LearningClaim"("experienceId");

-- CreateIndex
CREATE UNIQUE INDEX "LearningClaim_experienceId_claimRef_key" ON "LearningClaim"("experienceId", "claimRef");

-- CreateIndex
CREATE INDEX "LearningEvidence_experienceId_idx" ON "LearningEvidence"("experienceId");

-- CreateIndex
CREATE INDEX "LearningEvidence_claimRef_idx" ON "LearningEvidence"("claimRef");

-- CreateIndex
CREATE INDEX "LearningEvidence_sourceType_idx" ON "LearningEvidence"("sourceType");

-- CreateIndex
CREATE INDEX "LearningEvidence_freshnessStatus_idx" ON "LearningEvidence"("freshnessStatus");

-- CreateIndex
CREATE UNIQUE INDEX "LearningEvidence_experienceId_evidenceRef_key" ON "LearningEvidence"("experienceId", "evidenceRef");

-- CreateIndex
CREATE UNIQUE INDEX "EvidenceBundleMetadata_claimRef_key" ON "EvidenceBundleMetadata"("claimRef");

-- CreateIndex
CREATE INDEX "EvidenceBundleMetadata_bundleConfidence_idx" ON "EvidenceBundleMetadata"("bundleConfidence");

-- CreateIndex
CREATE INDEX "EvidenceBundleMetadata_freshnessOverall_idx" ON "EvidenceBundleMetadata"("freshnessOverall");

-- CreateIndex
CREATE UNIQUE INDEX "EvidenceBundleMetadata_experienceId_claimRef_key" ON "EvidenceBundleMetadata"("experienceId", "claimRef");

-- CreateIndex
CREATE INDEX "ExperienceTask_experienceId_idx" ON "ExperienceTask"("experienceId");

-- CreateIndex
CREATE UNIQUE INDEX "ExperienceTask_experienceId_taskRef_key" ON "ExperienceTask"("experienceId", "taskRef");

-- CreateIndex
CREATE INDEX "ValidationRecord_experienceId_idx" ON "ValidationRecord"("experienceId");

-- CreateIndex
CREATE INDEX "AIGenerationLog_tenantId_createdAt_idx" ON "AIGenerationLog"("tenantId", "createdAt");

-- CreateIndex
CREATE INDEX "AIGenerationLog_userId_createdAt_idx" ON "AIGenerationLog"("userId", "createdAt");

-- CreateIndex
CREATE INDEX "AIGenerationLog_experienceId_idx" ON "AIGenerationLog"("experienceId");

-- CreateIndex
CREATE INDEX "AIGenerationLog_promptHash_idx" ON "AIGenerationLog"("promptHash");

-- CreateIndex
CREATE INDEX "ExperienceRevision_experienceId_version_idx" ON "ExperienceRevision"("experienceId", "version");

-- CreateIndex
CREATE INDEX "ExperienceRevision_experienceId_createdAt_idx" ON "ExperienceRevision"("experienceId", "createdAt");

-- CreateIndex
CREATE INDEX "ExperienceEvent_experienceId_eventType_idx" ON "ExperienceEvent"("experienceId", "eventType");

-- CreateIndex
CREATE INDEX "ExperienceEvent_experienceId_createdAt_idx" ON "ExperienceEvent"("experienceId", "createdAt");

-- CreateIndex
CREATE INDEX "SimulationManifestVersion_manifestId_status_idx" ON "SimulationManifestVersion"("manifestId", "status");

-- CreateIndex
CREATE UNIQUE INDEX "SimulationManifestVersion_manifestId_version_key" ON "SimulationManifestVersion"("manifestId", "version");

-- CreateIndex
CREATE INDEX "SimulationLinkAudit_experienceId_createdAt_idx" ON "SimulationLinkAudit"("experienceId", "createdAt");

-- CreateIndex
CREATE INDEX "SimulationLinkAudit_simulationManifestId_createdAt_idx" ON "SimulationLinkAudit"("simulationManifestId", "createdAt");

-- CreateIndex
CREATE UNIQUE INDEX "AIPromptCache_rawIntentHash_key" ON "AIPromptCache"("rawIntentHash");

-- CreateIndex
CREATE INDEX "AIPromptCache_tenantId_rawIntentHash_idx" ON "AIPromptCache"("tenantId", "rawIntentHash");

-- CreateIndex
CREATE INDEX "AIPromptCache_tenantId_flowType_idx" ON "AIPromptCache"("tenantId", "flowType");

-- CreateIndex
CREATE INDEX "AIPromptCache_expiresAt_idx" ON "AIPromptCache"("expiresAt");

-- CreateIndex
CREATE INDEX "ValidationRecordExtended_experienceId_inputHash_idx" ON "ValidationRecordExtended"("experienceId", "inputHash");

-- CreateIndex
CREATE INDEX "ValidationRecordExtended_experienceId_validatedAt_idx" ON "ValidationRecordExtended"("experienceId", "validatedAt");

-- CreateIndex
CREATE INDEX "ReviewQueue_tenantId_assignedTo_idx" ON "ReviewQueue"("tenantId", "assignedTo");

-- CreateIndex
CREATE INDEX "ReviewQueue_tenantId_queuedAt_idx" ON "ReviewQueue"("tenantId", "queuedAt");

-- CreateIndex
CREATE INDEX "ReviewQueue_experienceId_idx" ON "ReviewQueue"("experienceId");

-- CreateIndex
CREATE INDEX "ReviewDecision_experienceId_createdAt_idx" ON "ReviewDecision"("experienceId", "createdAt");

-- CreateIndex
CREATE INDEX "ReviewDecision_reviewerId_createdAt_idx" ON "ReviewDecision"("reviewerId", "createdAt");

-- CreateIndex
CREATE UNIQUE INDEX "ExperienceAnalytics_experienceId_key" ON "ExperienceAnalytics"("experienceId");

-- CreateIndex
CREATE INDEX "ExperienceAnalytics_experienceId_idx" ON "ExperienceAnalytics"("experienceId");

-- CreateIndex
CREATE INDEX "ExperienceAutoRefinement_experienceId_idx" ON "ExperienceAutoRefinement"("experienceId");

-- CreateIndex
CREATE INDEX "ExperienceAutoRefinement_status_idx" ON "ExperienceAutoRefinement"("status");

-- CreateIndex
CREATE INDEX "ClaimExample_experienceId_claimRef_idx" ON "ClaimExample"("experienceId", "claimRef");

-- CreateIndex
CREATE INDEX "ClaimExample_type_idx" ON "ClaimExample"("type");

-- CreateIndex
CREATE INDEX "ClaimExample_exampleFamily_idx" ON "ClaimExample"("exampleFamily");

-- CreateIndex
CREATE INDEX "ClaimExample_manifestId_idx" ON "ClaimExample"("manifestId");

-- CreateIndex
CREATE INDEX "ClaimSimulation_experienceId_idx" ON "ClaimSimulation"("experienceId");

-- CreateIndex
CREATE INDEX "ClaimSimulation_simulationManifestId_idx" ON "ClaimSimulation"("simulationManifestId");

-- CreateIndex
CREATE UNIQUE INDEX "ClaimSimulation_experienceId_claimRef_key" ON "ClaimSimulation"("experienceId", "claimRef");

-- CreateIndex
CREATE INDEX "ClaimAnimation_experienceId_idx" ON "ClaimAnimation"("experienceId");

-- CreateIndex
CREATE INDEX "ClaimAnimation_isPrimary_idx" ON "ClaimAnimation"("isPrimary");

-- CreateIndex
CREATE INDEX "ClaimAnimation_manifestId_idx" ON "ClaimAnimation"("manifestId");

-- CreateIndex
CREATE UNIQUE INDEX "ClaimAnimation_experienceId_claimRef_variantKey_key" ON "ClaimAnimation"("experienceId", "claimRef", "variantKey");

-- CreateIndex
CREATE INDEX "ABExperiment_tenantId_status_idx" ON "ABExperiment"("tenantId", "status");

-- CreateIndex
CREATE INDEX "ABExperiment_experienceId_idx" ON "ABExperiment"("experienceId");

-- CreateIndex
CREATE INDEX "ABExperiment_status_createdAt_idx" ON "ABExperiment"("status", "createdAt");

-- CreateIndex
CREATE INDEX "ABExperimentAssignment_tenantId_variant_idx" ON "ABExperimentAssignment"("tenantId", "variant");

-- CreateIndex
CREATE INDEX "ABExperimentAssignment_experimentId_variant_idx" ON "ABExperimentAssignment"("experimentId", "variant");

-- CreateIndex
CREATE UNIQUE INDEX "ABExperimentAssignment_experimentId_userId_key" ON "ABExperimentAssignment"("experimentId", "userId");

-- CreateIndex
CREATE INDEX "ABExperimentObservation_experimentId_variant_idx" ON "ABExperimentObservation"("experimentId", "variant");

-- CreateIndex
CREATE INDEX "ABExperimentObservation_tenantId_observedAt_idx" ON "ABExperimentObservation"("tenantId", "observedAt");

-- CreateIndex
CREATE INDEX "ABExperimentObservation_experimentId_userId_idx" ON "ABExperimentObservation"("experimentId", "userId");

-- CreateIndex
CREATE UNIQUE INDEX "DriftSignal_autoRefinementId_key" ON "DriftSignal"("autoRefinementId");

-- CreateIndex
CREATE INDEX "DriftSignal_tenantId_experienceId_status_idx" ON "DriftSignal"("tenantId", "experienceId", "status");

-- CreateIndex
CREATE INDEX "DriftSignal_signalType_severity_idx" ON "DriftSignal"("signalType", "severity");

-- CreateIndex
CREATE INDEX "DriftSignal_detectedAt_idx" ON "DriftSignal"("detectedAt");

-- CreateIndex
CREATE UNIQUE INDEX "RegenerationInsight_autoRefinementId_key" ON "RegenerationInsight"("autoRefinementId");

-- CreateIndex
CREATE INDEX "RegenerationInsight_tenantId_experienceId_category_idx" ON "RegenerationInsight"("tenantId", "experienceId", "category");

-- CreateIndex
CREATE INDEX "RegenerationInsight_priority_status_idx" ON "RegenerationInsight"("priority", "status");

-- CreateIndex
CREATE INDEX "RegenerationInsight_createdAt_idx" ON "RegenerationInsight"("createdAt");

-- CreateIndex
CREATE UNIQUE INDEX "AutoRevisionConfig_tenantId_key" ON "AutoRevisionConfig"("tenantId");

-- CreateIndex
CREATE INDEX "AutoRevisionMetrics_date_idx" ON "AutoRevisionMetrics"("date");

-- CreateIndex
CREATE UNIQUE INDEX "AutoRevisionMetrics_tenantId_date_key" ON "AutoRevisionMetrics"("tenantId", "date");

-- CreateIndex
CREATE INDEX "Badge_tenantId_idx" ON "Badge"("tenantId");

-- CreateIndex
CREATE INDEX "Badge_tenantId_isActive_idx" ON "Badge"("tenantId", "isActive");

-- CreateIndex
CREATE INDEX "BadgeEarned_tenantId_userId_idx" ON "BadgeEarned"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "BadgeEarned_tenantId_badgeId_idx" ON "BadgeEarned"("tenantId", "badgeId");

-- CreateIndex
CREATE UNIQUE INDEX "BadgeEarned_tenantId_userId_badgeId_key" ON "BadgeEarned"("tenantId", "userId", "badgeId");

-- CreateIndex
CREATE INDEX "UserPoints_tenantId_totalPoints_idx" ON "UserPoints"("tenantId", "totalPoints" DESC);

-- CreateIndex
CREATE UNIQUE INDEX "UserPoints_tenantId_userId_key" ON "UserPoints"("tenantId", "userId");

-- CreateIndex
CREATE UNIQUE INDEX "DeviceToken_token_key" ON "DeviceToken"("token");

-- CreateIndex
CREATE INDEX "DeviceToken_tenantId_userId_idx" ON "DeviceToken"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "DeviceToken_tenantId_userId_isActive_idx" ON "DeviceToken"("tenantId", "userId", "isActive");

-- CreateIndex
CREATE UNIQUE INDEX "KernelPlugin_pluginId_key" ON "KernelPlugin"("pluginId");

-- CreateIndex
CREATE INDEX "KernelPlugin_tenantId_kernelType_idx" ON "KernelPlugin"("tenantId", "kernelType");

-- CreateIndex
CREATE INDEX "KernelPlugin_tenantId_idx" ON "KernelPlugin"("tenantId");

-- CreateIndex
CREATE INDEX "AutomationRule_tenantId_experienceId_idx" ON "AutomationRule"("tenantId", "experienceId");

-- CreateIndex
CREATE INDEX "AutomationRule_tenantId_enabled_idx" ON "AutomationRule"("tenantId", "enabled");

-- CreateIndex
CREATE UNIQUE INDEX "ContentAsset_legacyModuleId_key" ON "ContentAsset"("legacyModuleId");

-- CreateIndex
CREATE UNIQUE INDEX "ContentAsset_legacyExperienceId_key" ON "ContentAsset"("legacyExperienceId");

-- CreateIndex
CREATE INDEX "ContentAsset_tenantId_assetType_status_idx" ON "ContentAsset"("tenantId", "assetType", "status");

-- CreateIndex
CREATE INDEX "ContentAsset_tenantId_domain_idx" ON "ContentAsset"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "ContentAsset_tenantId_status_idx" ON "ContentAsset"("tenantId", "status");

-- CreateIndex
CREATE INDEX "ContentAsset_authorId_idx" ON "ContentAsset"("authorId");

-- CreateIndex
CREATE INDEX "ContentAsset_tenantId_semanticIndexStatus_idx" ON "ContentAsset"("tenantId", "semanticIndexStatus");

-- CreateIndex
CREATE INDEX "ContentAsset_legacyModuleId_idx" ON "ContentAsset"("legacyModuleId");

-- CreateIndex
CREATE INDEX "ContentAsset_legacyExperienceId_idx" ON "ContentAsset"("legacyExperienceId");

-- CreateIndex
CREATE UNIQUE INDEX "ContentAsset_tenantId_slug_key" ON "ContentAsset"("tenantId", "slug");

-- CreateIndex
CREATE INDEX "ContentAssetRevision_assetId_idx" ON "ContentAssetRevision"("assetId");

-- CreateIndex
CREATE UNIQUE INDEX "ContentAssetRevision_assetId_version_key" ON "ContentAssetRevision"("assetId", "version");

-- CreateIndex
CREATE INDEX "ContentBlock_assetId_orderIndex_idx" ON "ContentBlock"("assetId", "orderIndex");

-- CreateIndex
CREATE INDEX "ContentBlock_assetId_blockType_idx" ON "ContentBlock"("assetId", "blockType");

-- CreateIndex
CREATE UNIQUE INDEX "ContentBlock_assetId_blockRef_key" ON "ContentBlock"("assetId", "blockRef");

-- CreateIndex
CREATE INDEX "ArtifactManifest_assetId_manifestType_idx" ON "ArtifactManifest"("assetId", "manifestType");

-- CreateIndex
CREATE INDEX "ArtifactManifest_assetId_claimRef_idx" ON "ArtifactManifest"("assetId", "claimRef");

-- CreateIndex
CREATE INDEX "SemanticChunk_assetId_source_idx" ON "SemanticChunk"("assetId", "source");

-- CreateIndex
CREATE INDEX "SemanticChunk_assetId_embeddingStatus_idx" ON "SemanticChunk"("assetId", "embeddingStatus");

-- CreateIndex
CREATE INDEX "SemanticChunk_contentHash_idx" ON "SemanticChunk"("contentHash");

-- CreateIndex
CREATE UNIQUE INDEX "SemanticChunk_assetId_chunkRef_key" ON "SemanticChunk"("assetId", "chunkRef");

-- CreateIndex
CREATE UNIQUE INDEX "EmbeddingVector_chunkId_key" ON "EmbeddingVector"("chunkId");

-- CreateIndex
CREATE INDEX "EmbeddingVector_chunkId_idx" ON "EmbeddingVector"("chunkId");

-- CreateIndex
CREATE INDEX "RecommendationEdge_sourceAssetId_edgeType_idx" ON "RecommendationEdge"("sourceAssetId", "edgeType");

-- CreateIndex
CREATE INDEX "RecommendationEdge_targetAssetId_edgeType_idx" ON "RecommendationEdge"("targetAssetId", "edgeType");

-- CreateIndex
CREATE INDEX "RecommendationEdge_sourceAssetId_weight_idx" ON "RecommendationEdge"("sourceAssetId", "weight");

-- CreateIndex
CREATE UNIQUE INDEX "RecommendationEdge_sourceAssetId_targetAssetId_edgeType_key" ON "RecommendationEdge"("sourceAssetId", "targetAssetId", "edgeType");

-- CreateIndex
CREATE INDEX "GenerationRequest_tenantId_status_idx" ON "GenerationRequest"("tenantId", "status");

-- CreateIndex
CREATE INDEX "GenerationRequest_tenantId_requestedBy_idx" ON "GenerationRequest"("tenantId", "requestedBy");

-- CreateIndex
CREATE INDEX "GenerationRequest_tenantId_domain_idx" ON "GenerationRequest"("tenantId", "domain");

-- CreateIndex
CREATE INDEX "GenerationJob_requestId_status_idx" ON "GenerationJob"("requestId", "status");

-- CreateIndex
CREATE INDEX "GenerationJob_requestId_jobType_idx" ON "GenerationJob"("requestId", "jobType");

-- CreateIndex
CREATE INDEX "EvaluationRecord_tenantId_status_idx" ON "EvaluationRecord"("tenantId", "status");

-- CreateIndex
CREATE INDEX "EvaluationRecord_tenantId_assetId_idx" ON "EvaluationRecord"("tenantId", "assetId");

-- CreateIndex
CREATE INDEX "EvaluationRecord_generationJobId_idx" ON "EvaluationRecord"("generationJobId");

-- CreateIndex
CREATE INDEX "EvaluationRecord_generationRequestId_idx" ON "EvaluationRecord"("generationRequestId");

-- CreateIndex
CREATE INDEX "GenerationReviewDecision_tenantId_requestId_idx" ON "GenerationReviewDecision"("tenantId", "requestId");

-- CreateIndex
CREATE INDEX "GenerationReviewDecision_tenantId_status_idx" ON "GenerationReviewDecision"("tenantId", "status");

-- CreateIndex
CREATE INDEX "ExplorerEvent_tenantId_eventType_idx" ON "ExplorerEvent"("tenantId", "eventType");

-- CreateIndex
CREATE INDEX "ExplorerEvent_tenantId_assetId_idx" ON "ExplorerEvent"("tenantId", "assetId");

-- CreateIndex
CREATE INDEX "ExplorerEvent_tenantId_userId_idx" ON "ExplorerEvent"("tenantId", "userId");

-- CreateIndex
CREATE INDEX "ExplorerEvent_occurredAt_idx" ON "ExplorerEvent"("occurredAt");

-- CreateIndex
CREATE INDEX "RegenerationCandidate_tenantId_status_idx" ON "RegenerationCandidate"("tenantId", "status");

-- CreateIndex
CREATE INDEX "RegenerationCandidate_tenantId_assetId_idx" ON "RegenerationCandidate"("tenantId", "assetId");

-- CreateIndex
CREATE INDEX "RegenerationCandidate_tenantId_trigger_idx" ON "RegenerationCandidate"("tenantId", "trigger");

-- AddForeignKey
ALTER TABLE "ModuleTag" ADD CONSTRAINT "ModuleTag_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ModuleLearningObjective" ADD CONSTRAINT "ModuleLearningObjective_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ModuleContentBlock" ADD CONSTRAINT "ModuleContentBlock_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ModulePrerequisite" ADD CONSTRAINT "ModulePrerequisite_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ModulePrerequisite" ADD CONSTRAINT "ModulePrerequisite_prerequisiteModuleId_fkey" FOREIGN KEY ("prerequisiteModuleId") REFERENCES "Module"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ModuleRevision" ADD CONSTRAINT "ModuleRevision_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Enrollment" ADD CONSTRAINT "Enrollment_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Assessment" ADD CONSTRAINT "Assessment_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AssessmentObjective" ADD CONSTRAINT "AssessmentObjective_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "Assessment"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AssessmentItem" ADD CONSTRAINT "AssessmentItem_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "Assessment"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AssessmentAttempt" ADD CONSTRAINT "AssessmentAttempt_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "Assessment"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "MarketplaceListing" ADD CONSTRAINT "MarketplaceListing_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "LearningPathNode" ADD CONSTRAINT "LearningPathNode_pathId_fkey" FOREIGN KEY ("pathId") REFERENCES "LearningPath"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ClassroomStudent" ADD CONSTRAINT "ClassroomStudent_classroomId_fkey" FOREIGN KEY ("classroomId") REFERENCES "Classroom"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ClassroomAssignment" ADD CONSTRAINT "ClassroomAssignment_classroomId_fkey" FOREIGN KEY ("classroomId") REFERENCES "Classroom"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Post" ADD CONSTRAINT "Post_threadId_fkey" FOREIGN KEY ("threadId") REFERENCES "Thread"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Subscription" ADD CONSTRAINT "Subscription_stripeCustomerId_fkey" FOREIGN KEY ("stripeCustomerId") REFERENCES "StripeCustomer"("stripeCustomerId") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PaymentMethod" ADD CONSTRAINT "PaymentMethod_stripeCustomerId_fkey" FOREIGN KEY ("stripeCustomerId") REFERENCES "StripeCustomer"("stripeCustomerId") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Invoice" ADD CONSTRAINT "Invoice_stripeCustomerId_fkey" FOREIGN KEY ("stripeCustomerId") REFERENCES "StripeCustomer"("stripeCustomerId") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Invoice" ADD CONSTRAINT "Invoice_subscriptionId_fkey" FOREIGN KEY ("subscriptionId") REFERENCES "Subscription"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Transaction" ADD CONSTRAINT "Transaction_invoiceId_fkey" FOREIGN KEY ("invoiceId") REFERENCES "Invoice"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Transaction" ADD CONSTRAINT "Transaction_paymentMethodId_fkey" FOREIGN KEY ("paymentMethodId") REFERENCES "PaymentMethod"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "UsageSnapshot" ADD CONSTRAINT "UsageSnapshot_subscriptionId_fkey" FOREIGN KEY ("subscriptionId") REFERENCES "Subscription"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "StudyGroupMember" ADD CONSTRAINT "StudyGroupMember_groupId_fkey" FOREIGN KEY ("groupId") REFERENCES "StudyGroup"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "StudyGroupJoinRequest" ADD CONSTRAINT "StudyGroupJoinRequest_groupId_fkey" FOREIGN KEY ("groupId") REFERENCES "StudyGroup"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "StudyGroupInvite" ADD CONSTRAINT "StudyGroupInvite_groupId_fkey" FOREIGN KEY ("groupId") REFERENCES "StudyGroup"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "StudySession" ADD CONSTRAINT "StudySession_groupId_fkey" FOREIGN KEY ("groupId") REFERENCES "StudyGroup"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SessionRsvp" ADD CONSTRAINT "SessionRsvp_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "StudySession"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Forum" ADD CONSTRAINT "Forum_studyGroupId_fkey" FOREIGN KEY ("studyGroupId") REFERENCES "StudyGroup"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ForumTopic" ADD CONSTRAINT "ForumTopic_forumId_fkey" FOREIGN KEY ("forumId") REFERENCES "Forum"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ForumPost" ADD CONSTRAINT "ForumPost_topicId_fkey" FOREIGN KEY ("topicId") REFERENCES "ForumTopic"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PostReaction" ADD CONSTRAINT "PostReaction_postId_fkey" FOREIGN KEY ("postId") REFERENCES "ForumPost"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TutoringRequest" ADD CONSTRAINT "TutoringRequest_tutorId_fkey" FOREIGN KEY ("tutorId") REFERENCES "TutorProfile"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TutoringSession" ADD CONSTRAINT "TutoringSession_requestId_fkey" FOREIGN KEY ("requestId") REFERENCES "TutoringRequest"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TutoringSession" ADD CONSTRAINT "TutoringSession_tutorId_fkey" FOREIGN KEY ("tutorId") REFERENCES "TutorProfile"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TutoringReview" ADD CONSTRAINT "TutoringReview_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "TutoringSession"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TutoringReview" ADD CONSTRAINT "TutoringReview_tutorId_fkey" FOREIGN KEY ("tutorId") REFERENCES "TutorProfile"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ChatRoom" ADD CONSTRAINT "ChatRoom_studyGroupId_fkey" FOREIGN KEY ("studyGroupId") REFERENCES "StudyGroup"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ChatRoom" ADD CONSTRAINT "ChatRoom_tutoringSessionId_fkey" FOREIGN KEY ("tutoringSessionId") REFERENCES "TutoringSession"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ChatMessage" ADD CONSTRAINT "ChatMessage_roomId_fkey" FOREIGN KEY ("roomId") REFERENCES "ChatRoom"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ChatReadReceipt" ADD CONSTRAINT "ChatReadReceipt_roomId_fkey" FOREIGN KEY ("roomId") REFERENCES "ChatRoom"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SharedNote" ADD CONSTRAINT "SharedNote_studyGroupId_fkey" FOREIGN KEY ("studyGroupId") REFERENCES "StudyGroup"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "NoteComment" ADD CONSTRAINT "NoteComment_noteId_fkey" FOREIGN KEY ("noteId") REFERENCES "SharedNote"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "VRScene" ADD CONSTRAINT "VRScene_labId_fkey" FOREIGN KEY ("labId") REFERENCES "VRLab"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "VRInteractable" ADD CONSTRAINT "VRInteractable_sceneId_fkey" FOREIGN KEY ("sceneId") REFERENCES "VRScene"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "VRLabObjective" ADD CONSTRAINT "VRLabObjective_labId_fkey" FOREIGN KEY ("labId") REFERENCES "VRLab"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "VRSession" ADD CONSTRAINT "VRSession_labId_fkey" FOREIGN KEY ("labId") REFERENCES "VRLab"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "VRMultiplayerSession" ADD CONSTRAINT "VRMultiplayerSession_labId_fkey" FOREIGN KEY ("labId") REFERENCES "VRLab"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ConceptPrerequisite" ADD CONSTRAINT "ConceptPrerequisite_conceptId_fkey" FOREIGN KEY ("conceptId") REFERENCES "DomainConcept"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ConceptPrerequisite" ADD CONSTRAINT "ConceptPrerequisite_prerequisiteId_fkey" FOREIGN KEY ("prerequisiteId") REFERENCES "DomainConcept"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ConceptModuleMapping" ADD CONSTRAINT "ConceptModuleMapping_conceptId_fkey" FOREIGN KEY ("conceptId") REFERENCES "DomainConcept"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SimulationManifest" ADD CONSTRAINT "SimulationManifest_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SimulationTemplate" ADD CONSTRAINT "SimulationTemplate_conceptId_fkey" FOREIGN KEY ("conceptId") REFERENCES "DomainConcept"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SimulationTemplate" ADD CONSTRAINT "SimulationTemplate_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SimulationTemplate" ADD CONSTRAINT "SimulationTemplate_manifestId_fkey" FOREIGN KEY ("manifestId") REFERENCES "SimulationManifest"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TenantSettings" ADD CONSTRAINT "TenantSettings_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "tenants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "User" ADD CONSTRAINT "User_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "tenants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "LearnerProfile" ADD CONSTRAINT "LearnerProfile_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "LearnerMastery" ADD CONSTRAINT "LearnerMastery_profileId_fkey" FOREIGN KEY ("profileId") REFERENCES "LearnerProfile"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "KnowledgeGap" ADD CONSTRAINT "KnowledgeGap_profileId_fkey" FOREIGN KEY ("profileId") REFERENCES "LearnerProfile"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PreferenceChange" ADD CONSTRAINT "PreferenceChange_profileId_fkey" FOREIGN KEY ("profileId") REFERENCES "LearnerProfile"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "LearningPathway" ADD CONSTRAINT "LearningPathway_profileId_fkey" FOREIGN KEY ("profileId") REFERENCES "LearnerProfile"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "IdentityProvider" ADD CONSTRAINT "IdentityProvider_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "tenants"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SsoUserLink" ADD CONSTRAINT "SsoUserLink_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SsoUserLink" ADD CONSTRAINT "SsoUserLink_providerId_fkey" FOREIGN KEY ("providerId") REFERENCES "IdentityProvider"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DomainAuthorConcept" ADD CONSTRAINT "DomainAuthorConcept_domainId_fkey" FOREIGN KEY ("domainId") REFERENCES "DomainAuthor"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SimulationDefinition" ADD CONSTRAINT "SimulationDefinition_conceptId_fkey" FOREIGN KEY ("conceptId") REFERENCES "DomainAuthorConcept"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "VisualizationDefinition" ADD CONSTRAINT "VisualizationDefinition_conceptId_fkey" FOREIGN KEY ("conceptId") REFERENCES "DomainAuthorConcept"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ContentExample" ADD CONSTRAINT "ContentExample_conceptId_fkey" FOREIGN KEY ("conceptId") REFERENCES "DomainAuthorConcept"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "VisualizationSnapshot" ADD CONSTRAINT "VisualizationSnapshot_exampleId_fkey" FOREIGN KEY ("exampleId") REFERENCES "ContentExample"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "LearningExperience" ADD CONSTRAINT "LearningExperience_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "LearningExperience" ADD CONSTRAINT "LearningExperience_simulationManifestId_fkey" FOREIGN KEY ("simulationManifestId") REFERENCES "SimulationManifest"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "LearningClaim" ADD CONSTRAINT "LearningClaim_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "LearningEvidence" ADD CONSTRAINT "LearningEvidence_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "LearningEvidence" ADD CONSTRAINT "LearningEvidence_experienceId_claimRef_fkey" FOREIGN KEY ("experienceId", "claimRef") REFERENCES "LearningClaim"("experienceId", "claimRef") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "EvidenceBundleMetadata" ADD CONSTRAINT "EvidenceBundleMetadata_experienceId_claimRef_fkey" FOREIGN KEY ("experienceId", "claimRef") REFERENCES "LearningClaim"("experienceId", "claimRef") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ExperienceTask" ADD CONSTRAINT "ExperienceTask_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ValidationRecord" ADD CONSTRAINT "ValidationRecord_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ExperienceRevision" ADD CONSTRAINT "ExperienceRevision_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ExperienceEvent" ADD CONSTRAINT "ExperienceEvent_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SimulationManifestVersion" ADD CONSTRAINT "SimulationManifestVersion_manifestId_fkey" FOREIGN KEY ("manifestId") REFERENCES "SimulationManifest"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SimulationManifestExtension" ADD CONSTRAINT "SimulationManifestExtension_id_fkey" FOREIGN KEY ("id") REFERENCES "SimulationManifest"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SimulationLinkAudit" ADD CONSTRAINT "SimulationLinkAudit_simulationManifestId_fkey" FOREIGN KEY ("simulationManifestId") REFERENCES "SimulationManifest"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ValidationRecordExtended" ADD CONSTRAINT "ValidationRecordExtended_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ReviewQueue" ADD CONSTRAINT "ReviewQueue_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ReviewDecision" ADD CONSTRAINT "ReviewDecision_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ExperienceAnalytics" ADD CONSTRAINT "ExperienceAnalytics_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ExperienceAutoRefinement" ADD CONSTRAINT "ExperienceAutoRefinement_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ClaimExample" ADD CONSTRAINT "ClaimExample_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ClaimExample" ADD CONSTRAINT "ClaimExample_experienceId_claimRef_fkey" FOREIGN KEY ("experienceId", "claimRef") REFERENCES "LearningClaim"("experienceId", "claimRef") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ClaimSimulation" ADD CONSTRAINT "ClaimSimulation_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ClaimSimulation" ADD CONSTRAINT "ClaimSimulation_experienceId_claimRef_fkey" FOREIGN KEY ("experienceId", "claimRef") REFERENCES "LearningClaim"("experienceId", "claimRef") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ClaimSimulation" ADD CONSTRAINT "ClaimSimulation_simulationManifestId_fkey" FOREIGN KEY ("simulationManifestId") REFERENCES "SimulationManifest"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ClaimAnimation" ADD CONSTRAINT "ClaimAnimation_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ClaimAnimation" ADD CONSTRAINT "ClaimAnimation_experienceId_claimRef_fkey" FOREIGN KEY ("experienceId", "claimRef") REFERENCES "LearningClaim"("experienceId", "claimRef") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ABExperiment" ADD CONSTRAINT "ABExperiment_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ABExperimentAssignment" ADD CONSTRAINT "ABExperimentAssignment_experimentId_fkey" FOREIGN KEY ("experimentId") REFERENCES "ABExperiment"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ABExperimentObservation" ADD CONSTRAINT "ABExperimentObservation_experimentId_fkey" FOREIGN KEY ("experimentId") REFERENCES "ABExperiment"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DriftSignal" ADD CONSTRAINT "DriftSignal_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DriftSignal" ADD CONSTRAINT "DriftSignal_autoRefinementId_fkey" FOREIGN KEY ("autoRefinementId") REFERENCES "ExperienceAutoRefinement"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RegenerationInsight" ADD CONSTRAINT "RegenerationInsight_experienceId_fkey" FOREIGN KEY ("experienceId") REFERENCES "LearningExperience"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RegenerationInsight" ADD CONSTRAINT "RegenerationInsight_autoRefinementId_fkey" FOREIGN KEY ("autoRefinementId") REFERENCES "ExperienceAutoRefinement"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "BadgeEarned" ADD CONSTRAINT "BadgeEarned_badgeId_fkey" FOREIGN KEY ("badgeId") REFERENCES "Badge"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ContentAssetRevision" ADD CONSTRAINT "ContentAssetRevision_assetId_fkey" FOREIGN KEY ("assetId") REFERENCES "ContentAsset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ContentBlock" ADD CONSTRAINT "ContentBlock_assetId_fkey" FOREIGN KEY ("assetId") REFERENCES "ContentAsset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ArtifactManifest" ADD CONSTRAINT "ArtifactManifest_assetId_fkey" FOREIGN KEY ("assetId") REFERENCES "ContentAsset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SemanticChunk" ADD CONSTRAINT "SemanticChunk_assetId_fkey" FOREIGN KEY ("assetId") REFERENCES "ContentAsset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "EmbeddingVector" ADD CONSTRAINT "EmbeddingVector_chunkId_fkey" FOREIGN KEY ("chunkId") REFERENCES "SemanticChunk"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RecommendationEdge" ADD CONSTRAINT "RecommendationEdge_sourceAssetId_fkey" FOREIGN KEY ("sourceAssetId") REFERENCES "ContentAsset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RecommendationEdge" ADD CONSTRAINT "RecommendationEdge_targetAssetId_fkey" FOREIGN KEY ("targetAssetId") REFERENCES "ContentAsset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "GenerationJob" ADD CONSTRAINT "GenerationJob_requestId_fkey" FOREIGN KEY ("requestId") REFERENCES "GenerationRequest"("id") ON DELETE CASCADE ON UPDATE CASCADE;
