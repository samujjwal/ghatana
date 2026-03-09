# DCMaar – Guardian App – Coding Guidelines

## 1. Scope

These guidelines apply to the Guardian umbrella repo `@dcmaar/guardian` and its apps/backend/libs under this product.

## 2. Layering & Workspaces

- Keep app-specific logic in `apps/*` and shared logic in `packages/*` / `libs/*`.
- Backend services belong under `backend` with clear API and domain boundaries.

## 3. Scripts & Tooling

- Use the provided scripts (`build.sh`, `deploy.sh`) and workspace commands for build/test/lint/type-check.
- Follow DCMaar-wide linting and formatting rules.

## 4. Code Quality and Formatting

- Use TypeScript for new frontend/shared code; follow the repo `tsconfig` and `eslint` rules.
- Run the formatter and linters before committing:

```bash
pnpm -w run lint
pnpm -w run format
```

- Commit message guidelines: Use Conventional Commits (type(scope): subject). Example:

```
feat(extension): add time-windowed DNR rules for blocking
```

## 5. Pull Request Checklist

- Include a short description and the affected modules.
- Reference any relevant contracts or proto changes.
- Ensure unit and integration tests are included for new behavior.
- Verify `pnpm build` completes for modified packages.

## 6. Security and Secrets

- Do not commit secrets or `.env` files to the repository.
- Use `.env.example` for templates and CI secrets management for runtime secrets.

This document now includes actionable rules for code quality and PR reviews.
