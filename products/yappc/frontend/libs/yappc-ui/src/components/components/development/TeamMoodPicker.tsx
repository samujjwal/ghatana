/**
 * TeamMoodPicker Component
 *
 * @description Team mood selection component for retrospectives.
 * Displays emoji-based mood scale with visual feedback and team average.
 *
 * @doc.phase 3
 * @doc.component TeamMoodPicker
 */

import React, { useState, useCallback, useMemo } from 'react';

// ============================================================================
// Types
// ============================================================================

export interface TeamMember {
  id: string;
  name: string;
  avatar?: string;
  mood?: number;
}

export interface TeamMoodPickerProps {
  currentMood?: number;
  teamMembers?: TeamMember[];
  onMoodChange?: (mood: number) => void;
  readOnly?: boolean;
  showTeamAverage?: boolean;
  showConfidence?: boolean;
  confidence?: number;
  onConfidenceChange?: (value: number) => void;
}

// ============================================================================
// Constants
// ============================================================================

const MOOD_OPTIONS = [
  { value: 1, emoji: '😢', label: 'Very Bad' },
  { value: 2, emoji: '😕', label: 'Bad' },
  { value: 3, emoji: '😐', label: 'Neutral' },
  { value: 4, emoji: '😊', label: 'Good' },
  { value: 5, emoji: '😄', label: 'Great' },
];

// ============================================================================
// Main Component
// ============================================================================

export const TeamMoodPicker: React.FC<TeamMoodPickerProps> = ({
  currentMood,
  teamMembers = [],
  onMoodChange,
  readOnly = false,
  showTeamAverage = true,
  showConfidence = false,
  confidence = 50,
  onConfidenceChange,
}) => {
  const [hoveredMood, setHoveredMood] = useState<number | null>(null);

  // Calculate team average mood
  const teamAverage = useMemo(() => {
    const membersWithMood = teamMembers.filter((m) => m.mood !== undefined);
    if (membersWithMood.length === 0) return null;
    const sum = membersWithMood.reduce((acc, m) => acc + (m.mood || 0), 0);
    return sum / membersWithMood.length;
  }, [teamMembers]);

  // Get mood distribution
  const moodDistribution = useMemo(() => {
    const distribution: Record<number, number> = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
    teamMembers.forEach((m) => {
      if (m.mood !== undefined) {
        distribution[m.mood] = (distribution[m.mood] || 0) + 1;
      }
    });
    return distribution;
  }, [teamMembers]);

  // Get members by mood value
  const getMembersByMood = useCallback(
    (moodValue: number) => {
      return teamMembers.filter((m) => m.mood === moodValue);
    },
    [teamMembers]
  );

  // Handlers
  const handleMoodClick = useCallback(
    (moodValue: number) => {
      if (!readOnly && onMoodChange) {
        onMoodChange(moodValue);
      }
    },
    [readOnly, onMoodChange]
  );

  const handleMoodHover = useCallback((moodValue: number | null) => {
    setHoveredMood(moodValue);
  }, []);

  const handleConfidenceChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      if (onConfidenceChange) {
        onConfidenceChange(parseInt(e.target.value, 10));
      }
    },
    [onConfidenceChange]
  );

  return (
    <div className="team-mood-picker">
      {/* Header */}
      <div className="picker-header">
        <h4 className="picker-title">😊 Team Mood</h4>
        {showTeamAverage && teamAverage !== null && (
          <span className="team-average">
            Team Average: {teamAverage.toFixed(1)}/5
          </span>
        )}
      </div>

      {/* Mood Selector */}
      <div className="mood-selector">
        <span className="mood-label">How was this sprint?</span>
        <div className="mood-options">
          {MOOD_OPTIONS.map((option) => {
            const isSelected = currentMood === option.value;
            const isHovered = hoveredMood === option.value;
            const count = moodDistribution[option.value];
            const members = getMembersByMood(option.value);

            return (
              <button
                key={option.value}
                type="button"
                className={`mood-option ${isSelected ? 'mood-option--selected' : ''} ${
                  isHovered ? 'mood-option--hovered' : ''
                }`}
                onClick={() => handleMoodClick(option.value)}
                onMouseEnter={() => handleMoodHover(option.value)}
                onMouseLeave={() => handleMoodHover(null)}
                disabled={readOnly}
                aria-label={`${option.label} (${count} votes)`}
                title={option.label}
              >
                <span className="mood-emoji">{option.emoji}</span>
                {count > 0 && (
                  <span className="mood-count">{count}</span>
                )}
                
                {/* Tooltip with members */}
                {isHovered && members.length > 0 && (
                  <div className="mood-tooltip">
                    <span className="tooltip-title">{option.label}</span>
                    <div className="tooltip-members">
                      {members.map((m) => (
                        <span key={m.id} className="tooltip-member">
                          {m.name}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* Mood Distribution Bar */}
      {teamMembers.length > 0 && (
        <div className="mood-distribution">
          <div className="distribution-bar">
            {MOOD_OPTIONS.map((option) => {
              const count = moodDistribution[option.value];
              const percentage = (count / teamMembers.length) * 100;
              if (percentage === 0) return null;
              
              return (
                <div
                  key={option.value}
                  className={`distribution-segment distribution-segment--${option.value}`}
                  style={{ width: `${percentage}%` }}
                  title={`${option.label}: ${count} (${Math.round(percentage)}%)`}
                />
              );
            })}
          </div>
        </div>
      )}

      {/* Confidence Slider */}
      {showConfidence && (
        <div className="confidence-section">
          <div className="confidence-header">
            <span className="confidence-label">
              Confidence for next sprint:
            </span>
            <span className="confidence-value">{confidence}%</span>
          </div>
          <div className="confidence-slider-container">
            <span className="slider-label">Low</span>
            <input
              type="range"
              min="0"
              max="100"
              value={confidence}
              onChange={handleConfidenceChange}
              disabled={readOnly}
              className="confidence-slider"
              aria-label="Confidence level"
            />
            <span className="slider-label">High</span>
          </div>
          <div
            className="confidence-indicator"
            style={{
              background:
                confidence >= 70
                  ? '#10B981'
                  : confidence >= 40
                  ? '#F59E0B'
                  : '#EF4444',
            }}
          />
        </div>
      )}

      {/* CSS-in-JS Styles */}
      <style>{`
        .team-mood-picker {
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 12px;
          padding: 1.5rem;
        }

        .picker-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 1rem;
        }

        .picker-title {
          margin: 0;
          font-size: 0.875rem;
          font-weight: 600;
          color: #111827;
        }

        .team-average {
          font-size: 0.75rem;
          color: #6B7280;
        }

        .mood-selector {
          margin-bottom: 1rem;
        }

        .mood-label {
          display: block;
          margin-bottom: 0.75rem;
          font-size: 0.875rem;
          color: #374151;
        }

        .mood-options {
          display: flex;
          justify-content: center;
          gap: 1rem;
        }

        .mood-option {
          position: relative;
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 0.25rem;
          padding: 0.75rem;
          background: #F9FAFB;
          border: 2px solid #E5E7EB;
          border-radius: 12px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .mood-option:hover:not(:disabled) {
          background: #EFF6FF;
          border-color: #3B82F6;
          transform: translateY(-2px);
        }

        .mood-option:disabled {
          cursor: not-allowed;
        }

        .mood-option--selected {
          background: #EFF6FF;
          border-color: #3B82F6;
          box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.1);
        }

        .mood-emoji {
          font-size: 2rem;
        }

        .mood-count {
          position: absolute;
          top: -6px;
          right: -6px;
          min-width: 20px;
          height: 20px;
          padding: 0 4px;
          background: #3B82F6;
          color: #fff;
          font-size: 0.625rem;
          font-weight: 600;
          border-radius: 10px;
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .mood-tooltip {
          position: absolute;
          bottom: 100%;
          left: 50%;
          transform: translateX(-50%);
          margin-bottom: 8px;
          padding: 0.5rem 0.75rem;
          background: #1F2937;
          color: #fff;
          border-radius: 6px;
          font-size: 0.75rem;
          white-space: nowrap;
          z-index: 10;
          pointer-events: none;
        }

        .mood-tooltip::after {
          content: '';
          position: absolute;
          top: 100%;
          left: 50%;
          transform: translateX(-50%);
          border: 6px solid transparent;
          border-top-color: #1F2937;
        }

        .tooltip-title {
          display: block;
          font-weight: 600;
          margin-bottom: 0.25rem;
        }

        .tooltip-members {
          display: flex;
          flex-direction: column;
          gap: 0.125rem;
        }

        .tooltip-member {
          color: #D1D5DB;
        }

        .mood-distribution {
          margin-bottom: 1rem;
        }

        .distribution-bar {
          display: flex;
          height: 8px;
          background: #E5E7EB;
          border-radius: 4px;
          overflow: hidden;
        }

        .distribution-segment {
          transition: width 0.3s ease;
        }

        .distribution-segment--1 { background: #EF4444; }
        .distribution-segment--2 { background: #F97316; }
        .distribution-segment--3 { background: #FBBF24; }
        .distribution-segment--4 { background: #34D399; }
        .distribution-segment--5 { background: #10B981; }

        .confidence-section {
          padding-top: 1rem;
          border-top: 1px solid #E5E7EB;
        }

        .confidence-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 0.5rem;
        }

        .confidence-label {
          font-size: 0.875rem;
          color: #374151;
        }

        .confidence-value {
          font-size: 0.875rem;
          font-weight: 600;
          color: #111827;
        }

        .confidence-slider-container {
          display: flex;
          align-items: center;
          gap: 0.75rem;
        }

        .slider-label {
          font-size: 0.75rem;
          color: #6B7280;
          min-width: 30px;
        }

        .confidence-slider {
          flex: 1;
          height: 6px;
          -webkit-appearance: none;
          appearance: none;
          background: #E5E7EB;
          border-radius: 3px;
          cursor: pointer;
        }

        .confidence-slider::-webkit-slider-thumb {
          -webkit-appearance: none;
          appearance: none;
          width: 18px;
          height: 18px;
          border-radius: 50%;
          background: #3B82F6;
          cursor: pointer;
          border: 3px solid #fff;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
        }

        .confidence-slider::-moz-range-thumb {
          width: 18px;
          height: 18px;
          border-radius: 50%;
          background: #3B82F6;
          cursor: pointer;
          border: 3px solid #fff;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
        }

        .confidence-slider:disabled {
          cursor: not-allowed;
          opacity: 0.6;
        }

        .confidence-indicator {
          height: 4px;
          border-radius: 2px;
          margin-top: 0.5rem;
          transition: background 0.3s ease;
        }
      `}</style>
    </div>
  );
};

TeamMoodPicker.displayName = 'TeamMoodPicker';

export default TeamMoodPicker;
