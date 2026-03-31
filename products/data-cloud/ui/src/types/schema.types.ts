/**
 * Schema type definitions for the application.
 *
 * @doc.type types
 * @doc.purpose Domain schema types for collections, fields, and metadata structures
 * @doc.layer product
 * @doc.pattern Type Contract
 *
 * @module types/schema
 */

export interface MetaField {
  id: string;
  name: string;
  type: string;
  required?: boolean;
  description?: string;
  collectionId?: string;
  defaultValue?: unknown;
  validations?: Record<string, unknown>;
}

export interface MetaCollection {
  id: string;
  name: string;
  fields: MetaField[];
  description?: string;
  tenantId?: string;
  permission?: Record<string, unknown>;
  applications?: string[];
  createdAt?: string;
  updatedAt?: string;
}
