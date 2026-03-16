import React from 'react';
import type { STTResult, STTUtterance, WordTimestamp } from '@ghatana/audio-video-types';

export interface TranscriptionDisplayProps {
  result: STTResult | null;
  /** Highlight the word at this position (ms) for sync'd playback captions. */
  currentMs?: number;
  /** Show speaker labels when result includes diarization. */
  showSpeakers?: boolean;
  /** Show per-word confidence as opacity. */
  showConfidence?: boolean;
  /** Show processing metadata footer. */
  showMetadata?: boolean;
  onWordClick?: (word: WordTimestamp, utteranceIndex: number) => void;
  className?: string;
}

const SPEAKER_COLORS = [
  'text-blue-700 dark:text-blue-300',
  'text-green-700 dark:text-green-300',
  'text-purple-700 dark:text-purple-300',
  'text-orange-700 dark:text-orange-300',
  'text-pink-700 dark:text-pink-300',
] as const;

function getSpeakerColor(index: number): string {
  return SPEAKER_COLORS[index % SPEAKER_COLORS.length] ?? SPEAKER_COLORS[0];
}

interface WordSpanProps {
  word: WordTimestamp;
  utteranceIndex: number;
  isActive: boolean;
  showConfidence: boolean;
  onWordClick?: TranscriptionDisplayProps['onWordClick'];
}

function WordSpan({
  word,
  utteranceIndex,
  isActive,
  showConfidence,
  onWordClick,
}: WordSpanProps): React.ReactElement {
  const opacity = showConfidence ? Math.max(0.4, word.confidence) : 1;
  return (
    <span
      role={onWordClick ? 'button' : undefined}
      tabIndex={onWordClick ? 0 : undefined}
      onClick={onWordClick ? () => onWordClick(word, utteranceIndex) : undefined}
      onKeyDown={
        onWordClick
          ? (e) => {
              if (e.key === 'Enter') onWordClick(word, utteranceIndex);
            }
          : undefined
      }
      style={{ opacity }}
      className={[
        'mr-1 transition-colors',
        isActive ? 'bg-yellow-200 dark:bg-yellow-700 rounded font-semibold' : '',
        onWordClick ? 'cursor-pointer hover:underline' : '',
      ].join(' ')}
      title={
        showConfidence ? `${word.word} (${Math.round(word.confidence * 100)}%)` : undefined
      }
    >
      {word.word}
    </span>
  );
}

function UtteranceBlock({
  utterance,
  index,
  currentMs,
  showSpeakers,
  showConfidence,
  onWordClick,
}: {
  utterance: STTUtterance;
  index: number;
  currentMs: number;
  showSpeakers: boolean;
  showConfidence: boolean;
  onWordClick?: TranscriptionDisplayProps['onWordClick'];
}): React.ReactElement {
  const best = utterance.alternatives[0];
  if (!best) return <></>;

  const speakerIndex = utterance.speakers?.[0]?.speakerIndex ?? 0;

  return (
    <div className="utterance mb-3">
      {showSpeakers && utterance.speakers && (
        <span className={`text-xs font-semibold mr-2 ${getSpeakerColor(speakerIndex)}`}>
          Speaker {speakerIndex + 1}
        </span>
      )}
      <span className="text-gray-800 dark:text-gray-200 leading-relaxed">
        {best.words?.length
          ? best.words.map((w, i) => (
              <WordSpan
                key={i}
                word={w}
                utteranceIndex={index}
                isActive={currentMs >= w.startMs && currentMs <= w.endMs}
                showConfidence={showConfidence}
                onWordClick={onWordClick}
              />
            ))
          : best.transcript}
      </span>
    </div>
  );
}

/**
 * Renders an `STTResult` as readable text with optional word-level confidence
 * shading, speaker diarization labels, and playback-sync highlighting.
 */
export function TranscriptionDisplay({
  result,
  currentMs = 0,
  showSpeakers = false,
  showConfidence = false,
  showMetadata = false,
  onWordClick,
  className,
}: TranscriptionDisplayProps): React.ReactElement {
  if (!result) {
    return (
      <div
        className={`transcription-display text-gray-400 dark:text-gray-500 text-sm italic ${className ?? ''}`}
      >
        No transcription available.
      </div>
    );
  }

  return (
    <div
      className={`transcription-display ${className ?? ''}`}
      aria-live="polite"
      aria-label="Transcription"
    >
      <div className="utterances">
        {result.utterances.map((u, i) => (
          <UtteranceBlock
            key={i}
            utterance={u}
            index={i}
            currentMs={currentMs}
            showSpeakers={showSpeakers}
            showConfidence={showConfidence}
            onWordClick={onWordClick}
          />
        ))}
      </div>

      {showMetadata && (
        <div className="mt-3 pt-3 border-t border-gray-200 dark:border-gray-700 text-xs text-gray-400 dark:text-gray-500 flex flex-wrap gap-4">
          <span>Engine: {result.engine}</span>
          <span>Audio: {(result.audioDurationMs / 1000).toFixed(1)}s</span>
          <span>Processed in: {result.processingTimeMs}ms</span>
          {result.languageDetected && <span>Language: {result.languageDetected}</span>}
        </div>
      )}
    </div>
  );
}
