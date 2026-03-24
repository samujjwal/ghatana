/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC UI - Voice Command Overlay
 * 
 * Visual feedback component for voice command system.
 * Shows listening state, processing state, feedback messages, and help.
 */

import React, { useState, useCallback } from 'react';
import { useVoiceCommands, VoiceCommand, VOICE_COMMAND_HELP } from './useVoiceCommands';

export interface VoiceOverlayProps {
  /** Called when a voice command is recognized */
  onCommand: (command: VoiceCommand) => void;
  /** Called when voice system encounters an error */
  onError?: (error: Error) => void;
  /** Position on screen */
  position?: 'bottom-right' | 'bottom-left' | 'top-right' | 'top-left';
  /** Show help button */
  showHelp?: boolean;
  /** Custom class name */
  className?: string;
}

/**
 * VoiceOverlay Component
 * 
 * Floating voice control with microphone button, visual feedback,
 * and help dialog. Integrates with useVoiceCommands hook.
 * 
 * @example
 * ```tsx
 * <VoiceOverlay
 *   onCommand={(cmd) => handleVoiceCommand(cmd)}
 *   position="bottom-right"
 * />
 * ```
 */
export function VoiceOverlay({
  onCommand,
  onError,
  position = 'bottom-right',
  showHelp = true,
  className = '',
}: VoiceOverlayProps) {
  const [showHelpDialog, setShowHelpDialog] = useState(false);
  const [recentCommands, setRecentCommands] = useState<VoiceCommand[]>([]);

  const handleCommand = useCallback((command: VoiceCommand) => {
    setRecentCommands(prev => [command, ...prev].slice(0, 5));
    onCommand(command);
  }, [onCommand]);

  const {
    isListening,
    isProcessing,
    feedback,
    startListening,
    stopListening,
  } = useVoiceCommands({
    onCommand: handleCommand,
    onError,
  });

  const positionClass = `voice-overlay--${position}`;

  return (
    <div className={`voice-overlay ${positionClass} ${className}`}>
      {/* Main voice button */}
      <button
        className={`voice-overlay__button ${
          isListening ? 'voice-overlay__button--listening' : ''
        } ${isProcessing ? 'voice-overlay__button--processing' : ''}`}
        onClick={isListening ? stopListening : startListening}
        aria-label={isListening ? 'Stop listening' : 'Start voice command'}
        title={isListening ? 'Click to stop' : 'Click to speak'}
      >
        {isProcessing ? (
          <ProcessingIcon />
        ) : isListening ? (
          <ListeningIcon />
        ) : (
          <MicIcon />
        )}
      </button>

      {/* Status indicator */}
      {(isListening || isProcessing || feedback) && (
        <div className={`voice-overlay__status ${
          isListening ? 'voice-overlay__status--listening' : ''
        } ${isProcessing ? 'voice-overlay__status--processing' : ''}`}>
          {isListening && (
            <>
              <span className="voice-overlay__pulse" />
              <span>Listening...</span>
            </>
          )}
          {isProcessing && <span>Processing...</span>}
          {feedback && <span>{feedback}</span>}
        </div>
      )}

      {/* Recent commands */}
      {recentCommands.length > 0 && !isListening && !isProcessing && (
        <div className="voice-overlay__recent">
          <span className="voice-overlay__recent-label">Last command:</span>
          <span className="voice-overlay__recent-command">
            {recentCommands[0].intent.replace('_', ' ')}
          </span>
        </div>
      )}

      {/* Help button */}
      {showHelp && !isListening && !isProcessing && (
        <button
          className="voice-overlay__help-btn"
          onClick={() => setShowHelpDialog(true)}
          aria-label="Show voice commands help"
        >
          <HelpIcon />
        </button>
      )}

      {/* Help dialog */}
      {showHelpDialog && (
        <div 
          className="voice-overlay__dialog-overlay"
          onClick={() => setShowHelpDialog(false)}
        >
          <div 
            className="voice-overlay__dialog"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="voice-overlay__dialog-header">
              <h3>Voice Commands</h3>
              <button 
                className="voice-overlay__dialog-close"
                onClick={() => setShowHelpDialog(false)}
              >
                <CloseIcon />
              </button>
            </div>
            <pre className="voice-overlay__dialog-content">
              {VOICE_COMMAND_HELP}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
}

// Icon components
function MicIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="voice-overlay__icon">
      <path 
        d="M12 2a3 3 0 0 1 3 3v6a3 3 0 0 1-6 0V5a3 3 0 0 1 3-3z" 
        fill="currentColor"
      />
      <path 
        d="M19 10v2a7 7 0 0 1-14 0v-2M12 18v4M8 22h8" 
        stroke="currentColor" 
        strokeWidth="2" 
        strokeLinecap="round"
      />
    </svg>
  );
}

function ListeningIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="voice-overlay__icon voice-overlay__icon--listening">
      <rect x="6" y="4" width="3" height="10" rx="1.5" fill="currentColor">
        <animate attributeName="height" values="10;6;10" dur="0.6s" repeatCount="indefinite" />
        <animate attributeName="y" values="4;6;4" dur="0.6s" repeatCount="indefinite" />
      </rect>
      <rect x="10.5" y="4" width="3" height="10" rx="1.5" fill="currentColor">
        <animate attributeName="height" values="10;8;10" dur="0.5s" repeatCount="indefinite" />
        <animate attributeName="y" values="4;5;4" dur="0.5s" repeatCount="indefinite" />
      </rect>
      <rect x="15" y="4" width="3" height="10" rx="1.5" fill="currentColor">
        <animate attributeName="height" values="10;5;10" dur="0.7s" repeatCount="indefinite" />
        <animate attributeName="y" values="4;6.5;4" dur="0.7s" repeatCount="indefinite" />
      </rect>
    </svg>
  );
}

function ProcessingIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="voice-overlay__icon voice-overlay__icon--processing">
      <circle 
        cx="12" 
        cy="12" 
        r="10" 
        stroke="currentColor" 
        strokeWidth="2"
        strokeLinecap="round"
        strokeDasharray="40"
        strokeDashoffset="40"
      >
        <animate 
          attributeName="stroke-dashoffset" 
          values="40;0;40" 
          dur="1.5s" 
          repeatCount="indefinite" 
        />
        <animateTransform
          attributeName="transform"
          type="rotate"
          from="0 12 12"
          to="360 12 12"
          dur="1.5s"
          repeatCount="indefinite"
        />
      </circle>
    </svg>
  );
}

function HelpIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="voice-overlay__icon voice-overlay__icon--small">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" />
      <path d="M12 16v-4M12 8h.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="voice-overlay__icon voice-overlay__icon--small">
      <path d="M18 6L6 18M6 6l12 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

// CSS styles
export const voiceOverlayStyles = `
.voice-overlay {
  position: fixed;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  z-index: 1000;
}

.voice-overlay--bottom-right {
  bottom: 1.5rem;
  right: 1.5rem;
}

.voice-overlay--bottom-left {
  bottom: 1.5rem;
  left: 1.5rem;
}

.voice-overlay--top-right {
  top: 1.5rem;
  right: 1.5rem;
}

.voice-overlay--top-left {
  top: 1.5rem;
  left: 1.5rem;
}

.voice-overlay__button {
  width: 3.5rem;
  height: 3.5rem;
  border-radius: 50%;
  border: none;
  background: #3b82f6;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
  transition: all 0.2s ease;
}

.voice-overlay__button:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.5);
}

.voice-overlay__button--listening {
  background: #ef4444;
  animation: pulse 1.5s ease-in-out infinite;
}

.voice-overlay__button--processing {
  background: #f59e0b;
}

@keyframes pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4);
  }
  50% {
    box-shadow: 0 0 0 12px rgba(239, 68, 68, 0);
  }
}

.voice-overlay__icon {
  width: 1.5rem;
  height: 1.5rem;
}

.voice-overlay__icon--small {
  width: 1rem;
  height: 1rem;
}

.voice-overlay__status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: white;
  border-radius: 2rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  font-size: 0.875rem;
  color: #374151;
  white-space: nowrap;
}

.voice-overlay__status--listening {
  color: #ef4444;
}

.voice-overlay__status--processing {
  color: #f59e0b;
}

.voice-overlay__pulse {
  width: 8px;
  height: 8px;
  background: #ef4444;
  border-radius: 50%;
  animation: pulse-dot 1.5s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(0.8);
  }
}

.voice-overlay__recent {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  padding: 0.5rem;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 0.5rem;
  font-size: 0.75rem;
}

.voice-overlay__recent-label {
  color: #6b7280;
}

.voice-overlay__recent-command {
  color: #3b82f6;
  font-weight: 500;
  text-transform: capitalize;
}

.voice-overlay__help-btn {
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  border: none;
  background: white;
  color: #6b7280;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.2s;
}

.voice-overlay__help-btn:hover {
  color: #3b82f6;
  transform: scale(1.1);
}

.voice-overlay__dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1001;
}

.voice-overlay__dialog {
  background: white;
  border-radius: 0.75rem;
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  overflow: hidden;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
}

.voice-overlay__dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border-bottom: 1px solid #e5e7eb;
}

.voice-overlay__dialog-header h3 {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 600;
  color: #111827;
}

.voice-overlay__dialog-close {
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  border: none;
  background: #f3f4f6;
  color: #6b7280;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.voice-overlay__dialog-close:hover {
  background: #e5e7eb;
  color: #374151;
}

.voice-overlay__dialog-content {
  padding: 1rem;
  margin: 0;
  font-size: 0.875rem;
  line-height: 1.6;
  color: #374151;
  max-height: 60vh;
  overflow-y: auto;
  white-space: pre-wrap;
}
`;
