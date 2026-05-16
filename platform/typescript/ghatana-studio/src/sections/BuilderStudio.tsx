/**
 * Builder Studio
 *
 * UI Builder document creation, editing, and management.
 * Integrates with @ghatana/ui-builder public APIs for BuilderDocument operations.
 *
 * @doc.type component
 * @doc.purpose Builder document authoring and management
 * @doc.layer platform
 */

import type { ReactElement } from 'react';
import { useState, useEffect } from 'react';
import { Button, Typography, Card, CardContent, CardHeader } from '@ghatana/design-system';

// Import public APIs from ui-builder platform
import {
  createBuilderDocument,
  validateBuilderDocument,
  serializeBuilderDocument,
  deserializeBuilderDocument,
} from '@ghatana/ui-builder';
import type { DocumentId } from '@ghatana/ui-builder';
import { studioLogger } from '../logging/studioLogger';

type StudioBuilderDocument = ReturnType<typeof createBuilderDocument>;

interface DocumentListItem {
  id: string;
  name: string;
  updatedAt: string;
  document: StudioBuilderDocument;
}

export default function BuilderStudio(): ReactElement {
  const [documents, setDocuments] = useState<DocumentListItem[]>([]);
  const [selectedDocument, setSelectedDocument] = useState<DocumentListItem | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Load documents from localStorage (in a real app, this would be from a backend)
  useEffect(() => {
    loadDocuments();
  }, []);

  const loadDocuments = (): void => {
    try {
      const stored = localStorage.getItem('builder-studio-documents');
      if (stored) {
        const docs: DocumentListItem[] = JSON.parse(stored);
        setDocuments(docs);
      }
    } catch (err) {
      studioLogger.error('Failed to load documents', { error: err });
    }
  };

  const saveDocuments = (docs: DocumentListItem[]): void => {
    try {
      localStorage.setItem('builder-studio-documents', JSON.stringify(docs));
      setDocuments(docs);
    } catch (err) {
      studioLogger.error('Failed to save documents', { error: err });
      setError('Failed to save documents');
    }
  };

  const createNewDocument = (): void => {
    setIsLoading(true);
    setError(null);

    try {
      const newDoc = createBuilderDocument('studio-user', {
        documentId: `doc-${Date.now()}` as DocumentId,
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

      saveDocuments([...documents, newListItem]);
      setSelectedDocument(newListItem);
    } catch (err) {
      studioLogger.error('Failed to create document', { error: err });
      setError('Failed to create document');
    } finally {
      setIsLoading(false);
    }
  };

  const deleteDocument = (docId: string): void => {
    const updatedDocs = documents.filter(doc => doc.id !== docId);
    saveDocuments(updatedDocs);
    
    if (selectedDocument?.id === docId) {
      setSelectedDocument(null);
    }
  };

  const selectDocument = (doc: DocumentListItem): void => {
    setSelectedDocument(doc);
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
    reader.onload = (e) => {
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

        saveDocuments([...documents, importedDoc]);
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
              disabled={isLoading}
            >
              {isLoading ? 'Creating...' : 'New Document'}
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

          {/* Document Details/Editor */}
          <div className="lg:col-span-2">
            {selectedDocument ? (
              <Card>
                <CardHeader title={selectedDocument.name} />
                <CardContent>
                  <div className="space-y-4">
                    <div>
                      <Typography variant="body1" className="font-medium mb-2">
                        Document Information
                      </Typography>
                      <div className="grid grid-cols-2 gap-4 text-sm">
                        <div>
                          <span className="text-gray-500">ID:</span>
                          <span className="ml-2">{selectedDocument.id}</span>
                        </div>
                        <div>
                          <span className="text-gray-500">Schema Version:</span>
                          <span className="ml-2">{selectedDocument.document.schemaVersion}</span>
                        </div>
                        <div>
                          <span className="text-gray-500">Owner:</span>
                          <span className="ml-2">{selectedDocument.document.owner}</span>
                        </div>
                        <div>
                          <span className="text-gray-500">Nodes:</span>
                          <span className="ml-2">{Object.keys(selectedDocument.document.nodes).length}</span>
                        </div>
                      </div>
                    </div>

                    <div>
                      <Typography variant="body1" className="font-medium mb-2">
                        Description
                      </Typography>
                      <Typography variant="body2" className="text-gray-600">
                        {selectedDocument.document.metadata.description || 'No description provided'}
                      </Typography>
                    </div>

                    <div>
                      <Typography variant="body1" className="font-medium mb-2">
                        Tags
                      </Typography>
                      <div className="flex flex-wrap gap-2">
                        {selectedDocument.document.metadata.tags?.map((tag) => (
                          <span
                            key={tag}
                            className="px-2 py-1 bg-gray-100 text-gray-700 rounded-md text-sm"
                          >
                            {tag}
                          </span>
                        )) || (
                          <Typography variant="body2" className="text-gray-500">
                            No tags
                          </Typography>
                        )}
                      </div>
                    </div>

                    <div className="pt-4 border-t">
                      <Typography variant="body2" className="text-gray-500">
                        This document uses the @ghatana/ui-builder public APIs for creation, validation, and management.
                      </Typography>
                    </div>
                  </div>
                </CardContent>
              </Card>
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
