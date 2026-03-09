# AI Service Environment Variables Reference

> **Quick Reference** for all AI and fallback service configuration.
>
> Updated: Dec 21, 2025

## Summary Table

| Variable                     | Default                  | Type   | Purpose                     | Example                        |
| ---------------------------- | ------------------------ | ------ | --------------------------- | ------------------------------ |
| `USE_OLLAMA`                 | `false`                  | bool   | Enable local Ollama backend | `true`                         |
| `OLLAMA_BASE_URL`            | `http://localhost:11434` | URL    | Ollama API endpoint         | `http://localhost:11434`       |
| `OLLAMA_MODEL`               | `mistral`                | string | LLM model name              | `mistral`, `llama2`            |
| `OPENAI_API_KEY`             | (empty)                  | string | OpenAI API key (fallback)   | `sk-proj-...`                  |
| `WEB_SEARCH_API`             | `duckduckgo`             | enum   | Search provider             | `duckduckgo`, `google`, `bing` |
| `WEB_SEARCH_CACHE_TTL`       | `3600`                   | number | Cache expiry in seconds     | `3600`                         |
| `ENABLE_WEB_SEARCH_FALLBACK` | `true`                   | bool   | Enable web search fallback  | `true`                         |

---

## Detailed Configuration

### Ollama Backend

**Enable Local LLM**:

```bash
export USE_OLLAMA=true
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=mistral
```

**What it does**:

- Uses local Ollama server for AI requests
- No API costs, works offline
- Faster for development than cloud APIs

**Required setup**:

1. Install Ollama: https://ollama.ai
2. Run: `ollama serve`
3. Pull model: `ollama pull mistral`

**Models available**:

- `mistral` (recommended, 4.1GB)
- `llama2` (3.8GB)
- `neural-chat` (4.7GB)
- `phi` (1.6GB, lightweight)

---

### OpenAI Backend

**Enable Cloud AI**:

```bash
export OPENAI_API_KEY=sk-proj-your-key-here
```

**What it does**:

- Uses OpenAI GPT models (gpt-4o-mini by default)
- Requires paid API key
- Higher quality responses than local models
- Used as fallback when Ollama unavailable

**Getting API key**:

1. Go to https://platform.openai.com/api/keys
2. Create new secret key
3. Copy and set environment variable

**Cost**:

- ~$0.15 per 1M input tokens
- ~$0.60 per 1M output tokens
- (Prices vary by model, this is gpt-4o-mini)

---

### Web Search Fallback

**Configure search provider**:

```bash
export WEB_SEARCH_API=duckduckgo  # Free, recommended
# OR
export WEB_SEARCH_API=google      # Requires paid API key
# OR
export WEB_SEARCH_API=bing        # Requires paid API key
```

**What it does**:

- Provides search-based answers when AI service unavailable
- Always available (if internet connection)
- Caches results to reduce API calls

**DuckDuckGo (Recommended)**:

- ✅ Free, no API key needed
- ✅ Privacy-focused
- ✅ No rate limiting
- ⚠️ Slightly lower result quality

**Google**:

- 💰 $5/month for 1000 queries
- ✅ Best result quality
- ⚠️ Requires setup: https://programmablesearchengine.google.com

**Bing**:

- 💰 Paid enterprise API
- ✅ Good result quality
- ⚠️ Complex setup

---

## Architecture & Priority

```
User asks TutorPutor a question
  ↓
1. Ollama? (if USE_OLLAMA=true)
  ├─ Available? → Use it (FAST, FREE)
  └─ Failed? → Try next
  ↓
2. OpenAI? (if OPENAI_API_KEY set)
  ├─ Available? → Use it (SLOW, COSTS MONEY)
  └─ Failed? → Try next
  ↓
3. Web Search (always attempted)
  ├─ Results? → Use search answer (FAST, FREE)
  └─ Failed? → Try next
  ↓
4. Demo/Stub (last resort)
  └─ Return placeholder response
```

---

## Common Configurations

### Configuration 1: Local Development (Ollama Only)

```bash
export USE_OLLAMA=true
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=mistral
# Don't set OPENAI_API_KEY - uses Ollama only

./run-dev.sh
```

**Pros**: Free, fast, no API costs  
**Cons**: Slightly lower quality than GPT-4

---

### Configuration 2: Production-Ready (OpenAI + Fallbacks)

```bash
export OPENAI_API_KEY=sk-proj-your-key
export WEB_SEARCH_API=duckduckgo
# Don't set USE_OLLAMA - uses OpenAI

./run-dev.sh
```

**Pros**: Best quality, reliable  
**Cons**: $$ API costs

---

### Configuration 3: Hybrid (Ollama + OpenAI Backup)

```bash
export USE_OLLAMA=true
export OLLAMA_MODEL=mistral
export OPENAI_API_KEY=sk-proj-your-key  # Fallback only
export WEB_SEARCH_API=duckduckgo         # Second fallback

./run-dev.sh
```

**Pros**: Fast local dev with cloud backup  
**Cons**: Uses API when Ollama slow/down, slight cost

---

### Configuration 4: Testing All Services

```bash
export USE_OLLAMA=true
export OLLAMA_MODEL=llama2
export OPENAI_API_KEY=sk-proj-your-key
export WEB_SEARCH_API=google  # Best quality search

./run-dev.sh
```

**Pros**: Maximum capabilities  
**Cons**: Requires all setups, may incur costs

---

## Setting Environment Variables

### Option 1: Shell Export (Temporary)

```bash
export USE_OLLAMA=true
export OLLAMA_MODEL=mistral
./run-dev.sh
```

**Duration**: Only current terminal session  
**Useful for**: Testing different configs

---

### Option 2: Inline Assignment (Temporary)

```bash
USE_OLLAMA=true OLLAMA_MODEL=mistral ./run-dev.sh
```

**Duration**: Only this command  
**Useful for**: One-off runs

---

### Option 3: .env File (Persistent)

**File**: `/products/tutorputor/.env`

```bash
# AI Backends
USE_OLLAMA=true
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=mistral
OPENAI_API_KEY=sk-proj-your-key
WEB_SEARCH_API=duckduckgo
```

**Duration**: Every time you start dev  
**Useful for**: Regular development

---

### Option 4: Shell Profile (Global)

**File**: `~/.zshrc` or `~/.bashrc`

```bash
# Add to end of file
export USE_OLLAMA=true
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=mistral
export WEB_SEARCH_API=duckduckgo
```

**Duration**: Across all terminal sessions  
**Useful for**: System-wide defaults

---

## Checking Your Configuration

### Display current values

```bash
# Check Ollama settings
echo "USE_OLLAMA: ${USE_OLLAMA:-not set}"
echo "OLLAMA_BASE_URL: ${OLLAMA_BASE_URL:-not set}"
echo "OLLAMA_MODEL: ${OLLAMA_MODEL:-not set}"

# Check OpenAI settings
echo "OPENAI_API_KEY: ${OPENAI_API_KEY:-(empty)}"

# Check Web Search settings
echo "WEB_SEARCH_API: ${WEB_SEARCH_API:-duckduckgo (default)}"
```

### Check service availability

```bash
# Check Ollama running
curl -s http://localhost:11434/api/tags | jq .

# Check OpenAI API key
[ -n "$OPENAI_API_KEY" ] && echo "✓ OpenAI API key set" || echo "✗ OpenAI API key not set"

# Check internet (for web search)
curl -s https://duckduckgo.com >/dev/null && echo "✓ Internet available" || echo "✗ No internet"
```

---

## Troubleshooting

### Issue: "Ollama connection refused"

**Solution**:

```bash
# Verify Ollama running
ps aux | grep ollama
# or
docker ps | grep ollama

# Start Ollama
ollama serve
# or
docker run -d --name ollama -p 11434:11434 ollama/ollama
```

---

### Issue: "OPENAI_API_KEY is not valid"

**Solution**:

```bash
# Check key format (should start with sk-)
echo $OPENAI_API_KEY | grep -q "^sk-" && echo "✓ Correct format" || echo "✗ Invalid format"

# Update key
export OPENAI_API_KEY=sk-proj-your-actual-key
```

---

### Issue: "Web search not working"

**Solution**:

```bash
# Check internet
ping 8.8.8.8

# Try different provider
export WEB_SEARCH_API=duckduckgo  # Most reliable

# Check if DuckDuckGo API responding
curl -s "https://api.duckduckgo.com/?q=test&format=json" | head -20
```

---

### Issue: "Not sure which config is being used"

**Solution**:

```bash
# Check run-dev.sh startup output
# It will display:
# - Ollama status
# - OpenAI API key status
# - Web search provider
# - Active backend being used

./run-dev.sh | head -30
```

---

## Advanced Options

### Remote Ollama Server

```bash
export OLLAMA_BASE_URL=http://ollama-server.example.com:11434
export USE_OLLAMA=true
```

Useful for:

- Team development (shared GPU)
- CI/CD pipelines
- GPU-equipped machine as AI backend

---

### Model-Specific Configuration

```bash
# Use different models for different purposes
export OLLAMA_MODEL=mistral      # General questions
# (You'd need code changes to use different models for different purposes)
```

Available models:

```bash
ollama list
# mistral:latest
# llama2:latest
# neural-chat:latest
# phi:latest
```

---

### Cache Configuration

```bash
# Adjust web search cache TTL (time-to-live)
export WEB_SEARCH_CACHE_TTL=3600  # 1 hour (default)
export WEB_SEARCH_CACHE_TTL=86400 # 24 hours (longer cache)
export WEB_SEARCH_CACHE_TTL=0     # No cache (always fresh)
```

---

## Environment Validation

Run this script to check your setup:

```bash
#!/bin/bash

echo "=== TutorPutor AI Service Configuration Check ==="
echo ""

# Check Ollama
if [ "${USE_OLLAMA}" = "true" ]; then
  echo "🤖 Ollama:"
  if curl -s http://localhost:11434/api/tags >/dev/null 2>&1; then
    echo "   ✓ Running on $OLLAMA_BASE_URL"
    echo "   ✓ Using model: ${OLLAMA_MODEL}"
  else
    echo "   ✗ Not responding at $OLLAMA_BASE_URL"
    echo "   💡 Start with: ollama serve"
  fi
else
  echo "🤖 Ollama: Disabled (set USE_OLLAMA=true to enable)"
fi

echo ""

# Check OpenAI
if [ -n "$OPENAI_API_KEY" ]; then
  echo "🔑 OpenAI:"
  echo "   ✓ API key configured"
  KEY_HINT=$(echo $OPENAI_API_KEY | cut -c1-10)...
  echo "   Key: $KEY_HINT"
else
  echo "🔑 OpenAI: No API key (will use fallbacks)"
fi

echo ""

# Check Web Search
echo "🔍 Web Search:"
if [ -n "$WEB_SEARCH_API" ]; then
  echo "   ✓ Provider: $WEB_SEARCH_API"
else
  echo "   ✓ Provider: duckduckgo (default)"
fi

echo ""
echo "=== Summary ==="
if [ "${USE_OLLAMA}" = "true" ]; then
  echo "Primary: Ollama (local)"
elif [ -n "$OPENAI_API_KEY" ]; then
  echo "Primary: OpenAI (cloud)"
else
  echo "Primary: Web Search + Stub"
fi
```

Save as `check-ai-config.sh` and run:

```bash
chmod +x check-ai-config.sh
./check-ai-config.sh
```

---

## Reference Documentation

- [Ollama Setup Guide](./OLLAMA_SETUP_GUIDE.md) - Detailed Ollama instructions
- [AI Service Setup](./AI_SERVICE_DEVELOPMENT_SETUP.md) - Full AI service guide
- [run-dev.sh Reference](./RUN_DEV_QUICK_REFERENCE.md) - Development environment
- [AIProxyService Architecture](./AI_PROXY_SERVICE_ARCHITECTURE.md) - Implementation details

---

**Last Updated**: Dec 21, 2025  
**Status**: ✅ Production Ready
