import React from 'react';
import { NodeProps, Node, Handle, Position } from '@xyflow/react';
import { useAtom } from 'jotai';
import { selectedNodeAtom } from '@/stores/workflow.store';

type ApiCallNodeType = Node<{
  label?: string;
  config?: {
    method?: string;
    url?: string;
  };
  status?: 'idle' | 'running' | 'completed' | 'error' | string;
  error?: string;
}, 'apiCall'>;

/**
 * API Call node component for ReactFlow.
 *
 * <p><b>Purpose</b><br>
 * Represents an API call step in a workflow. Displays node status,
 * configuration, and error state.
 *
 * <p><b>Features</b><br>
 * - API endpoint display
 * - Method indicator (GET, POST, PUT, DELETE)
 * - Status display (pending, running, completed, error)
 * - Error highlighting
 * - Click selection
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { ApiCallNode } from '@/components/workflow/nodes/ApiCallNode';
 *
 * const nodeTypes = {
 *   apiCall: ApiCallNode,
 * };
 *
 * <ReactFlow nodeTypes={nodeTypes} ... />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose API call workflow node
 * @doc.layer frontend
 */
export function ApiCallNode(props: NodeProps<ApiCallNodeType>): React.ReactElement {
  const { data, id } = props;
  const [selectedNodeId] = useAtom(selectedNodeAtom);
  const isSelected = selectedNodeId === id;

  const method = data.config?.method ?? 'GET';
  const url = data.config?.url ?? 'https://api.example.com';
  const status = data.status ?? 'idle';

  const getStatusColor = () => {
    switch (status) {
      case 'running':
        return 'border-blue-500 bg-blue-50';
      case 'completed':
        return 'border-green-500 bg-green-50';
      case 'error':
        return 'border-red-500 bg-red-50';
      default:
        return 'border-gray-300 bg-white';
    }
  };

  const getMethodColor = () => {
    switch (method) {
      case 'POST':
        return 'bg-green-100 text-green-800';
      case 'PUT':
        return 'bg-blue-100 text-blue-800';
      case 'DELETE':
        return 'bg-red-100 text-red-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div
      className={`px-4 py-3 rounded-lg border-2 shadow-md transition-all ${getStatusColor()} ${
        isSelected ? 'ring-2 ring-primary-500' : ''
      }`}
    >
      <Handle type="target" position={Position.Top} />

      <div className="flex items-center gap-2 mb-2">
        <span className={`px-2 py-1 rounded text-xs font-semibold ${getMethodColor()}`}>
          {method}
        </span>
        <span className="text-xs text-gray-600">API Call</span>
      </div>

      <div className="text-sm font-medium text-gray-900 truncate">{data.label ?? 'API Call'}</div>
      <div className="text-xs text-gray-600 truncate mt-1">{url}</div>

      {status === 'error' && data.error && (
        <div className="text-xs text-red-600 mt-2 truncate">{data.error}</div>
      )}

      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}
