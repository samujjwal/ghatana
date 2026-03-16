import React, { useState, useId } from 'react';
import type { VoiceInfo } from '@ghatana/audio-video-types';

export interface VoiceSelectorProps {
  voices: readonly VoiceInfo[];
  selectedVoiceId?: string;
  onSelect: (voice: VoiceInfo) => void;
  /** Allow filtering by language. */
  filterLanguage?: string;
  /** Show a preview-play button per voice row (requires previewUrl). */
  showPreview?: boolean;
  disabled?: boolean;
  className?: string;
}

const GENDER_LABELS: Record<string, string> = {
  male: '♂',
  female: '♀',
  neutral: '◉',
};

/**
 * Accessible voice picker for TTS configuration.
 *
 * Renders a filterable list of `VoiceInfo` entries with language, gender,
 * SSML support badges, and optional audio previews.
 */
export function VoiceSelector({
  voices,
  selectedVoiceId,
  onSelect,
  filterLanguage,
  showPreview = false,
  disabled = false,
  className,
}: VoiceSelectorProps): React.ReactElement {
  const [search, setSearch] = useState('');
  const [playingId, setPlayingId] = useState<string | null>(null);
  const selectId = useId();

  const filtered = voices.filter((v) => {
    const matchesLang = !filterLanguage || v.language.startsWith(filterLanguage);
    const matchesSearch =
      !search ||
      v.name.toLowerCase().includes(search.toLowerCase()) ||
      v.language.toLowerCase().includes(search.toLowerCase());
    return matchesLang && matchesSearch;
  });

  const handlePreview = (voice: VoiceInfo, e: React.MouseEvent): void => {
    e.stopPropagation();
    if (!voice.previewUrl) return;
    const audio = new Audio(voice.previewUrl);
    setPlayingId(voice.id);
    void audio.play();
    audio.onended = () => setPlayingId(null);
  };

  return (
    <div className={`voice-selector flex flex-col gap-2 ${className ?? ''}`}>
      <label htmlFor={selectId} className="sr-only">
        Search voices
      </label>
      <input
        id={selectId}
        type="search"
        placeholder="Search voices…"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        disabled={disabled}
        className="w-full px-3 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200 focus-visible:outline focus-visible:outline-blue-400"
      />

      <ul
        role="listbox"
        aria-label="Available voices"
        className="max-h-72 overflow-y-auto border border-gray-200 dark:border-gray-700 rounded-md divide-y divide-gray-100 dark:divide-gray-700"
      >
        {filtered.length === 0 && (
          <li className="px-3 py-4 text-sm text-gray-400 dark:text-gray-500 text-center">
            No voices match your search.
          </li>
        )}
        {filtered.map((voice) => {
          const isSelected = voice.id === selectedVoiceId;
          return (
            <li
              key={voice.id}
              role="option"
              aria-selected={isSelected}
              onClick={() => !disabled && onSelect(voice)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  if (!disabled) onSelect(voice);
                }
              }}
              tabIndex={disabled ? -1 : 0}
              className={[
                'flex items-center gap-3 px-3 py-2 cursor-pointer transition',
                isSelected
                  ? 'bg-blue-50 dark:bg-blue-900/30'
                  : 'hover:bg-gray-50 dark:hover:bg-gray-800',
                disabled ? 'opacity-50 cursor-not-allowed' : '',
              ].join(' ')}
            >
              {/* Selection indicator */}
              <span
                className={`w-4 h-4 flex-shrink-0 rounded-full border-2 ${
                  isSelected
                    ? 'border-blue-600 bg-blue-600'
                    : 'border-gray-300 dark:border-gray-600'
                }`}
                aria-hidden
              />

              <span className="flex-1 min-w-0">
                <span className="block text-sm font-medium text-gray-800 dark:text-gray-200 truncate">
                  {voice.name}
                  <span className="ml-1 text-gray-400 dark:text-gray-500 text-xs">
                    {GENDER_LABELS[voice.gender] ?? ''}
                  </span>
                </span>
                <span className="block text-xs text-gray-400 dark:text-gray-500">
                  {voice.language}
                  {voice.supportsSSML && (
                    <span className="ml-2 px-1 py-0.5 bg-gray-100 dark:bg-gray-700 rounded text-gray-500 dark:text-gray-400">
                      SSML
                    </span>
                  )}
                  {voice.supportsStreaming && (
                    <span className="ml-1 px-1 py-0.5 bg-green-50 dark:bg-green-900/30 rounded text-green-600 dark:text-green-400">
                      Streaming
                    </span>
                  )}
                </span>
              </span>

              {showPreview && voice.previewUrl && (
                <button
                  onClick={(e) => handlePreview(voice, e)}
                  aria-label={`Preview voice ${voice.name}`}
                  disabled={disabled}
                  className="flex-shrink-0 text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 transition"
                >
                  {playingId === voice.id ? (
                    <span aria-hidden>⏹</span>
                  ) : (
                    <span aria-hidden>▶</span>
                  )}
                </button>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
