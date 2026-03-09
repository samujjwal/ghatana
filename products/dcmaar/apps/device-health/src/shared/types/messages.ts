// Message types for extension communication
export type MessageType = 
  | { type: 'CONTENT_SCRIPT_READY' }
  | { type: 'FETCH_DATA'; payload: { url: string } }
  | { type: 'STORAGE_UPDATE'; payload: unknown };

// Response type for message handlers
export interface MessageResponse {
  status: 'success' | 'error';
  data?: unknown;
  error?: string;
}

// Type guard for message validation
export function isMessageType(message: unknown): message is MessageType {
  return (
    typeof message === 'object' &&
    message !== null &&
    'type' in message &&
    typeof message.type === 'string' &&
    // Add more specific validation based on message type if needed
    (message.type === 'CONTENT_SCRIPT_READY' ||
     (message.type === 'FETCH_DATA' && 
      typeof (message as any).payload?.url === 'string') ||
     message.type === 'STORAGE_UPDATE')
  );
}

// Helper function to create success responses
export function createSuccessResponse(data?: unknown): MessageResponse {
  const response: MessageResponse = { status: 'success' };
  if (data !== undefined) {
    response.data = data;
  }
  return response;
}

// Helper function to create error responses
export function createErrorResponse(error: string, data?: unknown): MessageResponse {
  const response: MessageResponse = { status: 'error', error };
  if (data !== undefined) {
    response.data = data;
  }
  return response;
}

// Type for message handlers
export type MessageHandler = (
  message: MessageType,
  sender: chrome.runtime.MessageSender,
  sendResponse: (response?: MessageResponse) => void
) => boolean | void;
