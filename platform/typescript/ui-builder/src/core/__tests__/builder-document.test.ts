/**
 * @fileoverview Tests for BuilderDocument model.
 */

import { describe, it, expect } from "vitest";
import {
  CURRENT_SCHEMA_VERSION,
  createBuilderDocument,
  validateBuilderDocument,
  serializeBuilderDocument,
  deserializeBuilderDocument,
  getNode,
  getRootNodes,
  hasPrivacySensitiveData,
  requiresAccessibility,
  detectSchemaVersion,
  type BuilderDocument,
} from "../builder-document.js";
import { createNodeId, createDocumentId } from "../types.js";

describe("BuilderDocument", () => {
  describe("createBuilderDocument", () => {
    it("should create document with correct schema version", () => {
      const doc = createBuilderDocument("test-user");
      expect(doc.schemaVersion).toBe(CURRENT_SCHEMA_VERSION);
    });

    it("should create document with owner", () => {
      const doc = createBuilderDocument("test-user");
      expect(doc.owner).toBe("test-user");
    });

    it("should create document with root node", () => {
      const doc = createBuilderDocument("test-user");
      expect(doc.nodes[doc.root]).toBeDefined();
      expect(doc.nodes[doc.root].contractName).toBe("RootContainer");
    });

    it("should use provided document ID", () => {
      const id = createDocumentId();
      const doc = createBuilderDocument("test-user", { documentId: id });
      expect(doc.documentId).toBe(id);
    });

    it("should generate unique document ID if not provided", () => {
      const doc1 = createBuilderDocument("test-user");
      const doc2 = createBuilderDocument("test-user");
      expect(doc1.documentId).not.toBe(doc2.documentId);
    });

    it("should set creation timestamp", () => {
      const before = new Date().toISOString();
      const doc = createBuilderDocument("test-user");
      const after = new Date().toISOString();

      expect(doc.metadata.createdAt).toBeGreaterThanOrEqual(before);
      expect(doc.metadata.createdAt).toBeLessThanOrEqual(after);
    });

    it("should initialize with empty bindings", () => {
      const doc = createBuilderDocument("test-user");
      expect(doc.bindings).toHaveLength(0);
    });
  });

  describe("validateBuilderDocument", () => {
    it("should validate minimal valid document", () => {
      const doc = createBuilderDocument("test-user");
      const result = validateBuilderDocument(doc);
      expect(result.valid).toBe(true);
      expect(result.schemaValid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it("should reject invalid schema version", () => {
      const doc = {
        ...createBuilderDocument("test-user"),
        schemaVersion: "invalid",
      };
      const result = validateBuilderDocument(doc);
      expect(result.valid).toBe(false);
      expect(result.schemaValid).toBe(false);
    });

    it("should reject missing owner", () => {
      const doc = createBuilderDocument("test-user");
      const invalidDoc = { ...doc, owner: undefined };
      const result = validateBuilderDocument(invalidDoc);
      expect(result.valid).toBe(false);
    });

    it("should detect missing root node", () => {
      const doc = createBuilderDocument("test-user");
      const missingRootId = createNodeId();
      const invalidDoc = { ...doc, root: missingRootId };
      const result = validateBuilderDocument(invalidDoc);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.code === "MISSING_ROOT_NODE")).toBe(true);
    });

    it("should detect layout referencing non-existent node", () => {
      const doc = createBuilderDocument("test-user");
      const missingId = createNodeId();
      const invalidDoc = {
        ...doc,
        layout: {
          ...doc.layout,
          nodes: {
            ...doc.layout.nodes,
            [missingId]: {
              id: missingId,
              type: "leaf" as const,
            },
          },
        },
      };
      const result = validateBuilderDocument(invalidDoc);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.code === "LAYOUT_NODE_MISSING")).toBe(true);
    });

    it("should detect orphaned nodes", () => {
      const doc = createBuilderDocument("test-user");
      const orphanId = createNodeId();
      const invalidDoc = {
        ...doc,
        nodes: {
          ...doc.nodes,
          [orphanId]: {
            id: orphanId,
            contractName: "TestComponent",
            props: {},
            slots: {},
            bindings: [],
            metadata: {},
          },
        },
      };
      const result = validateBuilderDocument(invalidDoc);
      expect(result.warnings.some((w) => w.code === "ORPHANED_NODE")).toBe(true);
    });

    it("should detect missing child nodes", () => {
      const doc = createBuilderDocument("test-user");
      const missingChildId = createNodeId();
      const invalidDoc = {
        ...doc,
        layout: {
          ...doc.layout,
          nodes: {
            [doc.root]: {
              id: doc.root,
              type: "root" as const,
              children: [missingChildId],
            },
          },
        },
      };
      const result = validateBuilderDocument(invalidDoc);
      expect(result.errors.some((e) => e.code === "CHILD_NODE_MISSING")).toBe(true);
    });

    it("should detect empty binding target", () => {
      const doc = createBuilderDocument("test-user");
      const invalidDoc = {
        ...doc,
        bindings: [
          {
            id: "binding-1",
            type: "data" as const,
            source: "data.users",
            target: "",
          },
        ],
      };
      const result = validateBuilderDocument(invalidDoc);
      expect(result.errors.some((e) => e.code === "EMPTY_BINDING_TARGET")).toBe(true);
    });

    it("should warn about empty binding source", () => {
      const doc = createBuilderDocument("test-user");
      const invalidDoc = {
        ...doc,
        bindings: [
          {
            id: "binding-1",
            type: "data" as const,
            source: "",
            target: "node.props.value",
          },
        ],
      };
      const result = validateBuilderDocument(invalidDoc);
      expect(result.warnings.some((w) => w.code === "EMPTY_BINDING_SOURCE")).toBe(true);
    });

    it("should detect missing privacy PII nodes", () => {
      const doc = createBuilderDocument("test-user");
      const missingId = createNodeId();
      const invalidDoc = {
        ...doc,
        privacy: {
          classification: "confidential" as const,
          piiNodes: [missingId],
        },
      };
      const result = validateBuilderDocument(invalidDoc);
      expect(result.errors.some((e) => e.code === "PII_NODE_MISSING")).toBe(true);
    });

    it("should detect missing accessibility landmark nodes", () => {
      const doc = createBuilderDocument("test-user");
      const missingId = createNodeId();
      const invalidDoc = {
        ...doc,
        a11y: {
          landmarks: [{ type: "main" as const, nodeId: missingId }],
        },
      };
      const result = validateBuilderDocument(invalidDoc);
      expect(result.errors.some((e) => e.code === "LANDMARK_NODE_MISSING")).toBe(true);
    });

    it("should detect missing skip link targets", () => {
      const doc = createBuilderDocument("test-user");
      const missingId = createNodeId();
      const invalidDoc = {
        ...doc,
        a11y: {
          skipLinks: [{ targetId: missingId, label: "Skip to content" }],
        },
      };
      const result = validateBuilderDocument(invalidDoc);
      expect(result.errors.some((e) => e.code === "SKIP_LINK_TARGET_MISSING")).toBe(true);
    });
  });

  describe("serialization", () => {
    it("should serialize and deserialize document", () => {
      const doc = createBuilderDocument("test-user");
      const serialized = serializeBuilderDocument(doc);
      const result = deserializeBuilderDocument(serialized);

      expect(result.success).toBe(true);
      expect(result.document?.documentId).toBe(doc.documentId);
      expect(result.document?.owner).toBe(doc.owner);
    });

    it("should handle invalid JSON", () => {
      const result = deserializeBuilderDocument("invalid json");
      expect(result.success).toBe(false);
      expect(result.errors[0]).toContain("JSON");
    });

    it("should reject document with wrong schema", () => {
      const invalidDoc = { invalid: "data" };
      const result = deserializeBuilderDocument(JSON.stringify(invalidDoc));
      expect(result.success).toBe(false);
    });
  });

  describe("getNode", () => {
    it("should return existing node", () => {
      const doc = createBuilderDocument("test-user");
      const rootNode = getNode(doc, doc.root);
      expect(rootNode).toBeDefined();
      expect(rootNode?.contractName).toBe("RootContainer");
    });

    it("should return undefined for non-existent node", () => {
      const doc = createBuilderDocument("test-user");
      const missingId = createNodeId();
      const node = getNode(doc, missingId);
      expect(node).toBeUndefined();
    });
  });

  describe("getRootNodes", () => {
    it("should return child nodes of root", () => {
      const doc = createBuilderDocument("test-user");
      const childId = createNodeId();

      const docWithChild: BuilderDocument = {
        ...doc,
        nodes: {
          ...doc.nodes,
          [childId]: {
            id: childId,
            contractName: "ChildComponent",
            props: {},
            slots: {},
            bindings: [],
            metadata: {},
          },
        },
        layout: {
          ...doc.layout,
          nodes: {
            [doc.root]: {
              id: doc.root,
              type: "root",
              children: [childId],
            },
          },
        },
      };

      const rootNodes = getRootNodes(docWithChild);
      expect(rootNodes).toHaveLength(1);
      expect(rootNodes[0].id).toBe(childId);
    });

    it("should return empty array for no children", () => {
      const doc = createBuilderDocument("test-user");
      const rootNodes = getRootNodes(doc);
      expect(rootNodes).toHaveLength(0);
    });
  });

  describe("hasPrivacySensitiveData", () => {
    it("should detect restricted classification", () => {
      const doc = createBuilderDocument("test-user");
      const sensitiveDoc = {
        ...doc,
        privacy: {
          classification: "restricted" as const,
        },
      };
      expect(hasPrivacySensitiveData(sensitiveDoc)).toBe(true);
    });

    it("should detect consent required", () => {
      const doc = createBuilderDocument("test-user");
      const sensitiveDoc = {
        ...doc,
        privacy: {
          classification: "public" as const,
          consentRequired: true,
        },
      };
      expect(hasPrivacySensitiveData(sensitiveDoc)).toBe(true);
    });

    it("should detect PII nodes", () => {
      const doc = createBuilderDocument("test-user");
      const sensitiveDoc = {
        ...doc,
        privacy: {
          classification: "public" as const,
          piiNodes: [doc.root],
        },
      };
      expect(hasPrivacySensitiveData(sensitiveDoc)).toBe(true);
    });

    it("should detect node-level privacy metadata", () => {
      const doc = createBuilderDocument("test-user");
      const sensitiveDoc = {
        ...doc,
        nodes: {
          ...doc.nodes,
          [doc.root]: {
            ...doc.nodes[doc.root],
            metadata: {
              ...doc.nodes[doc.root].metadata,
              privacyMetadata: {
                requiresConsent: true,
              },
            },
          },
        },
      };
      expect(hasPrivacySensitiveData(sensitiveDoc)).toBe(true);
    });

    it("should return false for non-sensitive document", () => {
      const doc = createBuilderDocument("test-user");
      expect(hasPrivacySensitiveData(doc)).toBe(false);
    });
  });

  describe("requiresAccessibility", () => {
    it("should return true when a11y config exists", () => {
      const doc = createBuilderDocument("test-user");
      const a11yDoc = {
        ...doc,
        a11y: {
          title: "Accessible App",
        },
      };
      expect(requiresAccessibility(a11yDoc)).toBe(true);
    });

    it("should return false when no a11y config", () => {
      const doc = createBuilderDocument("test-user");
      expect(requiresAccessibility(doc)).toBe(false);
    });
  });

  describe("detectSchemaVersion", () => {
    it("should detect schemaVersion field", () => {
      const doc = createBuilderDocument("test-user");
      expect(detectSchemaVersion(doc)).toBe(CURRENT_SCHEMA_VERSION);
    });

    it("should detect version field as fallback", () => {
      const doc = { version: "0.9.0" };
      expect(detectSchemaVersion(doc)).toBe("0.9.0");
    });

    it("should return null for invalid input", () => {
      expect(detectSchemaVersion(null)).toBeNull();
      expect(detectSchemaVersion("string")).toBeNull();
      expect(detectSchemaVersion({})).toBeNull();
    });
  });
});
