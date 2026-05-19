/**
 * Builder Studio
 *
 * UI Builder document creation, editing, and management with visual authoring.
 * Integrates with @ghatana/ui-builder for document creation, serialization, and persistence.
 *
 * @doc.type component
 * @doc.purpose Builder document visual authoring and management
 * @doc.layer platform
 */

import type { ReactElement } from 'react';
import { useState, useEffect, useMemo } from 'react';
import { Button, Typography, Card, CardContent, CardHeader } from '@ghatana/design-system';

// Import public APIs from ui-builder platform
import {
  createBuilderDocument,
  validateBuilderDocument,
  serializeBuilderDocument,
  deserializeBuilderDocument,
  LocalStoragePersistenceAdapter,
  insertNode,
  updateNodeProps,
  type PersistenceAdapter,
  type DocumentVersion,
  type NodeId,
} from '@ghatana/ui-builder';
import type { BuilderDocument, ComponentInstance } from '@ghatana/ui-builder';
import { studioLogger } from '../logging/studioLogger';

// Import visual builder components
import { ComponentPalette, type ComponentContract } from '../components/builder/ComponentPalette';
import { ComponentTree } from '../components/builder/ComponentTree';
import { PropertyInspector } from '../components/builder/PropertyInspector';
import { ValidationPanel } from '../components/builder/ValidationPanel';
import { VisualCanvas } from '../components/builder/VisualCanvas';

/**
 * Custom persistence adapter that extends LocalStoragePersistenceAdapter
 * to support document listing and deletion for BuilderStudio.
 *
 * This adapter wraps the standard LocalStoragePersistenceAdapter and adds
 * a document index to track all documents, enabling list and delete operations.
 */
class StudioPersistenceAdapter implements PersistenceAdapter {
  private baseAdapter: LocalStoragePersistenceAdapter;
  private documentIndexKey: string;
  private documentIndex: Set<string> = new Set();

  constructor(namespace: string) {
    this.baseAdapter = new LocalStoragePersistenceAdapter(namespace);
    this.documentIndexKey = `${namespace}:index`;
    this.loadDocumentIndex();
  }

  private loadDocumentIndex(): void {
    try {
      const stored = localStorage.getItem(this.documentIndexKey);
      if (stored) {
        this.documentIndex = new Set(JSON.parse(stored));
      }
    } catch (err) {
      studioLogger.error('Failed to load document index', { error: err });
    }
  }

  private saveDocumentIndex(): void {
    try {
      localStorage.setItem(this.documentIndexKey, JSON.stringify([...this.documentIndex]));
    } catch (err) {
      studioLogger.error('Failed to save document index', { error: err });
    }
  }

  async save(doc: BuilderDocument, label: string): Promise<string> {
    const versionId = await this.baseAdapter.save(doc, label);
    this.documentIndex.add(doc.documentId);
    this.saveDocumentIndex();
    return versionId;
  }

  async load(documentId: string): Promise<BuilderDocument | null> {
    return this.baseAdapter.load(documentId);
  }

  async listVersions(documentId: string): Promise<readonly DocumentVersion[]> {
    return this.baseAdapter.listVersions(documentId);
  }

  async restoreVersion(documentId: string, versionId: string): Promise<BuilderDocument | null> {
    return this.baseAdapter.restoreVersion(documentId, versionId);
  }

  async deleteVersion(documentId: string, versionId: string): Promise<void> {
    await this.baseAdapter.deleteVersion(documentId, versionId);
  }

  async clearDocument(documentId: string): Promise<void> {
    await this.baseAdapter.clearDocument(documentId);
  }

  /**
   * List all document IDs in the persistence store.
   */
  async listDocumentIds(): Promise<readonly string[]> {
    return [...this.documentIndex];
  }

  /**
   * Delete a document entirely (all versions).
   */
  async deleteDocument(documentId: string): Promise<void> {
    await this.clearDocument(documentId);
    this.documentIndex.delete(documentId);
    this.saveDocumentIndex();
  }
}

// Use BuilderDocument directly since createBuilderDocument returns the canonical type
type StudioBuilderDocument = BuilderDocument;

interface DocumentListItem {
  id: string;
  name: string;
  updatedAt: string;
  document: StudioBuilderDocument;
}



export default function BuilderStudio(): ReactElement {
  const [documents, setDocuments] = useState<DocumentListItem[]>([]);
  const [selectedDocument, setSelectedDocument] = useState<DocumentListItem | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<NodeId | null>(null);
  const [selectedInstance, setSelectedInstance] = useState<ComponentInstance | null>(null);
  const [validationResult, setValidationResult] = useState<ReturnType<typeof validateBuilderDocument> | null>(null);

  // Use custom persistence adapter that supports list and delete operations.
  // Wrapped in useMemo so a new instance is NOT created on every render.
  const persistenceAdapter = useMemo(
    () => new StudioPersistenceAdapter('builder-studio-documents'),
    []
  );

  // Load documents from persistence adapter
  useEffect(() => {
    loadDocuments();
  }, []);

  const loadDocuments = async (): Promise<void> => {
    setError(null);
    try {
      const documentIds = await persistenceAdapter.listDocumentIds();
      const docs: DocumentListItem[] = [];
      
      for (const docId of documentIds) {
        const doc = await persistenceAdapter.load(docId);
        if (doc) {
          docs.push({
            id: doc.documentId,
            name: doc.metadata.description || 'Untitled Document',
            updatedAt: doc.metadata.updatedAt,
            document: doc,
          });
        }
      }
      
      setDocuments(docs);
    } catch (err) {
      studioLogger.error('Failed to load documents', { error: err });
      setError('Failed to load documents');
    }
  };

  const validateCurrentDocument = (): void => {
    if (selectedDocument) {
      const result = validateBuilderDocument(selectedDocument.document);
      setValidationResult(result);
    }
  };

  // Re-validate when document selection changes
  useEffect(() => {
    validateCurrentDocument();
  }, [selectedDocument]);

  const saveDocuments = async (docs: DocumentListItem[]): Promise<void> => {
    try {
      // Save each document using the persistence adapter
      for (const doc of docs) {
        await persistenceAdapter.save(doc.document, doc.name);
      }
      setDocuments(docs);
    } catch (err) {
      studioLogger.error('Failed to save documents', { error: err });
      setError('Failed to save documents');
    }
  };

  const createNewDocument = async (): Promise<void> => {
    setIsCreating(true);
    setError(null);

    try {
      const newDoc = createBuilderDocument('studio-user', {
        designSystemId: 'default-design-system',
        designSystemName: 'Default Design System',
      });

      const validation = validateBuilderDocument(newDoc);
      if (!validation.valid) {
        setError(`Document validation failed: ${validation.errors.join(', ')}`);
        return;
      }

      const newListItem: DocumentListItem = {
        id: newDoc.documentId,
        name: `New Document ${documents.length + 1}`,
        updatedAt: newDoc.metadata.updatedAt,
        document: newDoc,
      };

      await saveDocuments([...documents, newListItem]);
      setSelectedDocument(newListItem);
    } catch (err) {
      studioLogger.error('Failed to create document', { error: err });
      setError('Failed to create document');
    } finally {
      setIsCreating(false);
    }
  };

  const deleteDocument = async (docId: string): Promise<void> => {
    try {
      await persistenceAdapter.deleteDocument(docId);
      const updatedDocs = documents.filter(doc => doc.id !== docId);
      setDocuments(updatedDocs);
      
      if (selectedDocument?.id === docId) {
        setSelectedDocument(null);
      }
    } catch (err) {
      studioLogger.error('Failed to delete document', { error: err });
      setError('Failed to delete document');
    }
  };

  const selectDocument = (doc: DocumentListItem): void => {
    setSelectedDocument(doc);
    setSelectedNodeId(null);
    setSelectedInstance(null);
  };

  const handleNodeSelect = (nodeId: NodeId): void => {
    setSelectedNodeId(nodeId);
    const instance = selectedDocument?.document.nodes[nodeId];
    setSelectedInstance(instance || null);
  };

  const handlePropertyUpdate = (instanceId: NodeId, prop: string, value: unknown): void => {
    if (!selectedDocument) return;
    
    try {
      const updatedDoc = updateNodeProps(selectedDocument.document, instanceId, {
        [prop]: value,
      });
      
      const updatedListItem: DocumentListItem = {
        ...selectedDocument,
        document: updatedDoc,
      };
      
      setSelectedDocument(updatedListItem);
      setSelectedInstance(updatedDoc.nodes[instanceId] || null);
      validateCurrentDocument();
    } catch (err) {
      studioLogger.error('Failed to update property', { error: err });
      setError('Failed to update property');
    }
  };

  const handleComponentSelect = (contract: ComponentContract): void => {
    if (!selectedDocument) return;
    
    try {
      const rootId = selectedDocument.document.layout.rootId;
      
      const updatedDoc = insertNode(selectedDocument.document, {
        contractName: contract.name,
        props: contract.props || {},
        slots: {},
        bindings: [],
        metadata: {
          name: contract.displayName || contract.name,
          position: { x: 100, y: 100 },
          size: { width: 200, height: 100 },
        },
      }, rootId);
      
      const updatedListItem: DocumentListItem = {
        ...selectedDocument,
        document: updatedDoc,
      };
      
      setSelectedDocument(updatedListItem);
      validateCurrentDocument();
    } catch (err) {
      studioLogger.error('Failed to add component', { error: err });
      setError('Failed to add component');
    }
  };

  const exportDocument = (doc: DocumentListItem): void => {
    try {
      const json = serializeBuilderDocument(doc.document);
      const blob = new Blob([json], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${doc.name}.json`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      studioLogger.error('Failed to export document', { error: err });
      setError('Failed to export document');
    }
  };

  const importDocument = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = async (e) => {
      try {
        const json = e.target?.result as string;
        const result = deserializeBuilderDocument(json);
        
        if (!result.success) {
          setError(`Import failed: ${result.errors.join(', ')}`);
          return;
        }

        const importedDoc: DocumentListItem = {
          id: result.document!.documentId,
          name: file.name.replace('.json', ''),
          updatedAt: result.document!.metadata.updatedAt,
          document: result.document!,
        };

        await saveDocuments([...documents, importedDoc]);
      } catch (err) {
        studioLogger.error('Failed to import document', { error: err });
        setError('Failed to import document');
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="p-6">
      <div className="studio-section">
        <div className="flex items-center justify-between mb-6">
          <Typography variant="h2" className="text-2xl font-bold">
            Builder Studio
          </Typography>
          <div className="flex gap-2">
            <Button 
              variant="secondary" 
              onClick={() => document.getElementById('import-file')?.click()}
            >
              Import
            </Button>
            <input
              id="import-file"
              type="file"
              accept=".json"
              className="hidden"
              onChange={importDocument}
            />
            <Button 
              variant="primary" 
              onClick={createNewDocument}
              disabled={isCreating}
            >
              {isCreating ? 'Creating...' : 'New Document'}
            </Button>
          </div>
        </div>

        {error && (
          <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg">
            <Typography variant="body2" className="text-red-600">
              {error}
            </Typography>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Document List */}
          <div className="lg:col-span-1">
            <Card>
              <CardHeader title="Documents" />
              <CardContent>
                {documents.length === 0 ? (
                  <div className="text-center py-8">
                    <Typography variant="body2" className="text-gray-500">
                      No documents yet. Create your first BuilderDocument.
                    </Typography>
                  </div>
                ) : (
                  <div className="space-y-2">
                    {documents.map((doc) => (
                      <div
                        key={doc.id}
                        className={`p-3 border rounded-lg cursor-pointer transition-colors ${
                          selectedDocument?.id === doc.id
                            ? 'border-blue-500 bg-blue-50'
                            : 'border-gray-200 hover:border-gray-300'
                        }`}
                        onClick={() => selectDocument(doc)}
                      >
                        <div className="flex justify-between items-start">
                          <div>
                            <Typography variant="body1" className="font-medium">
                              {doc.name}
                            </Typography>
                            <Typography variant="body2" className="text-gray-500 text-sm">
                              {new Date(doc.updatedAt).toLocaleDateString()}
                            </Typography>
                          </div>
                          <div className="flex gap-1">
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={(e) => {
                                e.stopPropagation();
                                exportDocument(doc);
                              }}
                            >
                              Export
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={(e) => {
                                e.stopPropagation();
                                deleteDocument(doc.id);
                              }}
                            >
                              Delete
                            </Button>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Visual Builder Workspace */}
          <div className="lg:col-span-2">
            {selectedDocument ? (
              <div className="h-full flex flex-col">
                {/* Toolbar */}
                <div className="mb-4 flex items-center justify-between">
                  <Typography variant="h3" className="text-xl font-semibold">
                    {selectedDocument.name}
                  </Typography>
                  <div className="flex gap-2">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => {
                        const json = serializeBuilderDocument(selectedDocument.document);
                        const blob = new Blob([json], { type: 'application/json' });
                        const url = URL.createObjectURL(blob);
                        const a = document.createElement('a');
                        a.href = url;
                        a.download = `${selectedDocument.name}.json`;
                        a.click();
                        URL.revokeObjectURL(url);
                      }}
                    >
                      Export
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={validateCurrentDocument}
                    >
                      Validate
                    </Button>
                  </div>
                </div>

                {/* Builder Workspace */}
                <div className="flex-1 grid grid-cols-12 gap-4 h-[calc(100vh-200px)]">
                  {/* Left Panel - Component Palette and Tree */}
                  <div className="col-span-3 flex flex-col gap-4">
                    <Card className="flex-1 flex flex-col">
                      <CardHeader title="Components" />
                      <CardContent className="flex-1 p-0">
                        <ComponentPalette
                          contracts={[
                            { name: 'Button', displayName: 'Button', category: 'Form', description: 'Clickable button component' },
                            { name: 'Input', displayName: 'Input', category: 'Form', description: 'Text input field' },
                            { name: 'Card', displayName: 'Card', category: 'Layout', description: 'Card container component' },
                            { name: 'Typography', displayName: 'Typography', category: 'Content', description: 'Text display component' },
                          ]}
                          onComponentSelect={handleComponentSelect}
                        />
                      </CardContent>
                    </Card>
                    <Card className="flex-1 flex flex-col">
                      <CardHeader title="Tree" />
                      <CardContent className="flex-1 p-0">
                        <ComponentTree
                          document={selectedDocument.document}
                          selectedNodeId={selectedNodeId}
                          onNodeSelect={handleNodeSelect}
                        />
                      </CardContent>
                    </Card>
                  </div>

                  {/* Center Panel - Canvas */}
                  <div className="col-span-6 flex flex-col">
                    <Card className="flex-1 flex flex-col">
                      <CardHeader title="Canvas" />
                      <CardContent className="flex-1 p-0">
                        <VisualCanvas
                          document={selectedDocument.document}
                          selectedNodeIds={selectedNodeId ? [selectedNodeId] : []}
                          onSelectionChange={(nodeIds) => {
                            if (nodeIds.length > 0) {
                              handleNodeSelect(nodeIds[0] as NodeId);
                            } else {
                              setSelectedNodeId(null);
                              setSelectedInstance(null);
                            }
                          }}
                          height="100%"
                        />
                      </CardContent>
                    </Card>
                  </div>

                  {/* Right Panel - Properties and Validation */}
                  <div className="col-span-3 flex flex-col gap-4">
                    <Card className="flex-1 flex flex-col">
                      <CardHeader title="Properties" />
                      <CardContent className="flex-1 p-0">
                        <PropertyInspector
                          selectedInstance={selectedInstance}
                          onPropertyUpdate={handlePropertyUpdate}
                        />
                      </CardContent>
                    </Card>
                    <Card className="h-64 flex flex-col">
                      <CardHeader title="Validation" />
                      <CardContent className="flex-1 p-0">
                        <ValidationPanel
                          validationResult={validationResult}
                          onValidationErrorClick={(nodeId) => handleNodeSelect(nodeId as NodeId)}
                          onRevalidate={validateCurrentDocument}
                        />
                      </CardContent>
                    </Card>
                  </div>
                </div>
              </div>
            ) : (
              <Card>
                <CardContent className="py-12">
                  <div className="text-center">
                    <Typography variant="body1" className="text-gray-500 mb-4">
                      Select a document to view details or create a new document to get started.
                    </Typography>
                    <Button variant="primary" onClick={createNewDocument}>
                      Create New Document
                    </Button>
                  </div>
                </CardContent>
              </Card>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
