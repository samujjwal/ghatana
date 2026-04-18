/**
 * Study Group Matching Service
 *
 * Algorithm for suggesting study groups based on:
 * - Learning overlap (shared interests, topics, modules)
 * - Learning style compatibility
 * - Schedule compatibility
 * - Skill level matching
 * - Social graph analysis (friends of friends)
 *
 * @doc.type service
 * @doc.purpose Suggest optimal study group formations based on learning profiles
 * @doc.layer product
 * @doc.pattern Service
 */
import type { PrismaClient } from "@prisma/client";

export interface UserLearningProfile {
  userId: string;
  interests: string[];
  enrolledModules: string[];
  completedModules: string[];
  learningStyle?: "visual" | "auditory" | "reading" | "kinesthetic";
  preferredStudyTime?: "morning" | "afternoon" | "evening" | "night";
  skillLevels: Map<string, number>; // topic -> skill level (1-10)
  timezone?: string;
  languages: string[];
  peerConnections: string[]; // connected user IDs
}

export interface StudyGroupMatch {
  groupId: string;
  name: string;
  description: string;
  matchScore: number;
  matchReasons: string[];
  members: Array<{
    userId: string;
    name: string;
    commonInterests: string[];
  }>;
  commonTopics: string[];
  recommendedModules: string[];
  size: number;
  maxSize: number;
}

export interface GroupFormationSuggestion {
  suggestedMembers: string[];
  suggestedName: string;
  suggestedTopics: string[];
  compatibilityMatrix: Map<string, Map<string, number>>;
  averageCompatibility: number;
}

export class GroupMatchingService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Find study groups matching a user's profile
   */
  async findMatchingGroups(
    tenantId: string,
    userId: string,
    limit: number = 5
  ): Promise<StudyGroupMatch[]> {
    // Get user's learning profile
    const userProfile = await this.getUserLearningProfile(tenantId, userId);

    // Get available groups in tenant
    const availableGroups = await this.getAvailableGroups(tenantId, userId);

    // Calculate match scores
    const scoredGroups = availableGroups.map((group) =>
      this.calculateGroupMatchScore(userProfile, group)
    );

    // Sort by score and return top matches
    return scoredGroups
      .sort((a, b) => b.matchScore - a.matchScore)
      .slice(0, limit);
  }

  /**
   * Suggest new group formation
   */
  async suggestGroupFormation(
    tenantId: string,
    seedUserId: string,
    targetSize: number = 4
  ): Promise<GroupFormationSuggestion | null> {
    const seedProfile = await this.getUserLearningProfile(tenantId, seedUserId);

    // Find compatible users
    const candidateUsers = await this.findCompatibleUsers(
      tenantId,
      seedUserId,
      targetSize * 3 // Get more candidates for selection
    );

    if (candidateUsers.length < targetSize - 1) {
      return null;
    }

    // Calculate compatibility matrix
    const compatibilityMatrix = this.buildCompatibilityMatrix([
      seedProfile,
      ...candidateUsers.slice(0, targetSize - 1),
    ]);

    // Select optimal subset
    const optimalGroup = this.selectOptimalGroup(
      seedProfile,
      candidateUsers,
      targetSize - 1,
      compatibilityMatrix
    );

    const averageCompatibility = this.calculateAverageCompatibility(
      optimalGroup,
      compatibilityMatrix
    );

    // Generate group name and topics
    const { name, topics } = this.generateGroupIdentity(
      seedProfile,
      optimalGroup
    );

    return {
      suggestedMembers: optimalGroup.map((u) => u.userId),
      suggestedName: name,
      suggestedTopics: topics,
      compatibilityMatrix,
      averageCompatibility,
    };
  }

  /**
   * Calculate learning overlap between two users
   */
  calculateLearningOverlap(
    profile1: UserLearningProfile,
    profile2: UserLearningProfile
  ): {
    score: number;
    commonInterests: string[];
    commonModules: string[];
    complementarySkills: string[];
  } {
    // Common interests
    const commonInterests = profile1.interests.filter((i) =>
      profile2.interests.includes(i)
    );

    // Common enrolled modules
    const commonModules = profile1.enrolledModules.filter((m) =>
      profile2.enrolledModules.includes(m)
    );

    // Complementary skills (one is strong where other is weak)
    const complementarySkills: string[] = [];
    for (const [topic, skill1] of profile1.skillLevels) {
      const skill2 = profile2.skillLevels.get(topic);
      if (skill2 !== undefined) {
        const diff = Math.abs(skill1 - skill2);
        if (diff >= 3) {
          complementarySkills.push(topic);
        }
      }
    }

    // Calculate score
    let score = 0;
    score += commonInterests.length * 2; // Interests are valuable
    score += commonModules.length * 3; // Shared current learning is very valuable
    score += complementarySkills.length * 1.5; // Complementary skills are beneficial

    // Bonus for learning style compatibility
    if (profile1.learningStyle === profile2.learningStyle) {
      score += 2;
    }

    // Bonus for schedule compatibility
    if (profile1.preferredStudyTime === profile2.preferredStudyTime) {
      score += 1.5;
    }

    // Bonus for timezone proximity
    if (
      profile1.timezone &&
      profile2.timezone &&
      this.areTimezonesCompatible(profile1.timezone, profile2.timezone)
    ) {
      score += 1;
    }

    return {
      score: Math.min(score, 20), // Cap at 20
      commonInterests,
      commonModules,
      complementarySkills,
    };
  }

  /**
   * Calculate learning overlap between two users by their IDs
   */
  async getLearningOverlapBetweenUsers(
    tenantId: string,
    userId1: string,
    userId2: string,
  ): Promise<ReturnType<GroupMatchingService["calculateLearningOverlap"]>> {
    const [profile1, profile2] = await Promise.all([
      this.getUserLearningProfile(tenantId, userId1),
      this.getUserLearningProfile(tenantId, userId2),
    ]);
    return this.calculateLearningOverlap(profile1, profile2);
  }

  /**
   * Get user learning profile from database
   */
  private async getUserLearningProfile(
    tenantId: string,
    userId: string
  ): Promise<UserLearningProfile> {
    // Query user's enrollments and activity
    const enrollments = await this.prisma.$queryRaw<Array<{
      moduleId: string;
      status: string;
    }>>`
      SELECT "moduleId", status
      FROM "Enrollment"
      WHERE "tenantId" = ${tenantId}
        AND "userId" = ${userId}
    `.catch(() => []);

    const enrolledModules = enrollments
      .filter((e) => e.status === "active")
      .map((e) => e.moduleId);

    const completedModules = enrollments
      .filter((e) => e.status === "completed")
      .map((e) => e.moduleId);

    // Get user preferences
    const preferences = await this.prisma.$queryRaw<Array<{
      interests: string;
      learningStyle: string;
      preferredStudyTime: string;
      timezone: string;
      languages: string;
    }>>`
      SELECT interests, "learningStyle", "preferredStudyTime", timezone, languages
      FROM "UserProfile"
      WHERE "userId" = ${userId}
    `.catch(() => []);

    const pref = preferences[0];

    // Get connections
    const connections = await this.prisma.$queryRaw<Array<{ peerId: string }>>`
      SELECT "peerId"
      FROM "UserConnection"
      WHERE "userId" = ${userId}
        AND status = 'connected'
    `.catch(() => []);

    const profile: UserLearningProfile = {
      userId,
      interests: pref?.interests ? JSON.parse(pref.interests) : [],
      enrolledModules,
      completedModules,
      skillLevels: new Map(), // Would be populated from assessment data
      languages: pref?.languages ? JSON.parse(pref.languages) : ["en"],
      peerConnections: connections.map((c) => c.peerId),
    };
    if (pref?.learningStyle) {
      profile.learningStyle = pref.learningStyle as "visual" | "auditory" | "reading" | "kinesthetic";
    }
    if (pref?.preferredStudyTime) {
      profile.preferredStudyTime = pref.preferredStudyTime as "morning" | "afternoon" | "evening" | "night";
    }
    if (pref?.timezone) {
      profile.timezone = pref.timezone;
    }
    return profile;
  }

  /**
   * Get available groups for user
   */
  private async getAvailableGroups(
    tenantId: string,
    excludeUserId: string
  ): Promise<StudyGroupMatch[]> {
    const groups = await this.prisma.$queryRaw<Array<{
      id: string;
      name: string;
      description: string;
      maxSize: number;
      topics: string;
    }>>`
      SELECT g.id, g.name, g.description, g."maxSize", g.topics
      FROM "StudyGroup" g
      WHERE g."tenantId" = ${tenantId}
        AND g.status = 'active'
        AND g."isPrivate" = false
        AND (SELECT COUNT(*) FROM "GroupMembership" WHERE "groupId" = g.id) < g."maxSize"
        AND NOT EXISTS (
          SELECT 1 FROM "GroupMembership"
          WHERE "groupId" = g.id AND "userId" = ${excludeUserId}
        )
    `.catch(() => []);

    const result: StudyGroupMatch[] = [];

    for (const group of groups) {
      const members = await this.prisma.$queryRaw<Array<{
        userId: string;
        name: string;
        interests: string;
      }>>`
        SELECT u.id as "userId", u.name, p.interests
        FROM "GroupMembership" gm
        JOIN "User" u ON gm."userId" = u.id
        LEFT JOIN "UserProfile" p ON u.id = p."userId"
        WHERE gm."groupId" = ${group.id}
      `.catch(() => []);

      result.push({
        groupId: group.id,
        name: group.name,
        description: group.description,
        matchScore: 0, // Will be calculated
        matchReasons: [],
        members: members.map((m) => ({
          userId: m.userId,
          name: m.name,
          commonInterests: [],
        })),
        commonTopics: group.topics ? JSON.parse(group.topics) : [],
        recommendedModules: [],
        size: members.length,
        maxSize: group.maxSize,
      });
    }

    return result;
  }

  /**
   * Calculate match score between user and group
   */
  private calculateGroupMatchScore(
    userProfile: UserLearningProfile,
    group: StudyGroupMatch
  ): StudyGroupMatch {
    let score = 0;
    const reasons: string[] = [];

    // Common interests with existing members
    for (const member of group.members) {
      const commonInterests = member.commonInterests.filter((i) =>
        userProfile.interests.includes(i)
      );
      score += commonInterests.length * 1.5;
    }

    // Common topics
    const commonTopics = group.commonTopics.filter((t) =>
      userProfile.interests.includes(t)
    );
    if (commonTopics.length > 0) {
      score += commonTopics.length * 3;
      reasons.push(`Shares ${commonTopics.length} topic interests`);
    }

    // Enrolled modules match
    const moduleOverlap = group.commonTopics.filter((t) =>
      userProfile.enrolledModules.includes(t)
    );
    if (moduleOverlap.length > 0) {
      score += moduleOverlap.length * 4;
      reasons.push("Currently studying similar material");
    }

    // Social connection (friends of friends)
    const socialConnections = group.members.filter((m) =>
      userProfile.peerConnections.includes(m.userId)
    ).length;
    if (socialConnections > 0) {
      score += socialConnections * 2;
      reasons.push(`${socialConnections} mutual connections`);
    }

    // Group size preference (prefer groups with 2-5 members)
    if (group.size >= 2 && group.size <= 5) {
      score += 2;
    }

    // Normalize score to 0-100
    score = Math.min(score * 5, 100);

    return {
      ...group,
      matchScore: score,
      matchReasons: reasons,
    };
  }

  /**
   * Find compatible users for group formation
   */
  private async findCompatibleUsers(
    tenantId: string,
    excludeUserId: string,
    limit: number
  ): Promise<UserLearningProfile[]> {
    const users = await this.prisma.$queryRaw<Array<{ id: string }>>`
      SELECT id
      FROM "User"
      WHERE "tenantId" = ${tenantId}
        AND id != ${excludeUserId}
        AND status = 'active'
      LIMIT ${limit}
    `.catch(() => []);

    const profiles: UserLearningProfile[] = [];
    for (const user of users) {
      const profile = await this.getUserLearningProfile(tenantId, user.id);
      profiles.push(profile);
    }

    return profiles;
  }

  /**
   * Build compatibility matrix for a set of users
   */
  private buildCompatibilityMatrix(
    profiles: UserLearningProfile[]
  ): Map<string, Map<string, number>> {
    const matrix = new Map<string, Map<string, number>>();

    for (const p1 of profiles) {
      const row = new Map<string, number>();
      for (const p2 of profiles) {
        if (p1.userId === p2.userId) {
          row.set(p2.userId, 1); // Self compatibility is 1
        } else {
          const { score } = this.calculateLearningOverlap(p1, p2);
          row.set(p2.userId, score / 20); // Normalize to 0-1
        }
      }
      matrix.set(p1.userId, row);
    }

    return matrix;
  }

  /**
   * Select optimal group from candidates
   */
  private selectOptimalGroup(
    seedProfile: UserLearningProfile,
    candidates: UserLearningProfile[],
    targetSize: number,
    compatibilityMatrix: Map<string, Map<string, number>>
  ): UserLearningProfile[] {
    // Greedy selection: pick users with highest compatibility to seed
    const scored = candidates.map((c) => {
      const score = compatibilityMatrix.get(seedProfile.userId)?.get(c.userId) ?? 0;
      return { profile: c, score };
    });

    return scored
      .sort((a, b) => b.score - a.score)
      .slice(0, targetSize)
      .map((s) => s.profile);
  }

  /**
   * Calculate average compatibility within group
   */
  private calculateAverageCompatibility(
    group: UserLearningProfile[],
    matrix: Map<string, Map<string, number>>
  ): number {
    let total = 0;
    let count = 0;

    for (let i = 0; i < group.length; i++) {
      for (let j = i + 1; j < group.length; j++) {
        const gi = group[i];
        const gj = group[j];
        const score = gi !== undefined && gj !== undefined
          ? (matrix.get(gi.userId)?.get(gj.userId) ?? 0)
          : 0;
        total += score;
        count++;
      }
    }

    return count > 0 ? total / count : 0;
  }

  /**
   * Generate group name and identity
   */
  private generateGroupIdentity(
    seed: UserLearningProfile,
    members: UserLearningProfile[]
  ): { name: string; topics: string[] } {
    // Find most common topics
    const topicCounts = new Map<string, number>();

    for (const interest of seed.interests) {
      topicCounts.set(interest, (topicCounts.get(interest) ?? 0) + 1);
    }

    for (const member of members) {
      for (const interest of member.interests) {
        if (seed.interests.includes(interest)) {
          topicCounts.set(interest, (topicCounts.get(interest) ?? 0) + 1);
        }
      }
    }

    const topTopics = Array.from(topicCounts.entries())
      .sort((a, b) => b[1] - a[1])
      .slice(0, 2)
      .map((t) => t[0]);

    const name = topTopics.length > 0
      ? `${topTopics.map((t) => t.charAt(0).toUpperCase() + t.slice(1)).join(" & ")} Study Group`
      : "Learning Circle";

    return { name, topics: topTopics };
  }

  /**
   * Check if timezones are compatible (within 4 hours)
   */
  private areTimezonesCompatible(tz1: string, tz2: string): boolean {
    // Simplified check - in production would use proper timezone library
    try {
      const offset1 = this.getTimezoneOffset(tz1);
      const offset2 = this.getTimezoneOffset(tz2);
      return Math.abs(offset1 - offset2) <= 4;
    } catch {
      return false;
    }
  }

  private getTimezoneOffset(timezone: string): number {
    // This is a simplified implementation
    // In production, use a proper timezone library like date-fns-tz
    const date = new Date();
    const formatter = new Intl.DateTimeFormat("en-US", {
      timeZone: timezone,
      timeZoneName: "shortOffset",
    });
    const parts = formatter.formatToParts(date);
    const offsetPart = parts.find((p) => p.type === "timeZoneName");
    if (!offsetPart) return 0;

    // Parse offset like "GMT-5" or "GMT+2"
    const match = offsetPart.value.match(/GMT([+-])(\d+)/);
    if (!match) return 0;

    const [, sign, hours] = match;
    if (hours === undefined || sign === undefined) return 0;
    return parseInt(hours) * (sign === "-" ? -1 : 1);
  }
}
