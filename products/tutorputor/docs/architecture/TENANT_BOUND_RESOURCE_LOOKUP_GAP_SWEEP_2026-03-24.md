# TutorPutor Tenant-Bound Resource Lookup Gap Sweep

Date: 2026-03-24
Scope: `products/tutorputor/**`, with emphasis on `services/tutorputor-platform/src/**`
Author: GitHub Copilot

## Purpose

This document captures the current sweep for code paths that accept a tenant-scoped request context but still fetch, update, or relate resources by raw global IDs without re-binding those resources to the caller's tenant or parent resource.

This is a triaged report, not a raw grep dump. Findings below were manually reviewed to separate real authorization/tenant-bound lookup risks from patterns that already have an adequate scoped guard earlier in the flow.

## Method

- Searched TutorPutor backend code for Prisma lookups and mutations such as `findUnique`, `findFirst`, `update`, `delete`, `upsert`, `updateMany`, and `deleteMany`.
- Cross-checked whether the same code path also validated the parent resource, tenant, membership, or ownership before using the raw ID.
- Grouped results into confirmed findings and excluded patterns.

## Confirmed Findings

### 1. Forum reply creation trusts `parentId` without tenant/topic validation

Severity: High
Reachability: Service-level confirmed in live backend module

Location:

- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/forums.ts:402`

Code path:

```ts
if (args.parentId) {
  const parent = await this.prisma.forumPost.findUnique({
    where: { id: args.parentId },
  });
  if (parent) {
    depth = parent.depth + 1;
  }
}
```

Why this is a gap:

- The topic itself is tenant-validated through `requireTopic(args.tenantId, args.topicId, true)`.
- The optional parent post is not validated to belong to the same topic or tenant.
- A caller who knows another post ID can attach a reply to an unrelated parent post and inherit its depth.

Impact:

- Cross-topic or cross-tenant post graph corruption.
- Incorrect nesting and moderation state tied to a foreign parent post.

Recommended fix:

- Replace the raw `forumPost.findUnique({ id })` lookup with a helper that resolves the parent post through `topicId` and the topic's tenant-bound forum.
- Reject `parentId` unless it belongs to the same `topicId`.

### 2. Forum post edit/delete paths authorize by author only, not by tenant-bound post lookup

Severity: High
Reachability: Service-level confirmed in live backend module

Locations:

- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/forums.ts:514`
- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/forums.ts:552`

Code paths:

```ts
const post = await this.prisma.forumPost.findUnique({
  where: { id: args.postId },
});

if (post.authorId !== args.userId) {
  throw new Error("Only the author can edit this post");
}
```

```ts
const post = await this.prisma.forumPost.findUnique({
  where: { id: args.postId },
});

if (post.authorId !== args.userId) {
  throw new Error("Permission denied");
}
```

Why this is a gap:

- Both methods accept `tenantId` but do not use it when resolving the post.
- Authorization depends only on `post.authorId === args.userId`.
- If a user account can exist across multiple tenant contexts or if a caller can reference an older foreign-tenant post ID they authored, the current tenant boundary is bypassed.

Impact:

- Cross-tenant edits or soft-deletes of forum posts owned by the same user identity.

Recommended fix:

- Resolve the post through a tenant-bound helper that joins `forumPost -> forumTopic -> forum` and verifies `forum.tenantId === args.tenantId`.
- Reuse that helper everywhere a post ID enters the forum service.

### 3. Forum accepted-answer flow does not verify that `postId` belongs to the target topic

Severity: High
Reachability: Service-level confirmed in live backend module

Locations:

- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/forums.ts:576`
- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/forums.ts:597`
- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/forums.ts:795`

Relevant code:

```ts
const topic = await this.requireTopic(args.tenantId, args.topicId);

if (topic.authorId !== args.userId) {
  throw new Error("Only the topic author can mark an answer");
}

const post = await this.prisma.forumPost.update({
  where: { id: args.postId },
  data: { isAcceptedAnswer: true },
});
```

Why this is a gap:

- `requireTopic` does tenant-bind the topic via its forum in the default branch.
- The chosen answer post is updated by raw `postId` only.
- There is no check that the target post belongs to `args.topicId`.

Impact:

- A topic author can mark an unrelated post as the accepted answer for their topic.
- Cross-topic integrity corruption and misleading answer metadata.

Recommended fix:

- Fetch the candidate post through `where: { id: args.postId, topicId: args.topicId }` plus a tenant-bound topic/forum check.
- Reject mismatched post/topic pairs before updating `isAcceptedAnswer`.

### 4. Forum reaction flows mutate and reload posts by raw `postId`

Severity: High
Reachability: Service-level confirmed in live backend module

Locations:

- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/forums.ts:613`
- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/forums.ts:647`
- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/forums.ts:671`
- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/forums.ts:698`

Relevant code:

```ts
await this.prisma.forumPost.update({
  where: { id: args.postId },
  data: { likeCount: { increment: 1 } },
});

const post = await this.prisma.forumPost.findUnique({
  where: { id: args.postId },
  include: { reactions: true },
});
```

Why this is a gap:

- Reactions are keyed only by `postId`, `userId`, and type.
- The code never proves that the target post belongs to the caller's tenant before incrementing counts, notifying authors, or returning the post.

Impact:

- Cross-tenant mutation of reaction counters.
- Cross-tenant notification fan-out if a foreign post ID is used.

Recommended fix:

- Introduce a tenant-bound post lookup helper and use it before create/delete/update notification flows.
- Only mutate counts after the post is proven to belong to the caller's tenant.

### 5. Study-group invite and request handlers ignore `tenantId` when resolving the primary resource

Severity: Medium
Reachability: Latent service-level risk; route exposure for these exact methods was not visible in the currently mounted `social/routes.ts`

Locations:

- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/study-groups.ts:349`
- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/study-groups.ts:456`

Relevant code:

```ts
const request = await this.prisma.studyGroupJoinRequest.findUnique({
  where: { id: args.requestId },
});
```

```ts
const invite = await this.prisma.studyGroupInvite.findUnique({
  where: { id: args.inviteId },
});
```

Why this is a gap:

- Both methods accept `tenantId` but do not use it in the primary lookup.
- Follow-on logic relies on `groupId`, status, and role checks, but the core request/invite record itself is not tenant-bound at load time.

Impact:

- Invitation and join-request workflows can be driven by raw IDs without first proving tenant ownership.
- Even if later group-role checks limit exploitability, the tenant contract is inconsistent and fragile.

Recommended fix:

- Add helpers that resolve join requests and invites by joining back to the owning study group and checking the group's tenant.
- Reject the resource before any role or status transition logic runs.

### 6. Study-group member-role mutation and member removal use raw member IDs without proving group linkage

Severity: High
Reachability: Latent service-level risk; route exposure for these exact methods was not visible in the currently mounted `social/routes.ts`

Locations:

- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/study-groups.ts:527`
- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/study-groups.ts:536`
- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/study-groups.ts:544`
- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/study-groups.ts:550`

Relevant code:

```ts
await this.requireRole(args.groupId, args.updatedBy, ["OWNER"]);

const member = await this.prisma.studyGroupMember.update({
  where: { id: args.memberId },
  data: { role: this.mapRoleToDb(args.newRole) },
});
```

```ts
const member = await this.prisma.studyGroupMember.findUnique({
  where: { id: args.memberId },
});

await this.requireRole(args.groupId, args.removedBy, [
  "OWNER",
  "ADMIN",
  "MODERATOR",
]);
```

Why this is a gap:

- The caller is authorized against `args.groupId`.
- The code never verifies that `args.memberId` belongs to that same group.
- A privileged member of group A could target a member record from group B if they can supply its raw ID.

Impact:

- Cross-group role escalation or member removal.
- Potential cross-tenant effects if group IDs span tenants.

Recommended fix:

- Resolve the member by both `id` and `groupId`, then verify the owning group's tenant.
- Reject mismatched `memberId`/`groupId` pairs before update/delete.

### 7. Study-session RSVP trusts arbitrary `sessionId` and does not verify tenant or group membership

Severity: Medium
Reachability: Latent service-level risk; route exposure for this exact method was not visible in the currently mounted `social/routes.ts`

Location:

- `products/tutorputor/services/tutorputor-platform/src/modules/engagement/social/study-groups.ts:703`

Relevant code:

```ts
const rsvp = await this.prisma.sessionRsvp.upsert({
  where: {
    sessionId_userId: {
      sessionId: args.sessionId,
      userId: args.userId,
    },
  },
  create: {
    sessionId: args.sessionId,
    userId: args.userId,
```

Why this is a gap:

- The method never loads the session first.
- It does not verify that the session belongs to the caller's tenant or that the caller is allowed to RSVP for that session's group.

Impact:

- Unauthorized RSVP creation against arbitrary session IDs.
- Session participation metadata can drift across tenant or group boundaries.

Recommended fix:

- Resolve the session through a tenant-bound helper before the upsert.
- Enforce membership or visibility rules for the session's study group.

## Excluded Patterns Reviewed During This Sweep

These patterns looked suspicious in a text search but were not counted as tenant-bound lookup gaps after manual review:

- `tenant/routes.ts` and `auth/service.ts` delete SSO providers by raw ID only after a tenant-bound `findFirst({ id, tenantId })` existence check.
- `content/studio/routes.ts` deletes automation rules by raw ID only after a tenant-bound `findFirst({ id, tenantId, experienceId })` guard.
- `compliance/routes.ts` deletion-token request looks up the current user by JWT-derived `userId`, not by caller-supplied foreign identity.
- `engagement/social/chat.ts` loads a message by raw ID in `requireMessage`, but immediately re-binds access through `requireRoom(tenantId, message.roomId, userId)`.
- `collaboration/service.ts` answer-marking was previously a gap, but the current code now verifies the selected post belongs to the target thread.

## Recommended Remediation Pattern

The strongest recurring fix is to stop scattering raw `findUnique({ id })` lookups and replace them with tenant-aware helper methods per aggregate root.

Recommended helpers:

- `requireForumPostInTenant(tenantId, postId)`
- `requireForumPostInTopic(tenantId, topicId, postId)`
- `requireStudyGroupInviteInTenant(tenantId, inviteId)`
- `requireStudyGroupRequestInTenant(tenantId, requestId)`
- `requireStudyGroupMemberInGroup(tenantId, groupId, memberId)`
- `requireStudySessionInTenant(tenantId, sessionId)`

Recommended test additions:

- Forum post update/delete with a foreign-tenant or foreign-topic `postId`
- Forum answer-marking with a post from another topic
- Forum reply creation with a foreign `parentId`
- Study-group member-role update/removal with a `memberId` from another group
- RSVP creation against a session outside the caller's group/tenant

## Summary

Highest-value confirmed work from this sweep:

- Forum post handling still has several raw-ID mutations that are not tenant- or parent-bound.
- Study-group services contain a second cluster of tenant-agnostic lookups, especially around invites, join requests, member-role changes, and RSVP/session handling.
- Several initially suspicious delete-by-ID patterns were reviewed and intentionally excluded because they already perform a scoped existence check first.
