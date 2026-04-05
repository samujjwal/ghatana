type OpenAIConfig = {
  apiKey?: string;
  baseURL?: string;
  organization?: string;
  timeout?: number;
  maxRetries?: number;
};

const unsupported = () => {
  throw new Error('OpenAI SDK is not available in this browser build.');
};

export default class OpenAI {
  constructor(_config: OpenAIConfig) {}

  chat = {
    completions: {
      create: async (_params: unknown) => unsupported(),
    },
  };

  embeddings = {
    create: async (_params: unknown) => unsupported(),
  };
}
