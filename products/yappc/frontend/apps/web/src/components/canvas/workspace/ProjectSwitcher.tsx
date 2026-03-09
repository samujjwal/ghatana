/**
 * Project Switcher Modal (W3)
 * 
 * "Quick switch between projects (modal overlay)"
 * Matches spec W3: Project Switcher (Modal Overlay)
 * 
 * Features:
 * - Search projects
 * - Recent projects list
 * - Create new project
 * 
 * @doc.type component
 * @doc.purpose Project switching modal
 * @doc.layer product
 * @doc.pattern Modal/Dialog
 */

import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { Dialog, DialogTitle, DialogContent, Box, Typography, TextField, InputAdornment, InteractiveList as List, ListItemButton, ListItemIcon, ListItemText, IconButton, Button, Divider, Chip, Stack } from '@ghatana/ui';
import { Search as SearchIcon, X as CloseIcon, Folder as FolderIcon, Plus as AddIcon, Clock as RecentIcon, Star as StarIcon } from 'lucide-react';

interface Project {
    id: string;
    name: string;
    lastEdited: string;
    phase: string;
    phaseColor: 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning';
    progress: number;
}

// Mock data until we hook up to real API
const MOCK_PROJECTS: Project[] = [
    { id: 'proj_1', name: 'My SaaS Product', lastEdited: '2 hours ago', phase: 'BUILD', phaseColor: 'success', progress: 65 },
    { id: 'proj_2', name: 'Mobile App Redesign', lastEdited: 'Yesterday', phase: 'VALIDATE', phaseColor: 'warning', progress: 40 },
    { id: 'proj_3', name: 'API Platform', lastEdited: '3 days ago', phase: 'IDEATION', phaseColor: 'info', progress: 20 },
    { id: 'proj_4', name: 'Internal Dashboard', lastEdited: '1 week ago', phase: 'RUN', phaseColor: 'primary', progress: 90 },
];

export interface ProjectSwitcherProps {
    open: boolean;
    onClose: () => void;
    currentProjectId?: string;
}

export const ProjectSwitcher: React.FC<ProjectSwitcherProps> = ({
    open,
    onClose,
    currentProjectId
}) => {
    const navigate = useNavigate();
    const [searchQuery, setSearchQuery] = useState('');

    const handleSelectProject = (projectId: string) => {
        if (projectId !== currentProjectId) {
            navigate(`/app/p/${projectId}`);
        }
        onClose();
    };

    const handleCreateNew = () => {
        // In a real app this might open another dialog or navigate to creation wizard
        // For now, let's just create a new ID and go there
        const newId = `new_proj_${Date.now()}`;
        navigate(`/app/p/${newId}`);
        onClose();
    };

    const filteredProjects = MOCK_PROJECTS.filter(p =>
        p.name.toLowerCase().includes(searchQuery.toLowerCase())
    );

    const PhaseDot = ({ filled }: { filled: boolean }) => (
        <Box component="span" className="w-[8px] h-[8px] rounded-full" style={{ backgroundColor: filled ? 'currentColor' : '#9ca3af' }} />
    );

    // Helper to render progress dots
    const ProgressIndicator = ({ progress }: { progress: number }) => {
        // 5 dots total
        const dots = 5;
        const filledDots = Math.round((progress / 100) * dots);

        return (
            <Box className="flex items-center ml-2">
                {Array.from({ length: dots }).map((_, i) => (
                    <Box
                        key={i}
                        component="span"
                        className="w-[6px] h-[6px] rounded-full mx-1" style={{ backgroundColor: i < filledDots ? 'currentcolor' : '#d1d5db' }}
                    />
                ))}
            </Box>
        );
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            size="sm"
            fullWidth
            PaperProps={{
                sx: { borderRadius: 3, maxHeight: '80vh' }
            }}
        >
            <DialogTitle className="flex justify-between items-center pb-2">
                <span style={{ fontWeight: 'bold', fontSize: '1.25rem' }}>Switch Project</span>
                <IconButton onClick={onClose} size="sm" edge="end">
                    <CloseIcon />
                </IconButton>
            </DialogTitle>

            <DialogContent className="p-0">
                {/* Search Header */}
                <Box className="p-4 pb-4 border-b border-solid border-gray-200 dark:border-gray-700">
                    <TextField
                        fullWidth
                        placeholder="Search projects..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        variant="outlined"
                        size="sm"
                        InputProps={{
                            startAdornment: (
                                <InputAdornment position="start">
                                    <SearchIcon color="action" />
                                </InputAdornment>
                            ),
                            sx: { borderRadius: 2 }
                        }}
                    />
                </Box>

                {/* Project List */}
                <List className="pt-2 pb-2 overflow-y-auto max-h-[400px]">
                    {searchQuery.length === 0 && (
                        <Typography as="span" className="text-xs uppercase tracking-wider" color="text.secondary" className="px-6 py-2 block">
                            Recent
                        </Typography>
                    )}

                    {filteredProjects.length > 0 ? (
                        filteredProjects.map((project) => (
                            <ListItemButton
                                key={project.id}
                                selected={project.id === currentProjectId}
                                onClick={() => handleSelectProject(project.id)}
                                className="py-3 px-6 border-blue-600 hover:bg-gray-100 hover:dark:bg-gray-800" style={{ borderLeft: project.id === currentProjectId ? '3px solid' : '3px solid transparent' }}
                            >
                                <ListItemIcon className="min-w-[40px]">
                                    {project.id === currentProjectId ? (
                                        <FolderIcon tone="primary" />
                                    ) : (
                                        <FolderIcon color="disabled" />
                                    )}
                                </ListItemIcon>

                                <ListItemText
                                    primary={
                                        <Box display="flex" alignItems="center" justifyContent="space-between">
                                            <Typography as="p" fontWeight={project.id === currentProjectId ? 'bold' : 'medium'}>
                                                {project.name}
                                            </Typography>
                                            <Stack direction="row" alignItems="center" spacing={1}>
                                                <Chip
                                                    label={project.phase}
                                                    size="sm"
                                                    color={project.phaseColor}
                                                    variant="outlined"
                                                    className="h-[20px] text-[0.65rem]"
                                                />
                                                <ProgressIndicator progress={project.progress} />
                                            </Stack>
                                        </Box>
                                    }
                                    secondary={
                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                            Last edited {project.lastEdited}
                                        </Typography>
                                    }
                                />
                            </ListItemButton>
                        ))
                    ) : (
                        <Box className="p-8 text-center">
                            <Typography color="text.secondary">No projects found</Typography>
                        </Box>
                    )}
                </List>

                <Divider />

                {/* Footer Actions */}
                <Box className="p-4 bg-gray-50 dark:bg-gray-950">
                    <Button
                        fullWidth
                        variant="solid"
                        startIcon={<AddIcon />}
                        onClick={handleCreateNew}
                        className="rounded-lg"
                    >
                        New Project
                    </Button>
                </Box>
            </DialogContent>
        </Dialog>
    );
};
