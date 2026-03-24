# YAPPC Quick Start Guide

Get started with YAPPC in 5 minutes.

## 1. Prerequisites

- Java 21+
- Node.js 20+
- Docker

## 2. Installation

```bash
git clone https://github.com/ghatana/ghatana.git
cd ghatana/products/yappc
```

## 3. Start Services

```bash
# Start infrastructure
./start-infra.sh

# Start backend
./gradlew :services:run

# Start frontend (new terminal)
cd frontend
pnpm install
pnpm dev
```

## 4. Access YAPPC

Open http://localhost:3000

## 5. Create Your First Project

1. Click "New Project"
2. Select framework (React, Java, etc.)
3. Enter project name
4. Click "Generate"

Done! Your project is ready.

## Next Steps

- [AI Workflows](ai-workflows.md)
- [Canvas Guide](canvas-guide.md)
- [Development Guide](../DEVELOPMENT.md)
