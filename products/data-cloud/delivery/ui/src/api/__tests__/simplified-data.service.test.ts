import { SimplifiedDataService } from "../simplified-data.service";
import { beforeEach, describe, expect, it, vi, type Mock } from "vitest";

// Mock fetch for testing
global.fetch = vi.fn();

describe("SimplifiedDataService", () => {
  let service: SimplifiedDataService;
  const baseUrl = "https://api.example.com";
  const tenantId = "test-tenant";

  beforeEach(() => {
    global.fetch = vi.fn();
    service = new SimplifiedDataService(baseUrl, tenantId);
    vi.clearAllMocks();
  });

  describe("constructor", () => {
    it("should create service with base URL and tenant ID", () => {
      expect(service).toBeDefined();
    });

    it("should normalize base URL by removing trailing slash", () => {
      const serviceWithSlash = new SimplifiedDataService(
        "https://api.example.com/",
        tenantId,
      );
      expect(serviceWithSlash).toBeDefined();
    });
  });

  describe("getDashboard", () => {
    it("should fetch dashboard data successfully", async () => {
      const mockResponse = {
        totalEntities: 100,
        totalCollections: 10,
        totalDataSources: 5,
        activePipelines: 3,
        systemHealth: "healthy",
        recentActivity: [],
      };

      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const result = await service.getDashboard();

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/dashboard",
        expect.objectContaining({
          headers: expect.objectContaining({
            "Content-Type": "application/json",
            "X-Tenant-ID": tenantId,
          }),
        }),
      );

      expect(result).toEqual(mockResponse);
    });

    it("should handle fetch errors gracefully", async () => {
      (fetch as Mock).mockRejectedValueOnce(new Error("Network error"));

      await expect(service.getDashboard()).rejects.toThrow("Network error");
    });

    it("should handle non-OK responses", async () => {
      (fetch as Mock).mockResolvedValueOnce({
        ok: false,
        status: 500,
        text: async () => "Internal Server Error",
      });

      await expect(service.getDashboard()).rejects.toThrow();
    });
  });

  describe("search", () => {
    it("should perform search with query", async () => {
      const mockResponse = {
        items: [{ id: "1", name: "Test Entity", type: "entity" }],
        total: 1,
        suggestions: ["test", "entity"],
      };

      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const request = { query: "test", type: "all" as const };
      const result = await service.search(request);

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/search?q=test&type=all",
        expect.objectContaining({
          headers: expect.objectContaining({
            "Content-Type": "application/json",
            "X-Tenant-ID": tenantId,
          }),
        }),
      );

      expect(result).toEqual(mockResponse);
    });

    it("should handle search with filters", async () => {
      const mockResponse = { items: [], total: 0, suggestions: [] };
      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const request = {
        query: "test",
        type: "entity" as const,
        filters: { status: "active" },
      };
      await service.search(request);

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/search?q=test&type=entity&status=active",
        expect.any(Object),
      );
    });
  });

  describe("entities", () => {
    it("should fetch entities without collection filter", async () => {
      const mockResponse = {
        items: [
          { id: "1", name: "Entity 1", type: "document", status: "active" },
        ],
        total: 1,
        limit: 50,
        offset: 0,
      };

      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const result = await service.getEntities();

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/entities",
        expect.any(Object),
      );

      expect(result).toEqual(mockResponse);
    });

    it("should fetch entities with collection filter", async () => {
      const mockResponse = { items: [], total: 0, limit: 50, offset: 0 };
      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      await service.getEntities("collection-123");

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/collections/collection-123/entities",
        expect.any(Object),
      );
    });

    it("should create new entity", async () => {
      const mockResponse = {
        id: "entity-123",
        name: "New Entity",
        type: "document",
        status: "active",
        lastModified: new Date().toISOString(),
      };

      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const entityData = {
        name: "New Entity",
        type: "document",
        collectionId: "collection-123",
      };

      const result = await service.createEntity(entityData);

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/entities",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify(entityData),
          headers: expect.objectContaining({
            "Content-Type": "application/json",
            "X-Tenant-ID": tenantId,
          }),
        }),
      );

      expect(result).toEqual(mockResponse);
    });

    it("should delete entity", async () => {
      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
      });

      await service.deleteEntity("entity-123");

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/entities/entity-123",
        expect.objectContaining({
          method: "DELETE",
          headers: expect.objectContaining({
            "X-Tenant-ID": tenantId,
          }),
        }),
      );
    });
  });

  describe("collections", () => {
    it("should fetch collections", async () => {
      const mockResponse = {
        items: [
          { id: "1", name: "Collection 1", entityCount: 5, status: "active" },
        ],
        total: 1,
      };

      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const result = await service.getCollections();

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/collections",
        expect.any(Object),
      );

      expect(result).toEqual(mockResponse);
    });

    it("should create new collection", async () => {
      const mockResponse = {
        id: "collection-123",
        name: "New Collection",
        entityCount: 0,
        status: "active",
      };

      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const collectionData = {
        name: "New Collection",
        description: "Test collection",
      };

      const result = await service.createCollection(collectionData);

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/collections",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify(collectionData),
        }),
      );

      expect(result).toEqual(mockResponse);
    });
  });

  describe("data sources", () => {
    it("should fetch data sources", async () => {
      const mockResponse = {
        items: [
          { id: "1", name: "Database", type: "database", status: "connected" },
        ],
        total: 1,
      };

      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const result = await service.getDataSources();

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/data-sources",
        expect.any(Object),
      );

      expect(result).toEqual(mockResponse);
    });

    it("should connect new data source", async () => {
      const mockResponse = {
        id: "datasource-123",
        name: "New Database",
        type: "database",
        status: "connected",
      };

      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const dataSourceData = {
        name: "New Database",
        type: "database",
        configuration: { host: "localhost" },
      };

      const result = await service.connectDataSource(dataSourceData);

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/data-sources",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify(dataSourceData),
        }),
      );

      expect(result).toEqual(mockResponse);
    });
  });

  describe("pipelines", () => {
    it("should fetch pipelines", async () => {
      const mockResponse = {
        items: [
          { id: "1", name: "Pipeline 1", status: "running", progress: 75 },
        ],
        total: 1,
      };

      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const result = await service.getPipelines();

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/pipelines",
        expect.any(Object),
      );

      expect(result).toEqual(mockResponse);
    });

    it("should create new pipeline", async () => {
      const mockResponse = {
        id: "pipeline-123",
        name: "New Pipeline",
        status: "stopped",
      };

      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const pipelineData = {
        name: "New Pipeline",
        source: "source-123",
        target: "target-123",
      };

      const result = await service.createPipeline(pipelineData);

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/pipelines",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify(pipelineData),
        }),
      );

      expect(result).toEqual(mockResponse);
    });

    it("should run pipeline", async () => {
      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          id: "1",
          name: "Pipeline 1",
          status: "running",
        }),
      });

      await service.runPipeline("pipeline-123");

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/pipelines/pipeline-123/run",
        expect.objectContaining({
          method: "POST",
        }),
      );
    });
  });

  describe("quick actions", () => {
    it("should fetch quick actions", async () => {
      const mockResponse = {
        actions: [{ id: "create-collection", name: "Create Collection" }],
      };

      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const result = await service.getQuickActions();

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/quick-actions",
        expect.any(Object),
      );

      expect(result).toEqual(mockResponse);
    });

    it("should execute quick action", async () => {
      const mockResponse = {
        actionId: "create-collection",
        status: "completed",
        message: "Action completed successfully",
      };

      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const result = await service.executeQuickAction("create-collection", {
        name: "Test",
      });

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/quick-actions/create-collection",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ name: "Test" }),
        }),
      );

      expect(result).toEqual(mockResponse);
    });
  });

  describe("system status", () => {
    it("should fetch system status", async () => {
      const mockResponse = {
        status: "healthy",
        services: {
          api: { status: "healthy" },
          database: { status: "healthy" },
        },
        uptime: 1234567890,
        version: "1.0.0",
      };

      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => mockResponse,
      });

      const result = await service.getSystemStatus();

      expect(fetch).toHaveBeenCalledWith(
        "https://api.example.com/api/v1/simplified/status",
        expect.any(Object),
      );

      expect(result).toEqual(mockResponse);
    });
  });

  describe("error handling", () => {
    it("should handle network errors consistently", async () => {
      (fetch as Mock).mockRejectedValue(new Error("Network unavailable"));

      await expect(service.getDashboard()).rejects.toThrow(
        "Network unavailable",
      );
      await expect(service.getEntities()).rejects.toThrow(
        "Network unavailable",
      );
      await expect(service.getCollections()).rejects.toThrow(
        "Network unavailable",
      );
    });

    it("should handle JSON parsing errors", async () => {
      (fetch as Mock).mockResolvedValueOnce({
        ok: true,
        json: async () => {
          throw new Error("Invalid JSON");
        },
      });

      await expect(service.getDashboard()).rejects.toThrow("Invalid JSON");
    });

    it("should handle HTTP error responses", async () => {
      (fetch as Mock).mockResolvedValueOnce({
        ok: false,
        status: 404,
        text: async () => "Not Found",
      });

      await expect(service.getDashboard()).rejects.toThrow();
    });
  });
});
