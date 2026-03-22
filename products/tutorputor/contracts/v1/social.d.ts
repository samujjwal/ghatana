/**
 * @doc.type module
 * @doc.purpose Social Learning contracts - Study Groups, Forums, Peer Learning
 * @doc.layer contracts
 * @doc.pattern ValueObject
 */
export interface StudyGroup {
    id: string;
    tenantId: string;
    name: string;
    description: string;
    coverImageUrl?: string;
    createdBy: string;
    createdAt: Date;
    updatedAt: Date;
    visibility: StudyGroupVisibility;
    maxMembers: number;
    requireApproval: boolean;
    allowGuestView: boolean;
    subjects: string[];
    modules: string[];
    memberCount: number;
    lastActivityAt: Date;
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
    messagesCount: number;
    lastActiveAt: Date;
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
export interface StudySession {
    id: string;
    groupId: string;
    title: string;
    description?: string;
    createdBy: string;
    scheduledAt: Date;
    duration: number;
    timezone: string;
    type: StudySessionType;
    meetingUrl?: string;
    maxParticipants?: number;
    rsvpDeadline?: Date;
    moduleId?: string;
    lessonIds?: string[];
    agenda?: string;
    attachments?: SessionAttachment[];
    status: StudySessionStatus;
    startedAt?: Date;
    endedAt?: Date;
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
export interface Forum {
    id: string;
    tenantId: string;
    name: string;
    description: string;
    iconUrl?: string;
    scope: ForumScope;
    scopeId?: string;
    allowAnonymousPosts: boolean;
    requireModeration: boolean;
    allowAttachments: boolean;
    allowPolls: boolean;
    categories: ForumCategory[];
    topicCount: number;
    postCount: number;
    lastPostAt?: Date;
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
    content: string;
    contentFormat: 'markdown' | 'html' | 'plain';
    attachments?: TopicAttachment[];
    viewCount: number;
    replyCount: number;
    likeCount: number;
    isPinned: boolean;
    isLocked: boolean;
    isAnswered: boolean;
    answerId?: string;
    status: TopicStatus;
    moderatedBy?: string;
    moderatedAt?: Date;
    moderationNote?: string;
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
    content: string;
    contentFormat: 'markdown' | 'html' | 'plain';
    attachments?: TopicAttachment[];
    parentId?: string;
    depth: number;
    likeCount: number;
    isAcceptedAnswer: boolean;
    status: TopicStatus;
    moderatedBy?: string;
    moderatedAt?: Date;
    isEdited: boolean;
    editedAt?: Date;
    editHistory?: PostEdit[];
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
export interface TutorProfile {
    id: string;
    userId: string;
    tenantId: string;
    displayName: string;
    bio: string;
    avatarUrl?: string;
    subjects: string[];
    modules: string[];
    qualifications?: string[];
    isAvailable: boolean;
    availabilitySchedule?: AvailabilitySlot[];
    timezone: string;
    responseTime: string;
    sessionTypes: TutoringSessionType[];
    maxSessionsPerWeek: number;
    pricePerHour?: number;
    rating: number;
    reviewCount: number;
    sessionsCompleted: number;
    totalHelpedStudents: number;
    status: 'active' | 'paused' | 'inactive';
    verifiedAt?: Date;
    verifiedBy?: string;
    createdAt: Date;
    updatedAt: Date;
}
export interface AvailabilitySlot {
    dayOfWeek: number;
    startTime: string;
    endTime: string;
}
export type TutoringSessionType = 'text_chat' | 'video_call' | 'screen_share' | 'collaborative_whiteboard';
export interface TutoringRequest {
    id: string;
    studentId: string;
    tutorId?: string;
    tenantId: string;
    subject: string;
    moduleId?: string;
    lessonId?: string;
    title: string;
    description: string;
    attachments?: string[];
    preferredTypes: TutoringSessionType[];
    preferredTime?: Date;
    estimatedDuration: number;
    urgency: 'low' | 'medium' | 'high';
    status: TutoringRequestStatus;
    createdAt: Date;
    updatedAt: Date;
    acceptedAt?: Date;
    completedAt?: Date;
    cancelledAt?: Date;
    cancellationReason?: string;
}
export type TutoringRequestStatus = 'open' | 'matched' | 'in_progress' | 'completed' | 'cancelled' | 'expired';
export interface TutoringSession {
    id: string;
    requestId: string;
    studentId: string;
    tutorId: string;
    tenantId: string;
    type: TutoringSessionType;
    scheduledAt: Date;
    duration: number;
    meetingUrl?: string;
    moduleId?: string;
    lessonId?: string;
    notes?: string;
    sharedResources?: string[];
    status: 'scheduled' | 'in_progress' | 'completed' | 'no_show' | 'cancelled';
    startedAt?: Date;
    endedAt?: Date;
    actualDuration?: number;
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
    rating: number;
    helpfulness: number;
    communication: number;
    knowledge: number;
    comment?: string;
    privateNote?: string;
    response?: string;
    respondedAt?: Date;
    isVisible: boolean;
    moderatedBy?: string;
    moderatedAt?: Date;
    createdAt: Date;
    updatedAt: Date;
}
export interface ChatRoom {
    id: string;
    tenantId: string;
    type: ChatRoomType;
    name?: string;
    studyGroupId?: string;
    classroomId?: string;
    tutoringSessionId?: string;
    participants: string[];
    maxParticipants?: number;
    isEncrypted: boolean;
    retentionDays: number;
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
    type: ChatMessageType;
    content: string;
    metadata?: Record<string, unknown>;
    attachments?: ChatAttachment[];
    replyToId?: string;
    reactions: Record<string, string[]>;
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
export interface SharedNote {
    id: string;
    tenantId: string;
    createdBy: string;
    sharedWith: SharedNoteAccess[];
    studyGroupId?: string;
    title: string;
    content: string;
    version: number;
    allowEditing: boolean;
    allowComments: boolean;
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
    anchorStart?: number;
    anchorEnd?: number;
    content: string;
    parentId?: string;
    isResolved: boolean;
    resolvedBy?: string;
    resolvedAt?: Date;
    createdAt: Date;
    updatedAt: Date;
}
export interface CollaborativeWhiteboard {
    id: string;
    tenantId: string;
    studyGroupId?: string;
    tutoringSessionId?: string;
    name: string;
    canvasState: string;
    activeParticipants: string[];
    allowAnonymousView: boolean;
    createdAt: Date;
    updatedAt: Date;
}
export interface SocialActivity {
    id: string;
    tenantId: string;
    actorId: string;
    actorName: string;
    actorAvatarUrl?: string;
    type: SocialActivityType;
    targetType: 'study_group' | 'forum_topic' | 'forum_post' | 'tutoring_session' | 'shared_note';
    targetId: string;
    targetTitle: string;
    studyGroupId?: string;
    classroomId?: string;
    metadata?: Record<string, unknown>;
    createdAt: Date;
}
export type SocialActivityType = 'joined_group' | 'created_topic' | 'replied_topic' | 'liked_post' | 'scheduled_session' | 'completed_session' | 'shared_note' | 'earned_badge' | 'helped_peer';
export interface SocialNotification {
    id: string;
    userId: string;
    tenantId: string;
    type: SocialNotificationType;
    title: string;
    body: string;
    iconUrl?: string;
    actionUrl?: string;
    actorId?: string;
    actorName?: string;
    targetType?: string;
    targetId?: string;
    isRead: boolean;
    readAt?: Date;
    emailSent: boolean;
    pushSent: boolean;
    createdAt: Date;
}
export type SocialNotificationType = 'group_invite' | 'group_join_request' | 'group_message' | 'topic_reply' | 'post_mention' | 'post_reaction' | 'tutoring_request' | 'session_reminder' | 'review_received';
//# sourceMappingURL=social.d.ts.map