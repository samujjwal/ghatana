# Developer Documentation

**Welcome to the Ghatana Developer Platform**

This guide will help you get started with development on the Ghatana platform, including the Agentic Event Processor (AEP) and Data-Cloud products.

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Architecture Overview](#architecture-overview)
3. [Development Environment Setup](#development-environment-setup)
4. [Project Structure](#project-structure)
5. [Coding Standards](#coding-standards)
6. [Testing Guidelines](#testing-guidelines)
7. [Building and Running](#building-and-running)
8. [Contributing](#contributing)
9. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Prerequisites

- **Java 21** (LTS) - [Download](https://adoptium.net/)
- **Node.js 20+** - [Download](https://nodejs.org/)
- **Docker & Docker Compose** - [Download](https://docs.docker.com/get-docker/)
- **Gradle 8.5+** (or use wrapper)
- **Git** - [Download](https://git-scm.com/)

### Quick Start

```bash
# Clone the repository
git clone https://github.com/ghatana/ghatana.git
cd ghatana

# Build the entire project
./gradlew build

# Run tests
./gradlew test

# Start development environment
docker-compose up -d

# Run AEP locally
./gradlew :products:aep:run

# Run Data-Cloud locally
./gradlew :products:data-cloud:run
```

---

## Architecture Overview

### System Architecture

The Ghatana platform consists of two main products:

```
┌─────────────────────────────────────────────────────────────┐
│                      Ghatana Platform                       │
├──────────────────────────────┬──────────────────────────────┤
│   Agentic Event Processor    │      Data-Cloud Platform     │
│          (AEP)               │                              │
│  ┌────────────────────────┐ │  ┌──────────────────────────┐ │
│  │   Pipeline Engine      │ │  │   Entity Management      │ │
│  │   - Operators          │ │  │   - Collections          │ │
│  │   - Event Processing   │ │  │   - Queries              │ │
│  │   - HITL Review        │ │  │   - Storage Tiers        │ │
│  └────────────────────────┘ │  └──────────────────────────┘ │
│  ┌────────────────────────┐ │  ┌──────────────────────────┐ │
│  │   Agent Registry       │ │  │   Plugin System          │ │
│  │   - Custom Agents      │ │  │   - Storage Plugins      │ │
│  │   - Learning Agents    │ │  │   - Analytics Plugins    │ │
│  └────────────────────────┘ │  └──────────────────────────┘ │
├──────────────────────────────┴──────────────────────────────┤
│              Shared Infrastructure                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │
│  │   Kafka     │  │   Redis     │  │   PostgreSQL        │   │
│  │   (Events)  │  │   (Cache)   │  │   (Persistence)     │   │
│  └─────────────┘  └─────────────┘  └─────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Technology Stack

**Backend:**
- Java 21 (Virtual Threads, Pattern Matching)
- Spring Boot 3.2
- ActiveJ (Async I/O)
- Hibernate / JPA
- Kafka (Event Streaming)
- Redis (Caching)
- PostgreSQL (Primary DB)

**Frontend:**
- TypeScript 5.3
- React 18
- React Flow (Canvas)
- Jotai (State Management)
- TailwindCSS
- Playwright (E2E Testing)

**Infrastructure:**
- Kubernetes
- Docker & Docker Compose
- Helm Charts
- Prometheus & Grafana
- Jaeger (Tracing)

---

## Development Environment Setup

### 1. Java Setup

```bash
# Install Java 21 (using SDKMAN)
curl -s "https://get.sdkman.io" | bash
sdk install java 21.0.2-tem
sdk use java 21.0.2-tem

# Verify
java -version
```

### 2. Node.js Setup

```bash
# Install Node.js 20 (using nvm)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
nvm install 20
nvm use 20

# Verify
node -version
npm -version
```

### 3. IDE Configuration

**IntelliJ IDEA (Recommended):**
1. Install IntelliJ IDEA Ultimate or Community
2. Import the project as a Gradle project
3. Install plugins:
   - Lombok
   - Kotlin
   - Docker
   - Database Navigator
   - SonarLint

**VS Code:**
1. Install VS Code
2. Install extensions:
   - Extension Pack for Java
   - Spring Boot Extension Pack
   - TypeScript and JavaScript Language Features
   - ESLint
   - Prettier

### 4. Docker Environment

```bash
# Start all services
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f

# Stop all services
docker-compose down
```

**Services Started:**
- PostgreSQL (port 5432)
- Redis (port 6379)
- Kafka (port 9092)
- Zookeeper (port 2181)

---

## Project Structure

```
ghatana/
├── products/
│   ├── aep/                          # Agentic Event Processor
│   │   ├── api/                      # REST API controllers
│   │   ├── core/                     # Core engine
│   │   ├── operators/                # Operator implementations
│   │   ├── agents/                   # Agent registry
│   │   ├── ui/                       # React frontend
│   │   └── docs/                     # Documentation
│   │
│   └── data-cloud/                   # Data-Cloud Platform
│       ├── platform/                 # Core platform
│       │   ├── src/main/java/
│       │   │   ├── entity/           # Domain entities (102 files)
│       │   │   ├── infrastructure/  # Infrastructure (68 files)
│       │   │   ├── application/     # Application services (63 files)
│       │   │   └── plugins/         # Plugin system
│       │   └── build.gradle.kts
│       └── plugins/                  # Official plugins
│
├── platform/
│   ├── java/                         # Shared Java libraries
│   │   ├── agent-framework/          # Agent framework
│   │   ├── common/                   # Common utilities
│   │   └── persistence/              # Persistence layer
│   │
│   └── typescript/                   # Shared TypeScript libraries
│       ├── ui-components/            # React components
│       └── canvas/                   # Canvas framework
│
├── shared-services/                  # Shared microservices
│   ├── auth-service/                 # Authentication
│   ├── ai-inference-service/         # AI inference
│   └── ai-registry/                  # AI model registry
│
├── docs/                             # Documentation
├── scripts/                          # Automation scripts
├── config/                           # Configuration files
└── buildSrc/                         # Gradle build logic
```

---

## Coding Standards

### Java Standards

**Code Style:**
- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use 4 spaces for indentation
- Maximum line length: 120 characters
- Use `var` for local variables (Java 10+)

**Best Practices:**
```java
// ✅ DO: Use records for DTOs
public record CreatePipelineRequest(
    @NotNull String name,
    @Size(max = 500) String description,
    @NotEmpty List<OperatorConfig> operators
) {}

// ✅ DO: Use Optional for nullable returns
public Optional<Entity> findById(String id) {
    return Optional.ofNullable(entityMap.get(id));
}

// ✅ DO: Use constructor injection
@Service
public class PipelineService {
    private final PipelineRepository repository;
    private final EventPublisher eventPublisher;
    
    public PipelineService(
            PipelineRepository repository,
            EventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }
}

// ❌ DON'T: Use field injection
@Service
public class BadService {
    @Autowired  // Avoid this
    private SomeRepository repository;
}

// ✅ DO: Use Promise.of() pattern for async
public Promise<Entity> save(Entity entity) {
    try {
        entityManager.persist(entity);
        return Promise.of(entity);
    } catch (Exception e) {
        return Promise.ofException(e);
    }
}

// ❌ DON'T: Use Promise.ofBlocking()
// This causes "No reactor in current thread" errors
public Promise<Entity> badSave(Entity entity) {
    return Promise.ofBlocking(executor, () -> {
        entityManager.persist(entity);
        return entity;
    });
}
```

### TypeScript Standards

**Code Style:**
- Use [Prettier](https://prettier.io/) for formatting
- Use [ESLint](https://eslint.org/) for linting
- Follow [Airbnb JavaScript Style Guide](https://github.com/airbnb/javascript)

**Best Practices:**
```typescript
// ✅ DO: Use explicit types
interface Pipeline {
  id: string;
  name: string;
  operators: Operator[];
  status: 'active' | 'paused' | 'archived';
}

// ✅ DO: Use functional components with hooks
const PipelineCard: React.FC<PipelineCardProps> = ({ pipeline, onEdit }) => {
  const [isExpanded, setIsExpanded] = useState(false);
  
  const handleToggle = useCallback(() => {
    setIsExpanded(prev => !prev);
  }, []);
  
  return (
    <div className="pipeline-card">
      <h3>{pipeline.name}</h3>
      <button onClick={handleToggle}>
        {isExpanded ? 'Collapse' : 'Expand'}
      </button>
      {isExpanded && <PipelineDetails pipeline={pipeline} />}
    </div>
  );
};

// ✅ DO: Use custom hooks for reusable logic
const usePipeline = (id: string) => {
  const [pipeline, setPipeline] = useState<Pipeline | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  
  useEffect(() => {
    const fetchPipeline = async () => {
      try {
        const data = await api.getPipeline(id);
        setPipeline(data);
      } catch (err) {
        setError(err as Error);
      } finally {
        setLoading(false);
      }
    };
    
    fetchPipeline();
  }, [id]);
  
  return { pipeline, loading, error };
};
```

### Naming Conventions

| Type | Convention | Example |
|------|-----------|---------|
| Classes | PascalCase | `PipelineService` |
| Interfaces | PascalCase (adjective) | `Serializable`, `Runnable` |
| Methods | camelCase | `processEvent()` |
| Variables | camelCase | `pipelineId` |
| Constants | UPPER_SNAKE_CASE | `MAX_BATCH_SIZE` |
| Enums | PascalCase | `PipelineStatus` |
| Packages | lowercase | `com.ghatana.aep.pipeline` |
| Files | PascalCase | `PipelineCard.tsx` |
| CSS Classes | kebab-case | `pipeline-card` |

---

## Testing Guidelines

### Unit Tests (Java)

```java
@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {
    
    @Mock
    private PipelineRepository repository;
    
    @Mock
    private EventPublisher eventPublisher;
    
    @InjectMocks
    private PipelineService service;
    
    @Test
    void shouldCreatePipeline() {
        // Given
        CreatePipelineRequest request = new CreatePipelineRequest(
            "Test Pipeline",
            "Description",
            List.of()
        );
        
        // When
        Pipeline result = service.create(request);
        
        // Then
        assertThat(result.getName()).isEqualTo("Test Pipeline");
        verify(repository).save(any(Pipeline.class));
        verify(eventPublisher).publish(any(PipelineCreatedEvent.class));
    }
    
    @Test
    void shouldThrowExceptionWhenNameIsEmpty() {
        // Given
        CreatePipelineRequest request = new CreatePipelineRequest(
            "",
            "Description",
            List.of()
        );
        
        // When & Then
        assertThrows(ValidationException.class, () -> {
            service.create(request);
        });
    }
}
```

### Integration Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
class PipelineControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private PipelineRepository repository;
    
    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }
    
    @Test
    void shouldCreatePipelineViaApi() throws Exception {
        // Given
        String request = """
            {
                "name": "Test Pipeline",
                "description": "Test",
                "operators": []
            }
            """;
        
        // When & Then
        mockMvc.perform(post("/api/pipelines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Test Pipeline"));
    }
}
```

### E2E Tests (Playwright)

```typescript
import { test, expect } from '@playwright/test';

test.describe('Pipeline Management', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/pipelines');
    await page.waitForLoadState('networkidle');
  });

  test('should create new pipeline', async ({ page }) => {
    // Click create button
    await page.click('[data-testid="create-pipeline-btn"]');
    
    // Fill form
    await page.fill('[data-testid="pipeline-name"]', 'Test Pipeline');
    await page.fill('[data-testid="pipeline-description"]', 'Test Description');
    
    // Submit
    await page.click('[data-testid="submit-btn"]');
    
    // Verify
    await expect(page.locator('.success-message')).toBeVisible();
    await expect(page.locator('[data-testid="pipeline-list"]')).toContainText('Test Pipeline');
  });

  test('should execute pipeline', async ({ page }) => {
    // Create pipeline first
    await createTestPipeline(page);
    
    // Execute
    await page.click('[data-testid="execute-btn"]');
    
    // Verify execution started
    await expect(page.locator('[data-testid="execution-status"]')).toHaveText('Running');
    
    // Wait for completion
    await expect(page.locator('[data-testid="execution-status"]')).toHaveText('Completed', {
      timeout: 30000
    });
  });
});
```

### Test Coverage Requirements

- **AEP**: 85% unit test coverage, 60% E2E coverage
- **Data-Cloud**: 70% instruction coverage, 60% branch coverage

---

## Building and Running

### Build Commands

```bash
# Build entire project
./gradlew build

# Build specific product
./gradlew :products:aep:build
./gradlew :products:data-cloud:build

# Clean build
./gradlew clean build

# Build without tests (faster)
./gradlew build -x test

# Build Docker images
./gradlew bootBuildImage
```

### Run Commands

```bash
# Run AEP
./gradlew :products:aep:bootRun

# Run Data-Cloud
./gradlew :products:data-cloud:bootRun

# Run with specific profile
./gradlew :products:aep:bootRun --args='--spring.profiles.active=dev'

# Run UI development server
cd products/aep/ui
npm install
npm run dev
```

### Testing Commands

```bash
# Run all tests
./gradlew test

# Run tests for specific product
./gradlew :products:aep:test

# Run integration tests
./gradlew integrationTest

# Run E2E tests
cd products/aep/ui
npm run test:e2e

# Run with coverage
./gradlew test jacocoTestReport

# View coverage report
open products/aep/build/reports/jacoco/test/html/index.html
```

---

## Contributing

### Workflow

1. **Fork & Clone**
   ```bash
   git clone https://github.com/ghatana/ghatana.git
   cd ghatana
   ```

2. **Create Branch**
   ```bash
   git checkout -b feature/my-feature
   # or
   git checkout -b fix/bug-description
   ```

3. **Make Changes**
   - Write code following coding standards
   - Add tests
   - Update documentation

4. **Commit**
   ```bash
   git add .
   git commit -m "feat: add new feature
   
   - Detailed description of changes
   - Another point if needed"
   ```

5. **Push & PR**
   ```bash
   git push origin feature/my-feature
   # Then create PR via GitHub UI
   ```

### Commit Message Convention

Format: `<type>(<scope>): <subject>`

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `style`: Formatting
- `refactor`: Code refactoring
- `test`: Tests
- `chore`: Build/CI

Examples:
```
feat(pipeline): add support for conditional operators
fix(agent): resolve connection timeout issue
docs(api): update authentication examples
test(e2e): add pipeline execution tests
```

### Code Review Process

1. All code must be reviewed before merging
2. Address all review comments
3. Ensure CI passes (tests, linting, security scans)
4. Squash commits if requested
5. Merge only when approved by 2+ reviewers

---

## Troubleshooting

### Common Issues

**Build Fails with "Out of Memory"**
```bash
# Increase Gradle heap size
export GRADLE_OPTS="-Xmx4g -XX:MaxMetaspaceSize=512m"
./gradlew build
```

**Tests Fail with "No reactor in current thread"**
- This indicates Promise.ofBlocking() is being used
- Replace with Promise.of() pattern
- See: `docs/patterns/async-programming.md`

**Database Connection Issues**
```bash
# Reset database
docker-compose down -v
docker-compose up -d postgres
./gradlew :products:data-cloud:flywayMigrate
```

**Port Already in Use**
```bash
# Find and kill process
lsof -ti:8080 | xargs kill -9
# or use different port
./gradlew bootRun --args='--server.port=8081'
```

**Cache Issues**
```bash
# Clear all caches
./gradlew clean
rm -rf ~/.gradle/caches
npm cache clean --force
```

### Getting Help

- **Slack**: #developer-support
- **Email**: dev-support@ghatana.com
- **Issues**: https://github.com/ghatana/ghatana/issues
- **Documentation**: https://docs.ghatana.com

### Debugging Tips

**Enable Debug Logging:**
```yaml
# application-dev.yml
logging:
  level:
    com.ghatana: DEBUG
    org.springframework: INFO
```

**Remote Debugging:**
```bash
./gradlew bootRun --args='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005'
# Then attach IDE to port 5005
```

---

## Resources

### Documentation
- [AEP Operational Runbook](products/aep/docs/OPERATIONAL_RUNBOOK.md)
- [AEP API Documentation](products/aep/docs/API_DOCUMENTATION.md)
- [Architecture Decision Records](docs/adr/)

### Tools
- [SonarQube Dashboard](https://sonar.ghatana.com)
- [Grafana Monitoring](https://grafana.ghatana.com)
- [Jaeger Tracing](https://jaeger.ghatana.com)

### External Resources
- [ActiveJ Documentation](https://activej.io/)
- [Spring Boot Guide](https://spring.io/guides/gs/spring-boot/)
- [React Documentation](https://react.dev/)

---

**Happy Coding!** 🚀

For updates to this guide, please submit a PR.

**Last Updated**: March 19, 2026
