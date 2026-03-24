# Refactoring System Documentation

## Overview

YAPPC's refactoring system provides automated code transformations.

## Architecture

```
refactorer/
├── api/    # Refactoring API
└── engine/ # AST transformation engine
```

## Supported Refactorings

- Extract Method
- Rename Symbol
- Move Class
- Inline Variable
- Extract Interface

## Usage

```java
RefactoringEngine engine = new RefactoringEngine();

RefactoringRequest request = RefactoringRequest.builder()
    .type(RefactoringType.EXTRACT_METHOD)
    .sourceFile(file)
    .selection(selection)
    .build();

Promise<RefactoringResult> result = engine.refactor(request);
```

---

See [Refactorer API](../api/refactorer-api.md) for complete API reference.
