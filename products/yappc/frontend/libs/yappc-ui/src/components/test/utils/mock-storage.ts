/**
 * Mock storage and worker utilities for testing
 * @module test/utils/mock-storage
 */

import { vi } from 'vitest';

/**
 * Creates a mock File object
 *
 * @param bits - File content
 * @param filename - File name
 * @param options - File options (type, lastModified)
 * @returns Mock File object
 *
 * @example
 * ```typescript
 * const file = createMockFile(['content'], 'test.txt', { type: 'text/plain' });
 * expect(file.name).toBe('test.txt');
 * ```
 */
export function createMockFile(
  bits: string[] = [],
  filename = 'file.txt',
  options: { type?: string; lastModified?: number } = {}
) {
  const file = new File(bits as BlobPart[], filename, options);
  return {
    name: filename,
    size: bits.length,
    type: options.type ?? '',
    lastModified: options.lastModified ?? Date.now(),
    slice: vi.fn((start: number, end: number) => file.slice(start, end)),
    stream: vi.fn(() => ({})),
    text: vi.fn(() => Promise.resolve(bits.join(''))),
    arrayBuffer: vi.fn(() => Promise.resolve(new ArrayBuffer(0))),
  };
}

/**
 * Creates a mock FileList object
 *
 * @param files - Array of files
 * @returns Mock FileList
 *
 * @example
 * ```typescript
 * const fileList = createMockFileList([file1, file2]);
 * expect(fileList.length).toBe(2);
 * expect(fileList[0]).toBe(file1);
 * ```
 */
export function createMockFileList(files: File[] = []) {
  return {
    length: files.length,
    item: vi.fn((index: number) => {
      // eslint-disable-next-line security/detect-object-injection
      return files[index] ?? null;
    }),
    *[Symbol.iterator]() {
      yield* files;
    },
  };
}

/**
 * Creates a mock WebWorker
 *
 * @returns Mock WebWorker with postMessage and terminate
 *
 * @example
 * ```typescript
 * const worker = createMockWebWorker();
 * worker.postMessage({ data: 'test' });
 * expect(worker.postMessage).toHaveBeenCalled();
 * ```
 */
export function createMockWebWorker() {
  return {
    postMessage: vi.fn(),
    terminate: vi.fn(),
    onmessage: null,
    onerror: null,
  };
}

/**
 * Creates a mock Service Worker
 *
 * @returns Mock Service Worker with registration methods
 *
 * @example
 * ```typescript
 * const sw = createMockServiceWorker();
 * sw.postMessage({ type: 'SKIP_WAITING' });
 * expect(sw.postMessage).toHaveBeenCalled();
 * ```
 */
export function createMockServiceWorker() {
  return {
    postMessage: vi.fn(),
    onmessage: null,
    onerror: null,
    skipWaiting: vi.fn(),
  };
}

/**
 * Creates a mock WebSocket
 *
 * @param url - WebSocket URL
 * @returns Mock WebSocket with send and close
 *
 * @example
 * ```typescript
 * const ws = createMockWebSocket('ws://localhost');
 * ws.send('message');
 * expect(ws.send).toHaveBeenCalledWith('message');
 * ```
 */
export function createMockWebSocket(url = 'ws://localhost') {
  return {
    send: vi.fn(),
    close: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    onopen: null,
    onclose: null,
    onerror: null,
    onmessage: null,
    url,
    readyState: 1, // OPEN
    CONNECTING: 0,
    OPEN: 1,
    CLOSING: 2,
    CLOSED: 3,
  };
}

/**
 * Creates a mock Notification object
 *
 * @param title - Notification title
 * @param options - Notification options
 * @returns Mock Notification
 *
 * @example
 * ```typescript
 * const notif = createMockNotification('Title', { body: 'Body' });
 * notif.close();
 * expect(notif.close).toHaveBeenCalled();
 * ```
 */
export function createMockNotification(
  title = 'Test',
  options: { body?: string; icon?: string; tag?: string } = {}
) {
  return {
    title,
    body: options.body ?? '',
    icon: options.icon ?? '',
    tag: options.tag ?? '',
    close: vi.fn(),
    onclick: null,
    onclose: null,
    onerror: null,
    onshow: null,
  };
}

/**
 * Creates a mock Geolocation object
 *
 * @returns Mock Geolocation with getCurrentPosition
 *
 * @example
 * ```typescript
 * const geo = createMockGeolocation();
 * geo.getCurrentPosition(success);
 * expect(geo.getCurrentPosition).toHaveBeenCalled();
 * ```
 */
export function createMockGeolocation() {
  return {
    getCurrentPosition: vi.fn(),
    watchPosition: vi.fn(),
    clearWatch: vi.fn(),
  };
}

/**
 * Creates a mock SpeechSynthesis object
 *
 * @returns Mock SpeechSynthesis with speak and cancel
 *
 * @example
 * ```typescript
 * const synth = createMockSpeechSynthesis();
 * synth.speak(utterance);
 * expect(synth.speak).toHaveBeenCalled();
 * ```
 */
export function createMockSpeechSynthesis() {
  return {
    speak: vi.fn(),
    cancel: vi.fn(),
    pause: vi.fn(),
    resume: vi.fn(),
    getVoices: vi.fn(() => []),
    pending: false,
    paused: false,
    speaking: false,
  };
}

/**
 * Creates a mock SpeechRecognition object
 *
 * @returns Mock SpeechRecognition with start and stop
 *
 * @example
 * ```typescript
 * const recognition = createMockSpeechRecognition();
 * recognition.start();
 * expect(recognition.start).toHaveBeenCalled();
 * ```
 */
export function createMockSpeechRecognition() {
  return {
    start: vi.fn(),
    stop: vi.fn(),
    abort: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    onstart: null,
    onstop: null,
    onerror: null,
    onresult: null,
    continuous: false,
    interimResults: false,
    lang: 'en-US',
  };
}

/**
 * Creates a mock AudioContext (Web Audio API)
 *
 * @returns Mock AudioContext with createGain, createOscillator, etc.
 *
 * @example
 * ```typescript
 * const ctx = createMockAudioContext();
 * const osc = ctx.createOscillator();
 * expect(ctx.createOscillator).toHaveBeenCalled();
 * ```
 */
export function createMockAudioContext() {
  const mockNode = {
    connect: vi.fn(),
    disconnect: vi.fn(),
  };

  return {
    createGain: vi.fn(() => mockNode),
    createOscillator: vi.fn(() => ({
      ...mockNode,
      start: vi.fn(),
      stop: vi.fn(),
    })),
    createBufferSource: vi.fn(() => mockNode),
    createBiquadFilter: vi.fn(() => mockNode),
    createConvolver: vi.fn(() => mockNode),
    createDelay: vi.fn(() => mockNode),
    createDynamicsCompressor: vi.fn(() => mockNode),
    createAnalyser: vi.fn(() => mockNode),
    destination: mockNode,
    currentTime: 0,
    sampleRate: 44100,
    state: 'running',
    createBuffer: vi.fn(),
    decodeAudioData: vi.fn(),
  };
}
