import React, { useState, useRef, useEffect } from 'react';
import { useParams } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';
import { Button } from '../../components/ui/Button';
import { Textarea } from '../../components/ui/Textarea';

interface DMUser {
  id: string;
  name: string;
  avatarUrl: string;
  online: boolean;
  lastSeen: string;
}

interface DMMessage {
  id: string;
  senderId: string;
  senderName: string;
  senderAvatarUrl: string;
  content: string;
  timestamp: string;
  read: boolean;
}

interface DMConversation {
  user: DMUser;
  messages: DMMessage[];
}

const authHeaders = (): Record<string, string> => ({
  Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}`,
  'Content-Type': 'application/json',
});

/**
 * DirectMessagePage — DM conversation with message thread and compose input.
 *
 * @doc.type component
 * @doc.purpose Direct message conversation interface
 * @doc.layer product
 */
const DirectMessagePage: React.FC = () => {
  const { userId } = useParams<{ userId: string }>();
  const [draft, setDraft] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();

  const { data: conversation, isLoading, error } = useQuery<DMConversation>({
    queryKey: ['dm', userId],
    queryFn: async () => {
      const res = await fetch(`/api/messages/dm/${userId}`, { headers: authHeaders() });
      if (!res.ok) {
        throw new Error(await readErrorResponse(res, 'Failed to load conversation'));
      }
      return parseJsonResponse<DMConversation>(res, 'direct message conversation');
    },
    enabled: !!userId,
    refetchInterval: 5_000,
  });

  const sendMessage = useMutation<DMMessage, Error, string>({
    mutationFn: async (content: string) => {
      const res = await fetch(`/api/messages/dm/${userId}`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ content }),
      });
      if (!res.ok) throw new Error(await readErrorResponse(res, 'Failed to send'));
      return parseJsonResponse<DMMessage>(res, 'direct message send');
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dm', userId] });
    },
  });

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [conversation?.messages]);

  const handleSend = () => {
    const trimmed = draft.trim();
    if (!trimmed) return;
    sendMessage.mutate(trimmed);
    setDraft('');
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const formatTime = (ts: string) =>
    new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  const formatDateSeparator = (ts: string) =>
    new Date(ts).toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-info-border" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-destructive-bg/20 border border-destructive-border rounded-lg p-4 text-destructive">
          {error instanceof Error ? error.message : 'Failed to load conversation'}
        </div>
      </div>
    );
  }

  const user = conversation?.user;
  const messages = conversation?.messages ?? [];

  // Group by date
  const groups: { date: string; items: DMMessage[] }[] = [];
  let lastDate = '';
  for (const msg of messages) {
    const d = formatDateSeparator(msg.timestamp);
    if (d !== lastDate) {
      lastDate = d;
      groups.push({ date: d, items: [] });
    }
    groups[groups.length - 1].items.push(msg);
  }

  return (
    <div className="flex flex-col h-full max-h-screen">
      {/* User header */}
      <div className="flex items-center gap-3 px-6 py-3 border-b border-border bg-surface shrink-0">
        <div className="relative">
          <img
            src={user?.avatarUrl}
            alt={user?.name}
            className="w-9 h-9 rounded-full bg-surface"
          />
          <span
            className={`absolute bottom-0 right-0 w-2.5 h-2.5 rounded-full border-2 border-border ${
              user?.online ? 'bg-success-bg' : 'bg-surface-muted'
            }`}
          />
        </div>
        <div>
          <h1 className="text-base font-semibold text-fg-muted">{user?.name ?? 'User'}</h1>
          <p className="text-xs text-fg-muted">
            {user?.online
              ? 'Online'
              : user?.lastSeen
                ? `Last seen ${new Date(user.lastSeen).toLocaleString()}`
                : 'Offline'}
          </p>
        </div>
      </div>

      {/* Message thread */}
      <div className="flex-1 overflow-y-auto px-6 py-4 space-y-1">
        {messages.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-fg-muted">
            <svg className="w-12 h-12 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
            </svg>
            <p className="text-sm">Start a conversation with {user?.name ?? 'this user'}.</p>
          </div>
        ) : (
          groups.map((group) => (
            <div key={group.date}>
              <div className="flex items-center gap-3 my-4">
                <div className="flex-1 h-px bg-surface" />
                <span className="text-xs text-fg-muted font-medium">{group.date}</span>
                <div className="flex-1 h-px bg-surface" />
              </div>
              {group.items.map((msg) => (
                <div key={msg.id} className="flex gap-3 py-1.5 px-2 rounded-lg hover:bg-surface/50">
                  <img
                    src={msg.senderAvatarUrl}
                    alt={msg.senderName}
                    className="w-9 h-9 rounded-full mt-0.5 shrink-0 bg-surface"
                  />
                  <div className="min-w-0">
                    <div className="flex items-baseline gap-2">
                      <span className="text-sm font-semibold text-fg-muted">{msg.senderName}</span>
                      <span className="text-xs text-fg-muted">{formatTime(msg.timestamp)}</span>
                      {msg.read && msg.senderId !== userId && (
                        <span className="text-xs text-info-color">✓</span>
                      )}
                    </div>
                    <p className="text-sm text-fg-muted whitespace-pre-wrap break-words">{msg.content}</p>
                  </div>
                </div>
              ))}
            </div>
          ))
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Compose input */}
      <div className="px-6 py-3 border-t border-border bg-surface shrink-0">
        <div className="flex items-end gap-3 bg-surface border border-border rounded-lg px-4 py-2">
          <Textarea
            fullWidth
            resize="none"
            className="min-h-0 max-h-32 border-0 bg-transparent px-0 py-0 text-sm text-fg-muted placeholder-zinc-500 focus:ring-0 focus:ring-offset-0"
            rows={1}
            placeholder={`Message ${user?.name ?? ''}...`}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={handleKeyDown}
          />
          <Button
            onClick={handleSend}
            disabled={!draft.trim() || sendMessage.isPending}
            size="sm"
            loading={sendMessage.isPending}
            className="px-3 py-1.5 rounded-md"
          >
            Send
          </Button>
        </div>
      </div>
    </div>
  );
};

export default DirectMessagePage;
