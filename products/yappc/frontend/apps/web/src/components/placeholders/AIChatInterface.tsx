/**
 * AIChatInterface Placeholder Component
 * 
 * Temporary placeholder until the actual component is implemented in @ghatana/yappc-ui
 */

import React from 'react';

interface AIChatInterfaceProps {
  messages?: unknown[];
  onSendMessage?: (message: string) => void;
  onNodeClick?: (nodeId: string) => void;
  onValidationRequest?: (nodes: unknown) => void;
  isLoading?: boolean;
  className?: string;
}

export const AIChatInterface: React.FC<AIChatInterfaceProps> = ({
  messages = [],
  onSendMessage,
  isLoading = false,
}) => {
  const [input, setInput] = React.useState('');

  const handleSend = () => {
    if (input.trim() && onSendMessage) {
      onSendMessage(input);
      setInput('');
    }
  };

  return (
    <div className="flex flex-col h-full bg-zinc-900 rounded-lg border border-zinc-800">
      <div className="p-4 border-b border-zinc-800">
        <h3 className="text-lg font-semibold text-white">AI Assistant</h3>
      </div>
      
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {messages.map((msg: unknown, idx: number) => (
          <div
            key={idx}
            className={`p-3 rounded-lg ${
              msg.role === 'user' 
                ? 'bg-violet-500/10 text-white ml-8' 
                : 'bg-zinc-800 text-zinc-300 mr-8'
            }`}
          >
            {msg.content}
          </div>
        ))}
        {isLoading && (
          <div className="text-zinc-400 italic">AI is thinking...</div>
        )}
      </div>

      <div className="p-4 border-t border-zinc-800">
        <div className="flex gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSend()}
            placeholder="Ask the AI assistant..."
            className="flex-1 px-4 py-2 bg-zinc-800 border border-zinc-700 rounded-lg text-white placeholder-zinc-500 focus:outline-none focus:border-violet-500"
          />
          <button
            onClick={handleSend}
            disabled={!input.trim() || isLoading}
            className="px-6 py-2 bg-violet-500 text-white rounded-lg hover:bg-violet-600 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Send
          </button>
        </div>
      </div>
    </div>
  );
};
