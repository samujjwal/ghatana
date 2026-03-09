/**
 * Custom Prompt Pack Example
 * 
 * This example demonstrates how to create a custom prompt pack
 * for a specialized domain (music theory).
 *
 * @doc.type example
 * @doc.purpose Demonstrate custom prompt pack creation with SDK
 * @doc.layer product
 * @doc.pattern Example
 */

import {
  definePromptPackPlugin,
  pluginRegistry,
} from '@ghatana/tutorputor-sim-sdk';

// ============================================
// Define a custom domain prompt pack for Music Theory
// ============================================

const musicTheoryPromptPack = definePromptPackPlugin({
  metadata: {
    id: 'music-theory-prompts',
    name: 'Music Theory Prompt Pack',
    version: '1.0.0',
    description: 'LLM prompts and examples for music theory simulations',
    author: 'Tutorputor Team',
    license: 'MIT',
    tags: ['music', 'theory', 'chords', 'scales', 'intervals'],
  },
  
  domain: 'music_theory',
  
  systemPrompt: `You are a music theory simulation generator. You create educational simulations 
that visualize musical concepts like scales, chords, intervals, and progressions.

When generating a simulation manifest:

1. ENTITIES represent musical elements:
   - Notes (with properties: pitch, octave, duration, velocity)
   - Chords (with properties: root, quality, inversion)
   - Staff lines (with properties: clef, key_signature, time_signature)
   - Piano keys (with properties: note, is_black_key, octave)

2. VISUAL STYLES:
   - Notes use circles, color-coded by function (tonic=blue, dominant=red, subdominant=green)
   - Chords use rectangles encompassing their notes
   - Active/playing elements have opacity=1, inactive have opacity=0.3

3. STEPS describe the musical progression:
   - Each step can play/highlight notes or chords
   - Include timing based on musical duration (whole=4000ms, half=2000ms, quarter=1000ms)
   - Add annotations explaining theory concepts

4. ANIMATIONS:
   - Notes rise when played (position.y changes)
   - Chords spread when broken (arpeggiated)
   - Use easing: 'easeOutQuad' for natural movement

Return a valid SimulationManifest JSON.`,

  examples: [
    {
      input: 'Show the C major scale on a piano',
      output: JSON.stringify({
        id: 'example-c-major-scale',
        version: '1.0',
        domain: 'music_theory',
        title: 'C Major Scale',
        description: 'The C major scale consists of the notes C, D, E, F, G, A, B (all white keys)',
        entities: [
          { id: 'key-c', label: 'C', entityType: 'piano_key', position: { x: 0, y: 0 }, value: { pitch: 'C', octave: 4 }, visual: { color: '#FFFFFF', shape: 'rectangle' } },
          { id: 'key-d', label: 'D', entityType: 'piano_key', position: { x: 30, y: 0 }, value: { pitch: 'D', octave: 4 }, visual: { color: '#FFFFFF', shape: 'rectangle' } },
          { id: 'key-e', label: 'E', entityType: 'piano_key', position: { x: 60, y: 0 }, value: { pitch: 'E', octave: 4 }, visual: { color: '#FFFFFF', shape: 'rectangle' } },
          { id: 'key-f', label: 'F', entityType: 'piano_key', position: { x: 90, y: 0 }, value: { pitch: 'F', octave: 4 }, visual: { color: '#FFFFFF', shape: 'rectangle' } },
          { id: 'key-g', label: 'G', entityType: 'piano_key', position: { x: 120, y: 0 }, value: { pitch: 'G', octave: 4 }, visual: { color: '#FFFFFF', shape: 'rectangle' } },
          { id: 'key-a', label: 'A', entityType: 'piano_key', position: { x: 150, y: 0 }, value: { pitch: 'A', octave: 4 }, visual: { color: '#FFFFFF', shape: 'rectangle' } },
          { id: 'key-b', label: 'B', entityType: 'piano_key', position: { x: 180, y: 0 }, value: { pitch: 'B', octave: 4 }, visual: { color: '#FFFFFF', shape: 'rectangle' } },
          { id: 'key-c2', label: 'C', entityType: 'piano_key', position: { x: 210, y: 0 }, value: { pitch: 'C', octave: 5 }, visual: { color: '#FFFFFF', shape: 'rectangle' } },
        ],
        steps: [
          { id: 's1', stepNumber: 1, description: 'C - the tonic (home note)', duration: 800, actions: [{ actionType: 'highlight', targetIds: ['key-c'] }] },
          { id: 's2', stepNumber: 2, description: 'D - whole step up', duration: 800, actions: [{ actionType: 'highlight', targetIds: ['key-d'] }] },
          { id: 's3', stepNumber: 3, description: 'E - whole step up', duration: 800, actions: [{ actionType: 'highlight', targetIds: ['key-e'] }] },
          { id: 's4', stepNumber: 4, description: 'F - half step up', duration: 800, actions: [{ actionType: 'highlight', targetIds: ['key-f'] }] },
          { id: 's5', stepNumber: 5, description: 'G - whole step up', duration: 800, actions: [{ actionType: 'highlight', targetIds: ['key-g'] }] },
          { id: 's6', stepNumber: 6, description: 'A - whole step up', duration: 800, actions: [{ actionType: 'highlight', targetIds: ['key-a'] }] },
          { id: 's7', stepNumber: 7, description: 'B - whole step up', duration: 800, actions: [{ actionType: 'highlight', targetIds: ['key-b'] }] },
          { id: 's8', stepNumber: 8, description: 'C - half step up, back to tonic', duration: 800, actions: [{ actionType: 'highlight', targetIds: ['key-c2'] }] },
        ],
        keyframes: [],
        domainConfig: {
          tempo: 120,
          keySignature: 'C',
          timeSignature: '4/4',
        },
      }, null, 2),
    },
    {
      input: 'Demonstrate a I-IV-V-I chord progression in G major',
      output: JSON.stringify({
        id: 'example-chord-progression',
        version: '1.0',
        domain: 'music_theory',
        title: 'I-IV-V-I Progression in G Major',
        description: 'The most common chord progression in Western music: G - C - D - G',
        entities: [
          { id: 'chord-I', label: 'I (G)', entityType: 'chord', position: { x: 0, y: 0 }, value: { root: 'G', quality: 'major', notes: ['G', 'B', 'D'] }, visual: { color: '#4A90D9' } },
          { id: 'chord-IV', label: 'IV (C)', entityType: 'chord', position: { x: 100, y: 0 }, value: { root: 'C', quality: 'major', notes: ['C', 'E', 'G'] }, visual: { color: '#48BB78' } },
          { id: 'chord-V', label: 'V (D)', entityType: 'chord', position: { x: 200, y: 0 }, value: { root: 'D', quality: 'major', notes: ['D', 'F#', 'A'] }, visual: { color: '#F56565' } },
          { id: 'chord-I2', label: 'I (G)', entityType: 'chord', position: { x: 300, y: 0 }, value: { root: 'G', quality: 'major', notes: ['G', 'B', 'D'] }, visual: { color: '#4A90D9' } },
        ],
        steps: [
          { id: 's1', stepNumber: 1, description: 'G Major (I) - Tonic chord, establishes the key', duration: 2000, annotations: [{ id: 'a1', text: 'Tonic - home base', position: { x: 0, y: -30 } }] },
          { id: 's2', stepNumber: 2, description: 'C Major (IV) - Subdominant, creates movement away from tonic', duration: 2000, annotations: [{ id: 'a2', text: 'Subdominant - creates tension', position: { x: 100, y: -30 } }] },
          { id: 's3', stepNumber: 3, description: 'D Major (V) - Dominant, creates strong pull back to tonic', duration: 2000, annotations: [{ id: 'a3', text: 'Dominant - wants to resolve', position: { x: 200, y: -30 } }] },
          { id: 's4', stepNumber: 4, description: 'G Major (I) - Resolution back to tonic', duration: 2000, annotations: [{ id: 'a4', text: 'Resolution - feels complete', position: { x: 300, y: -30 } }] },
        ],
        keyframes: [],
        domainConfig: {
          tempo: 90,
          keySignature: 'G',
          timeSignature: '4/4',
        },
      }, null, 2),
    },
  ],
  
  entityTemplates: [
    {
      type: 'note',
      template: {
        entityType: 'note',
        visual: { shape: 'circle', size: 1, opacity: 1 },
        value: { pitch: 'C', octave: 4, duration: 'quarter', velocity: 80 },
      },
    },
    {
      type: 'chord',
      template: {
        entityType: 'chord',
        visual: { shape: 'rectangle', size: 2, opacity: 1 },
        value: { root: 'C', quality: 'major', inversion: 0 },
      },
    },
    {
      type: 'piano_key',
      template: {
        entityType: 'piano_key',
        visual: { shape: 'rectangle', size: 1, opacity: 1 },
        value: { note: 'C', is_black_key: false, octave: 4 },
      },
    },
    {
      type: 'staff',
      template: {
        entityType: 'staff',
        visual: { shape: 'custom', size: 1, opacity: 1 },
        value: { clef: 'treble', key_signature: 'C', time_signature: '4/4' },
      },
    },
  ],
});

// Register the prompt pack
pluginRegistry.register(musicTheoryPromptPack);

// Export for use
export { musicTheoryPromptPack };
