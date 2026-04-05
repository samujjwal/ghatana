import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

// ============================================================================
// Types
// ============================================================================

interface Channel {
  id: string;
  name: string;
  type: 'channel' | 'direct';
  description?: string;
  unreadCount: number;
  lastMessage?: {
    sender: string;
    text: string;
    timestamp: string;
  };
  memberCount: number;
}

interface Message {
  id: string;
  sender: string;
  text: string;
  timestamp: string;
  channelId: string;
}

interface ChannelMessages {
  channel: Channel;
  messages: Message[];
}

// ============================================================================
// API
// ============================================================================

async function fetchChannels(): Promise<Channel[]> {
  const res = await fetch('/api/messages/channels', {
    headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
  });
  if (!res.ok) throw new Error('Failed to load channels');
  return res.json();
}

async function fetchChannelMessages(channelId: string): Promise<ChannelMessages> {
  const res = await fetch(`/api/messages/channels/${channelId}`, {
    headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
  });
  if (!res.ok) throw new Error('Failed to load messages');
  return res.json();
}

// ============================================================================
// Component
// ============================================================================

/**
 * MessagesPage — Messaging hub.
 *
 * @doc.type component
 * @doc.purpose Channel list with message previews and sidebar layout
 * @doc.layer product
 */
const MessagesPage: React.FC = () => {
  const [selectedChannelId, setSelectedChannelId] = useState<string | null>(null);
  const [search, setSearch] = useState('');

  const { data: channels, isLoading, error } = useQuery<Channel[]>({
    queryKey: ['messages-channels'],
    queryFn: fetchChannels,
  });

  const { data: channelData, isLoading: messagesLoading } = useQuery<ChannelMessages>({
    queryKey: ['channel-messages', selectedChannelId],
    queryFn: () => fetchChannelMessages(selectedChannelId!),
    enabled: !!selectedChannelId,
  });

  const filteredChannels = channels?.filter((c) =>
    c.name.toLowerCase().includes(search.toLowerCase()),
  );

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
          Failed to load channels: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-[calc(100vh-4rem)] overflow-hidden">
      {/* Sidebar */}
      <div className="w-72 shrink-0 border-r border-zinc-800 flex flex-col bg-zinc-950">
        <div className="p-4 border-b border-zinc-800">
          <h1 className="text-lg font-bold text-zinc-100 mb-3">Messages</h1>
          <input
            type="text"
            placeholder="Search channels..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full px-3 py-2 bg-zinc-900 border border-zinc-800 rounded-lg text-zinc-100 text-sm placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500"
          />
        </div>

        <div className="flex-1 overflow-y-auto">
          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-500" />
            </div>
          ) : (
            <div className="py-1">
              {/* Channels */}
              <p className="px-4 py-2 text-xs font-semibold text-zinc-500 uppercase tracking-wider">Channels</p>
              {filteredChannels
                ?.filter((c) => c.type === 'channel')
                .map((channel) => (
                  <button
                    key={channel.id}
                    onClick={() => setSelectedChannelId(channel.id)}
                    className={`w-full text-left px-4 py-2.5 flex items-center gap-3 transition-colors ${
                      selectedChannelId === channel.id
                        ? 'bg-zinc-800 text-zinc-100'
                        : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
                    }`}
                  >
                    <span className="text-zinc-500 text-sm">#</span>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-medium truncate">{channel.name}</span>
                        {channel.unreadCount > 0 && (
                          <span className="ml-2 px-1.5 py-0.5 text-[10px] font-bold bg-blue-600 text-white rounded-full">
                            {channel.unreadCount}
                          </span>
                        )}
                      </div>
                      {channel.lastMessage && (
                        <p className="text-xs text-zinc-600 truncate mt-0.5">
                          {channel.lastMessage.sender}: {channel.lastMessage.text}
                        </p>
                      )}
                    </div>
                  </button>
                ))}

              {/* Direct Messages */}
              <p className="px-4 py-2 mt-3 text-xs font-semibold text-zinc-500 uppercase tracking-wider">Direct Messages</p>
              {filteredChannels
                ?.filter((c) => c.type === 'direct')
                .map((channel) => (
                  <button
                    key={channel.id}
                    onClick={() => setSelectedChannelId(channel.id)}
                    className={`w-full text-left px-4 py-2.5 flex items-center gap-3 transition-colors ${
                      selectedChannelId === channel.id
                        ? 'bg-zinc-800 text-zinc-100'
                        : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
                    }`}
                  >
                    <div className="w-5 h-5 rounded-full bg-zinc-700 flex items-center justify-center text-[10px] text-zinc-400">
                      {channel.name.charAt(0)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-medium truncate">{channel.name}</span>
                        {channel.unreadCount > 0 && (
                          <span className="ml-2 px-1.5 py-0.5 text-[10px] font-bold bg-blue-600 text-white rounded-full">
                            {channel.unreadCount}
                          </span>
                        )}
                      </div>
                      {channel.lastMessage && (
                        <p className="text-xs text-zinc-600 truncate mt-0.5">
                          {channel.lastMessage.text}
                        </p>
                      )}
                    </div>
                  </button>
                ))}

              {filteredChannels?.length === 0 && (
                <p className="px-4 py-8 text-center text-xs text-zinc-600">No channels found</p>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Main Area */}
      <div className="flex-1 flex flex-col bg-zinc-950/50">
        {selectedChannelId && channelData ? (
          <>
            {/* Channel Header */}
            <div className="px-6 py-4 border-b border-zinc-800 flex items-center gap-3">
              <div>
                <h2 className="text-sm font-semibold text-zinc-100">
                  {channelData.channel.type === 'channel' ? '#' : ''} {channelData.channel.name}
                </h2>
                {channelData.channel.description && (
                  <p className="text-xs text-zinc-500">{channelData.channel.description}</p>
                )}
              </div>
              <span className="ml-auto text-xs text-zinc-500">
                {channelData.channel.memberCount} members
              </span>
            </div>

            {/* Messages */}
            <div className="flex-1 overflow-y-auto px-6 py-4 space-y-4">
              {messagesLoading ? (
                <div className="flex items-center justify-center py-12">
                  <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-500" />
                </div>
              ) : (
                channelData.messages.map((msg) => (
                  <div key={msg.id} className="flex items-start gap-3 group">
                    <div className="w-8 h-8 rounded-full bg-zinc-800 flex items-center justify-center text-xs font-medium text-zinc-400 shrink-0">
                      {msg.sender.charAt(0)}
                    </div>
                    <div>
                      <div className="flex items-baseline gap-2">
                        <span className="text-sm font-semibold text-zinc-200">{msg.sender}</span>
                        <span className="text-[10px] text-zinc-600">{msg.timestamp}</span>
                      </div>
                      <p className="text-sm text-zinc-300 mt-0.5">{msg.text}</p>
                    </div>
                  </div>
                ))
              )}
            </div>

            {/* Compose */}
            <div className="px-6 py-4 border-t border-zinc-800">
              <div className="flex gap-2">
                <input
                  type="text"
                  placeholder={`Message #${channelData.channel.name}`}
                  className="flex-1 px-4 py-2.5 bg-zinc-900 border border-zinc-800 rounded-lg text-zinc-100 text-sm placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500"
                />
                <button className="px-4 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition-colors">
                  Send
                </button>
              </div>
            </div>
          </>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center text-zinc-500">
            <span className="text-4xl mb-3">💬</span>
            <p className="text-sm">Select a channel to start messaging</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default MessagesPage;
