import React, { useState, useRef, useEffect } from 'react';
import { useParams } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

interface ChannelMember {
  id: string;
  name: string;
  avatarUrl: string;
  online: boolean;
}

interface ChannelMessage {
  id: string;
  userId: string;
  userName: string;
  avatarUrl: string;
  content: string;
  timestamp: string;
  edited: boolean;
}

interface ChannelData {
  id: string;
  name: string;
  topic: string;
  members: ChannelMember[];
  messages: ChannelMessage[];
}

const authHeaders = (): Record<string, string> => ({
  Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}`,
  'Content-Type': 'application/json',
});

/**
 * ChannelPage — Chat channel with message list and compose input.
 *
 * @doc.type component
 * @doc.purpose Channel chat interface with real-time messaging
 * @doc.layer product
 */
const ChannelPage: React.FC = () => {
  const { channelId } = useParams<{ channelId: string }>();
  const [draft, setDraft] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();

  const { data: channel, isLoading, error } = useQuery<ChannelData>({
    queryKey: ['channel', channelId],
    queryFn: async () => {
      const res = await fetch(`/api/messages/channels/${channelId}`, { headers: authHeaders() });
      if (!res.ok) throw new Error('Failed to load channel');
      return res.json() as Promise<ChannelData>;
    },
    enabled: !!channelId,
    refetchInterval: 10_000,
  });

  const sendMessage = useMutation<ChannelMessage, Error, string>({
    mutationFn: async (content: string) => {
      const res = await fetch(`/api/messages/channels/${channelId}/messages`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ content }),
      });
      if (!res.ok) throw new Error('Failed to send message');
      return res.json() as Promise<ChannelMessage>;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['channel', channelId] });
    },
  });

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [channel?.messages]);

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

  const formatTime = (ts: string) => {
    const d = new Date(ts);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const formatDate = (ts: string) => {
    const d = new Date(ts);
    return d.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' });
  };

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
          {error instanceof Error ? error.message : 'Failed to load channel'}
        </div>
      </div>
    );
  }

  const onlineCount = channel?.members.filter((m) => m.online).length ?? 0;
  const messages = channel?.messages ?? [];

  // Group messages by date
  const groupedMessages: { date: string; items: ChannelMessage[] }[] = [];
  let currentDate = '';
  for (const msg of messages) {
    const msgDate = formatDate(msg.timestamp);
    if (msgDate !== currentDate) {
      currentDate = msgDate;
      groupedMessages.push({ date: msgDate, items: [] });
    }
    groupedMessages[groupedMessages.length - 1].items.push(msg);
  }

  return (
    <div className="flex flex-col h-full max-h-screen">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-zinc-800 bg-zinc-950 shrink-0">
        <div className="flex items-center gap-3">
          <span className="text-zinc-500 text-lg font-bold">#</span>
          <div>
            <h1 className="text-lg font-semibold text-zinc-100">{channel?.name ?? 'Channel'}</h1>
            {channel?.topic && (
              <p className="text-xs text-zinc-500 truncate max-w-md">{channel.topic}</p>
            )}
          </div>
        </div>
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-1.5 text-sm text-zinc-400">
            <span className="inline-block w-2 h-2 rounded-full bg-green-500" />
            <span>{onlineCount} online</span>
          </div>
          <div className="flex items-center gap-1.5 text-sm text-zinc-500">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            <span>{channel?.members.length ?? 0} members</span>
          </div>
        </div>
      </div>

      {/* Messages area */}
      <div className="flex-1 overflow-y-auto px-6 py-4 space-y-1">
        {messages.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-zinc-500">
            <svg className="w-12 h-12 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
            </svg>
            <p className="text-sm">No messages yet. Start the conversation!</p>
          </div>
        ) : (
          groupedMessages.map((group) => (
            <div key={group.date}>
              <div className="flex items-center gap-3 my-4">
                <div className="flex-1 h-px bg-zinc-800" />
                <span className="text-xs text-zinc-500 font-medium">{group.date}</span>
                <div className="flex-1 h-px bg-zinc-800" />
              </div>
              {group.items.map((msg) => (
                <div key={msg.id} className="flex gap-3 py-1.5 px-2 rounded-lg hover:bg-zinc-900/50 group">
                  <img
                    src={msg.avatarUrl}
                    alt={msg.userName}
                    className="w-9 h-9 rounded-full mt-0.5 shrink-0 bg-zinc-800"
                  />
                  <div className="min-w-0">
                    <div className="flex items-baseline gap-2">
                      <span className="text-sm font-semibold text-zinc-200">{msg.userName}</span>
                      <span className="text-xs text-zinc-600">{formatTime(msg.timestamp)}</span>
                      {msg.edited && (
                        <span className="text-xs text-zinc-600 italic">(edited)</span>
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

      {/* Compose */}
      <div className="px-6 py-3 border-t border-zinc-800 bg-zinc-950 shrink-0">
        <div className="flex items-end gap-3 bg-zinc-900 border border-zinc-700 rounded-lg px-4 py-2">
          <textarea
            className="flex-1 bg-transparent text-sm text-zinc-200 placeholder-zinc-500 resize-none outline-none max-h-32"
            rows={1}
            placeholder={`Message #${channel?.name ?? 'channel'}...`}
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

export default ChannelPage;
