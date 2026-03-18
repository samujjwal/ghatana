type AnthropicConfig = {
  apiKey?: string;
  baseURL?: string;
  timeout?: number;
  maxRetries?: number;
};

const unsupported = () => {
  throw new Error('Anthropic SDK is not available in this browser build.');
};

export default class Anthropic {
  constructor(_config: AnthropicConfig) {}

  messages = {
    create: async (_params: unknown) => unsupported(),
    stream: async (_params: unknown) => unsupported(),
  };
}
