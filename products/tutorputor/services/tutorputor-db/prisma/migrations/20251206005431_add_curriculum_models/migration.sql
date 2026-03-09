/*
  Warnings:

  - You are about to alter the column `feedback` on the `AssessmentAttempt` table. The data in that column could be lost. The data in that column will be cast from `String` to `Json`.
  - You are about to alter the column `responses` on the `AssessmentAttempt` table. The data in that column could be lost. The data in that column will be cast from `String` to `Json`.
  - You are about to alter the column `payload` on the `AssessmentDraft` table. The data in that column could be lost. The data in that column will be cast from `String` to `Json`.
  - You are about to alter the column `choices` on the `AssessmentItem` table. The data in that column could be lost. The data in that column will be cast from `String` to `Json`.
  - You are about to alter the column `metadata` on the `AssessmentItem` table. The data in that column could be lost. The data in that column will be cast from `String` to `Json`.
  - You are about to alter the column `payload` on the `LearningEvent` table. The data in that column could be lost. The data in that column will be cast from `String` to `Json`.
  - You are about to alter the column `payload` on the `ModuleContentBlock` table. The data in that column could be lost. The data in that column will be cast from `String` to `Json`.
  - You are about to alter the column `snapshot` on the `ModuleRevision` table. The data in that column could be lost. The data in that column will be cast from `String` to `Json`.

*/
-- CreateTable
CREATE TABLE "LearningPath" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "goal" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'ACTIVE',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "LearningPathNode" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "pathId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "orderIndex" INTEGER NOT NULL,
    "isOptional" BOOLEAN NOT NULL DEFAULT false,
    "completedAt" DATETIME,
    CONSTRAINT "LearningPathNode_pathId_fkey" FOREIGN KEY ("pathId") REFERENCES "LearningPath" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Classroom" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "teacherId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "ClassroomStudent" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "classroomId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "displayName" TEXT NOT NULL,
    "email" TEXT,
    "enrolledAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ClassroomStudent_classroomId_fkey" FOREIGN KEY ("classroomId") REFERENCES "Classroom" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ClassroomAssignment" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "classroomId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "assignedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "dueAt" DATETIME,
    CONSTRAINT "ClassroomAssignment_classroomId_fkey" FOREIGN KEY ("classroomId") REFERENCES "Classroom" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Thread" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT,
    "title" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'OPEN',
    "authorId" TEXT NOT NULL,
    "authorName" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "resolvedAt" DATETIME
);

-- CreateTable
CREATE TABLE "Post" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "threadId" TEXT NOT NULL,
    "authorId" TEXT NOT NULL,
    "authorName" TEXT NOT NULL,
    "content" TEXT NOT NULL,
    "isAnswer" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "Post_threadId_fkey" FOREIGN KEY ("threadId") REFERENCES "Thread" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "HelpRequest" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "question" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "answeredAt" DATETIME
);

-- CreateTable
CREATE TABLE "CheckoutSession" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "listingId" TEXT NOT NULL,
    "amountCents" INTEGER NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "paymentUrl" TEXT,
    "successUrl" TEXT,
    "cancelUrl" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "completedAt" DATETIME
);

-- CreateTable
CREATE TABLE "Purchase" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "listingId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "amountCents" INTEGER NOT NULL,
    "purchasedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateTable
CREATE TABLE "LTIPlatform" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "platformName" TEXT NOT NULL,
    "issuer" TEXT NOT NULL,
    "clientId" TEXT NOT NULL,
    "jwksUrl" TEXT NOT NULL,
    "authUrl" TEXT NOT NULL,
    "tokenUrl" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "StudyGroup" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "coverImageUrl" TEXT,
    "createdBy" TEXT NOT NULL,
    "visibility" TEXT NOT NULL DEFAULT 'PUBLIC',
    "maxMembers" INTEGER NOT NULL DEFAULT 50,
    "requireApproval" BOOLEAN NOT NULL DEFAULT false,
    "allowGuestView" BOOLEAN NOT NULL DEFAULT false,
    "subjects" TEXT NOT NULL,
    "modules" TEXT,
    "memberCount" INTEGER NOT NULL DEFAULT 1,
    "status" TEXT NOT NULL DEFAULT 'ACTIVE',
    "lastActivityAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "archivedAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "StudyGroupMember" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "groupId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "role" TEXT NOT NULL DEFAULT 'MEMBER',
    "invitedBy" TEXT,
    "messagesCount" INTEGER NOT NULL DEFAULT 0,
    "notificationsEnabled" BOOLEAN NOT NULL DEFAULT true,
    "mutedUntil" DATETIME,
    "lastActiveAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "joinedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "StudyGroupMember_groupId_fkey" FOREIGN KEY ("groupId") REFERENCES "StudyGroup" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "StudyGroupJoinRequest" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "groupId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "message" TEXT,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "reviewedBy" TEXT,
    "rejectionReason" TEXT,
    "reviewedAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "StudyGroupJoinRequest_groupId_fkey" FOREIGN KEY ("groupId") REFERENCES "StudyGroup" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "StudyGroupInvite" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "groupId" TEXT NOT NULL,
    "invitedEmail" TEXT NOT NULL,
    "invitedBy" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "acceptedAt" DATETIME,
    "expiresAt" DATETIME NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "StudyGroupInvite_groupId_fkey" FOREIGN KEY ("groupId") REFERENCES "StudyGroup" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "StudySession" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "groupId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "createdBy" TEXT NOT NULL,
    "scheduledAt" DATETIME NOT NULL,
    "duration" INTEGER NOT NULL,
    "timezone" TEXT NOT NULL DEFAULT 'UTC',
    "type" TEXT NOT NULL DEFAULT 'DISCUSSION',
    "meetingUrl" TEXT,
    "maxParticipants" INTEGER,
    "rsvpDeadline" DATETIME,
    "moduleId" TEXT,
    "lessonIds" TEXT,
    "agenda" TEXT,
    "attachments" TEXT,
    "status" TEXT NOT NULL DEFAULT 'SCHEDULED',
    "startedAt" DATETIME,
    "endedAt" DATETIME,
    "notes" TEXT,
    "recordingUrl" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "StudySession_groupId_fkey" FOREIGN KEY ("groupId") REFERENCES "StudyGroup" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "SessionRsvp" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "sessionId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "status" TEXT NOT NULL,
    "note" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "SessionRsvp_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "StudySession" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Forum" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "iconUrl" TEXT,
    "scope" TEXT NOT NULL DEFAULT 'GLOBAL',
    "scopeId" TEXT,
    "allowAnonymousPosts" BOOLEAN NOT NULL DEFAULT false,
    "requireModeration" BOOLEAN NOT NULL DEFAULT false,
    "allowAttachments" BOOLEAN NOT NULL DEFAULT true,
    "allowPolls" BOOLEAN NOT NULL DEFAULT true,
    "categories" TEXT,
    "topicCount" INTEGER NOT NULL DEFAULT 0,
    "postCount" INTEGER NOT NULL DEFAULT 0,
    "lastPostAt" DATETIME,
    "status" TEXT NOT NULL DEFAULT 'ACTIVE',
    "studyGroupId" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "Forum_studyGroupId_fkey" FOREIGN KEY ("studyGroupId") REFERENCES "StudyGroup" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ForumTopic" (
    "id" TEXT NOT NULL PRIMARY KEY,
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
    "status" TEXT NOT NULL DEFAULT 'PUBLISHED',
    "moderatedBy" TEXT,
    "moderatedAt" DATETIME,
    "moderationNote" TEXT,
    "lastReplyAt" DATETIME,
    "lastReplyBy" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "ForumTopic_forumId_fkey" FOREIGN KEY ("forumId") REFERENCES "Forum" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ForumPost" (
    "id" TEXT NOT NULL PRIMARY KEY,
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
    "status" TEXT NOT NULL DEFAULT 'PUBLISHED',
    "moderatedBy" TEXT,
    "moderatedAt" DATETIME,
    "isEdited" BOOLEAN NOT NULL DEFAULT false,
    "editedAt" DATETIME,
    "editHistory" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "ForumPost_topicId_fkey" FOREIGN KEY ("topicId") REFERENCES "ForumTopic" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "PostReaction" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "postId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "PostReaction_postId_fkey" FOREIGN KEY ("postId") REFERENCES "ForumPost" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "TutorProfile" (
    "id" TEXT NOT NULL PRIMARY KEY,
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
    "rating" REAL NOT NULL DEFAULT 0,
    "reviewCount" INTEGER NOT NULL DEFAULT 0,
    "sessionsCompleted" INTEGER NOT NULL DEFAULT 0,
    "totalHelpedStudents" INTEGER NOT NULL DEFAULT 0,
    "status" TEXT NOT NULL DEFAULT 'ACTIVE',
    "verifiedAt" DATETIME,
    "verifiedBy" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "TutoringRequest" (
    "id" TEXT NOT NULL PRIMARY KEY,
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
    "preferredTime" DATETIME,
    "estimatedDuration" INTEGER NOT NULL DEFAULT 60,
    "urgency" TEXT NOT NULL DEFAULT 'medium',
    "status" TEXT NOT NULL DEFAULT 'OPEN',
    "acceptedAt" DATETIME,
    "completedAt" DATETIME,
    "cancelledAt" DATETIME,
    "cancellationReason" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "TutoringRequest_tutorId_fkey" FOREIGN KEY ("tutorId") REFERENCES "TutorProfile" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "TutoringSession" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "requestId" TEXT NOT NULL,
    "studentId" TEXT NOT NULL,
    "tutorId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "scheduledAt" DATETIME NOT NULL,
    "duration" INTEGER NOT NULL,
    "meetingUrl" TEXT,
    "moduleId" TEXT,
    "lessonId" TEXT,
    "notes" TEXT,
    "sharedResources" TEXT,
    "status" TEXT NOT NULL DEFAULT 'SCHEDULED',
    "startedAt" DATETIME,
    "endedAt" DATETIME,
    "actualDuration" INTEGER,
    "recordingUrl" TEXT,
    "transcriptUrl" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "TutoringSession_requestId_fkey" FOREIGN KEY ("requestId") REFERENCES "TutoringRequest" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "TutoringSession_tutorId_fkey" FOREIGN KEY ("tutorId") REFERENCES "TutorProfile" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "TutoringReview" (
    "id" TEXT NOT NULL PRIMARY KEY,
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
    "respondedAt" DATETIME,
    "isVisible" BOOLEAN NOT NULL DEFAULT true,
    "moderatedBy" TEXT,
    "moderatedAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "TutoringReview_sessionId_fkey" FOREIGN KEY ("sessionId") REFERENCES "TutoringSession" ("id") ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT "TutoringReview_tutorId_fkey" FOREIGN KEY ("tutorId") REFERENCES "TutorProfile" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ChatRoom" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "name" TEXT,
    "studyGroupId" TEXT,
    "tutoringSessionId" TEXT,
    "participants" TEXT NOT NULL,
    "maxParticipants" INTEGER,
    "isEncrypted" BOOLEAN NOT NULL DEFAULT false,
    "retentionDays" INTEGER NOT NULL DEFAULT 90,
    "messageCount" INTEGER NOT NULL DEFAULT 0,
    "lastMessageAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "ChatRoom_studyGroupId_fkey" FOREIGN KEY ("studyGroupId") REFERENCES "StudyGroup" ("id") ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT "ChatRoom_tutoringSessionId_fkey" FOREIGN KEY ("tutoringSessionId") REFERENCES "TutoringSession" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ChatMessage" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "roomId" TEXT NOT NULL,
    "senderId" TEXT NOT NULL,
    "senderName" TEXT NOT NULL,
    "type" TEXT NOT NULL DEFAULT 'TEXT',
    "content" TEXT NOT NULL,
    "metadata" TEXT,
    "attachments" TEXT,
    "replyToId" TEXT,
    "reactions" TEXT,
    "status" TEXT NOT NULL DEFAULT 'sent',
    "editedAt" DATETIME,
    "deletedAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ChatMessage_roomId_fkey" FOREIGN KEY ("roomId") REFERENCES "ChatRoom" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ChatReadReceipt" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "roomId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "lastReadMessageId" TEXT NOT NULL,
    "lastReadAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ChatReadReceipt_roomId_fkey" FOREIGN KEY ("roomId") REFERENCES "ChatRoom" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "SharedNote" (
    "id" TEXT NOT NULL PRIMARY KEY,
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
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "SharedNote_studyGroupId_fkey" FOREIGN KEY ("studyGroupId") REFERENCES "StudyGroup" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "NoteComment" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "noteId" TEXT NOT NULL,
    "authorId" TEXT NOT NULL,
    "anchorStart" INTEGER,
    "anchorEnd" INTEGER,
    "content" TEXT NOT NULL,
    "parentId" TEXT,
    "isResolved" BOOLEAN NOT NULL DEFAULT false,
    "resolvedBy" TEXT,
    "resolvedAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "NoteComment_noteId_fkey" FOREIGN KEY ("noteId") REFERENCES "SharedNote" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "CollaborativeWhiteboard" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "studyGroupId" TEXT,
    "sessionId" TEXT,
    "name" TEXT NOT NULL,
    "canvasState" TEXT NOT NULL,
    "activeUsers" TEXT,
    "allowAnonymous" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "SocialActivity" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "actorId" TEXT NOT NULL,
    "actorName" TEXT NOT NULL,
    "actorAvatarUrl" TEXT,
    "type" TEXT NOT NULL,
    "targetType" TEXT NOT NULL,
    "targetId" TEXT NOT NULL,
    "targetTitle" TEXT NOT NULL,
    "studyGroupId" TEXT,
    "classroomId" TEXT,
    "metadata" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateTable
CREATE TABLE "SocialNotification" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "body" TEXT NOT NULL,
    "iconUrl" TEXT,
    "actionUrl" TEXT,
    "actorId" TEXT,
    "actorName" TEXT,
    "targetType" TEXT,
    "targetId" TEXT,
    "isRead" BOOLEAN NOT NULL DEFAULT false,
    "readAt" DATETIME,
    "emailSent" BOOLEAN NOT NULL DEFAULT false,
    "pushSent" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateTable
CREATE TABLE "NotificationPreference" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "emailEnabled" BOOLEAN NOT NULL DEFAULT true,
    "pushEnabled" BOOLEAN NOT NULL DEFAULT true,
    "preferences" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "VRLab" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "category" TEXT NOT NULL,
    "difficulty" TEXT NOT NULL,
    "thumbnailUrl" TEXT NOT NULL,
    "previewVideoUrl" TEXT,
    "estimatedDuration" INTEGER NOT NULL,
    "requiredDevices" TEXT NOT NULL,
    "minRequirements" TEXT NOT NULL,
    "completionRate" REAL NOT NULL DEFAULT 0,
    "averageRating" REAL NOT NULL DEFAULT 0,
    "totalSessions" INTEGER NOT NULL DEFAULT 0,
    "memberCount" INTEGER NOT NULL DEFAULT 0,
    "isPublished" BOOLEAN NOT NULL DEFAULT false,
    "tags" TEXT NOT NULL,
    "prerequisites" TEXT,
    "createdBy" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "VRScene" (
    "id" TEXT NOT NULL PRIMARY KEY,
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
    CONSTRAINT "VRScene_labId_fkey" FOREIGN KEY ("labId") REFERENCES "VRLab" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "VRInteractable" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "sceneId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "position" TEXT NOT NULL,
    "rotation" TEXT NOT NULL,
    "scale" TEXT NOT NULL,
    "modelUrl" TEXT NOT NULL,
    "materialOverrides" TEXT,
    "allowedInteractions" TEXT NOT NULL,
    "interactionRange" REAL NOT NULL,
    "behavior" TEXT NOT NULL,
    "tooltip" TEXT,
    "audioFeedbackUrl" TEXT,
    CONSTRAINT "VRInteractable_sceneId_fkey" FOREIGN KEY ("sceneId") REFERENCES "VRScene" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "VRLabObjective" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "labId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "order" INTEGER NOT NULL,
    "type" TEXT NOT NULL,
    "criteria" TEXT NOT NULL,
    "hints" TEXT NOT NULL,
    "points" INTEGER NOT NULL,
    "isOptional" BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT "VRLabObjective_labId_fkey" FOREIGN KEY ("labId") REFERENCES "VRLab" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "VRSession" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "labId" TEXT NOT NULL,
    "status" TEXT NOT NULL,
    "currentSceneId" TEXT NOT NULL,
    "deviceType" TEXT NOT NULL,
    "deviceInfo" TEXT NOT NULL,
    "progress" TEXT NOT NULL,
    "startedAt" DATETIME NOT NULL,
    "lastActiveAt" DATETIME NOT NULL,
    "endedAt" DATETIME,
    "totalDuration" INTEGER NOT NULL DEFAULT 0,
    "performanceMetrics" TEXT NOT NULL,
    CONSTRAINT "VRSession_labId_fkey" FOREIGN KEY ("labId") REFERENCES "VRLab" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "VRMultiplayerSession" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "labId" TEXT NOT NULL,
    "hostUserId" TEXT NOT NULL,
    "maxParticipants" INTEGER NOT NULL,
    "voiceChatEnabled" BOOLEAN NOT NULL DEFAULT true,
    "spatialAudioEnabled" BOOLEAN NOT NULL DEFAULT true,
    "participants" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'LOBBY',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "VRMultiplayerSession_labId_fkey" FOREIGN KEY ("labId") REFERENCES "VRLab" ("id") ON DELETE RESTRICT ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "VRAsset" (
    "id" TEXT NOT NULL PRIMARY KEY,
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
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateTable
CREATE TABLE "VRAnalyticsEvent" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "eventType" TEXT NOT NULL,
    "labId" TEXT,
    "sessionId" TEXT,
    "metadata" TEXT NOT NULL,
    "timestamp" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CreateTable
CREATE TABLE "DomainConcept" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "externalId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "domain" TEXT NOT NULL,
    "level" TEXT NOT NULL,
    "keywords" TEXT NOT NULL,
    "audienceTags" TEXT NOT NULL,
    "simulationMetadata" JSONB NOT NULL,
    "learningObjectMetadata" JSONB NOT NULL,
    "pedagogicalMetadata" JSONB NOT NULL,
    "crossDomainLinks" JSONB NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- CreateTable
CREATE TABLE "ConceptPrerequisite" (
    "conceptId" TEXT NOT NULL,
    "prerequisiteId" TEXT NOT NULL,

    PRIMARY KEY ("conceptId", "prerequisiteId"),
    CONSTRAINT "ConceptPrerequisite_conceptId_fkey" FOREIGN KEY ("conceptId") REFERENCES "DomainConcept" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "ConceptPrerequisite_prerequisiteId_fkey" FOREIGN KEY ("prerequisiteId") REFERENCES "DomainConcept" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "ConceptModuleMapping" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "conceptId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "simulationManifestIds" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ConceptModuleMapping_conceptId_fkey" FOREIGN KEY ("conceptId") REFERENCES "DomainConcept" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "Curriculum" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "domain" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "version" TEXT NOT NULL DEFAULT '1.0.0',
    "isPublished" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);

-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_Assessment" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "type" TEXT NOT NULL DEFAULT 'QUIZ',
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "version" INTEGER NOT NULL DEFAULT 1,
    "passingScore" INTEGER NOT NULL DEFAULT 80,
    "attemptsAllowed" INTEGER,
    "timeLimitMinutes" INTEGER,
    "createdBy" TEXT NOT NULL,
    "updatedBy" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "Assessment_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
INSERT INTO "new_Assessment" ("attemptsAllowed", "createdAt", "createdBy", "id", "moduleId", "passingScore", "status", "tenantId", "timeLimitMinutes", "title", "type", "updatedAt", "updatedBy", "version") SELECT "attemptsAllowed", "createdAt", "createdBy", "id", "moduleId", "passingScore", "status", "tenantId", "timeLimitMinutes", "title", "type", "updatedAt", "updatedBy", "version" FROM "Assessment";
DROP TABLE "Assessment";
ALTER TABLE "new_Assessment" RENAME TO "Assessment";
CREATE INDEX "Assessment_tenantId_moduleId_idx" ON "Assessment"("tenantId", "moduleId");
CREATE INDEX "Assessment_tenantId_status_idx" ON "Assessment"("tenantId", "status");
CREATE TABLE "new_AssessmentAttempt" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "assessmentId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'IN_PROGRESS',
    "responses" JSONB,
    "scorePercent" INTEGER,
    "feedback" JSONB,
    "startedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "submittedAt" DATETIME,
    "gradedAt" DATETIME,
    "timeSpentSeconds" INTEGER,
    CONSTRAINT "AssessmentAttempt_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "Assessment" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
INSERT INTO "new_AssessmentAttempt" ("assessmentId", "feedback", "gradedAt", "id", "responses", "scorePercent", "startedAt", "status", "submittedAt", "tenantId", "timeSpentSeconds", "userId") SELECT "assessmentId", "feedback", "gradedAt", "id", "responses", "scorePercent", "startedAt", "status", "submittedAt", "tenantId", "timeSpentSeconds", "userId" FROM "AssessmentAttempt";
DROP TABLE "AssessmentAttempt";
ALTER TABLE "new_AssessmentAttempt" RENAME TO "AssessmentAttempt";
CREATE INDEX "AssessmentAttempt_tenantId_userId_idx" ON "AssessmentAttempt"("tenantId", "userId");
CREATE INDEX "AssessmentAttempt_tenantId_assessmentId_idx" ON "AssessmentAttempt"("tenantId", "assessmentId");
CREATE TABLE "new_AssessmentDraft" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "createdBy" TEXT NOT NULL,
    "payload" JSONB NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO "new_AssessmentDraft" ("createdAt", "createdBy", "id", "moduleId", "payload", "tenantId") SELECT "createdAt", "createdBy", "id", "moduleId", "payload", "tenantId" FROM "AssessmentDraft";
DROP TABLE "AssessmentDraft";
ALTER TABLE "new_AssessmentDraft" RENAME TO "AssessmentDraft";
CREATE INDEX "AssessmentDraft_tenantId_moduleId_idx" ON "AssessmentDraft"("tenantId", "moduleId");
CREATE INDEX "AssessmentDraft_tenantId_createdBy_idx" ON "AssessmentDraft"("tenantId", "createdBy");
CREATE TABLE "new_AssessmentItem" (
    "id" TEXT NOT NULL PRIMARY KEY,
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
    CONSTRAINT "AssessmentItem_assessmentId_fkey" FOREIGN KEY ("assessmentId") REFERENCES "Assessment" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
INSERT INTO "new_AssessmentItem" ("assessmentId", "choices", "id", "itemType", "metadata", "modelAnswer", "orderIndex", "points", "prompt", "rubric", "stimulus") SELECT "assessmentId", "choices", "id", "itemType", "metadata", "modelAnswer", "orderIndex", "points", "prompt", "rubric", "stimulus" FROM "AssessmentItem";
DROP TABLE "AssessmentItem";
ALTER TABLE "new_AssessmentItem" RENAME TO "AssessmentItem";
CREATE TABLE "new_Enrollment" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'NOT_STARTED',
    "progressPercent" INTEGER NOT NULL DEFAULT 0,
    "startedAt" DATETIME,
    "completedAt" DATETIME,
    "timeSpentSeconds" INTEGER NOT NULL DEFAULT 0,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "Enrollment_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
INSERT INTO "new_Enrollment" ("completedAt", "createdAt", "id", "moduleId", "progressPercent", "startedAt", "status", "tenantId", "timeSpentSeconds", "updatedAt", "userId") SELECT "completedAt", "createdAt", "id", "moduleId", "progressPercent", "startedAt", "status", "tenantId", "timeSpentSeconds", "updatedAt", "userId" FROM "Enrollment";
DROP TABLE "Enrollment";
ALTER TABLE "new_Enrollment" RENAME TO "Enrollment";
CREATE INDEX "Enrollment_tenantId_userId_idx" ON "Enrollment"("tenantId", "userId");
CREATE UNIQUE INDEX "Enrollment_tenantId_userId_moduleId_key" ON "Enrollment"("tenantId", "userId", "moduleId");
CREATE TABLE "new_LearningEvent" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "moduleId" TEXT,
    "eventType" TEXT NOT NULL,
    "payload" JSONB,
    "timestamp" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO "new_LearningEvent" ("eventType", "id", "moduleId", "payload", "tenantId", "timestamp", "userId") SELECT "eventType", "id", "moduleId", "payload", "tenantId", "timestamp", "userId" FROM "LearningEvent";
DROP TABLE "LearningEvent";
ALTER TABLE "new_LearningEvent" RENAME TO "LearningEvent";
CREATE INDEX "LearningEvent_tenantId_eventType_idx" ON "LearningEvent"("tenantId", "eventType");
CREATE INDEX "LearningEvent_tenantId_userId_idx" ON "LearningEvent"("tenantId", "userId");
CREATE TABLE "new_MarketplaceListing" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "moduleId" TEXT NOT NULL,
    "creatorId" TEXT NOT NULL,
    "priceCents" INTEGER NOT NULL DEFAULT 0,
    "status" TEXT NOT NULL DEFAULT 'DRAFT',
    "visibility" TEXT NOT NULL DEFAULT 'PUBLIC',
    "publishedAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "MarketplaceListing_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
INSERT INTO "new_MarketplaceListing" ("createdAt", "creatorId", "id", "moduleId", "priceCents", "publishedAt", "status", "tenantId", "updatedAt", "visibility") SELECT "createdAt", "creatorId", "id", "moduleId", "priceCents", "publishedAt", "status", "tenantId", "updatedAt", "visibility" FROM "MarketplaceListing";
DROP TABLE "MarketplaceListing";
ALTER TABLE "new_MarketplaceListing" RENAME TO "MarketplaceListing";
CREATE INDEX "MarketplaceListing_tenantId_status_idx" ON "MarketplaceListing"("tenantId", "status");
CREATE INDEX "MarketplaceListing_tenantId_moduleId_idx" ON "MarketplaceListing"("tenantId", "moduleId");
CREATE INDEX "MarketplaceListing_tenantId_creatorId_idx" ON "MarketplaceListing"("tenantId", "creatorId");
CREATE TABLE "new_Module" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "tenantId" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "domain" TEXT NOT NULL,
    "difficulty" TEXT NOT NULL,
    "estimatedTimeMinutes" INTEGER NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'PUBLISHED',
    "description" TEXT NOT NULL,
    "version" INTEGER NOT NULL DEFAULT 1,
    "authorId" TEXT,
    "updatedBy" TEXT,
    "publishedAt" DATETIME,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL
);
INSERT INTO "new_Module" ("authorId", "createdAt", "description", "difficulty", "domain", "estimatedTimeMinutes", "id", "publishedAt", "slug", "status", "tenantId", "title", "updatedAt", "updatedBy", "version") SELECT "authorId", "createdAt", "description", "difficulty", "domain", "estimatedTimeMinutes", "id", "publishedAt", "slug", "status", "tenantId", "title", "updatedAt", "updatedBy", "version" FROM "Module";
DROP TABLE "Module";
ALTER TABLE "new_Module" RENAME TO "Module";
CREATE INDEX "Module_tenantId_domain_idx" ON "Module"("tenantId", "domain");
CREATE INDEX "Module_tenantId_status_idx" ON "Module"("tenantId", "status");
CREATE UNIQUE INDEX "Module_tenantId_slug_key" ON "Module"("tenantId", "slug");
CREATE TABLE "new_ModuleContentBlock" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "moduleId" TEXT NOT NULL,
    "orderIndex" INTEGER NOT NULL,
    "blockType" TEXT NOT NULL,
    "payload" JSONB NOT NULL,
    CONSTRAINT "ModuleContentBlock_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
INSERT INTO "new_ModuleContentBlock" ("blockType", "id", "moduleId", "orderIndex", "payload") SELECT "blockType", "id", "moduleId", "orderIndex", "payload" FROM "ModuleContentBlock";
DROP TABLE "ModuleContentBlock";
ALTER TABLE "new_ModuleContentBlock" RENAME TO "ModuleContentBlock";
CREATE TABLE "new_ModuleRevision" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "moduleId" TEXT NOT NULL,
    "version" INTEGER NOT NULL,
    "snapshot" JSONB NOT NULL,
    "createdBy" TEXT NOT NULL,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ModuleRevision_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
INSERT INTO "new_ModuleRevision" ("createdAt", "createdBy", "id", "moduleId", "snapshot", "version") SELECT "createdAt", "createdBy", "id", "moduleId", "snapshot", "version" FROM "ModuleRevision";
DROP TABLE "ModuleRevision";
ALTER TABLE "new_ModuleRevision" RENAME TO "ModuleRevision";
CREATE INDEX "ModuleRevision_moduleId_version_idx" ON "ModuleRevision"("moduleId", "version");
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;

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
CREATE INDEX "Curriculum_tenantId_isPublished_idx" ON "Curriculum"("tenantId", "isPublished");

-- CreateIndex
CREATE UNIQUE INDEX "Curriculum_tenantId_domain_key" ON "Curriculum"("tenantId", "domain");
