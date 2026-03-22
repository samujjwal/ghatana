/**
 * Animation State Management - Jotai atoms for animation authoring
 */

import { atom } from 'jotai';
import { atomWithStorage, atomWithReset } from 'jotai/utils';
import type { AnimationTrack, AnimationKeyframe } from '../index';

// =============================================================================
// Core State Atoms
// =============================================================================

export type { AnimationTrack, AnimationKeyframe } from '../index';
export const animationTracksAtom = atomWithStorage<AnimationTrack[]>('animator-tracks', []);

/** Currently selected track for editing */
export const selectedTrackAtom = atom<AnimationTrack | null>(null);

/** Current playback time in milliseconds */
export const currentTimeAtom = atomWithReset<number>(0);

/** Total timeline duration in milliseconds */
export const totalDurationAtom = atom<number>((get) => {
  const tracks = get(animationTracksAtom);
  if (tracks.length === 0) return 5000;
  return Math.max(
    5000,
    ...tracks.map((t) => (t.delay || 0) + t.duration)
  );
});

/** Whether animation is playing */
export const isPlayingAtom = atomWithReset<boolean>(false);

/** Current playback speed */
export const playbackSpeedAtom = atomWithStorage<number>('animator-speed', 1);

/** Zoom level for timeline (pixels per second) */
export const zoomLevelAtom = atomWithStorage<number>('animator-zoom', 50);

/** Whether to snap to grid */
export const snapToGridAtom = atomWithStorage<boolean>('animator-snap', true);

/** Grid size in milliseconds */
export const gridSizeAtom = atomWithStorage<number>('animator-grid', 100);

// =============================================================================
// History Management (Undo/Redo)
// =============================================================================

interface HistoryState {
  tracks: AnimationTrack[];
  timestamp: number;
}

const historyAtom = atomWithStorage<HistoryState[]>('animator-history', []);
const historyIndexAtom = atomWithStorage<number>('animator-history-index', -1);

export const canUndoAtom = atom((get) => {
  const index = get(historyIndexAtom);
  return index > 0;
});

export const canRedoAtom = atom((get) => {
  const history = get(historyAtom);
  const index = get(historyIndexAtom);
  return index < history.length - 1;
});

export const undoAtom = atom(null, (get, set) => {
  const index = get(historyIndexAtom);
  if (index > 0) {
    const newIndex = index - 1;
    set(historyIndexAtom, newIndex);
    const history = get(historyAtom);
    set(animationTracksAtom, history[newIndex]!.tracks);
  }
});

export const redoAtom = atom(null, (get, set) => {
  const index = get(historyIndexAtom);
  const history = get(historyAtom);
  if (index < history.length - 1) {
    const newIndex = index + 1;
    set(historyIndexAtom, newIndex);
    set(animationTracksAtom, history[newIndex]!.tracks);
  }
});

/** Add current state to history */
const addHistoryEntryAtom = atom(null, (get, set) => {
  const tracks = get(animationTracksAtom);
  const currentIndex = get(historyIndexAtom);
  const history = get(historyAtom);
  
  // Remove any future history if we're not at the end
  const newHistory = history.slice(0, currentIndex + 1);
  
  // Add new entry
  newHistory.push({
    tracks: JSON.parse(JSON.stringify(tracks)),
    timestamp: Date.now(),
  });
  
  // Limit history to 50 entries
  if (newHistory.length > 50) {
    newHistory.shift();
  }
  
  set(historyAtom, newHistory);
  set(historyIndexAtom, newHistory.length - 1);
});

// =============================================================================
// Track Actions
// =============================================================================

export const addTrackAtom = atom(null, (get, set, track: AnimationTrack) => {
  set(addHistoryEntryAtom);
  const tracks = get(animationTracksAtom);
  set(animationTracksAtom, [...tracks, track]);
});

export const updateTrackAtom = atom(null, (get, set, updatedTrack: AnimationTrack) => {
  set(addHistoryEntryAtom);
  const tracks = get(animationTracksAtom);
  set(
    animationTracksAtom,
    tracks.map((t) => (t.id === updatedTrack.id ? updatedTrack : t))
  );
});

export const deleteTrackAtom = atom(null, (get, set, trackId: string) => {
  set(addHistoryEntryAtom);
  const tracks = get(animationTracksAtom);
  set(animationTracksAtom, tracks.filter((t) => t.id !== trackId));
  
  // Clear selection if deleted track was selected
  const selected = get(selectedTrackAtom);
  if (selected?.id === trackId) {
    set(selectedTrackAtom, null);
  }
});

export const duplicateTrackAtom = atom(null, (get, set, trackId: string) => {
  set(addHistoryEntryAtom);
  const tracks = get(animationTracksAtom);
  const track = tracks.find((t) => t.id === trackId);
  if (track) {
    const newTrack: AnimationTrack = {
      ...track,
      id: `${track.id}-copy-${Date.now()}`,
      delay: (track.delay || 0) + track.duration,
    };
    set(animationTracksAtom, [...tracks, newTrack]);
  }
});

// =============================================================================
// Keyframe Actions
// =============================================================================

export const addKeyframeAtom = atom(
  null,
  (get, set, { trackId, keyframe }: { trackId: string; keyframe: AnimationKeyframe }) => {
    set(addHistoryEntryAtom);
    const tracks = get(animationTracksAtom);
    set(
      animationTracksAtom,
      tracks.map((t) => {
        if (t.id !== trackId) return t;
        return {
          ...t,
          keyframes: [...(t.keyframes || []), keyframe].sort((a, b) => a.time - b.time),
        };
      })
    );
  }
);

export const updateKeyframeAtom = atom(
  null,
  (get, set, {
    trackId,
    index,
    keyframe,
  }: {
    trackId: string;
    index: number;
    keyframe: AnimationKeyframe;
  }) => {
    set(addHistoryEntryAtom);
    const tracks = get(animationTracksAtom);
    set(
      animationTracksAtom,
      tracks.map((t) => {
        if (t.id !== trackId) return t;
        const keyframes = [...(t.keyframes || [])];
        keyframes[index] = keyframe;
        return { ...t, keyframes: keyframes.sort((a, b) => a.time - b.time) };
      })
    );
  }
);

export const deleteKeyframeAtom = atom(
  null,
  (get, set, { trackId, index }: { trackId: string; index: number }) => {
    set(addHistoryEntryAtom);
    const tracks = get(animationTracksAtom);
    set(
      animationTracksAtom,
      tracks.map((t) => {
        if (t.id !== trackId) return t;
        const keyframes = [...(t.keyframes || [])];
        keyframes.splice(index, 1);
        return { ...t, keyframes };
      })
    );
  }
);

// =============================================================================
// Derived Atoms
// =============================================================================

/** Get all unique targets */
export const uniqueTargetsAtom = atom((get) => {
  const tracks = get(animationTracksAtom);
  return [...new Set(tracks.map((t) => t.target))];
});

/** Get all unique properties */
export const uniquePropertiesAtom = atom((get) => {
  const tracks = get(animationTracksAtom);
  return [...new Set(tracks.map((t) => t.property))];
});

/** Get tracks for a specific target */
export const tracksForTargetAtom = atom((get, targetId: string) => {
  const tracks = get(animationTracksAtom);
  return tracks.filter((t) => t.target === targetId);
});

/** Calculate animation progress for a track at current time */
export const trackProgressAtom = atom((get, trackId: string) => {
  const currentTime = get(currentTimeAtom);
  const tracks = get(animationTracksAtom);
  const track = tracks.find((t) => t.id === trackId);
  
  if (!track) return 0;
  
  const trackTime = currentTime - (track.delay || 0);
  if (trackTime < 0) return 0;
  if (trackTime > track.duration) return 1;
  
  return trackTime / track.duration;
});

// =============================================================================
// Export/Import Actions
// =============================================================================

export const exportAnimationAtom = atom(null, (get, set, format: 'json' | 'css' | 'web-animations') => {
  const tracks = get(animationTracksAtom);
  
  switch (format) {
    case 'json':
      return JSON.stringify(tracks, null, 2);
    
    case 'css':
      return tracks.map((track) => {
        const keyframes = track.keyframes?.map((kf) => {
          return `${(kf.time / track.duration) * 100}% { ${track.property}: ${kf.value} }`;
        }).join('\n') || `
          0% { ${track.property}: ${track.from} }
          100% { ${track.property}: ${track.to} }
        `;
        
        return `
@keyframes ${track.id} {
${keyframes}
}

#${track.target} {
  animation: ${track.id} ${track.duration}ms ${track.easing || 'ease'} ${track.repeat || 0} ${track.yoyo ? 'alternate' : ''};
}`;
      }).join('\n');
    
    case 'web-animations':
      return tracks.map((track) => ({
        target: track.target,
        keyframes: track.keyframes?.map((kf) => ({
          [track.property]: kf.value,
          offset: kf.time / track.duration,
        })) || [
          { [track.property]: track.from, offset: 0 },
          { [track.property]: track.to, offset: 1 },
        ],
        options: {
          duration: track.duration,
          easing: track.easing,
          delay: track.delay,
          iterations: track.repeat === -1 ? Infinity : (track.repeat || 1),
          direction: track.yoyo ? 'alternate' : 'normal',
        },
      }));
    
    default:
      return '';
  }
});

export const importAnimationAtom = atom(null, (get, set, data: string | AnimationTrack[]) => {
  set(addHistoryEntryAtom);
  
  let tracks: AnimationTrack[];
  
  if (typeof data === 'string') {
    try {
      tracks = JSON.parse(data);
    } catch {
      console.error('Failed to parse animation data');
      return;
    }
  } else {
    tracks = data;
  }
  
  // Validate and normalize
  const validTracks = tracks.map((t) => ({
    ...t,
    id: t.id || `track-${Date.now()}-${Math.random()}`,
    delay: t.delay || 0,
    easing: t.easing || 'ease-in-out',
  }));
  
  set(animationTracksAtom, validTracks);
});

// =============================================================================
// Playback Actions
// =============================================================================

export const playAtom = atom(null, (get, set) => {
  set(isPlayingAtom, true);
});

export const pauseAtom = atom(null, (get, set) => {
  set(isPlayingAtom, false);
});

export const stopAtom = atom(null, (get, set) => {
  set(isPlayingAtom, false);
  set(currentTimeAtom, 0);
});

export const seekAtom = atom(null, (get, set, time: number) => {
  const duration = get(totalDurationAtom);
  set(currentTimeAtom, Math.max(0, Math.min(time, duration)));
});

export const skipForwardAtom = atom(null, (get, set, amount: number = 1000) => {
  const current = get(currentTimeAtom);
  const duration = get(totalDurationAtom);
  set(currentTimeAtom, Math.min(current + amount, duration));
});

export const skipBackwardAtom = atom(null, (get, set, amount: number = 1000) => {
  const current = get(currentTimeAtom);
  set(currentTimeAtom, Math.max(0, current - amount));
});
