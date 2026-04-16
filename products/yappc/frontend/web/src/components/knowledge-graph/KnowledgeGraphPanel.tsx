/**
 * Copyright (c) 2025 Ghatana Technologies
 * Knowledge Graph Panel
 *
 * React component for displaying and interacting with the knowledge graph.
 * Surfaces KG insights, relationships, and semantic search in the YAPPC UI.
 *
 * @doc.type component
 * @doc.purpose Knowledge graph visualization and interaction
 * @doc.layer ui
 * @doc.pattern Panel/Widget
 */

import React, { useState, useCallback, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  TextField,
  Typography,
  Chip,
  List,
  ListItem,
  ListItemText,
  IconButton,
  CircularProgress,
  Alert,
  Card,
  CardContent,
  Tooltip,
} from '@mui/material';
import {
  Search as SearchIcon,
  Refresh as RefreshIcon,
  AccountTree as GraphIcon,
  Link as LinkIcon,
  Lightbulb as InsightIcon,
} from '@mui/icons-material';

// Knowledge Graph API client
import { knowledgeGraphApi } from './knowledgeGraphApi';

// Types
interface KnowledgeNode {
  id: string;
  type: 'PROJECT' | 'WORKFLOW' | 'ENTITY' | 'RELATIONSHIP' | 'INSIGHT';
  label: string;
  description?: string;
  confidence: number;
  metadata: Record<string, unknown>;
}

interface KnowledgeEdge {
  source: string;
  target: string;
  type: string;
  weight: number;
}

interface KnowledgeGraphResult {
  nodes: KnowledgeNode[];
  edges: KnowledgeEdge[];
  insights: string[];
}

interface SemanticSearchResult {
  query: string;
  results: Array<{
    node: KnowledgeNode;
    score: number;
    context: string;
  }>;
}

interface Props {
  projectId?: string;
  className?: string;
}

/**
 * Knowledge Graph Panel Component
 *
 * Provides:
 * - Semantic search over project knowledge
 * - Relationship visualization
 * - AI-generated insights
 * - Entity exploration
 */
export const KnowledgeGraphPanel: React.FC<Props> = ({ projectId, className }) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedNode, setSelectedNode] = useState<KnowledgeNode | null>(null);

  // Fetch knowledge graph for project
  const {
    data: graphData,
    isLoading: isGraphLoading,
    error: graphError,
    refetch: refetchGraph,
  } = useQuery<KnowledgeGraphResult>({
    queryKey: ['knowledge-graph', projectId],
    queryFn: () => knowledgeGraphApi.getProjectGraph(projectId!),
    enabled: !!projectId,
  });

  // Semantic search
  const {
    data: searchResults,
    isLoading: isSearching,
    error: searchError,
  } = useQuery<SemanticSearchResult>({
    queryKey: ['semantic-search', searchQuery, projectId],
    queryFn: () => knowledgeGraphApi.semanticSearch(searchQuery, projectId),
    enabled: searchQuery.length > 2,
    debounce: 300,
  });

  const handleSearch = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(event.target.value);
    setSelectedNode(null);
  }, []);

  const handleNodeClick = useCallback((node: KnowledgeNode) => {
    setSelectedNode(node);
  }, []);

  const handleRefresh = useCallback(() => {
    refetchGraph();
  }, [refetchGraph]);

  // Render node type chip
  const renderNodeTypeChip = (type: KnowledgeNode['type']) => {
    const colors: Record<KnowledgeNode['type'], 'default' | 'primary' | 'secondary' | 'success' | 'warning'> = {
      PROJECT: 'primary',
      WORKFLOW: 'secondary',
      ENTITY: 'default',
      RELATIONSHIP: 'success',
      INSIGHT: 'warning',
    };

    return (
      <Chip
        size="small"
        label={type}
        color={colors[type]}
        sx={{ fontSize: '0.7rem' }}
      />
    );
  };

  // Loading state
  if (isGraphLoading) {
    return (
      <Box className={className} sx={{ p: 2, textAlign: 'center' }}>
        <CircularProgress size={40} />
        <Typography variant="body2" sx={{ mt: 2 }}>
          Loading knowledge graph...
        </Typography>
      </Box>
    );
  }

  // Error state
  if (graphError) {
    return (
      <Box className={className} sx={{ p: 2 }}>
        <Alert
          severity="error"
          action={
            <IconButton size="small" onClick={handleRefresh}>
              <RefreshIcon />
            </IconButton>
          }
        >
          Failed to load knowledge graph. Click refresh to retry.
        </Alert>
      </Box>
    );
  }

  return (
    <Box className={className} sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Header */}
      <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <GraphIcon sx={{ mr: 1 }} />
          <Typography variant="h6">Knowledge Graph</Typography>
          <IconButton size="small" sx={{ ml: 'auto' }} onClick={handleRefresh}>
            <RefreshIcon />
          </IconButton>
        </Box>

        {/* Search */}
        <TextField
          fullWidth
          size="small"
          placeholder="Search project knowledge..."
          value={searchQuery}
          onChange={handleSearch}
          InputProps={{
            startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} />,
          }}
        />
      </Box>

      {/* Content */}
      <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
        {/* Search Results */}
        {searchResults && searchResults.results.length > 0 && (
          <Box sx={{ mb: 3 }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Search Results ({searchResults.results.length})
            </Typography>
            <List dense>
              {searchResults.results.map((result, index) => (
                <ListItem
                  key={index}
                  button
                  onClick={() => handleNodeClick(result.node)}
                  selected={selectedNode?.id === result.node.id}
                >
                  <ListItemText
                    primary={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        {renderNodeTypeChip(result.node.type)}
                        <Typography variant="body2">{result.node.label}</Typography>
                        <Chip
                          size="small"
                          label={`${(result.score * 100).toFixed(0)}%`}
                          color={result.score > 0.8 ? 'success' : 'default'}
                          sx={{ ml: 'auto', fontSize: '0.7rem' }}
                        />
                      </Box>
                    }
                    secondary={result.context}
                  />
                </ListItem>
              ))}
            </List>
          </Box>
        )}

        {/* Insights */}
        {graphData?.insights && graphData.insights.length > 0 && (
          <Box sx={{ mb: 3 }}>
            <Typography variant="subtitle2" sx={{ mb: 1, display: 'flex', alignItems: 'center' }}>
              <InsightIcon sx={{ mr: 0.5, fontSize: '1rem' }} />
              AI Insights
            </Typography>
            {graphData.insights.map((insight, index) => (
              <Alert key={index} severity="info" sx={{ mb: 1 }}>
                {insight}
              </Alert>
            ))}
          </Box>
        )}

        {/* Selected Node Details */}
        {selectedNode && (
          <Card variant="outlined" sx={{ mb: 2 }}>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                {renderNodeTypeChip(selectedNode.type)}
                <Typography variant="h6">{selectedNode.label}</Typography>
              </Box>
              {selectedNode.description && (
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  {selectedNode.description}
                </Typography>
              )}
              <Typography variant="caption" color="text.secondary">
                Confidence: {(selectedNode.confidence * 100).toFixed(1)}%
              </Typography>

              {/* Related edges */}
              {graphData?.edges && (
                <Box sx={{ mt: 2 }}>
                  <Typography variant="subtitle2" sx={{ mb: 1 }}>
                    <LinkIcon sx={{ mr: 0.5, fontSize: '0.9rem' }} />
                    Relationships
                  </Typography>
                  {graphData.edges
                    .filter(e => e.source === selectedNode.id || e.target === selectedNode.id)
                    .map((edge, index) => (
                      <Chip
                        key={index}
                        size="small"
                        label={`${edge.type} (${(edge.weight * 100).toFixed(0)}%)`}
                        sx={{ mr: 0.5, mb: 0.5 }}
                      />
                    ))}
                </Box>
              )}
            </CardContent>
          </Card>
        )}

        {/* Node Summary */}
        {graphData?.nodes && (
          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Graph Summary
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              {Array.from(new Set(graphData.nodes.map(n => n.type))).map(type => {
                const count = graphData.nodes.filter(n => n.type === type).length;
                return (
                  <Tooltip key={type} title={`${count} ${type.toLowerCase()}s`}>
                    <Chip
                      label={`${type}: ${count}`}
                      size="small"
                      variant="outlined"
                    />
                  </Tooltip>
                );
              })}
            </Box>
          </Box>
        )}

        {/* Empty State */}
        {!searchQuery && (!graphData?.nodes || graphData.nodes.length === 0) && (
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <GraphIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 2 }} />
            <Typography variant="body2" color="text.secondary">
              {projectId
                ? 'No knowledge graph data available for this project yet.'
                : 'Select a project to view its knowledge graph.'}
            </Typography>
          </Box>
        )}
      </Box>
    </Box>
  );
};

export default KnowledgeGraphPanel;
