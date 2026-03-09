import { GitBranch as LayoutIcon } from 'lucide-react';
import {
  Box,
  Typography,
  Button,
  Stack,
  Select,
  FormControl,
  InputLabel,
  Surface as Paper,
} from '@ghatana/ui';
import { MenuItem } from '@ghatana/ui';
import React from 'react';

import type { CanvasTool, CanvasContext } from '../../../state/tools/ToolAPI';

/**
 *
 */
export class LayoutTool implements CanvasTool {
  id = 'layout';
  name = 'Auto Layout';
  description = 'Automatically arrange nodes';
  icon = '🔀';
  category = 'analysis' as const;

  private context: CanvasContext | null = null;

  /**
   *
   */
  initialize(context: CanvasContext): void {
    this.context = context;
  }

  /**
   *
   */
  private applyGridLayout(context: CanvasContext): void {
    const state = context.getCanvasState();
    const nodes = state.elements?.filter((el: unknown) => el.kind === 'node' || el.kind === 'component') || [];
    
    const cols = Math.ceil(Math.sqrt(nodes.length));
    const spacing = 200;

    nodes.forEach((node: unknown, index: number) => {
      const row = Math.floor(index / cols);
      const col = index % cols;
      
      context.updateElement(node.id, {
        position: {
          x: col * spacing + 100,
          y: row * spacing + 100,
        },
      });
    });
  }

  /**
   *
   */
  private applyHierarchicalLayout(context: CanvasContext): void {
    const state = context.getCanvasState();
    const nodes = state.elements?.filter((el: unknown) => el.kind === 'node' || el.kind === 'component') || [];
    
    // Simple top-to-bottom layout
    const spacing = 150;
    nodes.forEach((node: unknown, index: number) => {
      context.updateElement(node.id, {
        position: {
          x: 100 + (index % 3) * 250,
          y: 100 + Math.floor(index / 3) * spacing,
        },
      });
    });
  }

  /**
   *
   */
  renderPanel(context: CanvasContext): React.ReactNode {
    return (
      <LayoutPanel
        onApplyGrid={() => this.applyGridLayout(context)}
        onApplyHierarchical={() => this.applyHierarchicalLayout(context)}
      />
    );
  }
}

/**
 *
 */
interface LayoutPanelProps {
  onApplyGrid: () => void;
  onApplyHierarchical: () => void;
}

const LayoutPanel: React.FC<LayoutPanelProps> = ({ onApplyGrid, onApplyHierarchical }) => {
  const [layoutType, setLayoutType] = React.useState('grid');

  const handleApply = () => {
    if (layoutType === 'grid') {
      onApplyGrid();
    } else {
      onApplyHierarchical();
    }
  };

  return (
    <Paper className="p-4">
      <Box className="flex items-center mb-4">
        <LayoutIcon className="mr-2" />
        <Typography variant="h6">Auto Layout</Typography>
      </Box>

      <Stack spacing={2}>
        <FormControl fullWidth size="small">
          <InputLabel>Layout Type</InputLabel>
          <Select
            value={layoutType}
            label="Layout Type"
            onChange={(e) => setLayoutType(e.target.value)}
          >
            <MenuItem value="grid">Grid</MenuItem>
            <MenuItem value="hierarchical">Hierarchical</MenuItem>
          </Select>
        </FormControl>

        <Button variant="contained" onClick={handleApply} fullWidth>
          Apply Layout
        </Button>

        <Typography variant="caption" color="text.secondary">
          {layoutType === 'grid'
            ? 'Arranges nodes in a grid pattern'
            : 'Arranges nodes in a top-to-bottom hierarchy'}
        </Typography>
      </Stack>
    </Paper>
  );
};
