# Multi-Provider AI Guide

## Overview

The TutorPutor Simulation System now supports multiple AI providers for simulation generation and refinement, giving you flexibility in cost, performance, and model capabilities.

## Supported Providers

### 1. OpenAI
- **Models**: GPT-4, GPT-3.5-turbo, GPT-4-turbo-preview
- **Features**: Function calling, streaming, vision input
- **Best for**: Complex simulations, high-quality generation

### 2. Anthropic Claude
- **Models**: Claude 3 Opus, Sonnet, Haiku
- **Features**: Long context, vision input, streaming
- **Best for**: Detailed simulations, educational content

### 3. Ollama (Local Models)
- **Models**: Llama 2, Mistral, Code Llama, custom models
- **Features**: Free, local processing, privacy
- **Best for**: Cost-sensitive deployments, data privacy

### 4. Azure OpenAI
- **Models**: Same as OpenAI but hosted on Azure
- **Features**: Enterprise security, regional deployment
- **Best for**: Enterprise customers with Azure requirements

### 5. Google AI
- **Models**: Gemini Pro, Gemini Ultra
- **Features**: Multi-modal, Google ecosystem integration
- **Best for**: Google Cloud customers

### 6. Cohere
- **Models**: Command, Command R+
- **Features**: RAG capabilities, enterprise features
- **Best for**: Enterprise RAG applications

## Configuration

### Environment Setup

```bash
# OpenAI
OPENAI_API_KEY=sk-...

# Anthropic
ANTHROPIC_API_KEY=sk-ant-...

# Azure OpenAI
AZURE_OPENAI_API_KEY=...
AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
AZURE_OPENAI_DEPLOYMENT=gpt-4
AZURE_OPENAI_API_VERSION=2024-02-15-preview

# Google AI
GOOGLE_AI_API_KEY=...

# Cohere
COHERE_API_KEY=...

# Ollama (local)
OLLAMA_HOST=localhost
OLLAMA_PORT=11434
```

### Service Configuration

```typescript
// Example: Multi-provider setup
const aiConfig = {
  providers: [
    {
      name: 'openai-primary',
      config: {
        provider: 'openai',
        apiKey: process.env.OPENAI_API_KEY!,
        model: 'gpt-4-turbo-preview',
        maxRetries: 3,
        timeout: 30000
      },
      isDefault: true
    },
    {
      name: 'anthropic-backup',
      config: {
        provider: 'anthropic',
        apiKey: process.env.ANTHROPIC_API_KEY!,
        model: 'claude-3-sonnet-20240229',
        maxRetries: 3,
        timeout: 30000
      }
    },
    {
      name: 'ollama-local',
      config: {
        provider: 'ollama',
        host: 'localhost',
        port: 11434,
        model: 'llama2',
        maxRetries: 2,
        timeout: 60000
      }
    },
    {
      name: 'azure-enterprise',
      config: {
        provider: 'azure',
        apiKey: process.env.AZURE_OPENAI_API_KEY!,
        baseUrl: process.env.AZURE_OPENAI_ENDPOINT,
        deploymentId: 'gpt-4',
        apiVersion: '2024-02-15-preview',
        maxRetries: 3,
        timeout: 30000
      }
    }
  ],
  maxRetries: 3,
  cacheEnabled: true,
  rateLimit: {
    requestsPerMinute: 60,
    tokensPerMinute: 90000
  }
};
```

## Usage Patterns

### 1. Provider Selection

```typescript
// Use default provider
const result = await aiService.generate({
  prompt: "Create a physics simulation",
  systemPrompt: "You are a physics simulation expert"
});

// Use specific provider
const result = await aiService.generate({
  prompt: "Create a physics simulation",
  systemPrompt: "You are a physics simulation expert"
}, 'anthropic-backup');
```

### 2. Fallback Strategy

```typescript
async function generateWithFallback(request: AIRequest): Promise<AIResponse> {
  const providers = ['openai-primary', 'anthropic-backup', 'ollama-local'];
  
  for (const provider of providers) {
    try {
      return await aiService.generate(request, provider);
    } catch (error) {
      console.warn(`Provider ${provider} failed:`, error);
      continue;
    }
  }
  
  throw new Error('All AI providers failed');
}
```

### 3. Cost Optimization

```typescript
// Use cheaper models for simple tasks
const cheapResult = await aiService.generate({
  prompt: "Suggest parameter values",
  systemPrompt: "You are a parameter optimization assistant"
}, 'ollama-local');

// Use premium models for complex generation
const premiumResult = await aiService.generate({
  prompt: "Generate complete simulation manifest",
  systemPrompt: "You are an expert simulation designer"
}, 'openai-primary');
```

### 4. Streaming Responses

```typescript
// Stream generation for real-time feedback
const stream = aiService.generateStream({
  prompt: "Explain this simulation step",
  systemPrompt: "You are an educational assistant"
});

for await (const chunk of stream) {
  // Update UI in real-time
  updateContent(chunk);
}
```

## Model Comparison

| Provider | Model | Context | Cost/1K tokens | Speed | Best For |
|----------|-------|---------|----------------|-------|-----------|
| OpenAI | GPT-4 Turbo | 128K | $0.03 | Fast | Complex generation |
| Anthropic | Claude 3 Sonnet | 200K | $0.003 | Medium | Educational content |
| Ollama | Llama 2 70B | 4K | $0 | Slow | Local processing |
| Azure | GPT-4 | 128K | $0.03 | Fast | Enterprise |
| Google | Gemini Pro | 32K | $0.0005 | Fast | Google Cloud |
| Cohere | Command R+ | 128K | $0.003 | Fast | Enterprise RAG |

## Best Practices

### 1. Provider Selection Strategy

- **Primary**: Use OpenAI GPT-4 for complex simulations
- **Backup**: Configure Anthropic as fallback
- **Local**: Use Ollama for sensitive data or cost savings
- **Enterprise**: Use Azure for compliance requirements

### 2. Rate Limiting

- Configure different rate limits per provider
- Implement exponential backoff for retries
- Monitor usage and adjust limits accordingly

### 3. Cost Management

```typescript
// Track costs by provider
const costTracker = {
  openai: 0,
  anthropic: 0,
  ollama: 0,
  azure: 0
};

// Update costs after each request
function updateCosts(response: AIResponse) {
  const costPerToken = getCostPerToken(response.provider, response.model);
  const cost = (response.usage?.totalTokens || 0) * costPerToken;
  costTracker[response.provider] += cost;
}
```

### 4. Quality Assurance

```typescript
// Validate responses based on provider
function validateResponse(response: AIResponse, provider: string): boolean {
  switch (provider) {
    case 'openai':
      return response.confidence > 0.7;
    case 'anthropic':
      return response.confidence > 0.8;
    case 'ollama':
      return response.confidence > 0.6;
    default:
      return response.confidence > 0.7;
  }
}
```

## Troubleshooting

### Common Issues

1. **Rate Limiting**
   - Check provider-specific limits
   - Implement proper backoff strategies
   - Consider multiple providers for load balancing

2. **Model Availability**
   - Some models may be unavailable in certain regions
   - Have fallback models configured
   - Monitor model status endpoints

3. **Authentication Errors**
   - Verify API keys are correct
   - Check key permissions and quotas
   - Ensure proper endpoint URLs

4. **Performance Issues**
   - Monitor response times
   - Use streaming for long responses
   - Consider edge deployment for latency

### Monitoring

```typescript
// Health check all providers
async function checkAllProviders(): Promise<Record<string, boolean>> {
  const results: Record<string, boolean> = {};
  
  for (const provider of aiService.getAvailableProviders()) {
    try {
      results[provider] = await aiService.checkHealth(provider);
    } catch {
      results[provider] = false;
    }
  }
  
  return results;
}

// Monitor model availability
async function getAvailableModels(): Promise<AIModel[]> {
  const allModels: AIModel[] = [];
  
  for (const provider of aiService.getAvailableProviders()) {
    try {
      const models = await aiService.getModels(provider);
      allModels.push(...models);
    } catch (error) {
      console.warn(`Failed to get models from ${provider}:`, error);
    }
  }
  
  return allModels;
}
```

## Migration Guide

### From Single Provider

1. **Update Configuration**
   ```typescript
   // Before
   const config = {
     openaiApiKey: process.env.OPENAI_API_KEY,
     model: 'gpt-4'
   };

   // After
   const config = {
     providers: [{
       name: 'openai',
       config: {
         provider: 'openai',
         apiKey: process.env.OPENAI_API_KEY,
         model: 'gpt-4'
       },
       isDefault: true
     }]
   };
   ```

2. **Update Service Calls**
   ```typescript
   // Before
   const result = await callOpenAI(systemPrompt, userPrompt);

   // After
   const result = await callAI(systemPrompt, userPrompt);
   ```

3. **Add Fallbacks**
   ```typescript
   // Add backup providers for reliability
   config.providers.push({
     name: 'anthropic',
     config: {
       provider: 'anthropic',
       apiKey: process.env.ANTHROPIC_API_KEY
     }
   });
   ```

## Security Considerations

1. **API Key Management**
   - Store keys in environment variables
   - Rotate keys regularly
   - Use key management services in production

2. **Data Privacy**
   - Use Ollama for sensitive data
   - Configure data retention policies
   - Review provider privacy policies

3. **Access Control**
   - Implement provider-specific permissions
   - Audit AI usage by user
   - Set up alerts for unusual activity

## Future Enhancements

1. **Auto-Selection**: Automatically choose best provider based on request type
2. **Load Balancing**: Distribute requests across providers
3. **Fine-tuning**: Support for custom fine-tuned models
4. **Edge Deployment**: Local provider instances for low latency
5. **Cost Optimization**: Automatic provider switching based on cost targets
