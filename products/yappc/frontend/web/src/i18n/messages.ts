export type MessageKey = string;

const MESSAGE_FALLBACKS: Readonly<Record<string, string>> = {
  'shortcut.openGuidedAssistant': 'Open Guided Assistant',
};

export const translate = (key: string, fallback?: string): string => {
  return MESSAGE_FALLBACKS[key] ?? fallback ?? key;
};
