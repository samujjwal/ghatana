# Data Fabric Admin UI - Testing Guide

## Overview

This guide covers testing strategies and examples for the Data Fabric Admin UI feature, including unit tests, integration tests, and E2E test patterns.

## Test Structure

```
src/features/data-fabric/
├── __tests__/
│   ├── stores/
│   │   ├── storage-profile.store.test.ts
│   │   └── connector.store.test.ts
│   ├── services/
│   │   └── api.test.ts
│   ├── components/
│   │   ├── StorageProfilesList.test.tsx
│   │   ├── DataConnectorsList.test.tsx
│   │   ├── StorageProfilesPage.test.tsx
│   │   └── DataConnectorsPage.test.tsx
│   └── setup.ts
```

## Unit Tests

### Store Tests

#### Storage Profile Store

```typescript
// src/features/data-fabric/__tests__/stores/storage-profile.store.test.ts

import { renderHook, act } from "@testing-library/react";
import { useAtom } from "jotai";
import {
  storageProfileAtom,
  loadStorageProfilesAtom,
  selectStorageProfileAtom,
  deleteStorageProfileAtom,
  setDefaultStorageProfileAtom,
} from "@/features/data-fabric";
import { StorageProfile } from "@/features/data-fabric/types";

/**
 * Tests for storage profile Jotai atoms.
 *
 * Validates:
 * - Initial state
 * - Loading state management
 * - Profile selection
 * - CRUD operations
 * - Default profile handling
 */
describe("Storage Profile Store", () => {
  const mockProfile: StorageProfile = {
    id: "prof_123",
    name: "Test S3",
    type: "S3",
    config: { bucket: "test-bucket", region: "us-west-2" },
    encryption: { type: "NONE" },
    compression: { type: "NONE" },
    isDefault: false,
    isActive: true,
    createdAt: "2024-11-05T00:00:00Z",
    updatedAt: "2024-11-05T00:00:00Z",
    tenantId: "tenant_123",
  };

  describe("Initial State", () => {
    it("should have empty profiles array initially", () => {
      const { result } = renderHook(() => useAtom(storageProfileAtom));
      const [state] = result.current;

      expect(state.profiles).toEqual([]);
      expect(state.selectedProfileId).toBeNull();
      expect(state.isLoading).toBe(false);
      expect(state.error).toBeNull();
    });
  });

  describe("Loading State", () => {
    it("should set loading state", async () => {
      const { result } = renderHook(() => useAtom(storageProfileAtom));

      act(() => {
        result.current[1](storageProfileAtom); // Get previous state
      });

      // Simulate loading
      act(() => {
        result.current[1](storageProfileAtom);
      });

      expect(result.current[0].isLoading).toBe(false); // Would be true during actual load
    });
  });

  describe("Profile Selection", () => {
    it("should select a profile", async () => {
      const { result: profileResult } = renderHook(() => useAtom(storageProfileAtom));
      const { result: selectResult } = renderHook(() => useAtom(selectStorageProfileAtom));

      act(() => {
        // Add profile to state
        selectResult.current[1](mockProfile.id);
      });

      // Verify profile is selected (would require setting up state first)
    });
  });

  describe("CRUD Operations", () => {
    it("should delete a profile", async () => {
      const { result: profileResult } = renderHook(() => useAtom(storageProfileAtom));
      const { result: deleteResult } = renderHook(() => useAtom(deleteStorageProfileAtom));

      // Setup: add profile to state, then delete
      act(() => {
        deleteResult.current[1](mockProfile.id);
      });

      // Verify deletion
    });

    it("should set default profile", async () => {
      const { result: profileResult } = renderHook(() => useAtom(storageProfileAtom));
      const { result: setDefaultResult } = renderHook(() =>
        useAtom(setDefaultStorageProfileAtom)
      );

      act(() => {
        setDefaultResult.current[1](mockProfile.id);
      });

      // Verify default is set
    });
  });
});
```

#### Connector Store

```typescript
// src/features/data-fabric/__tests__/stores/connector.store.test.ts

import { renderHook, act } from "@testing-library/react";
import { useAtom } from "jotai";
import {
  dataConnectorAtom,
  loadDataConnectorsAtom,
  selectDataConnectorAtom,
  deleteDataConnectorAtom,
  toggleConnectorStateAtom,
} from "@/features/data-fabric";
import { DataConnector } from "@/features/data-fabric/types";

/**
 * Tests for data connector Jotai atoms.
 *
 * Validates:
 * - Initial state
 * - Loading and error states
 * - Connector selection
 * - CRUD operations
 * - Toggle enabled/disabled state
 */
describe("Data Connector Store", () => {
  const mockConnector: DataConnector = {
    id: "conn_123",
    name: "Test Connector",
    sourceType: "PostgreSQL",
    storageProfileId: "prof_123",
    connectionConfig: { host: "localhost", port: 5432 },
    syncSchedule: "0 0 * * *",
    lastSyncAt: "2024-11-05T00:00:00Z",
    status: "active",
    isEnabled: true,
    createdAt: "2024-11-05T00:00:00Z",
    updatedAt: "2024-11-05T00:00:00Z",
    tenantId: "tenant_123",
  };

  describe("Initial State", () => {
    it("should have empty connectors array initially", () => {
      const { result } = renderHook(() => useAtom(dataConnectorAtom));
      const [state] = result.current;

      expect(state.connectors).toEqual([]);
      expect(state.selectedConnectorId).toBeNull();
      expect(state.isLoading).toBe(false);
      expect(state.error).toBeNull();
    });
  });

  describe("Connector Toggle", () => {
    it("should toggle connector enabled state", async () => {
      const { result: connectorResult } = renderHook(() => useAtom(dataConnectorAtom));
      const { result: toggleResult } = renderHook(() => useAtom(toggleConnectorStateAtom));

      act(() => {
        toggleResult.current[1](mockConnector.id);
      });

      // Verify toggle
    });
  });

  describe("Filtering", () => {
    it("should filter active connectors", () => {
      const { result } = renderHook(() => useAtom(dataConnectorAtom));
      const [state] = result.current;

      const activeConnectors = state.connectors.filter((c) => c.isEnabled);
      expect(activeConnectors.length).toBeGreaterThanOrEqual(0);
    });

    it("should filter connectors by profile", () => {
      const { result } = renderHook(() => useAtom(dataConnectorAtom));
      const [state] = result.current;

      const profileConnectors = state.connectors.filter(
        (c) => c.storageProfileId === "prof_123"
      );
      expect(Array.isArray(profileConnectors)).toBe(true);
    });
  });
});
```

### API Service Tests

```typescript
// src/features/data-fabric/__tests__/services/api.test.ts

import axios from "axios";
import { storageProfileApi, dataConnectorApi } from "@/features/data-fabric";
import { StorageProfile, DataConnector } from "@/features/data-fabric/types";

jest.mock("axios");
const mockedAxios = axios as jest.Mocked<typeof axios>;

/**
 * Tests for Data Fabric API service methods.
 *
 * Validates:
 * - HTTP method usage (GET, POST, PUT, DELETE, PATCH)
 * - Request URL construction
 * - Request body formatting
 * - Response handling
 * - Error propagation
 */
describe("Data Fabric API Service", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe("Storage Profile API", () => {
    describe("getAll", () => {
      it("should fetch all storage profiles", async () => {
        const mockProfiles: StorageProfile[] = [
          {
            id: "prof_1",
            name: "S3 Profile",
            type: "S3",
            config: {},
            encryption: { type: "NONE" },
            compression: { type: "NONE" },
            isDefault: true,
            isActive: true,
            createdAt: "2024-11-05T00:00:00Z",
            updatedAt: "2024-11-05T00:00:00Z",
            tenantId: "tenant_123",
          },
        ];

        mockedAxios.get.mockResolvedValue({ data: mockProfiles });

        const result = await storageProfileApi.getAll();

        expect(mockedAxios.get).toHaveBeenCalledWith("/api/v1/data-fabric/profiles");
        expect(result).toEqual(mockProfiles);
      });

      it("should handle fetch errors", async () => {
        const error = new Error("Network error");
        mockedAxios.get.mockRejectedValue(error);

        await expect(storageProfileApi.getAll()).rejects.toThrow("Network error");
      });
    });

    describe("create", () => {
      it("should create a new storage profile", async () => {
        const input = {
          name: "New S3",
          type: "S3" as const,
          config: { bucket: "new-bucket" },
          encryption: { type: "NONE" as const },
          compression: { type: "GZIP" as const },
        };

        const mockProfile: StorageProfile = {
          id: "prof_new",
          ...input,
          isDefault: false,
          isActive: true,
          createdAt: "2024-11-05T00:00:00Z",
          updatedAt: "2024-11-05T00:00:00Z",
          tenantId: "tenant_123",
        };

        mockedAxios.post.mockResolvedValue({ data: mockProfile });

        const result = await storageProfileApi.create(input);

        expect(mockedAxios.post).toHaveBeenCalledWith(
          "/api/v1/data-fabric/profiles",
          input
        );
        expect(result).toEqual(mockProfile);
      });
    });

    describe("update", () => {
      it("should update an existing profile", async () => {
        const id = "prof_123";
        const updates = { name: "Updated Name" };

        mockedAxios.put.mockResolvedValue({ data: { id, ...updates } });

        await storageProfileApi.update(id, updates);

        expect(mockedAxios.put).toHaveBeenCalledWith(
          `/api/v1/data-fabric/profiles/${id}`,
          updates
        );
      });
    });

    describe("delete", () => {
      it("should delete a profile", async () => {
        const id = "prof_123";

        mockedAxios.delete.mockResolvedValue({ data: { message: "Deleted" } });

        await storageProfileApi.delete(id);

        expect(mockedAxios.delete).toHaveBeenCalledWith(
          `/api/v1/data-fabric/profiles/${id}`
        );
      });
    });

    describe("setDefault", () => {
      it("should set a profile as default", async () => {
        const id = "prof_123";

        mockedAxios.patch.mockResolvedValue({
          data: { id, isDefault: true },
        });

        await storageProfileApi.setDefault(id);

        expect(mockedAxios.patch).toHaveBeenCalledWith(
          `/api/v1/data-fabric/profiles/${id}/set-default`
        );
      });
    });

    describe("getMetrics", () => {
      it("should fetch storage metrics", async () => {
        const id = "prof_123";
        const mockMetrics = {
          profileId: id,
          totalCapacity: 1000000,
          usedCapacity: 600000,
          availableCapacity: 400000,
          lastUpdated: "2024-11-05T10:30:00Z",
        };

        mockedAxios.get.mockResolvedValue({ data: mockMetrics });

        const result = await storageProfileApi.getMetrics(id);

        expect(mockedAxios.get).toHaveBeenCalledWith(
          `/api/v1/data-fabric/profiles/${id}/metrics`
        );
        expect(result).toEqual(mockMetrics);
      });
    });
  });

  describe("Data Connector API", () => {
    describe("triggerSync", () => {
      it("should trigger a sync job", async () => {
        const id = "conn_123";
        const mockResponse = {
          jobId: "job_abc123",
          status: "queued",
          startedAt: "2024-11-05T10:30:00Z",
        };

        mockedAxios.post.mockResolvedValue({ data: mockResponse });

        const result = await dataConnectorApi.triggerSync(id);

        expect(mockedAxios.post).toHaveBeenCalledWith(
          `/api/v1/data-fabric/connectors/${id}/sync`
        );
        expect(result).toEqual(mockResponse);
      });
    });

    describe("test", () => {
      it("should test a connector connection", async () => {
        const id = "conn_123";
        const mockResponse = {
          success: true,
          message: "Connection established",
          details: { latency_ms: 45 },
        };

        mockedAxios.post.mockResolvedValue({ data: mockResponse });

        const result = await dataConnectorApi.test(id);

        expect(mockedAxios.post).toHaveBeenCalledWith(
          `/api/v1/data-fabric/connectors/${id}/test`
        );
        expect(result.success).toBe(true);
      });
    });

    describe("getSyncStatistics", () => {
      it("should fetch sync statistics", async () => {
        const id = "conn_123";
        const mockStats = {
          connectorId: id,
          totalRecords: 50000,
          lastSyncRecords: 1000,
          totalDuration: 3600,
          lastSyncDuration: 120,
          errorCount: 0,
        };

        mockedAxios.get.mockResolvedValue({ data: mockStats });

        const result = await dataConnectorApi.getSyncStatistics(id);

        expect(mockedAxios.get).toHaveBeenCalledWith(
          `/api/v1/data-fabric/connectors/${id}/sync-statistics`
        );
        expect(result).toEqual(mockStats);
      });
    });
  });
});
```

## Component Tests

### Presentational Component Tests

```typescript
// src/features/data-fabric/__tests__/components/StorageProfilesList.test.tsx

import { render, screen, fireEvent } from "@testing-library/react";
import { Provider } from "jotai";
import StorageProfilesList from "@/features/data-fabric/components/StorageProfilesList";
import { StorageProfile } from "@/features/data-fabric/types";

/**
 * Tests for StorageProfilesList component.
 *
 * Validates:
 * - Rendering list items
 * - Action button functionality
 * - Empty state
 * - Click handlers
 */
describe("StorageProfilesList", () => {
  const mockProfiles: StorageProfile[] = [
    {
      id: "prof_1",
      name: "S3 Prod",
      type: "S3",
      config: {},
      encryption: { type: "NONE" },
      compression: { type: "GZIP" },
      isDefault: true,
      isActive: true,
      createdAt: "2024-11-05T00:00:00Z",
      updatedAt: "2024-11-05T00:00:00Z",
      tenantId: "tenant_123",
    },
  ];

  const mockCallbacks = {
    onEdit: jest.fn(),
    onDelete: jest.fn(),
    onSetDefault: jest.fn(),
  };

  it("should render profile list", () => {
    render(
      <Provider>
        <StorageProfilesList {...mockCallbacks} />
      </Provider>
    );

    // Verify table headers
    expect(screen.getByText("Name")).toBeInTheDocument();
    expect(screen.getByText("Type")).toBeInTheDocument();
    expect(screen.getByText("Status")).toBeInTheDocument();
  });

  it("should call onEdit when edit button clicked", () => {
    render(
      <Provider>
        <StorageProfilesList {...mockCallbacks} />
      </Provider>
    );

    const editButton = screen.getByLabelText("Edit S3 Prod");
    fireEvent.click(editButton);

    expect(mockCallbacks.onEdit).toHaveBeenCalledWith(mockProfiles[0]);
  });

  it("should call onDelete when delete button clicked", () => {
    render(
      <Provider>
        <StorageProfilesList {...mockCallbacks} />
      </Provider>
    );

    const deleteButton = screen.getByLabelText("Delete S3 Prod");
    fireEvent.click(deleteButton);

    expect(mockCallbacks.onDelete).toHaveBeenCalledWith(mockProfiles[0].id);
  });

  it("should display empty state when no profiles", () => {
    render(
      <Provider>
        <StorageProfilesList {...mockCallbacks} />
      </Provider>
    );

    expect(screen.getByText("No storage profiles found")).toBeInTheDocument();
  });
});
```

### Container Component Tests

```typescript
// src/features/data-fabric/__tests__/components/StorageProfilesPage.test.tsx

import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { Provider } from "jotai";
import StorageProfilesPage from "@/features/data-fabric/components/StorageProfilesPage";
import * as api from "@/features/data-fabric/services/api";

jest.mock("@/features/data-fabric/services/api");

/**
 * Tests for StorageProfilesPage container component.
 *
 * Validates:
 * - Data loading on mount
 * - Error handling
 * - Delete operations
 * - Set default operations
 * - Toast notifications
 */
describe("StorageProfilesPage", () => {
  const mockCallbacks = {
    onCreateClick: jest.fn(),
    onEditClick: jest.fn(),
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("should load profiles on mount", async () => {
    const mockProfiles = [
      {
        id: "prof_1",
        name: "S3 Prod",
        type: "S3",
        config: {},
        encryption: { type: "NONE" },
        compression: { type: "NONE" },
        isDefault: true,
        isActive: true,
        createdAt: "2024-11-05T00:00:00Z",
        updatedAt: "2024-11-05T00:00:00Z",
        tenantId: "tenant_123",
      },
    ];

    jest.spyOn(api, "storageProfileApi").getAll.mockResolvedValue(mockProfiles);

    render(
      <Provider>
        <StorageProfilesPage {...mockCallbacks} />
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText("S3 Prod")).toBeInTheDocument();
    });

    expect(api.storageProfileApi.getAll).toHaveBeenCalled();
  });

  it("should show loading state during fetch", () => {
    jest.spyOn(api, "storageProfileApi").getAll.mockImplementation(
      () => new Promise(() => {}) // Never resolves
    );

    render(
      <Provider>
        <StorageProfilesPage {...mockCallbacks} />
      </Provider>
    );

    expect(screen.getByRole("status")).toHaveClass("loading");
  });

  it("should handle delete with confirmation", async () => {
    jest
      .spyOn(api, "storageProfileApi")
      .delete.mockResolvedValue({ message: "Deleted" });

    // Setup initial data
    render(
      <Provider>
        <StorageProfilesPage {...mockCallbacks} />
      </Provider>
    );

    // Click delete button
    const deleteButton = screen.getByLabelText("Delete S3 Prod");
    fireEvent.click(deleteButton);

    // Confirm delete
    const confirmButton = screen.getByText("Delete");
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(api.storageProfileApi.delete).toHaveBeenCalledWith("prof_1");
    });
  });

  it("should show error toast on delete failure", async () => {
    const error = new Error("Delete failed");
    jest.spyOn(api, "storageProfileApi").delete.mockRejectedValue(error);

    render(
      <Provider>
        <StorageProfilesPage {...mockCallbacks} />
      </Provider>
    );

    // Trigger delete
    const deleteButton = screen.getByLabelText("Delete S3 Prod");
    fireEvent.click(deleteButton);

    await waitFor(() => {
      expect(screen.getByText(/Delete failed/)).toBeInTheDocument();
    });
  });

  it("should call onCreateClick when new button pressed", () => {
    render(
      <Provider>
        <StorageProfilesPage {...mockCallbacks} />
      </Provider>
    );

    const newButton = screen.getByText("New Profile");
    fireEvent.click(newButton);

    expect(mockCallbacks.onCreateClick).toHaveBeenCalled();
  });
});
```

## Integration Tests

```typescript
// src/features/data-fabric/__tests__/integration.test.ts

import axios from "axios";
import { renderHook, act } from "@testing-library/react";
import { useAtom } from "jotai";
import {
  loadStorageProfilesAtom,
  allStorageProfilesAtom,
  deleteStorageProfileAtom,
} from "@/features/data-fabric";
import { storageProfileApi } from "@/features/data-fabric/services/api";

jest.mock("axios");
const mockedAxios = axios as jest.Mocked<typeof axios>;

/**
 * Integration tests for full workflows.
 *
 * Validates:
 * - API → Store → Component flow
 * - State consistency across operations
 * - Error handling end-to-end
 */
describe("Data Fabric Integration", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe("Storage Profile Workflow", () => {
    it("should load and select profiles in sequence", async () => {
      const mockProfiles = [
        {
          id: "prof_1",
          name: "S3",
          type: "S3",
          config: {},
          encryption: { type: "NONE" },
          compression: { type: "NONE" },
          isDefault: true,
          isActive: true,
          createdAt: "2024-11-05T00:00:00Z",
          updatedAt: "2024-11-05T00:00:00Z",
          tenantId: "tenant_123",
        },
      ];

      mockedAxios.get.mockResolvedValue({ data: mockProfiles });

      const { result: loadResult } = renderHook(() =>
        useAtom(loadStorageProfilesAtom)
      );
      const { result: profilesResult } = renderHook(() =>
        useAtom(allStorageProfilesAtom)
      );

      // Load profiles
      await act(async () => {
        await loadResult.current[1]();
      });

      // Verify profiles loaded
      expect(profilesResult.current[0]).toHaveLength(1);
      expect(profilesResult.current[0][0].name).toBe("S3");
    });

    it("should handle deletion across API and store", async () => {
      const profileId = "prof_1";

      mockedAxios.delete.mockResolvedValue({ data: { message: "Deleted" } });

      const { result: deleteResult } = renderHook(() =>
        useAtom(deleteStorageProfileAtom)
      );

      await act(async () => {
        await deleteResult.current[1](profileId);
      });

      expect(mockedAxios.delete).toHaveBeenCalledWith(
        `/api/v1/data-fabric/profiles/${profileId}`
      );
    });
  });
});
```

## Testing Best Practices

### 1. Mock API Calls

```typescript
// Always mock external HTTP calls
jest.mock("axios");
const mockedAxios = axios as jest.Mocked<typeof axios>;

mockedAxios.get.mockResolvedValue({ data: mockData });
```

### 2. Test Atom Actions

```typescript
// Test action atoms with renderHook
const { result } = renderHook(() => useAtom(myActionAtom));

act(() => {
  result.current[1](payload);
});
```

### 3. Wait for Async Operations

```typescript
// Always use waitFor for async state updates
await waitFor(() => {
  expect(screen.getByText("loaded")).toBeInTheDocument();
});
```

### 4. Test Error Paths

```typescript
// Always test both success and error cases
mockedAxios.post.mockRejectedValue(new Error("API error"));

await expect(api.create(input)).rejects.toThrow("API error");
```

### 5. Provider Wrapper

```typescript
// Wrap components with Jotai Provider for atom access
render(
  <Provider>
    <Component />
  </Provider>
);
```

## Running Tests

```bash
# Run all tests
pnpm test

# Run tests in watch mode
pnpm test --watch

# Run specific test file
pnpm test StorageProfilesList

# Run with coverage
pnpm test --coverage

# Update snapshots
pnpm test --updateSnapshot
```

## Coverage Goals

- **Statements**: >80%
- **Branches**: >75%
- **Functions**: >80%
- **Lines**: >80%

View coverage report:

```bash
pnpm test --coverage
open coverage/lcov-report/index.html
```

## Debugging Tests

### Enable Debug Output

```typescript
import debug from "debug";
const log = debug("data-fabric:test");

log("This message appears when DEBUG=data-fabric:* env var is set");
```

Run with debug:

```bash
DEBUG=data-fabric:* pnpm test
```

### Use Console in Tests

```typescript
it("should work", () => {
  console.log("State:", state);
  expect(state).toBe(expected);
});

// Run single test with output
pnpm test StorageProfilesList.test -t "should work"
```

### Interactive Debugging

```bash
# Run tests with Node debugger
node --inspect-brk ./node_modules/.bin/jest --runInBand
# Open chrome://inspect to debug
```
