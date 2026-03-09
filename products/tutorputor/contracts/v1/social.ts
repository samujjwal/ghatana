/**
 * @doc.type module
 * @doc.purpose Social Learning contracts - Study Groups, Forums, Peer Learning
 * @doc.layer contracts
 * @doc.pattern ValueObject
 */

// ============================================
// STUDY GROUPS
// ============================================

export interface StudyGroup {
  id: string;
  tenantId: string;
  name: string;
  description: string;
  coverImageUrl?: string;
  createdBy: string;
  createdAt: Date;
  updatedAt: Date;
  
  // Group settings
  visibility: StudyGroupVisibility;
  maxMembers: number;
  requireApproval: boolean;
  allowGuestView: boolean;
  
  // Focus areas
  subjects: string[];
  modules: string[]; // Associated module IDs
  
  // Activity tracking
  memberCount: number;
  lastActivityAt: Date;
  
  // Status
  status: StudyGroupStatus;
  archivedAt?: Date;
}

export type StudyGroupVisibility = 'public' | 'private' | 'classroom_only';
export type StudyGroupStatus = 'active' | 'archived' | 'suspended';

export interface StudyGroupMember {
  id: string;
  groupId: string;
  userId: string;
  role: StudyGroupRole;
  joinedAt: Date;
  invitedBy?: string;
  
  // Activity stats
  messagesCount: number;
  lastActiveAt: Date;
  
  // Notifications
  notificationsEnabled: boolean;
  mutedUntil?: Date;
}

export type StudyGroupRole = 'owner' | 'admin' | 'moderator' | 'member';

export interface StudyGroupInvite {
  id: string;
  groupId: string;
  invitedEmail: string;
  invitedBy: string;
  createdAt: Date;
  expiresAt: Date;
  status: 'pending' | 'accepted' | 'declined' | 'expired';
  acceptedAt?: Date;
}

export interface StudyGroupJoinRequest {
  id: string;
  groupId: string;
  userId: string;
  message?: string;
  createdAt: Date;
  status: 'pending' | 'approved' | 'rejected';
  reviewedBy?: string;
  reviewedAt?: Date;
  rejectionReason?: string;
}

// ============================================
// STUDY SESSIONS
// ============================================

export interface StudySession {
  id: string;
  groupId: string;
  title: string;
  description?: string;
  createdBy: string;
  
  // Timing
  scheduledAt: Date;
  duration: number; // minutes
  timezone: string;
  
  // Session type
  type: StudySessionType;
  meetingUrl?: string; // For video sessions
  
  // Participants
  maxParticipants?: number;
  rsvpDeadline?: Date;
  
  // Content
  moduleId?: string;
  lessonIds?: string[];
  agenda?: string;
  attachments?: SessionAttachment[];
  
  // Status
  status: StudySessionStatus;
  startedAt?: Date;
  endedAt?: Date;
  
  // Post-session
  notes?: string;
  recordingUrl?: string;
}

export type StudySessionType = 'discussion' | 'review' | 'quiz_practice' | 'video_call' | 'collaborative';
export type StudySessionStatus = 'scheduled' | 'in_progress' | 'completed' | 'cancelled';

export interface SessionAttachment {
  id: string;
  name: string;
  type: string;
  url: string;
  uploadedBy: string;
  uploadedAt: Date;
}

export interface SessionRsvp {
  sessionId: string;
  userId: string;
  status: 'attending' | 'maybe' | 'not_attending';
  respondedAt: Date;
  note?: string;
}

// ============================================
// DISCUSSION FORUMS
// ============================================

export interface Forum {
  id: string;
  tenantId: string;
  name: string;
  description: string;
  iconUrl?: string;
  
  // Scope
  scope: ForumScope;
  scopeId?: string; // groupId, classroomId, or moduleId
  
  // Settings
  allowAnonymousPosts: boolean;
  requireModeration: boolean;
  allowAttachments: boolean;
  allowPolls: boolean;
  
  // Categories
  categories: ForumCategory[];
  
  // Stats
  topicCount: number;
  postCount: number;
  lastPostAt?: Date;
  
  // Status
  status: 'active' | 'archived' | 'locked';
  createdAt: Date;
  updatedAt: Date;
}

export type ForumScope = 'global' | 'study_group' | 'classroom' | 'module';

export interface ForumCategory {
  id: string;
  name: string;
  description?: string;
  color: string;
  order: number;
}

export interface ForumTopic {
  id: string;
  forumId: string;
  categoryId?: string;
  title: string;
  slug: string;
  authorId: string;
  authorName: string;
  
  // Content
  content: string;
  contentFormat: 'markdown' | 'html' | 'plain';
  attachments?: TopicAttachment[];
  
  // Engagement
  viewCount: number;
  replyCount: number;
  likeCount: number;
  
  // Status
  isPinned: boolean;
  isLocked: boolean;
  isAnswered: boolean; // For Q&A topics
  answerId?: string;
  
  // Moderation
  status: TopicStatus;
  moderatedBy?: string;
  moderatedAt?: Date;
  moderationNote?: string;
  
  // Timestamps
  createdAt: Date;
  updatedAt: Date;
  lastReplyAt?: Date;
  lastReplyBy?: string;
}

export type TopicStatus = 'draft' | 'pending' | 'published' | 'hidden' | 'deleted';

export interface TopicAttachment {
  id: string;
  name: string;
  type: string;
  size: number;
  url: string;
}

export interface ForumPost {
  id: string;
  topicId: string;
  authorId: string;
  authorName: string;
  isAnonymous: boolean;
  
  // Content
  content: string;
  contentFormat: 'markdown' | 'html' | 'plain';
  attachments?: TopicAttachment[];
  
  // Threading
  parentId?: string; // For nested replies
  depth: number;
  
  // Engagement
  likeCount: number;
  isAcceptedAnswer: boolean;
  
  // Moderation
  status: TopicStatus;
  moderatedBy?: string;
  moderatedAt?: Date;
  
  // Edit history
  isEdited: boolean;
  editedAt?: Date;
  editHistory?: PostEdit[];
  
  // Timestamps
  createdAt: Date;
  updatedAt: Date;
}

export interface PostEdit {
  editedAt: Date;
  previousContent: string;
  editReason?: string;
}

export interface PostReaction {
  postId: string;
  userId: string;
  type: ReactionType;
  createdAt: Date;
}

export type ReactionType = 'like' | 'helpful' | 'insightful' | 'question' | 'celebrate';

// ============================================
// PEER TUTORING
// ============================================

export interface TutorProfile {
  id: string;
  userId: string;
  tenantId: string;
  
  // Profile
  displayName: string;
  bio: string;
  avatarUrl?: string;
  
  // Expertise
  subjects: string[];
  modules: string[]; // Modules they can help with
  qualifications?: string[];
  
  // Availability
  isAvailable: boolean;
  availabilitySchedule?: AvailabilitySlot[];
  timezone: string;
  responseTime: string; // e.g., "Usually within 2 hours"
  
  // Preferences
  sessionTypes: TutoringSessionType[];
  maxSessionsPerWeek: number;
  pricePerHour?: number; // If paid tutoring is enabled
  
  // Stats
  rating: number;
  reviewCount: number;
  sessionsCompleted: number;
  totalHelpedStudents: number;
  
  // Status
  status: 'active' | 'paused' | 'inactive';
  verifiedAt?: Date;
  verifiedBy?: string;
  
  createdAt: Date;
  updatedAt: Date;
}

export interface AvailabilitySlot {
  dayOfWeek: number; // 0-6 (Sunday-Saturday)
  startTime: string; // HH:mm format
  endTime: string;
}

export type TutoringSessionType = 'text_chat' | 'video_call' | 'screen_share' | 'collaborative_whiteboard';

export interface TutoringRequest {
  id: string;
  studentId: string;
  tutorId?: string; // null for open requests
  tenantId: string;
  
  // Request details
  subject: string;
  moduleId?: string;
  lessonId?: string;
  title: string;
  description: string;
  attachments?: string[];
  
  // Session preferences
  preferredTypes: TutoringSessionType[];
  preferredTime?: Date;
  estimatedDuration: number; // minutes
  urgency: 'low' | 'medium' | 'high';
  
  // Status
  status: TutoringRequestStatus;
  createdAt: Date;
  updatedAt: Date;
  acceptedAt?: Date;
  completedAt?: Date;
  cancelledAt?: Date;
  cancellationReason?: string;
}

export type TutoringRequestStatus = 
  | 'open' 
  | 'matched' 
  | 'in_progress' 
  | 'completed' 
  | 'cancelled' 
  | 'expired';

export interface TutoringSession {
  id: string;
  requestId: string;
  studentId: string;
  tutorId: string;
  tenantId: string;
  
  // Session details
  type: TutoringSessionType;
  scheduledAt: Date;
  duration: number; // minutes
  meetingUrl?: string;
  
  // Content
  moduleId?: string;
  lessonId?: string;
  notes?: string;
  sharedResources?: string[];
  
  // Status
  status: 'scheduled' | 'in_progress' | 'completed' | 'no_show' | 'cancelled';
  startedAt?: Date;
  endedAt?: Date;
  actualDuration?: number; // minutes
  
  // Recording/Transcript (if enabled)
  recordingUrl?: string;
  transcriptUrl?: string;
  
  createdAt: Date;
  updatedAt: Date;
}

export interface TutoringReview {
  id: string;
  sessionId: string;
  reviewerId: string;
  revieweeId: string;
  
  // Rating
  rating: number; // 1-5
  
  // Detailed feedback
  helpfulness: number; // 1-5
  communication: number; // 1-5
  knowledge: number; // 1-5
  
  // Written feedback
  comment?: string;
  privateNote?: string; // Only visible to admins
  
  // Response
  response?: string;
  respondedAt?: Date;
  
  // Moderation
  isVisible: boolean;
  moderatedBy?: string;
  moderatedAt?: Date;
  
  createdAt: Date;
  updatedAt: Date;
}

// ============================================
// REAL-TIME CHAT
// ============================================

export interface ChatRoom {
  id: string;
  tenantId: string;
  type: ChatRoomType;
  name?: string;
  
  // Scope
  studyGroupId?: string;
  classroomId?: string;
  tutoringSessionId?: string;
  
  // Participants
  participants: string[];
  maxParticipants?: number;
  
  // Settings
  isEncrypted: boolean;
  retentionDays: number;
  
  // Stats
  messageCount: number;
  lastMessageAt?: Date;
  
  createdAt: Date;
  updatedAt: Date;
}

export type ChatRoomType = 'direct' | 'study_group' | 'classroom' | 'tutoring' | 'support';

export interface ChatMessage {
  id: string;
  roomId: string;
  senderId: string;
  senderName: string;
  
  // Content
  type: ChatMessageType;
  content: string;
  metadata?: Record<string, unknown>;
  
  // Attachments
  attachments?: ChatAttachment[];
  
  // Reply
  replyToId?: string;
  
  // Reactions
  reactions: Record<string, string[]>; // emoji -> userIds
  
  // Status
  status: 'sent' | 'delivered' | 'read' | 'deleted';
  editedAt?: Date;
  deletedAt?: Date;
  
  createdAt: Date;
}

export type ChatMessageType = 'text' | 'image' | 'file' | 'code' | 'math' | 'quiz_share' | 'system';

export interface ChatAttachment {
  id: string;
  name: string;
  type: string;
  size: number;
  url: string;
  thumbnailUrl?: string;
}

export interface ChatReadReceipt {
  roomId: string;
  userId: string;
  lastReadMessageId: string;
  lastReadAt: Date;
}

// ============================================
// COLLABORATIVE FEATURES
// ============================================

export interface SharedNote {
  id: string;
  tenantId: string;
  
  // Ownership
  createdBy: string;
  sharedWith: SharedNoteAccess[];
  studyGroupId?: string;
  
  // Content
  title: string;
  content: string; // JSON for rich text
  version: number;
  
  // Collaboration
  allowEditing: boolean;
  allowComments: boolean;
  
  // Source
  moduleId?: string;
  lessonId?: string;
  
  createdAt: Date;
  updatedAt: Date;
  lastEditedBy?: string;
}

export interface SharedNoteAccess {
  userId: string;
  permission: 'view' | 'comment' | 'edit';
  addedAt: Date;
  addedBy: string;
}

export interface NoteComment {
  id: string;
  noteId: string;
  authorId: string;
  
  // Position
  anchorStart?: number;
  anchorEnd?: number;
  
  // Content
  content: string;
  
  // Threading
  parentId?: string;
  
  // Status
  isResolved: boolean;
  resolvedBy?: string;
  resolvedAt?: Date;
  
  createdAt: Date;
  updatedAt: Date;
}

export interface CollaborativeWhiteboard {
  id: string;
  tenantId: string;
  
  // Context
  studyGroupId?: string;
  tutoringSessionId?: string;
  
  // Content
  name: string;
  canvasState: string; // JSON canvas data
  
  // Participants
  activeParticipants: string[];
  
  // Settings
  allowAnonymousView: boolean;
  
  createdAt: Date;
  updatedAt: Date;
}

// ============================================
// ACTIVITY FEEDS
// ============================================

export interface SocialActivity {
  id: string;
  tenantId: string;
  actorId: string;
  actorName: string;
  actorAvatarUrl?: string;
  
  // Action
  type: SocialActivityType;
  
  // Target
  targetType: 'study_group' | 'forum_topic' | 'forum_post' | 'tutoring_session' | 'shared_note';
  targetId: string;
  targetTitle: string;
  
  // Context
  studyGroupId?: string;
  classroomId?: string;
  
  // Metadata
  metadata?: Record<string, unknown>;
  
  createdAt: Date;
}

export type SocialActivityType =
  | 'joined_group'
  | 'created_topic'
  | 'replied_topic'
  | 'liked_post'
  | 'scheduled_session'
  | 'completed_session'
  | 'shared_note'
  | 'earned_badge'
  | 'helped_peer';

// ============================================
// NOTIFICATIONS
// ============================================

export interface SocialNotification {
  id: string;
  userId: string;
  tenantId: string;
  
  // Type
  type: SocialNotificationType;
  
  // Content
  title: string;
  body: string;
  iconUrl?: string;
  actionUrl?: string;
  
  // Source
  actorId?: string;
  actorName?: string;
  
  // Target
  targetType?: string;
  targetId?: string;
  
  // Status
  isRead: boolean;
  readAt?: Date;
  
  // Delivery
  emailSent: boolean;
  pushSent: boolean;
  
  createdAt: Date;
}

export type SocialNotificationType =
  | 'group_invite'
  | 'group_join_request'
  | 'group_message'
  | 'topic_reply'
  | 'post_mention'
  | 'post_reaction'
  | 'tutoring_request'
  | 'session_reminder'
  | 'review_received';
