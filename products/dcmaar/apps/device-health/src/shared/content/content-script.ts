/// <reference types="chrome" />

import { MessageType, MessageResponse } from '../types/messages';

// Minimal type declaration for chrome.runtime
interface ChromeRuntime {
  sendMessage: <T, R>(message: T, callback?: (response: R) => void) => Promise<R>;
  onMessage: {
    addListener: (callback: (
      message: any,
      sender: any,
      sendResponse: (response?: any) => void
    ) => boolean | void) => void;
  };
}

declare const chrome: {
  runtime: ChromeRuntime;
};

console.log('[DCMAAR][ContentScript] Content script loaded');

// Notify the service worker that content script is ready
const notifyServiceWorker = async (): Promise<void> => {
  try {
    const response = await chrome.runtime.sendMessage<MessageType, MessageResponse>({
      type: 'CONTENT_SCRIPT_READY',
    });
    console.log('Service worker response:', response);
  } catch (error) {
    console.error('Failed to communicate with service worker:', error);
  }
};

// Initialize content script
const init = (): void => {
  console.log('Initializing content script');
  notifyServiceWorker();
  
  // Add your content script logic here
  // Example: Listen for messages from the service worker
  chrome.runtime.onMessage.addListener((message: unknown) => {
    try {
      const type = (message && typeof message === 'object' && 'type' in (message as any)) ? (message as any).type : undefined;
      console.debug('[DCMAAR][ContentScript] runtime.onMessage received', { type, message });
    } catch {}

    // Handle messages here
    console.log('Message received in content script:', message);
  });
};

// Start the content script
init();
