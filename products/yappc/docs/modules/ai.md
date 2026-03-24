# AI Integration Documentation

## Overview

YAPPC integrates with LLM providers for AI-powered development features.

## Supported Providers

- OpenAI (GPT-4, GPT-3.5)
- Anthropic (Claude)
- Ollama (Local models)

## Configuration

```yaml
ai:
  provider: openai
  model: gpt-4
  apiKey: ${OPENAI_API_KEY}
  temperature: 0.7
  maxTokens: 2000
```

## Usage

```java
AiService aiService = new AiService();

Promise<AiResponse> response = aiService.complete(
    "Generate a React component for a login form"
);
```

## Features

- Natural language code generation
- Code explanation and documentation
- Bug detection and fixes
- Test generation
- Architecture suggestions

---

See [AI Service API](../api/ai-api.md) for complete API reference.
