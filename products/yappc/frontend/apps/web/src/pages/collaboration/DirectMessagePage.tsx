import React, { useState, useRef, useEffect } from 'react';
import { useParams } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

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
      if (!res.ok) throw new Error('Failed to load conversation');
      return res.json() as Promise<DMConversation>;
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
      if (!res.ok) throw new Error('Failed to send');
      return res.json() as Promise<DMMessage>;
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
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
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
      <div className="flex items-center gap-3 px-6 py-3 border-b border-zinc-800 bg-zinc-950 shrink-0">
        <div className="relative">
          <img
            src={user?.avatarUrl}
            alt={user?.name}
            className="w-9 h-9 rounded-full bg-zinc-800"
          />
          <span
            className={`absolute bottom-0 right-0 w-2.5 h-2.5 rounded-full border-2 border-zinc-950 ${
              user?.online ? 'bg-green-500' : 'bg-zinc-600'
            }`}
          />
        </div>
        <div>
          <h1 className="text-base font-semibold text-zinc-100">{user?.name ?? 'User'}</h1>
          <p className="text-xs text-zinc-500">
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
          <div className="flex flex-col items-center justify-center py-20 text-zinc-500">
            <svg className="w-12 h-12 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
            </svg>
            <p className="text-sm">Start a conversation with {user?.name ?? 'this user'}.</p>
          </div>
        ) : (
          groups.map((group) => (
            <div key={group.date}>
              <div className="flex items-center gap-3 my-4">
                <div className="flex-1 h-px bg-zinc-800" />
                <span className="text-xs text-zinc-500 font-medium">{group.date}</span>
                <div className="flex-1 h-px bg-zinc-800" />
              </div>
              {group.items.map((msg) => (
                <div key={msg.id} className="flex gap-3 py-1.5 px-2 rounded-lg hover:bg-zinc-900/50">
                  <img
                    src={msg.senderAvatarUrl}
                    alt={msg.senderName}
                    className="w-9 h-9 rounded-full mt-0.5 shrink-0 bg-zinc-800"
                  />
                  <div className="min-w-0">
                    <div className="flex items-baseline gap-2">
                      <span className="text-sm font-semibold text-zinc-200">{msg.senderName}</span>
                      <span className="text-xs text-zinc-600">{formatTime(msg.timestamp)}</span>
                      {msg.read && msg.senderId !== userId && (
                        <span className="text-xs text-blue-500">✓</span>
                      )}
                    </div>
                    <p className="text-sm text-zinc-300 whitespace-pre-wrap break-words">{msg.content}</p>
                  </div>
                </div>
              ))}
            </div>
          ))
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Compose input */}
      <div className="px-6 py-3 border-t border-zinc-800 bg-zinc-950 shrink-0">
        <div className="flex items-end gap-3 bg-zinc-900 border border-zinc-700 rounded-lg px-4 py-2">
          <textarea
            className="flex-1 bg-transparent text-sm text-zinc-200 placeholder-zinc-500 resize-none outline-none max-h-32"
            rows={1}
            placeholder={`Message ${user?.name ?? ''}...`}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={handleKeyDown}
          />
          <button
            onClick={handleSend}
            disabled={!draft.trim() || sendMessage.isPending}
            className="px-3 py-1.5 bg-blue-600 text-white text-sm font-medium rounded-md hover:bg-blue-500 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            {sendMessage.isPending ? 'Sending…' : 'Send'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default DirectMessagePage;
