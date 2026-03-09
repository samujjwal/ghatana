# Ollama Architecture & Flow Diagrams

> Visual reference for Ollama integration in TutorPutor

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        TutorPutor Frontend                      │
│                   (React 19, TanStack Query)                    │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ POST /api/content-studio/ai/generate
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│                  Fastify Backend API Server                     │
│                  (Port 3000)                                    │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ handleTutorQuery()
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│              AIProxyService (Main Controller)                   │
│  - Constructor: Detects USE_OLLAMA env var                     │
│  - callLLM(): Routes to appropriate backend                    │
│  - getHealthStatus(): Checks all services                      │
└─┬────────┬────────┬────────────────────────────────────────────┘
  │        │        │
  │ TIER 1 │ TIER 2 │ TIER 3          TIER 4
  ↓        ↓        ↓                   ↓
┌────┐  ┌─────┐  ┌─────┐           ┌──────┐
│    │  │     │  │     │           │      │
│    │  │     │  │     │           │      │
└────┘  └─────┘  └─────┘           └──────┘
Ollama  OpenAI  WebSearch  Demo Response
(Local) (Cloud)  (Search)      (Stub)
```

---

## Request Flow with Fallback

```
Client Request
    │
    ↓
AIProxyService.callLLM(prompt, moduleTitle)
    │
    ├─── Check: USE_OLLAMA = true?
    │       YES ↓
    │   ┌─────────────────────┐
    │   │ callOllama(prompt)  │
    │   └──────┬──────────────┘
    │          │
    │          ├─ Success? ──→ Return Ollama Response ✓
    │          │
    │          └─ Failed ──→ Continue...
    │              │
    │              ↓
    │
    ├─── Check: OPENAI_API_KEY set?
    │       YES ↓
    │   ┌──────────────────────────┐
    │   │ openai.chat.completions  │
    │   │      .create()           │
    │   └──────┬───────────────────┘
    │          │
    │          ├─ Success? ──→ Return OpenAI Response ✓
    │          │
    │          └─ Failed ──→ Continue...
    │              │
    │              ↓
    │
    ├─── Check: Internet available?
    │       YES ↓
    │   ┌──────────────────────┐
    │   │ WebSearchService     │
    │   │   .search(question)  │
    │   └──────┬───────────────┘
    │          │
    │          ├─ Results? ──→ Return Search Response ✓
    │          │
    │          └─ Failed ──→ Continue...
    │              │
    │              ↓
    │
    └─── Return Demo/Stub Response ✓ (Last resort)

Legend: ✓ = Success, all tiers below skipped
```

---

## Ollama Configuration Path

```
┌────────────────────┐
│  Environment Vars  │  (Highest Priority)
│  - USE_OLLAMA      │
│  - OLLAMA_BASE_URL │
│  - OLLAMA_MODEL    │
└─────────┬──────────┘
          │
          ↓
┌────────────────────┐
│  Constructor Arg   │  (Code Config)
│  config.useOllama  │
│  config.ollamaUrl  │
│  config.ollamaModel│
└─────────┬──────────┘
          │
          ↓
┌────────────────────┐
│   Default Values   │  (Fallback)
│  useOllama: false  │
│  url: localhost... │
│  model: mistral    │
└────────────────────┘
```

---

## Service Initialization Diagram

```
TutorPutorAIProxyService Constructor
    │
    ├─ Check: process.env.USE_OLLAMA === "true"?
    │   ├─ YES → this.useOllama = true
    │   └─ NO  → this.useOllama = false
    │
    ├─ Check: useOllama OR config.useOllama?
    │   ├─ YES → this.ollamaModel = config.ollamaModel || env || "mistral"
    │   └─ NO  → (ollama vars unused)
    │
    ├─ Check: !this.useOllama AND OPENAI_API_KEY?
    │   ├─ YES → this.openai = new OpenAI({ apiKey })
    │   └─ NO  → this.openai = null
    │
    └─ this.webSearchService = new WebSearchService()
            │
            └─ Always initialized (used in all tiers)
```

---

## Backend Priority Decision Tree

```
                   callLLM(prompt)
                       │
                       ├──────────────────┐
                       │                  │
                   Is Ollama         Is OpenAI
                    enabled?         available?
                    /     \          /      \
                  YES     NO       YES       NO
                   │       │        │         │
                   ↓       │        │         │
            Try Ollama    Skip     ↓        Skip
              │                Try OpenAI   │
              ├─OK?               │        │
              │ │              ├─OK?      │
              │ ├─YES→Done     │ │        │
              │ └─NO          │ ├─YES→Done
              │    │          │ └─NO      │
              │    ↓          │    │      │
              └────┼──────────┴────┼──────┘
                   │               │
                   └─Try WebSearch─┘
                       │
                    ├─Results?
                    │ │
                    │ ├─YES → Done
                    │ └─NO
                    │    │
                    └────→ Demo Response (Last Resort)
```

---

## Health Check Flow

```
service.getHealthStatus()
    │
    ├─ Ollama Check:
    │   ├─ useOllama === true?
    │   │   ├─ YES → Try fetch(/api/tags)
    │   │   │   ├─ 200? → ollamaAvailable = true
    │   │   │   └─ ERROR → ollamaAvailable = false
    │   │   └─ NO → ollamaAvailable = false
    │
    ├─ OpenAI Check:
    │   └─ openaiAvailable = !!this.openai
    │
    ├─ Determine Active Backend:
    │   ├─ ollamaAvailable? → "ollama (mistral)"
    │   ├─ openaiAvailable? → "openai (gpt-4o-mini)"
    │   ├─ webSearch ready? → "web-search-fallback"
    │   └─ else → "demo"
    │
    └─ Return:
        {
          ollama: { available, baseUrl, model },
          openai: { available, model },
          webSearch: { available },
          activeBackend: string
        }
```

---

## Ollama API Call Sequence

```
callLLM() decides to use Ollama
    │
    ↓
callOllama(prompt)
    │
    ├─ Construct request:
    │   POST /v1/chat/completions
    │   Headers: Content-Type: application/json
    │   Body: {
    │     model: "mistral",
    │     messages: [{ role: "user", content: prompt }],
    │     temperature: 0.7,
    │     max_tokens: 1500
    │   }
    │
    ├─ Send to: http://localhost:11434/v1/chat/completions
    │
    ├─ Ollama processes request
    │   ├─ Load model if not in memory
    │   ├─ Generate response
    │   └─ Return in 2-4 seconds
    │
    ├─ Parse response:
    │   {
    │     choices: [{
    │       message: {
    │         content: "Generated answer text..."
    │       }
    │     }]
    │   }
    │
    ├─ Extract: content = response.choices[0].message.content
    │
    ├─ Validation:
    │   ├─ response.ok? → OK
    │   ├─ content? → OK
    │   ├─ else → throw error → fallback to OpenAI
    │
    └─ Return: content
```

---

## Environment Configuration Hierarchy

```
┌──────────────────────────────────────────┐
│        PRIORITY 1: Environment Vars      │
│  (set before running ./run-dev.sh)       │
│  - export USE_OLLAMA=true                │
│  - export OLLAMA_BASE_URL=...            │
│  - export OLLAMA_MODEL=mistral           │
└──────────────────────┬───────────────────┘
                       │
                    USED IF
                       │
                       ↓
┌──────────────────────────────────────────┐
│        PRIORITY 2: Code Config           │
│  (passed to constructor)                 │
│  new AIProxyService({                    │
│    useOllama: true,                      │
│    ollamaModel: 'mistral'                │
│  })                                      │
└──────────────────────┬───────────────────┘
                       │
                    USED IF
                       │
                       ↓
┌──────────────────────────────────────────┐
│        PRIORITY 3: Defaults              │
│  (hard-coded fallback)                   │
│  - useOllama = false                     │
│  - ollamaBaseUrl = localhost:11434       │
│  - ollamaModel = mistral                 │
└──────────────────────────────────────────┘
```

---

## Model Selection Flowchart

```
                Choose Model
                    │
        ┌───────────┼────────────┐
        │           │            │
      Speed      Quality      Memory
      needed?    needed?       available?
        │           │            │
        ↓           ↓            ↓
      Need        Best         Budget
      fast?       quality?      limited?
      │           │            │
    YES         YES           YES
      │           │            │
      ↓           ↓            ↓
    phi       mistral        phi 2.7GB
   1-2s       2-3s          (1.6GB)
   2GB         4GB
            (recommended)
      │           │            │
      └───────────┼────────────┘
                  │
        ┌─────────┴─────────┐
        │                   │
      Education:        Alternative:
      mistral (7B)      neural-chat (7B)
      or llama2 (7B)    for mobile-friendly
```

---

## Startup Sequence (run-dev.sh)

```
./run-dev.sh
    │
    ├─ Display header
    │
    ├─ Check Docker available
    │
    ├─ Start Docker services (if needed)
    │   └─ PostgreSQL, Redis, etc.
    │
    ├─ AI Service Configuration Check
    │   ├─ Check OPENAI_API_KEY
    │   ├─ Check WEB_SEARCH_API
    │   ├─ ✅ Display Ollama status
    │   │   ├─ If USE_OLLAMA=true:
    │   │   │   ├─ Show OLLAMA_BASE_URL
    │   │   │   ├─ Show OLLAMA_MODEL
    │   │   │   └─ Show startup instructions
    │   │   └─ If USE_OLLAMA=false:
    │   │       └─ Show "Ollama disabled" message
    │   │
    │   └─ Display summary
    │
    ├─ Start backend services
    │   ├─ Start Fastify on port 3000
    │   ├─ Start AI Proxy service
    │   ├─ Verify services healthy
    │   └─ Show active backend
    │
    └─ Display summary with:
        ├─ All service URLs
        ├─ Logs file location
        ├─ Documentation links
        └─ Active AI backend
```

---

## Integration Points

```
┌─────────────────────────────────────────────────────────┐
│ Frontend (React/TypeScript)                             │
│ - User asks tutoring question                           │
│ - No code changes needed for Ollama                     │
└────────────────────┬────────────────────────────────────┘
                     │
    POST /api/content-studio/ai/generate
                     │
                     ↓
┌─────────────────────────────────────────────────────────┐
│ Backend (Fastify)                                       │
│ - Route handler                                         │
│ - No code changes needed for Ollama                     │
└────────────────────┬────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────┐
│ AIProxyService                                          │
│ ✅ Updated: callLLM() supports Ollama                   │
│ ✅ Updated: Constructor detects env vars                │
│ ✅ Added: callOllama() method                           │
│ ✅ Added: getHealthStatus() method                      │
└────────────────────┬────────────────────────────────────┘
                     │
          ┌──────────┼──────────┬──────────┐
          │          │          │          │
          ↓          ↓          ↓          ↓
      Ollama      OpenAI    WebSearch   Demo
      (NEW)      (exist)    (exist)    (exist)
      Local       Cloud      Search    Stub
      Free        $$         Free      Response
```

---

## Technology Stack

```
Layer 1: Frontend
├─ React 19.2.3
├─ TypeScript
└─ TanStack Query

Layer 2: API Gateway
├─ Fastify (Port 3000)
└─ Prisma 7.2.0 (ORM)

Layer 3: AI Service
├─ AIProxyService (Main controller)
├─ OpenAI Client SDK (fallback)
├─ Web Search Service (fallback)
└─ ✅ Ollama HTTP Client (new)

Layer 4: Backend Providers
├─ Ollama (http://localhost:11434) - LOCAL
├─ OpenAI API (api.openai.com) - CLOUD
├─ DuckDuckGo Search API - WEB
└─ Stub Generator - LOCAL

Layer 5: Data
├─ PostgreSQL (metadata)
├─ SQLite (dev, fallback)
└─ Redis (caching)
```

---

## Error Handling Chain

```
User Request
    │
    ├─ Try Ollama
    │   ├─ Connection error?
    │   │   └─ Log warning, continue
    │   ├─ Model not found?
    │   │   └─ Log error, continue
    │   ├─ Timeout (>5s)?
    │   │   └─ Log error, continue
    │   └─ Success? → Return ✓
    │
    ├─ Try OpenAI
    │   ├─ API key invalid?
    │   │   └─ Log warning, continue
    │   ├─ Rate limit exceeded?
    │   │   └─ Log error, continue
    │   ├─ Network error?
    │   │   └─ Log error, continue
    │   └─ Success? → Return ✓
    │
    ├─ Try Web Search
    │   ├─ No internet?
    │   │   └─ Log warning, continue
    │   ├─ No results?
    │   │   └─ Log debug, continue
    │   └─ Results found? → Return ✓
    │
    └─ Return Demo Response (guaranteed)
        └─ Always succeeds, no errors
```

---

## Files & Structure

```
/products/tutorputor/
├─ services/
│  └─ tutorputor-ai-proxy/
│     ├─ src/
│     │  ├─ service.ts ✅ (Updated: +Ollama support)
│     │  ├─ web-search.ts (Existing: web search)
│     │  └─ index.ts
│     └─ package.json
├─ docs/
│  ├─ OLLAMA_SETUP_GUIDE.md ✅ (NEW)
│  ├─ AI_ENVIRONMENT_VARIABLES.md ✅ (NEW)
│  ├─ OLLAMA_INTEGRATION_COMPLETE.md ✅ (NEW)
│  ├─ OLLAMA_QUICK_START.md ✅ (NEW)
│  ├─ SESSION_OLLAMA_INTEGRATION_SUMMARY.md ✅ (NEW)
│  ├─ OLLAMA_ARCHITECTURE_FLOW.md ✅ (YOU ARE HERE)
│  ├─ AI_SERVICE_DEVELOPMENT_SETUP.md (Existing)
│  ├─ WEB_SEARCH_IMPLEMENTATION.md (Existing)
│  └─ RUN_DEV_QUICK_REFERENCE.md (Existing)
└─ run-dev.sh ✅ (Updated: Ollama config display)
```

---

**Last Updated**: Dec 21, 2025  
**Status**: ✅ Production Ready  
**Architecture**: Ollama → OpenAI → WebSearch → Stub
