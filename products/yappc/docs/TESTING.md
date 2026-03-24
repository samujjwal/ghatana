# YAPPC Testing Guide

## Overview

YAPPC uses a comprehensive testing strategy:

- **Unit Tests:** Test individual components in isolation
- **Integration Tests:** Test component interactions
- **E2E Tests:** Test complete user workflows
- **Performance Tests:** Test system performance

## Backend Testing

### Unit Tests (JUnit 5)

```java
@Test
void shouldAnalyzeCode() {
    // Given
    CodeAnalysisAgent agent = new CodeAnalysisAgent();
    String code = "public class Example {}";
    
    // When
    Promise<AnalysisResult> result = agent.analyze(code);
    
    // Then
    assertThat(result.getResult().getComplexity()).isLessThan(10);
}
```

### ActiveJ Tests

```java
@ExtendWith(EventloopTestExtension.class)
class AsyncTest {
    @Test
    void shouldHandleAsync(Eventloop eventloop) {
        Promise<String> promise = Promise.of("test");
        
        String result = eventloop.submit(() -> promise).join();
        
        assertThat(result).isEqualTo("test");
    }
}
```

## Frontend Testing

### Component Tests (Vitest + Testing Library)

```typescript
import { render, screen } from '@testing-library/react';
import { Button } from './Button';

test('renders button with text', () => {
  render(<Button>Click me</Button>);
  expect(screen.getByText('Click me')).toBeInTheDocument();
});
```

### Hook Tests

```typescript
import { renderHook } from '@testing-library/react';
import { useCanvasState } from './useCanvasState';

test('manages canvas state', () => {
  const { result } = renderHook(() => useCanvasState());
  
  expect(result.current.nodes).toEqual([]);
});
```

### E2E Tests (Playwright)

```typescript
import { test, expect } from '@playwright/test';

test('user can create project', async ({ page }) => {
  await page.goto('http://localhost:3000');
  await page.click('text=New Project');
  await page.fill('[name="projectName"]', 'Test Project');
  await page.click('text=Create');
  
  await expect(page.locator('text=Test Project')).toBeVisible();
});
```

## Test Coverage

### Backend Coverage Goals

- **Unit Tests:** 80%+ coverage
- **Integration Tests:** Key workflows covered
- **Critical Paths:** 100% coverage

### Frontend Coverage Goals

- **Components:** 80%+ coverage
- **Hooks:** 90%+ coverage
- **Utilities:** 95%+ coverage

## Running Tests

```bash
# Backend
./gradlew test                    # All tests
./gradlew test --tests "*Agent*"  # Specific tests

# Frontend
pnpm test                         # All tests
pnpm test:coverage                # With coverage
pnpm test:e2e                     # E2E tests
```

## Best Practices

1. **Test Behavior, Not Implementation**
2. **Use Descriptive Test Names**
3. **Follow AAA Pattern** (Arrange, Act, Assert)
4. **Mock External Dependencies**
5. **Keep Tests Fast and Isolated**

---

**Coverage Reports:** `build/reports/jacoco` (backend), `coverage/` (frontend)
