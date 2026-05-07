/**
 * UnifiedRightPanel - AI Assistant Panel
 */

import React, { useState } from 'react';
import {
  Box,
  Tabs,
  Tab,
  Typography,
  IconButton,
  Button,
} from '@ghatana/design-system';
import { Drawer } from '@ghatana/design-system';
import { Zap as Bolt, X as Close, Lightbulb, Palette, Bot as SmartToy } from 'lucide-react';
import { useAtom } from 'jotai';
import { rightPanelOpenAtom, uiAtom, type UIState } from '../../../state/atoms/unifiedCanvasAtom';
import { ShapeStylePicker } from './ShapeStylePicker';

type CanvasPanelNode = {
    id: string;
    type: string;
    data: Record<string, unknown>;
};

type RightPanelTab = UIState['rightPanelTab'];

interface UnifiedRightPanelProps {
    selectedNodeIds?: string[];
    nodes?: CanvasPanelNode[];
    onUpdateNode?: (nodeId: string, updates: Record<string, unknown>) => void;
}

export function UnifiedRightPanel({ selectedNodeIds = [], nodes = [], onUpdateNode }: UnifiedRightPanelProps) {
    const [panelOpen, setPanelOpen] = useAtom(rightPanelOpenAtom);
    const [ui, setUI] = useAtom(uiAtom);
    const [activeTab, setActiveTab] = useState<RightPanelTab>(ui.rightPanelTab);

    const handleTabChange = (_event: React.SyntheticEvent, newValue: RightPanelTab) => {
        setActiveTab(newValue);
        setUI({ ...ui, rightPanelTab: newValue });
    };

    if (!panelOpen) {
        return (
            <IconButton
                onClick={() => setPanelOpen(true)}
                className="absolute right-[16px] top-[50%] bg-white dark:bg-surface border border-border dark:border-border hover:bg-surface-muted hover:dark:bg-surface" style={{ transform: 'translateY(-50%)' }} >
                <SmartToy size={16} />
            </IconButton>
        );
    }

    return (
        <Drawer
            anchor="right"
            open={panelOpen}
            onClose={() => setPanelOpen(false)}
            className="relative h-full w-[360px] shrink-0 border-l"
            PaperProps={{ style: { borderColor: 'divider' } }}
        >
            <Box className="flex flex-col h-full">
                {/* Header */}
                <Box
                    className="flex items-center justify-between p-4 border-border dark:border-border border-b" >
                    <Typography variant="h6">Assistant</Typography>
                    <IconButton size="small" onClick={() => setPanelOpen(false)}>
                        <Close size={16} />
                    </IconButton>
                </Box>

                {/* Tabs */}
                <Tabs value={activeTab} onChange={handleTabChange} variant="underline" fullWidth>
                    <Tab icon={<Palette size={16} />} label="Style" value="style" />
                    <Tab icon={<Lightbulb size={16} />} label="Guide" value="guidance" />
                    <Tab icon={<SmartToy size={16} />} label="AI" value="ai" />
                    <Tab icon={<Bolt size={16} />} label="Gen" value="generate" />
                </Tabs>

                {/* Content */}
                <Box className="flex-1 overflow-auto">
                    {activeTab === 'style' && (
                        <ShapeStylePicker
                            selectedNodeIds={selectedNodeIds}
                            nodes={nodes}
                            onUpdateNode={onUpdateNode ?? (() => {})}
                        />
                    )}
                    {activeTab === 'guidance' && (
                        <Box className="p-4">
                            <Typography variant="subtitle2" gutterBottom>
                                What to do now:
                            </Typography>
                            <Box className="mt-4 flex flex-col gap-2">
                                <Box className="p-4 rounded bg-surface-muted dark:bg-surface">
                                    <Typography variant="body2">
                                        1. Complete architecture diagram
                                    </Typography>
                                </Box>
                                <Box className="p-4 rounded bg-surface-muted dark:bg-surface">
                                    <Typography variant="body2">
                                        2. Document API decisions
                                    </Typography>
                                </Box>
                                <Box className="p-4 rounded bg-surface-muted dark:bg-surface">
                                    <Typography variant="body2">
                                        3. Create UX wireframes
                                    </Typography>
                                </Box>
                            </Box>
                        </Box>
                    )}

                    {activeTab === 'ai' && (
                        <Box>
                            <Typography variant="subtitle2" gutterBottom>
                                Ask the guided assistant anything...
                            </Typography>
                            <Box className="mt-4">
                                <Box
                                    className="p-3 rounded border border-border dark:border-border min-h-[100px]"
                                >
                                    <Typography variant="body2" color="text.secondary">
                                        What should I do next?
                                    </Typography>
                                </Box>
                                <Button variant="contained" fullWidth className="mt-2">
                                    Send
                                </Button>
                            </Box>
                        </Box>
                    )}

                    {activeTab === 'validate' && (
                        <Box>
                            <Typography variant="h4" align="center" className="my-6">
                                78%
                            </Typography>
                            <Typography variant="subtitle2" gutterBottom>
                                Validation Score
                            </Typography>
                            <Box className="mt-4">
                                <Box className="p-4 rounded mb-2" style={{ backgroundColor: 'error.50' }} >
                                    <Typography variant="body2" fontWeight={600}>
                                        ❌ Missing API endpoint
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        Location: User Service
                                    </Typography>
                                </Box>
                                <Box className="p-4 rounded" style={{ backgroundColor: 'warning.50' }} >
                                    <Typography variant="body2" fontWeight={600}>
                                        ⚠️ No tests defined
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        Recommended: Add unit tests
                                    </Typography>
                                </Box>
                            </Box>
                        </Box>
                    )}

                    {activeTab === 'generate' && (
                        <Box>
                            <Typography variant="subtitle2" gutterBottom>
                                Ready to generate:
                            </Typography>
                            <Box className="mt-4 flex flex-col gap-2">
                                <Typography variant="body2">✓ 3 API endpoints</Typography>
                                <Typography variant="body2">✓ 2 React components</Typography>
                                <Typography variant="body2">✓ 1 Database schema</Typography>
                            </Box>
                            <Button variant="contained" fullWidth className="mt-6">
                                Generate Code →
                            </Button>
                        </Box>
                    )}
                </Box>
            </Box>
        </Drawer>
    );
}
