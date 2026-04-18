/**
 * Suggested Study Groups Component
 *
 * Displays AI-suggested study groups with:
 * - Match scores and reasons
 * - Group preview
 * - One-click join
 * - Smart group creation
 *
 * @doc.type component
 * @doc.purpose Display and manage suggested study groups
 * @doc.layer product
 * @doc.pattern Component
 */
import { useState } from "react";
import {
  Users,
  Sparkles,
  ChevronRight,
  BookOpen,
  Clock,
  Zap,
  Plus,
  Loader2,
  Check,
} from "lucide-react";
import { Card, Badge, Button, Avatar } from "@ghatana/design-system";

export interface SuggestedGroup {
  groupId: string;
  name: string;
  description: string;
  matchScore: number;
  matchReasons: string[];
  members: Array<{
    userId: string;
    name: string;
    avatar?: string;
    commonInterests: string[];
  }>;
  commonTopics: string[];
  size: number;
  maxSize: number;
}

export interface GroupFormationSuggestionUI {
  suggestedMembers: Array<{
    userId: string;
    name: string;
    avatar?: string;
    matchScore: number;
  }>;
  suggestedName: string;
  suggestedTopics: string[];
  averageCompatibility: number;
}

interface SuggestedGroupsProps {
  suggestedGroups: SuggestedGroup[];
  formationSuggestion?: GroupFormationSuggestionUI | null;
  onJoinGroup: (groupId: string) => Promise<void>;
  onCreateGroup: (
    members: string[],
    name: string,
    topics: string[]
  ) => Promise<void>;
  isLoading?: boolean;
}

export function SuggestedGroups({
  suggestedGroups,
  formationSuggestion,
  onJoinGroup,
  onCreateGroup,
  isLoading = false,
}: SuggestedGroupsProps) {
  const [joiningGroupId, setJoiningGroupId] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [selectedMembers, setSelectedMembers] = useState<string[]>([]);

  const handleJoin = async (groupId: string) => {
    setJoiningGroupId(groupId);
    try {
      await onJoinGroup(groupId);
    } finally {
      setJoiningGroupId(null);
    }
  };

  const handleCreateGroup = async () => {
    if (!formationSuggestion) return;

    setIsCreating(true);
    try {
      const membersToInvite =
        selectedMembers.length > 0
          ? selectedMembers
          : formationSuggestion.suggestedMembers.map((m) => m.userId);

      await onCreateGroup(
        membersToInvite,
        formationSuggestion.suggestedName,
        formationSuggestion.suggestedTopics
      );
    } finally {
      setIsCreating(false);
    }
  };

  const toggleMember = (userId: string) => {
    setSelectedMembers((prev) =>
      prev.includes(userId)
        ? prev.filter((id) => id !== userId)
        : [...prev, userId]
    );
  };

  const getScoreColor = (score: number) => {
    if (score >= 80) return "bg-green-500";
    if (score >= 60) return "bg-blue-500";
    if (score >= 40) return "bg-yellow-500";
    return "bg-gray-400";
  };

  return (
    <div className="space-y-6">
      {/* Smart Group Formation */}
      {formationSuggestion && (
        <Card className="p-6 border-2 border-purple-200 bg-purple-50">
          <div className="flex items-center gap-2 mb-4">
            <Sparkles className="w-5 h-5 text-purple-600" />
            <h2 className="text-lg font-semibold text-purple-900">
              AI-Suggested Study Group
            </h2>
            <Badge className="bg-purple-100 text-purple-800">
              {Math.round(formationSuggestion.averageCompatibility * 100)}% Compatible
            </Badge>
          </div>

          <p className="text-gray-600 mb-4">
            We found {formationSuggestion.suggestedMembers.length} learners who
            would be great study partners for you based on your shared interests
            and learning goals.
          </p>

          <div className="mb-4">
            <h3 className="text-sm font-medium text-gray-700 mb-2">
              Suggested Members
            </h3>
            <div className="flex flex-wrap gap-3">
              {formationSuggestion.suggestedMembers.map((member) => (
                <button
                  key={member.userId}
                  onClick={() => toggleMember(member.userId)}
                  className={`flex items-center gap-2 p-2 rounded-lg border transition-colors ${
                    selectedMembers.length === 0 ||
                    selectedMembers.includes(member.userId)
                      ? "bg-white border-gray-200 hover:border-purple-300"
                      : "bg-gray-100 border-gray-200 opacity-50"
                  }`}
                >
                  <Avatar className="w-8 h-8">
                    {member.avatar ? (
                      <img src={member.avatar} alt={member.name} />
                    ) : (
                      <span className="text-xs">{member.name.charAt(0)}</span>
                    )}
                  </Avatar>
                  <div className="text-left">
                    <p className="text-sm font-medium">{member.name}</p>
                    <p className="text-xs text-gray-500">
                      {Math.round(member.matchScore * 100)}% match
                    </p>
                  </div>
                  {(selectedMembers.length === 0 ||
                    selectedMembers.includes(member.userId)) && (
                    <Check className="w-4 h-4 text-green-500" />
                  )}
                </button>
              ))}
            </div>
          </div>

          <div className="mb-4">
            <h3 className="text-sm font-medium text-gray-700 mb-2">
              Topics: {formationSuggestion.suggestedName}
            </h3>
            <div className="flex flex-wrap gap-2">
              {formationSuggestion.suggestedTopics.map((topic) => (
                <Badge key={topic} variant="secondary">
                  {topic}
                </Badge>
              ))}
            </div>
          </div>

          <Button
            onClick={handleCreateGroup}
            disabled={isCreating}
            className="w-full"
          >
            {isCreating ? (
              <Loader2 className="w-4 h-4 animate-spin mr-2" />
            ) : (
              <Plus className="w-4 h-4 mr-2" />
            )}
            Create Group with Selected Members
          </Button>
        </Card>
      )}

      {/* Existing Group Suggestions */}
      <div>
        <h2 className="text-lg font-semibold mb-4">Join an Existing Group</h2>

        {suggestedGroups.length === 0 ? (
          <Card className="p-8 text-center">
            <Users className="w-12 h-12 mx-auto text-gray-400 mb-3" />
            <p className="text-gray-600">
              No matching groups found right now. Check back later or create
              your own group above!
            </p>
          </Card>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {suggestedGroups.map((group) => (
              <Card
                key={group.groupId}
                className="p-4 hover:shadow-md transition-shadow"
              >
                <div className="flex items-start justify-between mb-3">
                  <div>
                    <h3 className="font-semibold text-lg">{group.name}</h3>
                    <p className="text-sm text-gray-600 line-clamp-2">
                      {group.description}
                    </p>
                  </div>
                  <div
                    className={`w-12 h-12 rounded-full flex items-center justify-center text-white text-sm font-bold ${getScoreColor(
                      group.matchScore
                    )}`}
                  >
                    {Math.round(group.matchScore)}%
                  </div>
                </div>

                {/* Match Reasons */}
                <div className="flex flex-wrap gap-1 mb-3">
                  {group.matchReasons.map((reason, i) => (
                    <Badge
                      key={i}
                      variant="outline"
                      className="text-xs bg-green-50 text-green-700 border-green-200"
                    >
                      <Zap className="w-3 h-3 mr-1" />
                      {reason}
                    </Badge>
                  ))}
                </div>

                {/* Topics */}
                <div className="flex flex-wrap gap-1 mb-3">
                  {group.commonTopics.slice(0, 3).map((topic) => (
                    <Badge key={topic} variant="secondary" className="text-xs">
                      <BookOpen className="w-3 h-3 mr-1" />
                      {topic}
                    </Badge>
                  ))}
                </div>

                {/* Members */}
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <div className="flex -space-x-2">
                      {group.members.slice(0, 3).map((member) => (
                        <Avatar
                          key={member.userId}
                          className="w-8 h-8 border-2 border-white"
                        >
                          {member.avatar ? (
                            <img src={member.avatar} alt={member.name} />
                          ) : (
                            <span className="text-xs">
                              {member.name.charAt(0)}
                            </span>
                          )}
                        </Avatar>
                      ))}
                    </div>
                    <span className="text-sm text-gray-500">
                      {group.size}/{group.maxSize} members
                    </span>
                  </div>
                  <div className="flex items-center gap-1 text-sm text-gray-500">
                    <Clock className="w-4 h-4" />
                    <span>Active</span>
                  </div>
                </div>

                <Button
                  onClick={() => handleJoin(group.groupId)}
                  disabled={joiningGroupId === group.groupId || isLoading}
                  className="w-full"
                >
                  {joiningGroupId === group.groupId ? (
                    <Loader2 className="w-4 h-4 animate-spin mr-2" />
                  ) : (
                    <>
                      Join Group
                      <ChevronRight className="w-4 h-4 ml-1" />
                    </>
                  )}
                </Button>
              </Card>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// Hook for managing group suggestions
export function useSuggestedGroups(tenantId: string, userId: string) {
  const [suggestedGroups, setSuggestedGroups] = useState<SuggestedGroup[]>([]);
  const [formationSuggestion, setFormationSuggestion] =
    useState<GroupFormationSuggestionUI | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const fetchSuggestions = async () => {
    setIsLoading(true);
    try {
      // Fetch suggested groups
      const groupsResponse = await fetch(
        `/api/collaboration/groups/suggested?tenantId=${tenantId}&userId=${userId}`
      );
      const groupsData = await groupsResponse.json();
      setSuggestedGroups(groupsData.groups || []);

      // Fetch formation suggestion
      const formationResponse = await fetch(
        `/api/collaboration/groups/formation-suggestion?tenantId=${tenantId}&userId=${userId}`
      );
      const formationData = await formationResponse.json();
      setFormationSuggestion(formationData.suggestion || null);
    } catch (error) {
      console.error("Failed to fetch group suggestions:", error);
    } finally {
      setIsLoading(false);
    }
  };

  const joinGroup = async (groupId: string) => {
    const response = await fetch("/api/collaboration/groups/join", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ tenantId, userId, groupId }),
    });

    if (!response.ok) {
      throw new Error("Failed to join group");
    }

    // Remove joined group from suggestions
    setSuggestedGroups((prev) => prev.filter((g) => g.groupId !== groupId));
  };

  const createGroup = async (
    members: string[],
    name: string,
    topics: string[]
  ) => {
    const response = await fetch("/api/collaboration/groups/create", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        tenantId,
        userId,
        name,
        topics,
        initialMembers: members,
      }),
    });

    if (!response.ok) {
      throw new Error("Failed to create group");
    }

    // Clear formation suggestion after creation
    setFormationSuggestion(null);
  };

  return {
    suggestedGroups,
    formationSuggestion,
    isLoading,
    fetchSuggestions,
    joinGroup,
    createGroup,
  };
}
