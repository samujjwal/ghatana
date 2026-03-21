import React from 'react';
import { useAtom } from 'jotai';
import { aiStateAtom, AISuggestion } from '../state/canvasAtoms';

import './AIAssistant.css';

/**
 * AIAssistant - Contextual AI suggestions panel
 */
export function AIAssistant() {
  const [aiState] = useAtom(aiStateAtom);

  const handleApplySuggestion = (suggestion: AISuggestion) => {
    console.log('Applying suggestion:', suggestion);
    // Implementation would apply the suggestion to canvas
  };

  const handleDismiss = (suggestionId: string) => {
    console.log('Dismissing suggestion:', suggestionId);
    // Implementation would remove suggestion from list
  };

  if (!aiState.suggestions.length && !aiState.isProcessing) {
    return (
      <div className="ai-assistant ai-assistant--empty">
        <div className="ai-assistant__icon">🤖</div>
        <p className="ai-assistant__text">AI suggestions will appear here</p>
      </div>
    );
  }

  return (
    <div className="ai-assistant">
      <div className="ai-assistant__header">
        <span className="ai-assistant__icon">🤖</span>
        <span className="ai-assistant__title">AI Suggestions</span>
        {aiState.isProcessing && (
          <span className="ai-assistant__processing">Thinking...</span>
        )}
      </div>

      <div className="ai-assistant__suggestions">
        {aiState.suggestions.map((suggestion) => (
          <div
            key={suggestion.id}
            className={`ai-assistant__suggestion ai-assistant__suggestion--${suggestion.type}`}
          >
            <div className="ai-assistant__suggestion-header">
              <span className="ai-assistant__suggestion-type">
                {suggestion.type === 'layout' && '📐'}
                {suggestion.type === 'content' && '📝'}
                {suggestion.type === 'organization' && '📂'}
              </span>
              <span className="ai-assistant__confidence">
                {Math.round(suggestion.confidence * 100)}%
              </span>
            </div>

            <h4 className="ai-assistant__suggestion-title">{suggestion.title}</h4>
            <p className="ai-assistant__suggestion-desc">{suggestion.description}</p>

            <div className="ai-assistant__actions">
              <button
                className="ai-assistant__apply"
                onClick={() => handleApplySuggestion(suggestion)}
              >
                Apply
              </button>
              <button
                className="ai-assistant__dismiss"
                onClick={() => handleDismiss(suggestion.id)}
              >
                Dismiss
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default AIAssistant;
