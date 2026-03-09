import { useState } from "react";
import { Box, Card } from "@/components/ui";
import {
    useThreads,
    useThread,
    useCreateThread,
    useCreatePost,
    useVoteOnPost
} from "../../hooks/useCollaboration";
import { useRealtimeCollaboration, useTypingIndicators } from "../../hooks/useRealtimeCollaboration";

// Local type definitions
type ModuleId = string;
type ThreadId = string;
type ThreadStatus = "OPEN" | "RESOLVED" | "CLOSED";

interface Post {
    id: string;
    threadId: string;
    authorId: string;
    authorName: string;
    content: string;
    createdAt: string;
    updatedAt?: string;
    isAnswer?: boolean;
    voteCount?: number;
}

interface Thread {
    id: string;
    tenantId: string;
    moduleId?: string;
    title: string;
    status: ThreadStatus;
    authorId: string;
    authorName?: string;
    content: string;
    posts: Post[];
    createdAt: string;
    resolvedAt?: string;
    replyCount?: number;
}

interface CollaborationPanelProps {
    moduleId?: string;
}

/**
 * Discussion forum panel for module-specific Q&A and collaboration.
 * Includes real-time updates via WebSocket.
 */
export function CollaborationPanel({ moduleId }: CollaborationPanelProps) {
    const [selectedThreadId, setSelectedThreadId] = useState<string | null>(null);
    const [showCreateThread, setShowCreateThread] = useState(false);

    // Real-time collaboration
    const {
        isConnected,
        activeUsers,
        sendTypingIndicator
    } = useRealtimeCollaboration({
        moduleId: moduleId as ModuleId,
        threadId: selectedThreadId as ThreadId,
        enabled: !!moduleId
    });

    const { data: threadsData, isLoading } = useThreads(moduleId);
    const threads = threadsData?.threads ?? [];

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-48">
                <div className="text-gray-600">Loading discussions...</div>
            </div>
        );
    }

    return (
        <Box className="h-full flex flex-col">
            <div className="flex justify-between items-center mb-4">
                <div className="flex items-center gap-3">
                    <h2 className="text-xl font-semibold">Discussions</h2>
                    {isConnected && activeUsers > 0 && (
                        <span className="text-sm text-green-600 flex items-center gap-1">
                            <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
                            {activeUsers} online
                        </span>
                    )}
                </div>
                <button
                    onClick={() => setShowCreateThread(true)}
                    className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm"
                >
                    + New Thread
                </button>
            </div>

            {selectedThreadId ? (
                <ThreadView
                    threadId={selectedThreadId}
                    onBack={() => setSelectedThreadId(null)}
                    onTyping={(isTyping) => sendTypingIndicator(selectedThreadId as ThreadId, isTyping)}
                />
            ) : (
                <ThreadList
                    threads={threads}
                    onSelectThread={setSelectedThreadId}
                />
            )}

            {showCreateThread && moduleId && (
                <CreateThreadModal
                    moduleId={moduleId}
                    onClose={() => setShowCreateThread(false)}
                    onSuccess={(threadId) => {
                        setShowCreateThread(false);
                        setSelectedThreadId(threadId);
                    }}
                />
            )}
        </Box>
    );
}

interface ThreadListProps {
    threads: Thread[];
    onSelectThread: (threadId: string) => void;
}

function ThreadList({ threads, onSelectThread }: ThreadListProps) {
    if (threads.length === 0) {
        return (
            <Card className="p-8 text-center text-gray-500">
                No discussions yet. Start a conversation!
            </Card>
        );
    }

    return (
        <div className="space-y-3 overflow-y-auto">
            {threads.map((thread) => (
                <Card
                    key={thread.id}
                    onClick={() => onSelectThread(thread.id)}
                    className="p-4 cursor-pointer hover:bg-gray-50 transition-colors"
                >
                    <div className="flex justify-between items-start">
                        <div className="flex-1">
                            <h3 className="font-medium text-gray-900">{thread.title}</h3>
                            <p className="text-sm text-gray-600 mt-1 line-clamp-2">
                                {thread.content}
                            </p>
                        </div>
                        <ThreadStatusBadge status={thread.status} />
                    </div>
                    <div className="flex gap-4 mt-3 text-sm text-gray-500">
                        <span>{thread.authorName ?? "Anonymous"}</span>
                        <span>•</span>
                        <span>{thread.replyCount ?? 0} replies</span>
                        <span>•</span>
                        <span>{formatTimeAgo(thread.createdAt)}</span>
                    </div>
                </Card>
            ))}
        </div>
    );
}

interface ThreadViewProps {
    threadId: string;
    onBack: () => void;
    onTyping?: (isTyping: boolean) => void;
}

function ThreadView({ threadId, onBack, onTyping }: ThreadViewProps) {
    const { data, isLoading } = useThread(threadId);
    const [replyContent, setReplyContent] = useState("");
    const createPostMutation = useCreatePost();
    const { typingUsers } = useTypingIndicators(threadId as ThreadId);

    const handleContentChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
        const value = e.target.value;
        setReplyContent(value);
        // Send typing indicator
        onTyping?.(value.length > 0);
    };

    const handleSubmitReply = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!replyContent.trim()) return;

        try {
            await createPostMutation.mutateAsync({
                threadId,
                content: replyContent.trim()
            });
            setReplyContent("");
            onTyping?.(false);
        } catch (error) {
            console.error("Failed to post reply:", error);
        }
    };

    if (isLoading || !data) {
        return <div className="text-center py-8">Loading thread...</div>;
    }

    const { thread, posts } = data;

    return (
        <div className="flex-1 flex flex-col min-h-0">
            <button
                onClick={onBack}
                className="text-blue-600 hover:text-blue-800 mb-4 flex items-center gap-1"
            >
                ← Back to discussions
            </button>

            <Card className="p-4 mb-4">
                <div className="flex justify-between items-start mb-2">
                    <h2 className="text-lg font-semibold">{thread.title}</h2>
                    <ThreadStatusBadge status={thread.status} />
                </div>
                <p className="text-gray-700 whitespace-pre-wrap">{thread.content}</p>
                <div className="text-sm text-gray-500 mt-3">
                    {thread.authorName ?? "Anonymous"} • {formatTimeAgo(thread.createdAt)}
                </div>
            </Card>

            <div className="flex-1 overflow-y-auto space-y-3 mb-4">
                {posts.map((post) => (
                    <PostCard key={post.id} post={post} />
                ))}
            </div>

            {/* Typing indicator */}
            {typingUsers.length > 0 && (
                <div className="text-sm text-gray-500 italic mb-2">
                    {typingUsers.length === 1
                        ? `${typingUsers[0]} is typing...`
                        : `${typingUsers.join(", ")} are typing...`
                    }
                </div>
            )}

            <form onSubmit={handleSubmitReply} className="mt-auto">
                <div className="flex gap-2">
                    <textarea
                        value={replyContent}
                        onChange={handleContentChange}
                        placeholder="Write a reply..."
                        className="flex-1 px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 resize-none"
                        rows={2}
                    />
                    <button
                        type="submit"
                        disabled={!replyContent.trim() || createPostMutation.isPending}
                        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 self-end"
                    >
                        Reply
                    </button>
                </div>
            </form>
        </div>
    );
}

interface PostCardProps {
    post: Post;
}

function PostCard({ post }: PostCardProps) {
    const voteMutation = useVoteOnPost();

    const handleVote = (vote: "up" | "down") => {
        voteMutation.mutate({ postId: post.id, vote });
    };

    return (
        <Card className="p-4">
            <p className="text-gray-700 whitespace-pre-wrap">{post.content}</p>
            <div className="flex justify-between items-center mt-3">
                <div className="text-sm text-gray-500">
                    {post.authorName ?? "Anonymous"} • {formatTimeAgo(post.createdAt)}
                </div>
                <div className="flex items-center gap-2">
                    <button
                        onClick={() => handleVote("up")}
                        className="p-1 hover:bg-gray-100 rounded"
                    >
                        ▲
                    </button>
                    <span className="font-medium">{post.voteCount ?? 0}</span>
                    <button
                        onClick={() => handleVote("down")}
                        className="p-1 hover:bg-gray-100 rounded"
                    >
                        ▼
                    </button>
                </div>
            </div>
        </Card>
    );
}

interface CreateThreadModalProps {
    moduleId: string;
    onClose: () => void;
    onSuccess: (threadId: string) => void;
}

function CreateThreadModal({ moduleId, onClose, onSuccess }: CreateThreadModalProps) {
    const [title, setTitle] = useState("");
    const [content, setContent] = useState("");
    const createMutation = useCreateThread();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!title.trim() || !content.trim()) return;

        try {
            const thread = await createMutation.mutateAsync({
                moduleId,
                title: title.trim(),
                content: content.trim()
            });
            onSuccess(thread.id);
        } catch (error) {
            console.error("Failed to create thread:", error);
        }
    };

    return (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
            <Card className="w-full max-w-lg p-6">
                <h2 className="text-xl font-semibold mb-4">Start a Discussion</h2>
                <form onSubmit={handleSubmit} className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Title *
                        </label>
                        <input
                            type="text"
                            value={title}
                            onChange={(e) => setTitle(e.target.value)}
                            placeholder="What's your question?"
                            className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500"
                            required
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Content *
                        </label>
                        <textarea
                            value={content}
                            onChange={(e) => setContent(e.target.value)}
                            placeholder="Provide more details..."
                            className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500"
                            rows={5}
                            required
                        />
                    </div>
                    <div className="flex justify-end gap-3">
                        <button
                            type="button"
                            onClick={onClose}
                            className="px-4 py-2 text-gray-600 hover:text-gray-800"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={createMutation.isPending}
                            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
                        >
                            {createMutation.isPending ? "Creating..." : "Post"}
                        </button>
                    </div>
                </form>
            </Card>
        </div>
    );
}

function ThreadStatusBadge({ status }: { status: string }) {
    const colors: Record<string, string> = {
        open: "bg-green-100 text-green-800",
        answered: "bg-blue-100 text-blue-800",
        closed: "bg-gray-100 text-gray-800"
    };

    return (
        <span
            className={`px-2 py-1 text-xs font-medium rounded-full ${colors[status] ?? colors.open
                }`}
        >
            {status}
        </span>
    );
}

function formatTimeAgo(date: Date | string): string {
    const now = new Date();
    const then = new Date(date);
    const diffMs = now.getTime() - then.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMins < 1) return "just now";
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return then.toLocaleDateString();
}
