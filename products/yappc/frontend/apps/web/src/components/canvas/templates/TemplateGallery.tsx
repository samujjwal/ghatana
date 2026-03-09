/**
 * Template Gallery Component
 * 
 * Quick-start templates for YAPPC App Creator
 * Provides pre-configured architectures for common patterns
 * 
 * @doc.type component
 * @doc.purpose Template gallery with pre-built architectures
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useMemo } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@ghatana/ui';
import { CardMedia, TextField } from '@ghatana/ui';
import type { CanvasState } from '../../../components/canvas/workspace/canvasAtoms';

// ============================================================================
// Types
// ============================================================================

export interface AppTemplate {
    id: string;
    name: string;
    description: string;
    category: 'web' | 'mobile' | 'backend' | 'data' | 'ai' | 'microservices';
    difficulty: 'beginner' | 'intermediate' | 'advanced';
    thumbnail?: string;
    tags: string[];
    popular?: boolean;
    nodeCount: number;
    estimatedTime: string;
    canvasState: CanvasState;
}

export interface TemplateGalleryProps {
    open: boolean;
    onClose: () => void;
    onSelectTemplate: (template: AppTemplate) => void;
    recentTemplates?: string[]; // IDs of recently used templates
}

// ============================================================================
// Template Definitions
// ============================================================================

const TEMPLATES: AppTemplate[] = [
    {
        id: 'three-tier-web-app',
        name: '3-Tier Web Application',
        description: 'Classic web architecture with frontend, API layer, and database. Perfect for standard CRUD applications.',
        category: 'web',
        difficulty: 'beginner',
        tags: ['react', 'rest-api', 'postgresql', 'nginx'],
        popular: true,
        nodeCount: 6,
        estimatedTime: '2-3 hours',
        canvasState: {
            elements: [
                {
                    id: 'frontend-1',
                    type: 'component',
                    position: { x: 100, y: 100 },
                    size: { width: 160, height: 80 },
                    data: { label: 'React Frontend', technology: 'React', description: 'User interface layer' },
                },
                {
                    id: 'api-1',
                    type: 'api',
                    position: { x: 400, y: 100 },
                    size: { width: 160, height: 80 },
                    data: { label: 'REST API', technology: 'Node.js/Express', description: 'Business logic layer' },
                },
                {
                    id: 'database-1',
                    type: 'data',
                    position: { x: 700, y: 100 },
                    size: { width: 160, height: 80 },
                    data: { label: 'PostgreSQL', technology: 'PostgreSQL', description: 'Data persistence layer' },
                },
            ],
            connections: [
                { id: 'conn-1', source: 'frontend-1', target: 'api-1', label: 'HTTP/REST' },
                { id: 'conn-2', source: 'api-1', target: 'database-1', label: 'SQL' },
            ],
            metadata: {
                lifecycle: { phase: 'design', version: 1 },
                created: Date.now(),
                updated: Date.now(),
            },
        },
    },
    {
        id: 'microservices-architecture',
        name: 'Microservices Architecture',
        description: 'Scalable microservices with API gateway, service mesh, and distributed data stores.',
        category: 'microservices',
        difficulty: 'advanced',
        tags: ['kubernetes', 'docker', 'api-gateway', 'service-mesh', 'kafka'],
        popular: true,
        nodeCount: 12,
        estimatedTime: '1-2 days',
        canvasState: {
            elements: [
                {
                    id: 'api-gateway',
                    type: 'api',
                    position: { x: 400, y: 50 },
                    size: { width: 180, height: 80 },
                    data: { label: 'API Gateway', technology: 'Kong/Nginx', description: 'Single entry point' },
                },
                {
                    id: 'user-service',
                    type: 'component',
                    position: { x: 200, y: 200 },
                    size: { width: 150, height: 80 },
                    data: { label: 'User Service', technology: 'Spring Boot', description: 'User management' },
                },
                {
                    id: 'order-service',
                    type: 'component',
                    position: { x: 400, y: 200 },
                    size: { width: 150, height: 80 },
                    data: { label: 'Order Service', technology: 'Node.js', description: 'Order processing' },
                },
                {
                    id: 'inventory-service',
                    type: 'component',
                    position: { x: 600, y: 200 },
                    size: { width: 150, height: 80 },
                    data: { label: 'Inventory Service', technology: 'Python/FastAPI', description: 'Stock management' },
                },
                {
                    id: 'message-bus',
                    type: 'flow',
                    position: { x: 400, y: 350 },
                    size: { width: 180, height: 80 },
                    data: { label: 'Message Bus (Kafka)', technology: 'Apache Kafka', description: 'Event streaming' },
                },
            ],
            connections: [
                { id: 'conn-1', source: 'api-gateway', target: 'user-service', label: 'gRPC' },
                { id: 'conn-2', source: 'api-gateway', target: 'order-service', label: 'gRPC' },
                { id: 'conn-3', source: 'api-gateway', target: 'inventory-service', label: 'gRPC' },
                { id: 'conn-4', source: 'order-service', target: 'message-bus', label: 'Event' },
                { id: 'conn-5', source: 'inventory-service', target: 'message-bus', label: 'Subscribe' },
            ],
            metadata: {
                lifecycle: { phase: 'design', version: 1 },
                created: Date.now(),
                updated: Date.now(),
            },
        },
    },
    {
        id: 'blank-canvas',
        name: 'Blank Canvas',
        description: 'Start from scratch with a completely empty canvas. Build your unique architecture.',
        category: 'web',
        difficulty: 'beginner',
        tags: ['custom', 'blank', 'freestyle'],
        nodeCount: 0,
        estimatedTime: 'Varies',
        canvasState: {
            elements: [],
            connections: [],
            metadata: {
                lifecycle: { phase: 'design', version: 1 },
                created: Date.now(),
                updated: Date.now(),
            },
        },
    },
];

// ============================================================================
// Component
// ============================================================================

export const TemplateGallery: React.FC<TemplateGalleryProps> = ({
    open,
    onClose,
    onSelectTemplate,
    recentTemplates = [],
}) => {
    const [selectedCategory, setSelectedCategory] = useState<string>('all');
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedTemplate, setSelectedTemplate] = useState<AppTemplate | null>(null);

    // Filter templates
    const filteredTemplates = useMemo(() => {
        let filtered = TEMPLATES;

        // Category filter
        if (selectedCategory !== 'all') {
            filtered = filtered.filter((t) => t.category === selectedCategory);
        }

        // Search filter
        if (searchQuery) {
            const query = searchQuery.toLowerCase();
            filtered = filtered.filter(
                (t) =>
                    t.name.toLowerCase().includes(query) ||
                    t.description.toLowerCase().includes(query) ||
                    t.tags.some((tag) => tag.toLowerCase().includes(query))
            );
        }

        return filtered;
    }, [selectedCategory, searchQuery]);

    // Recent templates
    const recentTemplatesList = useMemo(() => {
        return TEMPLATES.filter((t) => recentTemplates.includes(t.id));
    }, [recentTemplates]);

    // Popular templates
    const popularTemplates = useMemo(() => {
        return TEMPLATES.filter((t) => t.popular);
    }, []);

    const handleSelectTemplate = (template: AppTemplate) => {
        setSelectedTemplate(template);
    };

    const handleUseTemplate = () => {
        if (selectedTemplate) {
            onSelectTemplate(selectedTemplate);
            onClose();
        }
    };

    return (
        <>
            <Dialog
                open={open}
                onClose={onClose}
                maxWidth="lg"
                fullWidth
                PaperProps={{
                    sx: {
                        minHeight: '80vh',
                        maxHeight: '90vh',
                    },
                }}
            >
                <DialogTitle>
                    <Stack direction="row" alignItems="center" justifyContent="space-between">
                        <Typography variant="h5" component="div">
                            ✨ Quick Start Templates
                        </Typography>
                        <TextField
                            size="small"
                            placeholder="Search templates..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-[300px]"
                        />
                    </Stack>
                </DialogTitle>

                <DialogContent dividers>
                    {/* Category Tabs */}
                    <Tabs
                        value={selectedCategory}
                        onChange={(_, value) => setSelectedCategory(value)}
                        className="mb-6 border-gray-200 dark:border-gray-700 border-b" >
                        <Tab label="All Templates" value="all" />
                        <Tab label="Web Apps" value="web" />
                        <Tab label="Mobile" value="mobile" />
                        <Tab label="Backend" value="backend" />
                        <Tab label="Microservices" value="microservices" />
                        <Tab label="AI/ML" value="ai" />
                        <Tab label="Data" value="data" />
                    </Tabs>

                    {/* Popular Templates Section */}
                    {selectedCategory === 'all' && !searchQuery && popularTemplates.length > 0 && (
                        <Box className="mb-8">
                            <Stack direction="row" alignItems="center" spacing={1} className="mb-4">
                                <Typography variant="h6">Popular Templates</Typography>
                            </Stack>
                            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                                {popularTemplates.map((template) => (
                                    <TemplateCard
                                        key={template.id}
                                        template={template}
                                        onSelect={handleSelectTemplate}
                                        selected={selectedTemplate?.id === template.id}
                                    />
                                ))}
                            </div>
                        </Box>
                    )}

                    {/* Recent Templates Section */}
                    {selectedCategory === 'all' && !searchQuery && recentTemplatesList.length > 0 && (
                        <Box className="mb-8">
                            <Stack direction="row" alignItems="center" spacing={1} className="mb-4">
                                <Typography variant="h6">Recently Used</Typography>
                            </Stack>
                            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                                {recentTemplatesList.map((template) => (
                                    <TemplateCard
                                        key={template.id}
                                        template={template}
                                        onSelect={handleSelectTemplate}
                                        selected={selectedTemplate?.id === template.id}
                                    />
                                ))}
                            </div>
                        </Box>
                    )}

                    {/* All Templates */}
                    <Box>
                        {selectedCategory === 'all' && !searchQuery && (
                            <Typography variant="h6" className="mb-4">
                                All Templates
                            </Typography>
                        )}
                        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                            {filteredTemplates.map((template) => (
                                <TemplateCard
                                    key={template.id}
                                    template={template}
                                    onSelect={handleSelectTemplate}
                                    selected={selectedTemplate?.id === template.id}
                                />
                            ))}
                        </div>

                        {filteredTemplates.length === 0 && (
                            <Box className="py-16 text-center">
                                <Typography variant="h6" color="text.secondary" gutterBottom>
                                    No templates found
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    Try adjusting your search or category filter
                                </Typography>
                            </Box>
                        )}
                    </Box>
                </DialogContent>

                <DialogActions>
                    <Button onClick={onClose} color="secondary">
                        Cancel
                    </Button>
                    <Button
                        onClick={handleUseTemplate}
                        variant="contained"
                        color="primary"
                        disabled={!selectedTemplate}
                    >
                        Use Template
                    </Button>
                </DialogActions>
            </Dialog>
        </>
    );
};

// ============================================================================
// Template Card Component
// ============================================================================

interface TemplateCardProps {
    template: AppTemplate;
    onSelect: (template: AppTemplate) => void;
    selected: boolean;
}

const TemplateCard: React.FC<TemplateCardProps> = ({ template, onSelect, selected }) => {
    const difficultyColor = {
        beginner: 'success',
        intermediate: 'warning',
        advanced: 'error',
    } as const;

    return (
        <Card
            className="cursor-pointer transition-all duration-200 hover:shadow" style={{ border: selected ? 2 : 1, borderColor: selected ? 'primary.main' : 'divider' }}
            onClick={() => onSelect(template)}
        >
            {template.thumbnail && (
                <CardMedia
                    component="img"
                    height="140"
                    image={template.thumbnail}
                    alt={template.name}
                />
            )}
            <CardContent>
                <Stack spacing={1.5}>
                    <Box>
                        <Typography variant="h6" component="div" gutterBottom>
                            {template.name}
                        </Typography>
                        <Typography variant="body2" color="text.secondary" className="min-h-[40px]">
                            {template.description}
                        </Typography>
                    </Box>

                    <Stack direction="row" spacing={1} flexWrap="wrap">
                        <Chip
                            label={template.difficulty}
                            size="small"
                            color={difficultyColor[template.difficulty]}
                        />
                        <Chip
                            label={`${template.nodeCount} components`}
                            size="small"
                            variant="outlined"
                        />
                        <Chip
                            label={template.estimatedTime}
                            size="small"
                            variant="outlined"
                        />
                    </Stack>

                    <Stack direction="row" spacing={0.5} flexWrap="wrap" className="min-h-[24px]">
                        {template.tags.slice(0, 3).map((tag) => (
                            <Chip
                                key={tag}
                                label={tag}
                                size="small"
                                variant="outlined"
                                className="text-[0.7rem]"
                            />
                        ))}
                        {template.tags.length > 3 && (
                            <Chip
                                label={`+${template.tags.length - 3}`}
                                size="small"
                                variant="outlined"
                                className="text-[0.7rem]"
                            />
                        )}
                    </Stack>
                </Stack>
            </CardContent>
        </Card>
    );
};

export default TemplateGallery;
