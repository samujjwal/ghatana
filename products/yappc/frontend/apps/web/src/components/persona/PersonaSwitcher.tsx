/**
 * PersonaSwitcher Component
 * 
 * Compact persona switcher for the sidebar.
 * Shows active personas with visual indicators for human vs AI (virtual) personas.
 * 
 * @doc.type component
 * @doc.purpose Persona selection UI
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import {
  usePersona,
  PERSONA_DEFINITIONS,
  ALL_PERSONA_TYPES,
  type PersonaType
} from '../../context/PersonaContext';

interface PersonaSwitcherProps {
  variant?: 'compact' | 'expanded';
  allowMultiple?: boolean;
  showInSidebar?: boolean;
  className?: string;
}

export function PersonaSwitcher({
  variant = 'compact',
  allowMultiple = true,
  showInSidebar = true,
  className = '',
}: PersonaSwitcherProps) {
  const {
    activePersonas,
    primaryPersona,
    virtualPersonas,
    togglePersona,
    setPrimaryPersona,
    isVirtualPersona,
  } = usePersona();

  const [isExpanded, setIsExpanded] = useState(false);

  if (variant === 'compact' && !isExpanded) {
    return (
      <div className={`${className}`} role="region" aria-label="Persona selection">
        {/* Compact view - show active persona icons */}
        <button
          onClick={() => setIsExpanded(true)}
          className="w-full flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors"
          title="Switch persona"
          aria-expanded={isExpanded}
          aria-haspopup="listbox"
          aria-label={`Current role: ${PERSONA_DEFINITIONS[primaryPersona].name}. ${virtualPersonas.length} AI agents active. Click to change roles.`}
        >
          <div className="flex -space-x-1">
            {activePersonas.slice(0, 3).map((personaId) => {
              const persona = PERSONA_DEFINITIONS[personaId];
              return (
                <div
                  key={personaId}
                  className="w-6 h-6 rounded-full flex items-center justify-center text-xs border-2 border-bg-paper"
                  style={{ backgroundColor: persona.color }}
                  title={persona.name}
                >
                  {persona.icon}
                </div>
              );
            })}
            {activePersonas.length > 3 && (
              <div className="w-6 h-6 rounded-full bg-grey-200 dark:bg-grey-700 flex items-center justify-center text-xs border-2 border-bg-paper">
                +{activePersonas.length - 3}
              </div>
            )}
          </div>
          <span className="text-sm text-text-secondary flex-1 text-left truncate">
            {PERSONA_DEFINITIONS[primaryPersona].shortName}
            {virtualPersonas.length > 0 && (
              <span className="ml-1 text-xs text-primary-500">
                +{virtualPersonas.length} AI
              </span>
            )}
          </span>
          <svg className="w-4 h-4 text-text-secondary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>
      </div>
    );
  }

  return (
    <div
      className={`bg-bg-paper rounded-lg border border-divider ${className}`}
      role="region"
      aria-label="Role selection panel"
    >
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 border-b border-divider">
        <span className="text-xs font-medium text-text-secondary uppercase tracking-wider" id="persona-list-label">
          Your Roles
        </span>
        {variant === 'compact' && (
          <button
            onClick={() => setIsExpanded(false)}
            className="p-1 rounded hover:bg-grey-100 dark:hover:bg-grey-800"
            aria-label="Collapse role selection"
          >
            <svg className="w-4 h-4 text-text-secondary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
            </svg>
          </button>
        )}
      </div>

      {/* Human Personas */}
      <div className="p-2 space-y-1" role="listbox" aria-labelledby="persona-list-label" aria-multiselectable={allowMultiple}>
        {ALL_PERSONA_TYPES.map((personaId) => {
          const persona = PERSONA_DEFINITIONS[personaId];
          const isActive = activePersonas.includes(personaId);
          const isPrimary = primaryPersona === personaId;
          const isVirtual = isVirtualPersona(personaId);

          return (
            <div
              key={personaId}
              role="option"
              aria-selected={isActive}
              aria-label={`${persona.name}${isActive ? ', selected' : ''}${isPrimary ? ', primary role' : ''}${isVirtual ? ', handled by AI agent' : ''}`}
              tabIndex={0}
              className={`
                flex items-center gap-2 px-2 py-1.5 rounded-md cursor-pointer transition-colors
                ${isActive ? 'bg-primary-50 dark:bg-primary-900/20' : 'hover:bg-grey-50 dark:hover:bg-grey-800'}
                ${isVirtual ? 'opacity-60' : ''}
                focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-1
              `}
              onClick={() => {
                if (allowMultiple) {
                  togglePersona(personaId);
                } else {
                  setPrimaryPersona(personaId);
                }
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  if (allowMultiple) {
                    togglePersona(personaId);
                  } else {
                    setPrimaryPersona(personaId);
                  }
                }
              }}
            >
              {/* Checkbox/Radio */}
              <div className={`
                w-4 h-4 rounded-${allowMultiple ? 'sm' : 'full'} border-2 flex items-center justify-center
                ${isActive
                  ? 'border-primary-500 bg-primary-500'
                  : 'border-grey-300 dark:border-grey-600'
                }
              `}>
                {isActive && (
                  <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                  </svg>
                )}
              </div>

              {/* Icon */}
              <div
                className="w-6 h-6 rounded-full flex items-center justify-center text-sm"
                style={{ backgroundColor: `${persona.color}20` }}
              >
                {persona.icon}
              </div>

              {/* Name */}
              <span className={`
                flex-1 text-sm
                ${isActive ? 'text-text-primary font-medium' : 'text-text-secondary'}
              `}>
                {persona.shortName}
              </span>

              {/* Primary badge */}
              {isPrimary && isActive && (
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-primary-100 dark:bg-primary-900/40 text-primary-700 dark:text-primary-300 font-medium">
                  Primary
                </span>
              )}

              {/* Virtual/AI badge */}
              {isVirtual && (
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-purple-100 dark:bg-purple-900/40 text-purple-700 dark:text-purple-300 font-medium flex items-center gap-0.5">
                  <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                  </svg>
                  AI
                </span>
              )}
            </div>
          );
        })}
      </div>

      {/* Virtual Personas Info */}
      {virtualPersonas.length > 0 && (
        <div className="px-3 py-2 border-t border-divider bg-purple-50/50 dark:bg-purple-900/10">
          <div className="flex items-start gap-2">
            <svg className="w-4 h-4 text-purple-500 mt-0.5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <p className="text-xs text-purple-700 dark:text-purple-300">
              <strong>{virtualPersonas.length} AI Agent{virtualPersonas.length > 1 ? 's' : ''}</strong> will handle
              {' '}{virtualPersonas.map(p => PERSONA_DEFINITIONS[p].shortName).join(', ')} tasks automatically.
            </p>
          </div>
        </div>
      )}

      {/* Set Primary (when multiple selected) */}
      {allowMultiple && activePersonas.length > 1 && (
        <div className="px-3 py-2 border-t border-divider">
          <label className="text-xs text-text-secondary block mb-1">Primary Role:</label>
          <select
            value={primaryPersona}
            onChange={(e) => setPrimaryPersona(e.target.value as PersonaType)}
            className="w-full text-sm px-2 py-1 rounded border border-divider bg-bg-default focus:outline-none focus:ring-2 focus:ring-primary-500"
          >
            {activePersonas.map((personaId) => (
              <option key={personaId} value={personaId}>
                {PERSONA_DEFINITIONS[personaId].name}
              </option>
            ))}
          </select>
        </div>
      )}
    </div>
  );
}

/**
 * PersonaSwitcherCompact
 * 
 * Collapsed sidebar version - shows only icons
 */
export function PersonaSwitcherCompact({ className = '' }: { className?: string }) {
  const { activePersonas, primaryPersona, virtualPersonas } = usePersona();
  const [showTooltip, setShowTooltip] = useState(false);
  const [tooltipPosition, setTooltipPosition] = useState({ top: 0, left: 0 });
  const buttonRef = useRef<HTMLButtonElement>(null);

  // Calculate tooltip position when showing
  const updateTooltipPosition = useCallback(() => {
    if (buttonRef.current && typeof document !== 'undefined') {
      const rect = buttonRef.current.getBoundingClientRect();
      setTooltipPosition({
        top: rect.top + window.scrollY,
        left: rect.right + window.scrollX + 8, // 8px gap
      });
    }
  }, []);

  const primaryDef = PERSONA_DEFINITIONS[primaryPersona];

  return (
    <div className={`relative ${className}`}>
      <button
        ref={buttonRef}
        className="w-full p-2 rounded-lg hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors flex flex-col items-center gap-1"
        onMouseEnter={() => {
          updateTooltipPosition();
          setShowTooltip(true);
        }}
        onMouseLeave={() => setShowTooltip(false)}
        title={`${primaryDef.name}${virtualPersonas.length > 0 ? ` + ${virtualPersonas.length} AI` : ''}`}
      >
        <div
          className="w-8 h-8 rounded-full flex items-center justify-center text-base"
          style={{ backgroundColor: primaryDef.color }}
        >
          {primaryDef.icon}
        </div>
        {virtualPersonas.length > 0 && (
          <div className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-purple-500 text-white text-[10px] flex items-center justify-center font-bold">
            {virtualPersonas.length}
          </div>
        )}
      </button>

      {/* Tooltip - Rendered via portal to avoid overflow clipping */}
      {showTooltip && typeof document !== 'undefined' && createPortal(
        <div
          className="bg-bg-paper border border-divider rounded-lg shadow-lg p-2 min-w-[160px]"
          style={{
            position: 'fixed',
            top: `${tooltipPosition.top}px`,
            left: `${tooltipPosition.left}px`,
            zIndex: 9999,
          }}
        >
          {(() => {
            console.log('PersonaSwitcher tooltip portal rendered with position:', tooltipPosition);
            return null;
          })()}
          <div className="text-xs font-medium text-text-primary mb-1">Active Roles</div>
          <div className="space-y-1">
            {activePersonas.map((id) => (
              <div key={id} className="flex items-center gap-1.5 text-xs text-text-secondary">
                <span>{PERSONA_DEFINITIONS[id].icon}</span>
                <span>{PERSONA_DEFINITIONS[id].shortName}</span>
                {id === primaryPersona && (
                  <span className="text-primary-500 text-[10px]">(Primary)</span>
                )}
              </div>
            ))}
          </div>
          {virtualPersonas.length > 0 && (
            <>
              <div className="text-xs font-medium text-purple-600 dark:text-purple-400 mt-2 mb-1">AI Agents</div>
              <div className="space-y-1">
                {virtualPersonas.map((id) => (
                  <div key={id} className="flex items-center gap-1.5 text-xs text-text-secondary">
                    <span>{PERSONA_DEFINITIONS[id].icon}</span>
                    <span>{PERSONA_DEFINITIONS[id].shortName}</span>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>,
        document.body
      )}
    </div>
  );
}

export default PersonaSwitcher;
