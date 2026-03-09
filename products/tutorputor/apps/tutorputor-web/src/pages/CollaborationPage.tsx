import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { Box, Card, Text, Button, Badge, Spinner } from "@/components/ui";
import { PageHeader } from "../components/PageHeader";
import { apiClient } from "@/api/tutorputorClient";

interface Thread {
    id: string;
    title: string;
    content?: string;
    status: string;
    replyCount?: number;
    authorName?: string;
    createdAt: string;
}

interface Post {
    id: string;
    content: string;
    authorName?: string;
    createdAt: string;
}

/**
 * Collaboration page for Q&A discussions and help requests.
 * 
 * @doc.type component
 * @doc.purpose Enable peer collaboration and discussions
 * @doc.layer product
 * @doc.pattern Page
 */
export function CollaborationPage() {
    const queryClient = useQueryClient();
    const [searchParams] = useSearchParams();
    // moduleId may be passed as ?moduleId=<id> when navigating from a specific module
    const moduleId = searchParams.get("moduleId") ?? "";
    const [showNewThread, setShowNewThread] = useState(false);
    const [newThreadTitle, setNewThreadTitle] = useState("");
    const [newThreadContent, setNewThreadContent] = useState("");
    const [selectedThread, setSelectedThread] = useState<string | null>(null);
    const [replyContent, setReplyContent] = useState("");

    const { data: threads, isLoading } = useQuery<{ threads: Thread[] }>({
        queryKey: ["collaboration", "threads"],
        queryFn: () => apiClient.listThreads()
    });

    const { data: threadDetail } = useQuery<{ thread: Thread; posts: Post[] }>({
        queryKey: ["collaboration", "thread", selectedThread],
        queryFn: () => apiClient.getThread(selectedThread!),
        enabled: !!selectedThread
    });

    const createThreadMutation = useMutation({
        mutationFn: () => apiClient.createThread({
            moduleId,
            title: newThreadTitle,
            content: newThreadContent
        }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["collaboration", "threads"] });
            setShowNewThread(false);
            setNewThreadTitle("");
            setNewThreadContent("");
        }
    });

    const replyMutation = useMutation({
        mutationFn: () => apiClient.createPost(selectedThread!, replyContent),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["collaboration", "thread", selectedThread] });
            setReplyContent("");
        }
    });

    if (isLoading) {
        return (
            <Box className="flex items-center justify-center min-h-[400px]">
                <Spinner size="lg" />
            </Box>
        );
    }

    const threadList = threads?.threads ?? [];

    return (
        <Box>
            <Box className="max-w-6xl mx-auto p-6">
                <PageHeader
                    title="Discussion Forum"
                    description="Ask questions and help your peers"
                    actions={
                        <Button onClick={() => setShowNewThread(true)}>
                            New Discussion
                        </Button>
                    }
                />

                <Box className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Thread List */}
                    <Box className="lg:col-span-1 space-y-3">
                        {threadList.length === 0 ? (
                            <Card className="p-4 text-center">
                                <Text className="text-gray-500">No discussions yet. Start one!</Text>
                            </Card>
                        ) : (
                            threadList.map((thread: Thread) => (
                                <Card
                                    key={thread.id}
                                    className={`p-4 cursor-pointer transition-colors ${selectedThread === thread.id
                                        ? "border-blue-500 bg-blue-50"
                                        : "hover:bg-gray-50"
                                        }`}
                                    onClick={() => setSelectedThread(thread.id)}
                                >
                                    <Box className="flex items-start justify-between mb-2">
                                        <Text className="font-medium text-gray-900 line-clamp-2">
                                            {thread.title}
                                        </Text>
                                        <Badge
                                            variant="soft"
                                            tone={
                                                thread.status === "RESOLVED"
                                                    ? "success"
                                                    : thread.status === "CLOSED"
                                                        ? "neutral"
                                                        : "warning"
                                            }
                                            className="ml-2 flex-shrink-0"
                                        >
                                            {thread.status}
                                        </Badge>
                                    </Box>
                                    <Text className="text-sm text-gray-500">
                                        {thread.replyCount ?? 0} replies • {thread.authorName ?? "Anonymous"}
                                    </Text>
                                </Card>
                            ))
                        )}
                    </Box>

                    {/* Thread Detail */}
                    <Box className="lg:col-span-2">
                        {selectedThread && threadDetail ? (
                            <Card className="p-6">
                                <Box className="border-b pb-4 mb-4">
                                    <Text as="h2" className="text-xl font-bold text-gray-900">
                                        {threadDetail.thread.title}
                                    </Text>
                                    <Text className="text-sm text-gray-500 mt-1">
                                        Posted by {threadDetail.thread.authorName ?? "Anonymous"} • {new Date(threadDetail.thread.createdAt).toLocaleDateString()}
                                    </Text>
                                </Box>

                                <Text className="text-gray-700 mb-6">
                                    {threadDetail.thread.content}
                                </Text>

                                {/* Replies */}
                                <Box className="space-y-4 mb-6">
                                    {(threadDetail.posts ?? []).map((post: Post) => (
                                        <Box key={post.id} className="pl-4 border-l-2 border-gray-200">
                                            <Text className="text-sm text-gray-500 mb-1">
                                                {post.authorName ?? "Anonymous"} • {new Date(post.createdAt).toLocaleDateString()}
                                            </Text>
                                            <Text className="text-gray-700">{post.content}</Text>
                                        </Box>
                                    ))}
                                </Box>

                                {/* Reply Form */}
                                <Box className="pt-4 border-t">
                                    <textarea
                                        className="w-full p-3 border border-gray-200 rounded-lg mb-3"
                                        rows={3}
                                        placeholder="Write a reply..."
                                        value={replyContent}
                                        onChange={(e) => setReplyContent(e.target.value)}
                                    />
                                    <Button
                                        onClick={() => replyMutation.mutate()}
                                        disabled={!replyContent.trim() || replyMutation.isPending}
                                    >
                                        {replyMutation.isPending ? "Posting..." : "Post Reply"}
                                    </Button>
                                </Box>
                            </Card>
                        ) : (
                            <Card className="p-8 text-center">
                                <Text className="text-gray-500">
                                    Select a discussion to view details
                                </Text>
                            </Card>
                        )}
                    </Box>
                </Box>

                {/* New Thread Modal */}
                {showNewThread && (
                    <Box className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                        <Card className="w-full max-w-lg p-6 m-4">
                            <Text as="h2" className="text-xl font-bold text-gray-900 mb-4">
                                Start a New Discussion
                            </Text>
                            <Box className="space-y-4">
                                <input
                                    type="text"
                                    className="w-full p-3 border border-gray-200 rounded-lg"
                                    placeholder="Discussion title"
                                    value={newThreadTitle}
                                    onChange={(e) => setNewThreadTitle(e.target.value)}
                                />
                                <textarea
                                    className="w-full p-3 border border-gray-200 rounded-lg"
                                    rows={4}
                                    placeholder="Describe your question or topic..."
                                    value={newThreadContent}
                                    onChange={(e) => setNewThreadContent(e.target.value)}
                                />
                                <Box className="flex justify-end gap-3">
                                    <Button variant="outline" onClick={() => setShowNewThread(false)}>
                                        Cancel
                                    </Button>
                                    <Button
                                        onClick={() => createThreadMutation.mutate()}
                                        disabled={!newThreadTitle.trim() || !newThreadContent.trim() || createThreadMutation.isPending}
                                    >
                                        {createThreadMutation.isPending ? "Creating..." : "Create Discussion"}
                                    </Button>
                                </Box>
                            </Box>
                        </Card>
                    </Box>
                )}
            </Box>
        </Box>
    );
}
