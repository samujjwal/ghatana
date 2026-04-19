/**
 * Connection Editor
 *
 * Editor for creating and managing component connections (event, data, navigation).
 *
 * @packageDocumentation
 */

import { Plus as AddIcon, Trash2 as DeleteIcon } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  IconButton,
  Divider,
  Button,
  TextField,
  Select,
  MenuItem,
  Paper,
} from '@ghatana/design-system';
import React, { useState, useCallback } from 'react';

import type {
  EventConnection,
  DataConnection,
  NavigationConnection,
} from '@yappc/config-schema';

/**
 * @doc.type component
 * @doc.purpose Component connection editor for event, data, and navigation connections
 * @doc.layer product
 * @doc.pattern Widget
 */
interface ConnectionEditorProps {
  connections?: {
    events?: EventConnection[];
    data?: DataConnection[];
    navigation?: NavigationConnection[];
  };
  onConnectionsChange?: (connections: {
    events?: EventConnection[];
    data?: DataConnection[];
    navigation?: NavigationConnection[];
  }) => void;
  availableComponents?: Array<{ id: string; type: string }>;
}

type ConnectionType = 'event' | 'data' | 'navigation';

export const ConnectionEditor: React.FC<ConnectionEditorProps> = ({
  connections = {},
  onConnectionsChange,
  availableComponents = [],
}) => {
  const [activeTab, setActiveTab] = useState<ConnectionType>('event');
  const [editingConnection, setEditingConnection] = useState<
    EventConnection | DataConnection | NavigationConnection | null
  >(null);

  const eventConnections = connections.events || [];
  const dataConnections = connections.data || [];
  const navigationConnections = connections.navigation || [];

  const handleAddConnection = useCallback(() => {
    const baseId = `conn-${Date.now()}`;

    if (activeTab === 'event') {
      const newConnection: EventConnection = {
        id: baseId,
        sourceComponentId: '',
        sourceEvent: 'onClick',
        targetComponentId: '',
        targetAction: 'setState',
      };
      setEditingConnection(newConnection);
    } else if (activeTab === 'data') {
      const newConnection: DataConnection = {
        id: baseId,
        sourceId: '',
        sourcePath: '',
        targetComponentId: '',
        targetProp: '',
        mode: 'one-way',
      };
      setEditingConnection(newConnection);
    } else if (activeTab === 'navigation') {
      const newConnection: NavigationConnection = {
        id: baseId,
        sourceComponentId: '',
        sourceEvent: 'onClick',
        targetPageId: '',
        targetRoute: '/',
      };
      setEditingConnection(newConnection);
    }
  }, [activeTab]);

  const handleSaveConnection = useCallback(() => {
    if (!editingConnection) return;

    const updated = { ...connections };

    if (activeTab === 'event') {
      updated.events = [...eventConnections, editingConnection as EventConnection];
    } else if (activeTab === 'data') {
      updated.data = [...dataConnections, editingConnection as DataConnection];
    } else if (activeTab === 'navigation') {
      updated.navigation = [...navigationConnections, editingConnection as NavigationConnection];
    }

    onConnectionsChange?.(updated);
    setEditingConnection(null);
  }, [editingConnection, connections, activeTab, eventConnections, dataConnections, navigationConnections, onConnectionsChange]);

  const handleDeleteConnection = useCallback(
    (id: string, type: ConnectionType) => {
      const updated = { ...connections };

      if (type === 'event') {
        updated.events = eventConnections.filter((c) => c.id !== id);
      } else if (type === 'data') {
        updated.data = dataConnections.filter((c) => c.id !== id);
      } else if (type === 'navigation') {
        updated.navigation = navigationConnections.filter((c) => c.id !== id);
      }

      onConnectionsChange?.(updated);
    },
    [connections, eventConnections, dataConnections, navigationConnections, onConnectionsChange]
  );

  const handleUpdateEditingConnection = useCallback(
    (field: string, value: unknown) => {
      if (!editingConnection) return;
      setEditingConnection({ ...editingConnection, [field]: value });
    },
    [editingConnection]
  );

  const renderConnectionForm = () => {
    if (!editingConnection) return null;

    return (
      <Paper variant="outlined" className="p-4 mt-4">
        <Typography variant="subtitle2" gutterBottom>
          {activeTab === 'event' && 'Event Connection'}
          {activeTab === 'data' && 'Data Connection'}
          {activeTab === 'navigation' && 'Navigation Connection'}
        </Typography>

        <Stack spacing={2}>
          {activeTab === 'event' && (
            <>
              <TextField
                label="Source Component"
                select
                value={(editingConnection as EventConnection).sourceComponentId}
                onChange={(e) => handleUpdateEditingConnection('sourceComponentId', e.target.value)}
                fullWidth
                size="small"
              >
                {availableComponents.map((comp) => (
                  <MenuItem key={comp.id} value={comp.id}>
                    {comp.type} ({comp.id})
                  </MenuItem>
                ))}
              </TextField>

              <TextField
                label="Source Event"
                value={(editingConnection as EventConnection).sourceEvent}
                onChange={(e) => handleUpdateEditingConnection('sourceEvent', e.target.value)}
                fullWidth
                size="small"
              />

              <TextField
                label="Target Component"
                select
                value={(editingConnection as EventConnection).targetComponentId}
                onChange={(e) => handleUpdateEditingConnection('targetComponentId', e.target.value)}
                fullWidth
                size="small"
              >
                {availableComponents.map((comp) => (
                  <MenuItem key={comp.id} value={comp.id}>
                    {comp.type} ({comp.id})
                  </MenuItem>
                ))}
              </TextField>

              <TextField
                label="Target Action"
                value={(editingConnection as EventConnection).targetAction}
                onChange={(e) => handleUpdateEditingConnection('targetAction', e.target.value)}
                fullWidth
                size="small"
              />

              <TextField
                label="Transform (optional)"
                value={(editingConnection as EventConnection).transform || ''}
                onChange={(e) => handleUpdateEditingConnection('transform', e.target.value)}
                fullWidth
                size="small"
              />
            </>
          )}

          {activeTab === 'data' && (
            <>
              <TextField
                label="Source Component"
                select
                value={(editingConnection as DataConnection).sourceId}
                onChange={(e) => handleUpdateEditingConnection('sourceId', e.target.value)}
                fullWidth
                size="small"
              >
                {availableComponents.map((comp) => (
                  <MenuItem key={comp.id} value={comp.id}>
                    {comp.type} ({comp.id})
                  </MenuItem>
                ))}
              </TextField>

              <TextField
                label="Source Path"
                value={(editingConnection as DataConnection).sourcePath}
                onChange={(e) => handleUpdateEditingConnection('sourcePath', e.target.value)}
                fullWidth
                size="small"
                placeholder="e.g., value"
              />

              <TextField
                label="Target Component"
                select
                value={(editingConnection as DataConnection).targetComponentId}
                onChange={(e) => handleUpdateEditingConnection('targetComponentId', e.target.value)}
                fullWidth
                size="small"
              >
                {availableComponents.map((comp) => (
                  <MenuItem key={comp.id} value={comp.id}>
                    {comp.type} ({comp.id})
                  </MenuItem>
                ))}
              </TextField>

              <TextField
                label="Target Property"
                value={(editingConnection as DataConnection).targetProp}
                onChange={(e) => handleUpdateEditingConnection('targetProp', e.target.value)}
                fullWidth
                size="small"
                placeholder="e.g., value"
              />

              <TextField
                label="Mode"
                select
                value={(editingConnection as DataConnection).mode}
                onChange={(e) => handleUpdateEditingConnection('mode', e.target.value)}
                fullWidth
                size="small"
              >
                <MenuItem value="one-way">One-way</MenuItem>
                <MenuItem value="two-way">Two-way</MenuItem>
                <MenuItem value="one-time">One-time</MenuItem>
              </TextField>

              <TextField
                label="Transform (optional)"
                value={(editingConnection as DataConnection).transform || ''}
                onChange={(e) => handleUpdateEditingConnection('transform', e.target.value)}
                fullWidth
                size="small"
              />
            </>
          )}

          {activeTab === 'navigation' && (
            <>
              <TextField
                label="Source Component"
                select
                value={(editingConnection as NavigationConnection).sourceComponentId}
                onChange={(e) => handleUpdateEditingConnection('sourceComponentId', e.target.value)}
                fullWidth
                size="small"
              >
                {availableComponents.map((comp) => (
                  <MenuItem key={comp.id} value={comp.id}>
                    {comp.type} ({comp.id})
                  </MenuItem>
                ))}
              </TextField>

              <TextField
                label="Source Event"
                value={(editingConnection as NavigationConnection).sourceEvent}
                onChange={(e) => handleUpdateEditingConnection('sourceEvent', e.target.value)}
                fullWidth
                size="small"
              />

              <TextField
                label="Target Page ID"
                value={(editingConnection as NavigationConnection).targetPageId}
                onChange={(e) => handleUpdateEditingConnection('targetPageId', e.target.value)}
                fullWidth
                size="small"
              />

              <TextField
                label="Target Route"
                value={(editingConnection as NavigationConnection).targetRoute}
                onChange={(e) => handleUpdateEditingConnection('targetRoute', e.target.value)}
                fullWidth
                size="small"
                placeholder="/page-path"
              />
            </>
          )}

          <Stack direction="row" spacing={1}>
            <Button
              variant="contained"
              onClick={handleSaveConnection}
              size="small"
            >
              Save
            </Button>
            <Button
              variant="outlined"
              onClick={() => setEditingConnection(null)}
              size="small"
            >
              Cancel
            </Button>
          </Stack>
        </Stack>
      </Paper>
    );
  };

  const renderConnectionList = (type: ConnectionType) => {
    const list =
      type === 'event'
        ? eventConnections
        : type === 'data'
        ? dataConnections
        : navigationConnections;

    if (list.length === 0) {
      return (
        <Typography color="text.secondary" variant="body2">
          No {type} connections
        </Typography>
      );
    }

    return (
      <Stack spacing={1}>
        {list.map((connection) => (
          <Paper
            key={connection.id}
            variant="outlined"
            className="p-2 flex justify-between items-center"
          >
            <Box className="flex-1">
              <Typography variant="body2" className="font-medium">
                {type === 'event' && (connection as EventConnection).sourceEvent}
                {type === 'data' && `${(connection as DataConnection).sourcePath} → ${(connection as DataConnection).targetProp}`}
                {type === 'navigation' && (connection as NavigationConnection).targetRoute}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {type === 'event' && `${(connection as EventConnection).sourceComponentId} → ${(connection as EventConnection).targetComponentId}`}
                {type === 'data' && `${(connection as DataConnection).sourceId} → ${(connection as DataConnection).targetComponentId}`}
                {type === 'navigation' && `${(connection as NavigationConnection).sourceComponentId} → ${(connection as NavigationConnection).targetPageId}`}
              </Typography>
            </Box>
            <IconButton
              size="small"
              color="error"
              onClick={() => handleDeleteConnection(connection.id, type)}
            >
              <DeleteIcon size={16} />
            </IconButton>
          </Paper>
        ))}
      </Stack>
    );
  };

  return (
    <Box data-testid="connection-editor">
      <Typography variant="h6" gutterBottom>
        Connections
      </Typography>

      {/* Tabs */}
      <Stack direction="row" spacing={1} mb={2}>
        <Button
          variant={activeTab === 'event' ? 'contained' : 'outlined'}
          size="small"
          onClick={() => setActiveTab('event')}
        >
          Events ({eventConnections.length})
        </Button>
        <Button
          variant={activeTab === 'data' ? 'contained' : 'outlined'}
          size="small"
          onClick={() => setActiveTab('data')}
        >
          Data ({dataConnections.length})
        </Button>
        <Button
          variant={activeTab === 'navigation' ? 'contained' : 'outlined'}
          size="small"
          onClick={() => setActiveTab('navigation')}
        >
          Navigation ({navigationConnections.length})
        </Button>
      </Stack>

      <Divider />

      {/* Add Button */}
      <Box className="mt-2">
        <Button
          variant="outlined"
          startIcon={<AddIcon size={16} />}
          onClick={handleAddConnection}
          size="small"
          disabled={editingConnection !== null}
        >
          Add {activeTab} Connection
        </Button>
      </Box>

      {/* Connection List */}
      <Box className="mt-2">{renderConnectionList(activeTab)}</Box>

      {/* Edit Form */}
      {renderConnectionForm()}
    </Box>
  );
};
