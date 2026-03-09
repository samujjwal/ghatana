# Data Fabric Admin UI - Day 17 Implementation

## Overview

The **Data Fabric Admin UI** provides comprehensive management interfaces for storage profiles and data connectors within the Collection Entity System. This feature enables administrators to:

- **Storage Profiles**: Configure cloud/local storage backends (S3, Azure Blob, GCS, PostgreSQL, MongoDB, Snowflake, Databricks, HDFS)
- **Data Connectors**: Link data sources to storage backends with scheduled synchronization
- **State Management**: Centralized Jotai store for profile and connector state
- **Real-time Operations**: Create, read, update, delete operations with immediate UI updates

## Architecture

### Directory Structure

```
src/features/data-fabric/
├── components/
│   ├── StorageProfilesList.tsx       # Storage profiles list component
│   ├── StorageProfilesPage.tsx       # Storage profiles admin page
│   ├── DataConnectorsList.tsx        # Data connectors list component
│   └── DataConnectorsPage.tsx        # Data connectors admin page
├── stores/
│   ├── storage-profile.store.ts      # Jotai storage profile state
│   └── connector.store.ts            # Jotai connector state
├── services/
│   └── api.ts                        # HTTP API clients
├── types/
│   └── index.ts                      # TypeScript interfaces and enums
└── index.ts                          # Public API exports
```

### Layered Architecture

Following project conventions:

- **API Layer**: `services/api.ts` - HTTP client methods
- **Application Layer**: `components/*Page.tsx` - Container components orchestrating state
- **Domain Layer**: `types/index.ts` - Data models and interfaces
- **Infrastructure Layer**: `stores/*.store.ts` - Jotai state management

## Types

### Storage System Support

**Supported Storage Types:**
- `S3` - Amazon S3
- `AZURE_BLOB` - Azure Blob Storage
- `GCS` - Google Cloud Storage
- `POSTGRESQL` - PostgreSQL databases
- `MONGODB` - MongoDB
- `SNOWFLAKE` - Snowflake
- `DATABRICKS` - Databricks
- `HDFS` - Hadoop Distributed File System

**Encryption Types:**
- `NONE` - No encryption
- `AES_256` - AES-256 encryption
- `KMS` - Key Management Service
- `MANAGED` - Provider-managed encryption

**Compression Types:**
- `NONE` - No compression
- `GZIP` - GZip compression
- `SNAPPY` - Snappy compression
- `ZSTD` - Zstandard compression

### Core Interfaces

#### StorageProfile

```typescript
interface StorageProfile {
  id: string;
  name: string;
  type: StorageType;
  description?: string;
  config: Record<string, unknown>;
  encryption: { type: EncryptionType; keyId?: string };
  compression: { type: CompressionType };
  isDefault: boolean;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  tenantId: string;
}
```

#### DataConnector

```typescript
interface DataConnector {
  id: string;
  name: string;
  sourceType: string;
  storageProfileId: string;
  connectionConfig: Record<string, unknown>;
  syncSchedule?: string;
  lastSyncAt?: string;
  status: "active" | "inactive" | "error" | "testing";
  statusMessage?: string;
  isEnabled: boolean;
  createdAt: string;
  updatedAt: string;
  tenantId: string;
}
```

#### StorageMetrics

```typescript
interface StorageMetrics {
  profileId: string;
  totalCapacity: number;
  usedCapacity: number;
  availableCapacity: number;
  lastUpdated: string;
}
```

#### SyncStatistics

```typescript
interface SyncStatistics {
  connectorId: string;
  totalRecords: number;
  lastSyncRecords: number;
  totalDuration: number;
  lastSyncDuration: number;
  errorCount: number;
  lastError?: string;
}
```

## State Management (Jotai)

### Storage Profile Atoms

**Core Atom:**
- `storageProfileAtom` - Main state container

**Derived Atoms (Read-Only):**
- `allStorageProfilesAtom` - All profiles
- `selectedStorageProfileAtom` - Currently selected profile
- `profilesByTypeAtom` - Profiles grouped by storage type
- `storageProfileLoadingAtom` - Loading state
- `storageProfileErrorAtom` - Error state
- `selectedProfileMetricsAtom` - Metrics for selected profile

**Action Atoms (Write):**
- `loadStorageProfilesAtom` - Load profiles
- `setStorageProfileLoadingAtom` - Update loading state
- `setStorageProfileErrorAtom` - Set error message
- `selectStorageProfileAtom` - Select a profile
- `addStorageProfileAtom` - Add new profile
- `updateStorageProfileAtom` - Update existing profile
- `deleteStorageProfileAtom` - Delete a profile
- `updateStorageMetricsAtom` - Update storage metrics
- `setDefaultStorageProfileAtom` - Set profile as default
- `resetStorageProfileAtom` - Reset to initial state

### Connector Atoms

**Core Atom:**
- `dataConnectorAtom` - Main state container

**Derived Atoms (Read-Only):**
- `allDataConnectorsAtom` - All connectors
- `selectedDataConnectorAtom` - Currently selected connector
- `activeConnectorsAtom` - Active connectors only
- `connectorsByProfileAtom` - Connectors grouped by storage profile
- `connectorLoadingAtom` - Loading state
- `connectorErrorAtom` - Error state
- `testingConnectorIdAtom` - Connector being tested
- `selectedConnectorStatisticsAtom` - Statistics for selected connector

**Action Atoms (Write):**
- `loadDataConnectorsAtom` - Load connectors
- `setConnectorLoadingAtom` - Update loading state
- `setConnectorErrorAtom` - Set error message
- `selectDataConnectorAtom` - Select a connector
- `addDataConnectorAtom` - Add new connector
- `updateDataConnectorAtom` - Update existing connector
- `deleteDataConnectorAtom` - Delete a connector
- `updateSyncStatisticsAtom` - Update sync statistics
- `setTestingConnectorAtom` - Set connector being tested
- `toggleConnectorStateAtom` - Toggle enabled/disabled state
- `resetConnectorAtom` - Reset to initial state

## API Service

### storageProfileApi

HTTP client methods for storage profile operations:

```typescript
storageProfileApi.getAll()              // Get all profiles
storageProfileApi.getById(id)           // Get single profile
storageProfileApi.create(input)         // Create profile
storageProfileApi.update(id, input)     // Update profile
storageProfileApi.delete(id)            // Delete profile
storageProfileApi.setDefault(id)        // Set as default
storageProfileApi.getMetrics(id)        // Get storage metrics
```

### dataConnectorApi

HTTP client methods for connector operations:

```typescript
dataConnectorApi.getAll()               // Get all connectors
dataConnectorApi.getById(id)            // Get single connector
dataConnectorApi.create(input)          // Create connector
dataConnectorApi.update(id, input)      // Update connector
dataConnectorApi.delete(id)             // Delete connector
dataConnectorApi.test(id)               // Test connection
dataConnectorApi.triggerSync(id)        // Trigger manual sync
dataConnectorApi.getSyncStatistics(id)  // Get sync statistics
dataConnectorApi.getByProfile(id)       // Get connectors for profile
```

## Components

### StorageProfilesList

**Purpose**: Display storage profiles in a table with actions

**Features:**
- Sortable columns (Name, Type, Status, Default)
- Edit/Delete/Set Default actions
- Row click selection
- Empty state

**Props:**
```typescript
interface StorageProfilesListProps {
  onEdit: (profile: StorageProfile) => void;
  onDelete: (profileId: string) => void;
  onSetDefault: (profileId: string) => void;
}
```

### StorageProfilesPage

**Purpose**: Admin page for storage profile management

**Features:**
- Load all profiles on mount
- Create new profiles
- Edit existing profiles
- Delete profiles with confirmation
- Set profiles as default
- Error handling with toast notifications
- Loading states
- Empty state with CTA

**Props:**
```typescript
interface StorageProfilesPageProps {
  onCreateClick: () => void;
  onEditClick: (profile: StorageProfile) => void;
}
```

### DataConnectorsList

**Purpose**: Display data connectors in a table with actions

**Features:**
- Sortable columns (Name, Source Type, Status, Last Sync)
- Error status with tooltip messages
- Edit/Delete/Sync actions
- Row click selection
- Empty state

**Props:**
```typescript
interface DataConnectorsListProps {
  onEdit: (connector: DataConnector) => void;
  onDelete: (connectorId: string) => void;
  onSync: (connectorId: string) => void;
}
```

### DataConnectorsPage

**Purpose**: Admin page for data connector management

**Features:**
- Load all connectors on mount
- Create new connectors
- Edit existing connectors
- Delete connectors with confirmation
- Trigger manual syncs
- Update sync statistics after sync
- Error handling with toast notifications
- Loading states
- Empty state with CTA

**Props:**
```typescript
interface DataConnectorsPageProps {
  onCreateClick: () => void;
  onEditClick: (connector: DataConnector) => void;
}
```

## Usage Example

```typescript
import React, { useState } from "react";
import {
  StorageProfilesPage,
  DataConnectorsPage,
  type StorageProfile,
  type DataConnector,
} from "@/features/data-fabric";

export function DataFabricAdmin() {
  const [activeTab, setActiveTab] = useState<"profiles" | "connectors">("profiles");
  const [editingProfile, setEditingProfile] = useState<StorageProfile | null>(null);
  const [editingConnector, setEditingConnector] = useState<DataConnector | null>(null);

  return (
    <div className="space-y-6">
      <div className="flex gap-2">
        <button
          onClick={() => setActiveTab("profiles")}
          className={activeTab === "profiles" ? "font-bold" : ""}
        >
          Storage Profiles
        </button>
        <button
          onClick={() => setActiveTab("connectors")}
          className={activeTab === "connectors" ? "font-bold" : ""}
        >
          Data Connectors
        </button>
      </div>

      {activeTab === "profiles" && (
        <StorageProfilesPage
          onCreateClick={() => setEditingProfile(null)}
          onEditClick={setEditingProfile}
        />
      )}

      {activeTab === "connectors" && (
        <DataConnectorsPage
          onCreateClick={() => setEditingConnector(null)}
          onEditClick={setEditingConnector}
        />
      )}
    </div>
  );
}
```

## Testing

### Unit Tests

Test files created in `__tests__/`:
- `storage-profile.store.test.ts` - Storage profile state management
- `connector.store.test.ts` - Connector state management
- `StorageProfilesList.test.tsx` - List component
- `DataConnectorsList.test.tsx` - List component
- `StorageProfilesPage.test.tsx` - Page component
- `DataConnectorsPage.test.tsx` - Page component
- `api.test.ts` - API client methods

### Testing Strategy

1. **Store Tests**: Verify atom operations and state updates
2. **Component Tests**: Verify rendering, user interactions, and callbacks
3. **Page Tests**: Verify data loading, error handling, and form operations
4. **API Tests**: Verify HTTP calls and error handling

## Development

### Build

```bash
cd products/collection-entity-system/ui
pnpm build
```

### Dev Server

```bash
pnpm dev
```

### Linting

```bash
pnpm lint
```

### Type Checking

```bash
pnpm type-check
```

## Design Standards

### Following copilot-instructions.md

- **Reuse-First**: Leverages existing Jotai patterns from UI architecture
- **Atomic Design**: Components organized as atoms → molecules → organisms → pages
- **State Colocation**: Jotai atoms stored in feature-specific stores
- **Type Safety**: Full TypeScript with no implicit `any`
- **Documentation**: Comprehensive JSDoc with @doc.* metadata tags

### Styling

- **Tailwind CSS**: Utility-first CSS with design system integration
- **Responsive**: Mobile-first responsive design
- **Accessibility**: ARIA attributes where needed

### Error Handling

- **Toast Notifications**: User-friendly error/success messages via sonner
- **Loading States**: Visual feedback during API calls
- **Empty States**: Helpful guidance when no data exists

## Future Enhancements

1. **Form Components**: Create/Edit modals for profiles and connectors
2. **Storage Metrics**: Dashboard with capacity and usage charts
3. **Sync Scheduling**: UI for configuring cron-based sync schedules
4. **Connection Testing**: Pre-save connection validation UI
5. **Bulk Operations**: Multi-select and bulk actions
6. **Advanced Filtering**: Filter by type, status, date ranges
7. **Export/Import**: Backup and restore configurations
8. **Audit Logging**: Track configuration changes by user

## Dependencies

- **jotai** (^2.6.0) - State management
- **axios** (^1.6.0) - HTTP client
- **lucide-react** (^0.553.0) - Icons
- **tailwindcss** (^3.3.0) - Styling
- **clsx** (^2.0.0) - Conditional classes
- **sonner** (^2.0.7) - Toast notifications
- **react** (^18.2.0) - Core framework
- **react-dom** (^18.2.0) - DOM rendering

## Related Documentation

- [Jotai State Management Guide](../../../docs/JOTAI_STATE_MANAGEMENT.md)
- [UI Architecture](../../../docs/UI_ARCHITECTURE.md)
- [Collection Entity System](../../README.md)
- [Project Conventions](../../../.github/copilot-instructions.md)
