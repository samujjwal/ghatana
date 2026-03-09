# YAPPC App Creator

**AI-Native Platform for DevSecOps Workflow Automation**

> **Version:** 0.1.0-alpha  
> **Status:** Active Development  
> **Tech Stack:** React 19, TypeScript, Next.js, Vite, Tailwind CSS

---

## 🚀 Quick Start

```bash
# Install dependencies
pnpm install

# Start development server
pnpm dev:web

# Run tests
pnpm test

# Build for production
pnpm build:web
```

**Development server:** http://localhost:5173

---

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| [Architecture](docs/ARCHITECTURE.md) | System design and technical architecture |
| [Developer Guide](docs/DEVELOPER_GUIDE.md) | Development workflows and best practices |
| [Testing Guide](docs/TESTING_GUIDE.md) | Testing strategy and guidelines |
| [Canvas Feature](docs/features/canvas.md) | Unified canvas documentation |
| [AI Integration](docs/features/ai-integration.md) | AI capabilities and setup |

---

## 🏗️ Architecture Overview

```
app-creator/
├── apps/           # Applications (web, desktop, mobile)
├── libs/           # Shared libraries (35 consolidated modules)
├── packages/       # Build tools and utilities
├── docs/           # Documentation
└── e2e/            # End-to-end tests
```

### Key Technologies

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Frontend** | React 19, TypeScript | UI components and state management |
| **State** | Jotai, TanStack Query | App state and server cache |
| **Styling** | Tailwind CSS | Utility-first CSS framework |
| **Canvas** | ReactFlow, Yjs | Collaborative visual editor |
| **Testing** | Vitest, Playwright | Unit and E2E testing |
| **Build** | Vite, pnpm workspaces | Fast builds and monorepo |
| **AI** | LangChain, OpenAI, Ollama | AI-powered features |

---

## 🎯 Features

### ✅ Completed
- **Unified Canvas** - Collaborative visual workflow editor
- **AI-Powered Agents** - Intelligent code generation and analysis
- **CRDT Collaboration** - Real-time multi-user editing
- **Lifecycle Explorer** - Project lifecycle visualization
- **Design Tokens** - Centralized design system management

### 🚧 In Progress
- **IDE Integration** - Embedded code editor
- **Advanced Layouts** - Responsive layout system
- **Performance Optimization** - Bundle size and runtime improvements

### 📋 Planned
- **Plugin System** - Extensible architecture
- **Cloud Sync** - Cross-device synchronization
- **Advanced Analytics** - Usage metrics and insights

---

## 🧪 Testing

```bash
# Unit tests
pnpm test

# E2E tests
pnpm test:e2e

# Coverage
pnpm test:coverage

# Type checking
pnpm typecheck
```

**Coverage Thresholds:**
- Standard: 70% (enforced)
- Critical paths: 90% (canvas, state, viewport)

---

## 🔧 Development

### Prerequisites
- Node.js 20+
- pnpm 10.26.2+
- Git

### Scripts

| Command | Description |
|---------|-------------|
| `pnpm dev:web` | Start web development server |
| `pnpm build:web` | Build web application |
| `pnpm test` | Run unit tests |
| `pnpm test:e2e` | Run E2E tests |
| `pnpm lint` | Lint codebase |
| `pnpm typecheck` | Type checking |
| `pnpm storybook` | Start Storybook |

### Code Quality

- **ESLint** - Linting with strict rules
- **Prettier** - Code formatting
- **TypeScript** - Strict type checking
- **Dependency Cruiser** - Architecture governance
- **JSCPD** - Duplicate code detection

---

## 📦 Libraries (Consolidated)

**35 core libraries** organized by domain:

### UI & Components
- `@yappc/ui` - Core UI component library
- `@yappc/canvas` - Unified canvas system
- `@yappc/code-editor` - Embedded code editor
- `@yappc/diagram` - Diagram and flowchart components

### State & Data
- `@yappc/store` - Application state management
- `@yappc/crdt` - CRDT collaboration engine
- `@yappc/api` - API client and GraphQL

### AI & Intelligence
- `@yappc/ai-core` - AI infrastructure and models
- `@yappc/ai-ui` - AI user interface components
- `@yappc/agents` - Intelligent agent system

### Development Tools
- `@yappc/design-tokens` - Design system tokens
- `@yappc/testing` - Testing utilities
- `@yappc/auth` - Authentication and authorization

_See [Library Architecture](docs/architecture/libraries.md) for complete list and dependencies._

---

## 🚀 Deployment

### Production Build

```bash
# Build all apps
pnpm build

# Preview production build
pnpm preview:web
```

### Docker

```bash
# Build Docker image
docker build -t yappc-app-creator .

# Run container
docker run -p 5173:5173 yappc-app-creator
```

### Environment Variables

```env
# .env.production
VITE_API_URL=https://api.yappc.com
VITE_AI_PROVIDER=openai
VITE_ENABLE_ANALYTICS=true
```

---

## 🤝 Contributing

1. Read [Developer Guide](docs/DEVELOPER_GUIDE.md)
2. Follow [Code Standards](docs/guidelines/CODING.md)
3. Write tests (coverage required)
4. Submit pull request

**Commit Convention:** [Conventional Commits](https://www.conventionalcommits.org/)

---

## 📊 Project Status

| Metric | Value |
|--------|-------|
| **Code Files** | 3,815 TS/TSX |
| **Libraries** | 35 (consolidated) |
| **Test Coverage** | 70-90% |
| **Bundle Size** | <500KB (main) |
| **Build Time** | <2 min |

---

## 📄 License

Proprietary - Ghatana Platform

---

## 🔗 Links

- [YAPPC Core (Java)](../core/README.md) - Backend services
- [Ghatana Platform](../../../README.md) - Root workspace
- [Data Cloud](../../../products/data-cloud/README.md) - Event platform

---

**Last Updated:** 2026-01-27  
**Maintained by:** YAPPC Engineering Team
