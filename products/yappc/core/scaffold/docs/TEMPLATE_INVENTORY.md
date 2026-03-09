# Template Inventory - Missing Templates Analysis

## Current Status
- **Total existing .hbs files:** 395
- **Target from plan:** ~530+ templates
- **Missing:** ~135-140 templates

## Analysis by Pack Category

### Backend Packs - Missing Templates

#### go-service-gin (partially complete)
Missing:
- [ ] .dockerignore.hbs ✅ CREATED
- [ ] internal/middleware/cors.go.hbs ✅ CREATED
- [ ] internal/middleware/logger.go.hbs ✅ CREATED
- [ ] internal/middleware/recovery.go.hbs ✅ CREATED
- [ ] internal/middleware/metrics.go.hbs ✅ CREATED
- [ ] internal/model/response.go.hbs ✅ CREATED
- [ ] internal/service/health.go.hbs ✅ CREATED
- [ ] tests/handler_test.go.hbs
- [ ] .golangci.yml.hbs

#### go-service-chi (partially complete)
Missing:
- [ ] .dockerignore.hbs
- [ ] internal/middleware/cors.go.hbs ✅ CREATED
- [ ] internal/middleware/logger.go.hbs ✅ CREATED
- [ ] internal/middleware/recovery.go.hbs ✅ CREATED
- [ ] internal/middleware/metrics.go.hbs
- [ ] internal/model/response.go.hbs ✅ CREATED
- [ ] internal/service/health.go.hbs
- [ ] tests/handler_test.go.hbs
- [ ] .golangci.yml.hbs

#### python-service-fastapi (sparse)
Missing:
- [ ] README.md.hbs ✅ CREATED
- [ ] Makefile.hbs ✅ CREATED
- [ ] .gitignore.hbs ✅ CREATED
- [ ] .dockerignore.hbs
- [ ] docker-compose.yml.hbs
- [ ] app/middleware/cors.py.hbs
- [ ] app/middleware/logging.py.hbs
- [ ] app/models/response.py.hbs
- [ ] app/routers/health.py.hbs
- [ ] app/routers/api.py.hbs
- [ ] tests/test_main.py.hbs
- [ ] tests/conftest.py.hbs
- [ ] ruff.toml.hbs
- [ ] mypy.ini.hbs
- [ ] pytest.ini.hbs

#### rust-service-actix (very sparse - only 3 files!)
Missing:
- [ ] README.md.hbs
- [ ] Dockerfile.hbs
- [ ] .dockerignore.hbs
- [ ] docker-compose.yml.hbs
- [ ] .gitignore.hbs
- [ ] clippy.toml.hbs
- [ ] rustfmt.toml.hbs
- [ ] src/config.rs.hbs
- [ ] src/middleware/cors.rs.hbs
- [ ] src/middleware/logger.rs.hbs
- [ ] src/models/response.rs.hbs
- [ ] src/routes/health.rs.hbs
- [ ] src/routes/api.rs.hbs
- [ ] tests/integration_test.rs.hbs

#### dotnet-api-minimal (very sparse - only 4 files!)
Missing:
- [ ] README.md.hbs
- [ ] Dockerfile.hbs
- [ ] .dockerignore.hbs
- [ ] docker-compose.yml.hbs
- [ ] .gitignore.hbs
- [ ] Makefile.hbs
- [ ] Controllers/HealthController.cs.hbs
- [ ] Middleware/CorsMiddleware.cs.hbs
- [ ] Middleware/LoggingMiddleware.cs.hbs
- [ ] Models/ApiResponse.cs.hbs
- [ ] Services/HealthService.cs.hbs
- [ ] Tests/{{projectName}}.Tests.csproj.hbs
- [ ] Tests/HealthControllerTests.cs.hbs
- [ ] .editorconfig.hbs

### Frontend Packs - Missing Templates

#### vue-vite (very sparse - only 4 files!)
Missing:
- [ ] README.md.hbs
- [ ] Dockerfile.hbs
- [ ] .dockerignore.hbs
- [ ] docker-compose.yml.hbs
- [ ] .gitignore.hbs
- [ ] Makefile.hbs
- [ ] nginx.conf.hbs
- [ ] tsconfig.node.json.hbs
- [ ] vitest.config.ts.hbs
- [ ] src/App.vue.hbs
- [ ] src/main.ts.hbs
- [ ] src/router/index.ts.hbs
- [ ] src/stores/counter.ts.hbs
- [ ] src/components/HelloWorld.vue.hbs
- [ ] src/assets/main.css.hbs
- [ ] tests/unit/example.spec.ts.hbs

#### ts-react-nextjs (sparse)
Missing:
- [ ] README.md.hbs
- [ ] Makefile.hbs
- [ ] .gitignore.hbs
- [ ] .dockerignore.hbs
- [ ] docker-compose.yml.hbs
- [ ] .eslintrc.json.hbs
- [ ] next-env.d.ts.hbs
- [ ] public/favicon.ico
- [ ] app/layout.tsx.hbs
- [ ] app/page.tsx.hbs
- [ ] app/api/health/route.ts.hbs
- [ ] components/Header.tsx.hbs
- [ ] lib/utils.ts.hbs
- [ ] tests/page.test.tsx.hbs

### Infrastructure Packs - Missing Templates

#### k8s-manifests (very sparse!)
Missing:
- [ ] README.md.hbs
- [ ] Makefile.hbs
- [ ] k8s/namespace.yaml.hbs
- [ ] k8s/deployment.yaml.hbs
- [ ] k8s/service.yaml.hbs
- [ ] k8s/ingress.yaml.hbs
- [ ] k8s/configmap.yaml.hbs
- [ ] k8s/secret.yaml.hbs
- [ ] k8s/hpa.yaml.hbs
- [ ] k8s/pdb.yaml.hbs
- [ ] k8s/networkpolicy.yaml.hbs
- [ ] k8s/serviceaccount.yaml.hbs
- [ ] k8s/rbac.yaml.hbs
- [ ] helm/Chart.yaml.hbs
- [ ] helm/values.yaml.hbs
- [ ] helm/templates/_helpers.tpl.hbs

#### github-actions (empty!)
Missing:
- [ ] README.md.hbs
- [ ] .github/workflows/ci.yml.hbs
- [ ] .github/workflows/cd.yml.hbs
- [ ] .github/workflows/pr.yml.hbs
- [ ] .github/workflows/release.yml.hbs
- [ ] .github/dependabot.yml.hbs

### New Packs Needed (Don't Exist Yet)

#### python-library (NEW PACK)
- [ ] All templates needed

#### python-cli (NEW PACK)
- [ ] All templates needed

#### rust-cli (NEW PACK)
- [ ] All templates needed

#### go-cli (NEW PACK)
- [ ] All templates needed

#### dotnet-library (NEW PACK)
- [ ] All templates needed

#### cpp-library-cmake (NEW PACK)
- [ ] All templates needed

#### ts-node-express (NEW PACK)
- [ ] All templates needed

#### svelte-vite (NEW PACK)
- [ ] All templates needed

#### terraform-infra (NEW PACK)
- [ ] All templates needed

#### gitlab-ci (NEW PACK)
- [ ] All templates needed

## Summary

**Existing Sparse Packs to Complete:**
- rust-service-actix: ~14 files needed
- dotnet-api-minimal: ~14 files needed
- vue-vite: ~16 files needed
- python-service-fastapi: ~15 files needed
- k8s-manifests: ~16 files needed
- github-actions: ~6 files needed
- go-service-gin: ~2 files needed
- go-service-chi: ~4 files needed
- ts-react-nextjs: ~14 files needed

**New Packs to Create:** ~10 packs × ~15 files each = ~150 files

**Total Missing:** ~250 template files

## Action Plan
1. Complete sparse existing packs first (~100 files)
2. Create new packs second (~150 files)
3. Total delivery: ~250 new template files
