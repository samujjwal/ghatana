/**
 * Type Selector Modal Component
 * 
 * Modal for changing the content type of an artifact node.
 * Shows compatible types first, warns about lossy conversions.
 * 
 * @doc.type component
 * @doc.purpose Content type switching UI
 * @doc.layer product
 * @doc.pattern Modal Dialog
 */

import React, { useState } from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, Typography, InteractiveList as List, ListItemButton, ListItemIcon, ListItemText, Accordion, AccordionSummary, AccordionDetails, Alert, Box, Chip, Grid } from '@ghatana/ui';
import { ChevronDown as ExpandIcon, Code as CodeIcon, FileText as DocIcon, Building2 as ArchitectureIcon, Bug as TestIcon, Plug as ApiIcon, Table as TableIcon, ClipboardList as RequirementIcon, BarChart3 as ChartIcon } from 'lucide-react';
import { ArtifactType } from '@/types/fow-stages';

// Content type metadata
interface ContentTypeInfo {
    type: ArtifactType;
    label: string;
    description: string;
    icon: React.ReactNode;
    category: 'code' | 'design' | 'documentation' | 'data' | 'requirements';
}

const CONTENT_TYPES: ContentTypeInfo[] = [
    // Code types
    { type: 'code', label: 'Code Editor', description: 'Write and edit source code', icon: <CodeIcon />, category: 'code' },
    { type: 'test', label: 'Test Case', description: 'Automated test specifications', icon: <TestIcon />, category: 'code' },

    // Design types
    { type: 'architecture', label: 'Architecture Diagram', description: 'System architecture visualization', icon: <ArchitectureIcon />, category: 'design' },
    { type: 'design', label: 'Design Mockup', description: 'UI/UX design mockups', icon: <ArchitectureIcon />, category: 'design' },

    // Documentation types
    { type: 'documentation', label: 'Markdown Document', description: 'Rich text documentation', icon: <DocIcon />, category: 'documentation' },
    { type: 'adr', label: 'Architecture Decision Record', description: 'Technical decision documentation', icon: <DocIcon />, category: 'documentation' },

    // Requirements types
    { type: 'requirement', label: 'Requirement', description: 'Functional requirements', icon: <RequirementIcon />, category: 'requirements' },
    { type: 'brief', label: 'Project Brief', description: 'High-level project overview', icon: <RequirementIcon />, category: 'requirements' },

    // Data types
    { type: 'evidence', label: 'API Specification', description: 'REST/GraphQL API specs', icon: <ApiIcon />, category: 'data' },
];

// Compatibility matrix - defines which conversions are compatible
const COMPATIBILITY_MATRIX: Record<ArtifactType, ArtifactType[]> = {
    'code': ['test', 'documentation', 'evidence'],
    'test': ['code', 'documentation'],
    'architecture': ['design', 'documentation'],
    'design': ['architecture', 'documentation'],
    'documentation': ['code', 'test', 'architecture', 'design', 'requirement', 'brief', 'adr'],
    'adr': ['documentation', 'requirement'],
    'requirement': ['brief', 'documentation', 'test'],
    'brief': ['requirement', 'documentation'],
    'evidence': ['documentation', 'code'],
    'deployment': ['documentation', 'code'],
};

interface TypeSelectorModalProps {
    open: boolean;
    currentType: ArtifactType;
    artifactId: string;
    onClose: () => void;
    onTypeChange: (artifactId: string, newType: ArtifactType) => void;
}

export const TypeSelectorModal: React.FC<TypeSelectorModalProps> = ({
    open,
    currentType,
    artifactId,
    onClose,
    onTypeChange,
}) => {
    const [selectedType, setSelectedType] = useState<ArtifactType>(currentType);

    const compatibleTypes = COMPATIBILITY_MATRIX[currentType] || [];
    const isCompatible = compatibleTypes.includes(selectedType);
    const isDifferent = selectedType !== currentType;

    const handleConfirm = () => {
        if (isDifferent) {
            onTypeChange(artifactId, selectedType);
        }
        onClose();
    };

    const getTypeInfo = (type: ArtifactType): ContentTypeInfo | undefined => {
        return CONTENT_TYPES.find(t => t.type === type);
    };

    const renderTypeOption = (typeInfo: ContentTypeInfo, isRecommended: boolean = false) => (
        <ListItemButton
            key={typeInfo.type}
            selected={typeInfo.type === selectedType}
            onClick={() => setSelectedType(typeInfo.type)}
            className="rounded mb-1" style={{ border: typeInfo.type === selectedType ? 2 : 1, borderColor: typeInfo.type === selectedType ? 'primary.main' : 'divider' }}
        >
            <ListItemIcon>{typeInfo.icon}</ListItemIcon>
            <ListItemText
                primary={
                    <Box className="flex items-center gap-2">
                        {typeInfo.label}
                        {isRecommended && (
                            <Chip label="Recommended" size="sm" tone="success" />
                        )}
                    </Box>
                }
                secondary={typeInfo.description}
            />
        </ListItemButton>
    );

    return (
        <Dialog
            open={open}
            onClose={onClose}
            size="md"
            fullWidth
        >
            <DialogTitle>Change Content Type</DialogTitle>
            <DialogContent>
                <Box className="mb-6">
                    <Typography as="p" className="text-sm font-medium" color="text.secondary">
                        Current Type:
                    </Typography>
                    <Box className="flex items-center gap-2 mt-1">
                        {getTypeInfo(currentType)?.icon}
                        <Typography as="h6">
                            {getTypeInfo(currentType)?.label}
                        </Typography>
                    </Box>
                </Box>

                {/* Compatible Types (Recommended) */}
                {compatibleTypes.length > 0 && (
                    <Box className="mb-6">
                        <Typography as="h6" gutterBottom className="flex items-center gap-2">
                            Recommended Types
                            <Chip label="Compatible" size="sm" tone="success" variant="outlined" />
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary" gutterBottom>
                            These conversions preserve data and structure
                        </Typography>
                        <List>
                            {compatibleTypes.map(type => {
                                const typeInfo = getTypeInfo(type);
                                return typeInfo ? renderTypeOption(typeInfo, true) : null;
                            })}
                        </List>
                    </Box>
                )}

                {/* All Types */}
                <Accordion>
                    <AccordionSummary expandIcon={<ExpandIcon />}>
                        <Typography as="h6">All Content Types</Typography>
                    </AccordionSummary>
                    <AccordionDetails>
                        <Grid container spacing={2}>
                            {['code', 'design', 'documentation', 'requirements', 'data'].map(category => {
                                const categoryTypes = CONTENT_TYPES.filter(t => t.category === category);
                                if (categoryTypes.length === 0) return null;

                                return (
                                    <Grid item xs={12} key={category}>
                                        <Typography as="p" className="text-sm font-medium" color="text.secondary" gutterBottom className="capitalize">
                                            {category}
                                        </Typography>
                                        <List dense>
                                            {categoryTypes.map(typeInfo => renderTypeOption(typeInfo, false))}
                                        </List>
                                    </Grid>
                                );
                            })}
                        </Grid>
                    </AccordionDetails>
                </Accordion>

                {/* Warning for incompatible conversions */}
                {isDifferent && !isCompatible && (
                    <Alert severity="warning" className="mt-4">
                        <Typography as="p" className="text-sm" fontWeight="bold" gutterBottom>
                            ⚠️ Potentially Lossy Conversion
                        </Typography>
                        <Typography as="p" className="text-sm">
                            Changing from <strong>{getTypeInfo(currentType)?.label}</strong> to <strong>{getTypeInfo(selectedType)?.label}</strong> may result in data loss.
                            The system will attempt to preserve as much information as possible, but some structure or content may not transfer correctly.
                        </Typography>
                    </Alert>
                )}

                {/* Info for compatible conversions */}
                {isDifferent && isCompatible && (
                    <Alert severity="info" className="mt-4">
                        <Typography as="p" className="text-sm">
                            ✓ This conversion is compatible. Data will be automatically migrated to the new format.
                        </Typography>
                    </Alert>
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>
                    Cancel
                </Button>
                <Button
                    onClick={handleConfirm}
                    variant="solid"
                    disabled={!isDifferent}
                    color={isDifferent && !isCompatible ? 'warning' : 'primary'}
                >
                    {isDifferent && !isCompatible ? 'Change Anyway' : 'Change Type'}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
