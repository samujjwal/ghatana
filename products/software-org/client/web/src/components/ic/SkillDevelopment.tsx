import React, { useState } from 'react';
import {
    Card,
    CardContent,
    CardHeader,
    Typography,
    Box,
    Chip,
    Button,
    IconButton,
    LinearProgress,
    TextField,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    List,
    ListItem,
    ListItemText,
    Divider,
    Alert,
    Tabs,
    Tab,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    Rating,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Paper,
} from '@ghatana/design-system';
import {
    Add as AddIcon,
    Edit as EditIcon,
    Delete as DeleteIcon,
    School as SchoolIcon,
    EmojiEvents as EmojiEventsIcon,
    TrendingUp as TrendingUpIcon,
    CheckCircle as CheckCircleIcon,
    Timeline as TimelineIcon,
    Code as CodeIcon,
    Book as BookIcon,
    Verified as VerifiedIcon,
    Star as StarIcon,
    RadioButtonUnchecked as RadioButtonUncheckedIcon,
} from '@ghatana/design-system/icons';

// ==================== TYPES ====================

export interface Skill {
    id: string;
    name: string;
    category: 'technical' | 'soft' | 'domain' | 'tools' | 'language';
    proficiency: 1 | 2 | 3 | 4 | 5; // 1=Beginner, 2=Intermediate, 3=Advanced, 4=Expert, 5=Master
    yearsOfExperience?: number;
    lastUsed?: string; // ISO date string
    endorsements?: number;
    verified?: boolean;
}

export interface LearningPath {
    id: string;
    title: string;
    description: string;
    category: Skill['category'];
    targetSkills: string[]; // Skill IDs
    progress: number; // 0-100
    status: 'not_started' | 'in_progress' | 'completed';
    startDate?: string;
    completionDate?: string;
    estimatedHours: number;
    actualHours?: number;
    resources: LearningResource[];
}

export interface LearningResource {
    id: string;
    title: string;
    type: 'course' | 'book' | 'article' | 'video' | 'project' | 'certification';
    url?: string;
    provider?: string;
    completed: boolean;
    completionDate?: string;
    notes?: string;
}

export interface Certification {
    id: string;
    name: string;
    provider: string;
    category: Skill['category'];
    status: 'planning' | 'in_progress' | 'passed' | 'expired';
    issueDate?: string;
    expiryDate?: string;
    credentialId?: string;
    credentialUrl?: string;
    relatedSkills: string[]; // Skill IDs
}

export interface SkillDevelopmentProps {
    skills?: Skill[];
    learningPaths?: LearningPath[];
    certifications?: Certification[];
    onAddSkill?: (skill: Omit<Skill, 'id'>) => void;
    onUpdateSkill?: (skillId: string, updates: Partial<Skill>) => void;
    onDeleteSkill?: (skillId: string) => void;
    onCreateLearningPath?: (path: Omit<LearningPath, 'id' | 'progress'>) => void;
    onUpdateLearningPath?: (pathId: string, updates: Partial<LearningPath>) => void;
    onCompleteResource?: (pathId: string, resourceId: string) => void;
    onAddCertification?: (cert: Omit<Certification, 'id'>) => void;
    onUpdateCertification?: (certId: string, updates: Partial<Certification>) => void;
}

// ==================== MOCK DATA ====================

const mockSkills: Skill[] = [
    { id: 'skill-1', name: 'React', category: 'technical', proficiency: 4, yearsOfExperience: 3, lastUsed: '2025-12-10T00:00:00Z', endorsements: 12, verified: true },
    { id: 'skill-2', name: 'TypeScript', category: 'technical', proficiency: 4, yearsOfExperience: 3, lastUsed: '2025-12-10T00:00:00Z', endorsements: 10, verified: true },
    { id: 'skill-3', name: 'Node.js', category: 'technical', proficiency: 3, yearsOfExperience: 2, lastUsed: '2025-12-08T00:00:00Z', endorsements: 8 },
    { id: 'skill-4', name: 'AWS', category: 'tools', proficiency: 3, yearsOfExperience: 2, lastUsed: '2025-12-05T00:00:00Z', endorsements: 5 },
    { id: 'skill-5', name: 'Leadership', category: 'soft', proficiency: 3, yearsOfExperience: 2, endorsements: 7 },
    { id: 'skill-6', name: 'Python', category: 'language', proficiency: 2, yearsOfExperience: 1, lastUsed: '2025-11-20T00:00:00Z' },
    { id: 'skill-7', name: 'System Design', category: 'domain', proficiency: 3, yearsOfExperience: 2, endorsements: 4 },
    { id: 'skill-8', name: 'Docker', category: 'tools', proficiency: 3, yearsOfExperience: 2, lastUsed: '2025-12-09T00:00:00Z', endorsements: 6 },
    { id: 'skill-9', name: 'Communication', category: 'soft', proficiency: 4, yearsOfExperience: 3, endorsements: 15, verified: true },
    { id: 'skill-10', name: 'PostgreSQL', category: 'technical', proficiency: 3, yearsOfExperience: 2, lastUsed: '2025-12-07T00:00:00Z', endorsements: 5 },
];

const mockLearningPaths: LearningPath[] = [
    {
        id: 'path-1',
        title: 'AWS Solutions Architect',
        description: 'Master AWS cloud services and architecture patterns',
        category: 'tools',
        targetSkills: ['skill-4'],
        progress: 60,
        status: 'in_progress',
        startDate: '2025-09-01T00:00:00Z',
        estimatedHours: 100,
        actualHours: 45,
        resources: [
            { id: 'res-1', title: 'AWS Certified Solutions Architect Course', type: 'course', provider: 'A Cloud Guru', completed: true, completionDate: '2025-10-15T00:00:00Z' },
            { id: 'res-2', title: 'AWS Well-Architected Framework', type: 'article', url: 'https://aws.amazon.com/architecture/well-architected/', completed: true, completionDate: '2025-10-20T00:00:00Z' },
            { id: 'res-3', title: 'Practice Exam 1', type: 'certification', completed: true, completionDate: '2025-11-10T00:00:00Z', notes: 'Score: 72%' },
            { id: 'res-4', title: 'Practice Exam 2', type: 'certification', completed: true, completionDate: '2025-12-05T00:00:00Z', notes: 'Score: 78%' },
            { id: 'res-5', title: 'Build serverless application', type: 'project', completed: false },
            { id: 'res-6', title: 'Final certification exam', type: 'certification', completed: false },
        ],
    },
    {
        id: 'path-2',
        title: 'Advanced React Patterns',
        description: 'Learn advanced React patterns and performance optimization',
        category: 'technical',
        targetSkills: ['skill-1'],
        progress: 100,
        status: 'completed',
        startDate: '2025-06-01T00:00:00Z',
        completionDate: '2025-08-31T00:00:00Z',
        estimatedHours: 40,
        actualHours: 38,
        resources: [
            { id: 'res-7', title: 'React Patterns by Kent C. Dodds', type: 'course', provider: 'Epic React', completed: true, completionDate: '2025-07-15T00:00:00Z' },
            { id: 'res-8', title: 'Performance optimization techniques', type: 'article', completed: true, completionDate: '2025-08-01T00:00:00Z' },
            { id: 'res-9', title: 'Build component library', type: 'project', completed: true, completionDate: '2025-08-30T00:00:00Z' },
        ],
    },
    {
        id: 'path-3',
        title: 'System Design Fundamentals',
        description: 'Learn to design scalable distributed systems',
        category: 'domain',
        targetSkills: ['skill-7'],
        progress: 30,
        status: 'in_progress',
        startDate: '2025-11-01T00:00:00Z',
        estimatedHours: 60,
        actualHours: 15,
        resources: [
            { id: 'res-10', title: 'Designing Data-Intensive Applications', type: 'book', provider: 'Martin Kleppmann', completed: false },
            { id: 'res-11', title: 'System Design Interview Course', type: 'course', provider: 'Educative', completed: false },
            { id: 'res-12', title: 'Design URL shortener', type: 'project', completed: true, completionDate: '2025-11-20T00:00:00Z' },
        ],
    },
];

const mockCertifications: Certification[] = [
    {
        id: 'cert-1',
        name: 'AWS Solutions Architect Associate',
        provider: 'Amazon Web Services',
        category: 'tools',
        status: 'in_progress',
        relatedSkills: ['skill-4'],
    },
    {
        id: 'cert-2',
        name: 'React Developer Certification',
        provider: 'Meta',
        category: 'technical',
        status: 'passed',
        issueDate: '2024-06-15T00:00:00Z',
        expiryDate: '2027-06-15T00:00:00Z',
        credentialId: 'META-REACT-2024-12345',
        credentialUrl: 'https://credentials.meta.com/12345',
        relatedSkills: ['skill-1', 'skill-2'],
    },
    {
        id: 'cert-3',
        name: 'Professional Scrum Master I',
        provider: 'Scrum.org',
        category: 'soft',
        status: 'passed',
        issueDate: '2023-03-10T00:00:00Z',
        credentialId: 'PSM-I-2023-67890',
        relatedSkills: ['skill-5', 'skill-9'],
    },
];

// ==================== COMPONENT ====================

export const SkillDevelopment: React.FC<SkillDevelopmentProps> = ({
    skills = mockSkills,
    learningPaths = mockLearningPaths,
    certifications = mockCertifications,
    onAddSkill,
    onUpdateSkill,
    onDeleteSkill,
    onCreateLearningPath,
    onUpdateLearningPath,
    onCompleteResource,
    onAddCertification,
    onUpdateCertification,
}) => {
    const [selectedTab, setSelectedTab] = useState(0);
    const [addSkillDialogOpen, setAddSkillDialogOpen] = useState(false);
    const [editSkillDialogOpen, setEditSkillDialogOpen] = useState(false);
    const [selectedSkill, setSelectedSkill] = useState<Skill | null>(null);
    const [skillFormData, setSkillFormData] = useState<Partial<Skill>>({
        name: '',
        category: 'technical',
        proficiency: 1,
    });

    // Helper functions
    const getProficiencyLabel = (proficiency: number): string => {
        switch (proficiency) {
            case 1: return 'Beginner';
            case 2: return 'Intermediate';
            case 3: return 'Advanced';
            case 4: return 'Expert';
            case 5: return 'Master';
            default: return 'Unknown';
        }
    };

    const getCategoryColor = (category: Skill['category']): 'primary' | 'secondary' | 'info' | 'success' | 'warning' => {
        switch (category) {
            case 'technical': return 'primary';
            case 'soft': return 'secondary';
            case 'domain': return 'info';
            case 'tools': return 'success';
            case 'language': return 'warning';
        }
    };

    const getStatusColor = (status: LearningPath['status'] | Certification['status']): 'success' | 'info' | 'warning' | 'default' => {
        switch (status) {
            case 'completed':
            case 'passed':
                return 'success';
            case 'in_progress':
                return 'info';
            case 'planning':
                return 'warning';
            case 'not_started':
            case 'expired':
            default:
                return 'default';
        }
    };

    const formatDate = (dateString?: string): string => {
        if (!dateString) return 'N/A';
        return new Date(dateString).toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
        });
    };

    const handleAddSkill = () => {
        if (onAddSkill && skillFormData.name && skillFormData.category && skillFormData.proficiency) {
            onAddSkill(skillFormData as Omit<Skill, 'id'>);
            setAddSkillDialogOpen(false);
            setSkillFormData({ name: '', category: 'technical', proficiency: 1 });
        }
    };

    const handleUpdateSkill = () => {
        if (onUpdateSkill && selectedSkill && skillFormData) {
            onUpdateSkill(selectedSkill.id, skillFormData);
            setEditSkillDialogOpen(false);
            setSelectedSkill(null);
        }
    };

    // Group skills by category
    const skillsByCategory = skills.reduce((acc, skill) => {
        if (!acc[skill.category]) acc[skill.category] = [];
        acc[skill.category].push(skill);
        return acc;
    }, {} as Record<Skill['category'], Skill[]>);

    return (
        <Box sx={{ p: 3 }}>
            {/* Header */}
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
                <Typography variant="h4">Skill Development</Typography>
                <Button
                    variant="contained"
                    color="primary"
                    startIcon={<AddIcon />}
                    onClick={() => setAddSkillDialogOpen(true)}
                >
                    Add Skill
                </Button>
            </Box>

            {/* Tabs */}
            <Tabs value={selectedTab} onChange={(_, newValue) => setSelectedTab(newValue)} sx={{ mb: 3 }}>
                <Tab label="Skill Matrix" />
                <Tab label={`Learning Paths (${learningPaths.length})`} />
                <Tab label={`Certifications (${certifications.length})`} />
            </Tabs>

            {/* Tab 1: Skill Matrix */}
            {selectedTab === 0 && (
                <Box>
                    {/* Skill Overview Cards */}
                    <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: 2, mb: 3 }}>
                        <Card variant="outlined">
                            <CardContent>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Total Skills
                                </Typography>
                                <Typography variant="h4">{skills.length}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                    {skills.filter(s => s.verified).length} verified
                                </Typography>
                            </CardContent>
                        </Card>

                        <Card variant="outlined">
                            <CardContent>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Expert Level
                                </Typography>
                                <Typography variant="h4">
                                    {skills.filter(s => s.proficiency >= 4).length}
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                    skills at expert or master level
                                </Typography>
                            </CardContent>
                        </Card>

                        <Card variant="outlined">
                            <CardContent>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Endorsements
                                </Typography>
                                <Typography variant="h4">
                                    {skills.reduce((sum, s) => sum + (s.endorsements || 0), 0)}
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                    total endorsements received
                                </Typography>
                            </CardContent>
                        </Card>

                        <Card variant="outlined">
                            <CardContent>
                                <Typography variant="body2" color="text.secondary" gutterBottom>
                                    Learning Progress
                                </Typography>
                                <Typography variant="h4">
                                    {Math.round(learningPaths.reduce((sum, p) => sum + p.progress, 0) / learningPaths.length)}%
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                    average across all paths
                                </Typography>
                            </CardContent>
                        </Card>
                    </Box>

                    {/* Skills by Category */}
                    {Object.entries(skillsByCategory).map(([category, categorySkills]) => (
                        <Card key={category} sx={{ mb: 3 }}>
                            <CardHeader
                                title={
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                        <Typography variant="h6" sx={{ textTransform: 'capitalize' }}>
                                            {category} Skills
                                        </Typography>
                                        <Chip label={categorySkills.length} size="small" color={getCategoryColor(category as Skill['category'])} />
                                    </Box>
                                }
                            />
                            <CardContent>
                                <TableContainer>
                                    <Table>
                                        <TableHead>
                                            <TableRow>
                                                <TableCell>Skill</TableCell>
                                                <TableCell>Proficiency</TableCell>
                                                <TableCell>Experience</TableCell>
                                                <TableCell>Last Used</TableCell>
                                                <TableCell>Endorsements</TableCell>
                                                <TableCell>Actions</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {categorySkills.map((skill) => (
                                                <TableRow key={skill.id}>
                                                    <TableCell>
                                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                                            <Typography variant="body1">{skill.name}</Typography>
                                                            {skill.verified && <VerifiedIcon color="primary" fontSize="small" />}
                                                        </Box>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                                            <Rating value={skill.proficiency} readOnly size="small" max={5} />
                                                            <Typography variant="caption" color="text.secondary">
                                                                {getProficiencyLabel(skill.proficiency)}
                                                            </Typography>
                                                        </Box>
                                                    </TableCell>
                                                    <TableCell>
                                                        {skill.yearsOfExperience ? `${skill.yearsOfExperience} years` : 'N/A'}
                                                    </TableCell>
                                                    <TableCell>
                                                        <Typography variant="body2" color="text.secondary">
                                                            {formatDate(skill.lastUsed)}
                                                        </Typography>
                                                    </TableCell>
                                                    <TableCell>
                                                        {skill.endorsements && (
                                                            <Chip label={skill.endorsements} size="small" icon={<StarIcon />} />
                                                        )}
                                                    </TableCell>
                                                    <TableCell>
                                                        <IconButton
                                                            size="small"
                                                            onClick={() => {
                                                                setSelectedSkill(skill);
                                                                setSkillFormData(skill);
                                                                setEditSkillDialogOpen(true);
                                                            }}
                                                        >
                                                            <EditIcon fontSize="small" />
                                                        </IconButton>
                                                        <IconButton
                                                            size="small"
                                                            color="error"
                                                            onClick={() => onDeleteSkill?.(skill.id)}
                                                        >
                                                            <DeleteIcon fontSize="small" />
                                                        </IconButton>
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            </CardContent>
                        </Card>
                    ))}
                </Box>
            )}

            {/* Tab 2: Learning Paths */}
            {selectedTab === 1 && (
                <Box>
                    {learningPaths.length === 0 ? (
                        <Alert severity="info">No learning paths yet. Create one to track your learning journey!</Alert>
                    ) : (
                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                            {learningPaths.map((path) => {
                                const completedResources = path.resources.filter(r => r.completed).length;
                                const totalResources = path.resources.length;

                                return (
                                    <Card key={path.id}>
                                        <CardHeader
                                            avatar={<SchoolIcon color="primary" />}
                                            title={
                                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                                                    <Typography variant="h6">{path.title}</Typography>
                                                    <Chip
                                                        label={path.status.replace('_', ' ').toUpperCase()}
                                                        size="small"
                                                        color={getStatusColor(path.status)}
                                                    />
                                                    <Chip
                                                        label={path.category.toUpperCase()}
                                                        size="small"
                                                        color={getCategoryColor(path.category)}
                                                        variant="outlined"
                                                    />
                                                </Box>
                                            }
                                            subheader={
                                                <Box>
                                                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                                                        {path.description}
                                                    </Typography>
                                                    <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                                                        {path.startDate && (
                                                            <Typography variant="caption" color="text.secondary">
                                                                📅 Started {formatDate(path.startDate)}
                                                            </Typography>
                                                        )}
                                                        {path.completionDate && (
                                                            <Typography variant="caption" color="text.secondary">
                                                                ✅ Completed {formatDate(path.completionDate)}
                                                            </Typography>
                                                        )}
                                                        <Typography variant="caption" color="text.secondary">
                                                            ⏱️ {path.actualHours || 0}/{path.estimatedHours}h
                                                        </Typography>
                                                        <Typography variant="caption" color="text.secondary">
                                                            📚 {completedResources}/{totalResources} resources
                                                        </Typography>
                                                    </Box>
                                                </Box>
                                            }
                                        />
                                        <CardContent>
                                            {/* Progress */}
                                            <Box sx={{ mb: 3 }}>
                                                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                                                    <Typography variant="subtitle2">Progress</Typography>
                                                    <Typography variant="h6" color="primary">
                                                        {path.progress}%
                                                    </Typography>
                                                </Box>
                                                <LinearProgress
                                                    variant="determinate"
                                                    value={path.progress}
                                                    sx={{ height: 8, borderRadius: 1 }}
                                                    color={getStatusColor(path.status)}
                                                />
                                            </Box>

                                            {/* Resources */}
                                            <Typography variant="subtitle2" gutterBottom>
                                                Learning Resources
                                            </Typography>
                                            <List>
                                                {path.resources.map((resource, index) => (
                                                    <React.Fragment key={resource.id}>
                                                        {index > 0 && <Divider />}
                                                        <ListItem
                                                            sx={{ px: 0 }}
                                                            secondaryAction={
                                                                !resource.completed && (
                                                                    <Button
                                                                        size="small"
                                                                        onClick={() => onCompleteResource?.(path.id, resource.id)}
                                                                    >
                                                                        Mark Complete
                                                                    </Button>
                                                                )
                                                            }
                                                        >
                                                            <ListItemText
                                                                primary={
                                                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                                                        {resource.completed ? (
                                                                            <CheckCircleIcon color="success" fontSize="small" />
                                                                        ) : (
                                                                            <RadioButtonUncheckedIcon color="action" fontSize="small" />
                                                                        )}
                                                                        <Typography variant="body1">{resource.title}</Typography>
                                                                        <Chip label={resource.type.toUpperCase()} size="small" variant="outlined" />
                                                                    </Box>
                                                                }
                                                                secondary={
                                                                    <Box sx={{ ml: 4 }}>
                                                                        {resource.provider && (
                                                                            <Typography variant="caption" color="text.secondary">
                                                                                Provider: {resource.provider}
                                                                            </Typography>
                                                                        )}
                                                                        {resource.completed && resource.completionDate && (
                                                                            <Typography variant="caption" color="text.secondary" sx={{ ml: 2 }}>
                                                                                Completed {formatDate(resource.completionDate)}
                                                                            </Typography>
                                                                        )}
                                                                        {resource.notes && (
                                                                            <Typography variant="caption" color="text.secondary" sx={{ ml: 2 }}>
                                                                                • {resource.notes}
                                                                            </Typography>
                                                                        )}
                                                                    </Box>
                                                                }
                                                            />
                                                        </ListItem>
                                                    </React.Fragment>
                                                ))}
                                            </List>
                                        </CardContent>
                                    </Card>
                                );
                            })}
                        </Box>
                    )}
                </Box>
            )}

            {/* Tab 3: Certifications */}
            {selectedTab === 2 && (
                <Box>
                    {certifications.length === 0 ? (
                        <Alert severity="info">No certifications tracked yet. Add your first certification!</Alert>
                    ) : (
                        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(350px, 1fr))', gap: 3 }}>
                            {certifications.map((cert) => (
                                <Card key={cert.id}>
                                    <CardHeader
                                        avatar={<EmojiEventsIcon color="warning" />}
                                        title={cert.name}
                                        subheader={cert.provider}
                                        action={
                                            <Chip
                                                label={cert.status.replace('_', ' ').toUpperCase()}
                                                size="small"
                                                color={getStatusColor(cert.status)}
                                            />
                                        }
                                    />
                                    <CardContent>
                                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                                            <Box>
                                                <Typography variant="caption" color="text.secondary">
                                                    Category
                                                </Typography>
                                                <Box sx={{ mt: 0.5 }}>
                                                    <Chip
                                                        label={cert.category.toUpperCase()}
                                                        size="small"
                                                        color={getCategoryColor(cert.category)}
                                                    />
                                                </Box>
                                            </Box>

                                            {cert.issueDate && (
                                                <Box>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Issue Date
                                                    </Typography>
                                                    <Typography variant="body2">{formatDate(cert.issueDate)}</Typography>
                                                </Box>
                                            )}

                                            {cert.expiryDate && (
                                                <Box>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Expiry Date
                                                    </Typography>
                                                    <Typography variant="body2">{formatDate(cert.expiryDate)}</Typography>
                                                </Box>
                                            )}

                                            {cert.credentialId && (
                                                <Box>
                                                    <Typography variant="caption" color="text.secondary">
                                                        Credential ID
                                                    </Typography>
                                                    <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>
                                                        {cert.credentialId}
                                                    </Typography>
                                                </Box>
                                            )}

                                            {cert.relatedSkills.length > 0 && (
                                                <Box>
                                                    <Typography variant="caption" color="text.secondary" gutterBottom>
                                                        Related Skills
                                                    </Typography>
                                                    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mt: 0.5 }}>
                                                        {cert.relatedSkills.map((skillId) => {
                                                            const skill = skills.find(s => s.id === skillId);
                                                            return skill ? (
                                                                <Chip key={skillId} label={skill.name} size="small" variant="outlined" />
                                                            ) : null;
                                                        })}
                                                    </Box>
                                                </Box>
                                            )}

                                            {cert.credentialUrl && (
                                                <Button
                                                    size="small"
                                                    variant="outlined"
                                                    href={cert.credentialUrl}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                >
                                                    View Credential
                                                </Button>
                                            )}
                                        </Box>
                                    </CardContent>
                                </Card>
                            ))}
                        </Box>
                    )}
                </Box>
            )}

            {/* Add Skill Dialog */}
            <Dialog open={addSkillDialogOpen} onClose={() => setAddSkillDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Add New Skill</DialogTitle>
                <DialogContent>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
                        <TextField
                            label="Skill Name"
                            fullWidth
                            required
                            value={skillFormData.name}
                            onChange={(e) => setSkillFormData({ ...skillFormData, name: e.target.value })}
                        />

                        <FormControl fullWidth required>
                            <InputLabel>Category</InputLabel>
                            <Select
                                value={skillFormData.category}
                                label="Category"
                                onChange={(e) => setSkillFormData({ ...skillFormData, category: e.target.value as Skill['category'] })}
                            >
                                <MenuItem value="technical">Technical</MenuItem>
                                <MenuItem value="soft">Soft Skills</MenuItem>
                                <MenuItem value="domain">Domain Knowledge</MenuItem>
                                <MenuItem value="tools">Tools & Platforms</MenuItem>
                                <MenuItem value="language">Programming Language</MenuItem>
                            </Select>
                        </FormControl>

                        <Box>
                            <Typography variant="body2" gutterBottom>
                                Proficiency Level
                            </Typography>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                                <Rating
                                    value={skillFormData.proficiency}
                                    onChange={(_, value) => setSkillFormData({ ...skillFormData, proficiency: value as Skill['proficiency'] })}
                                    max={5}
                                />
                                <Typography variant="body2" color="text.secondary">
                                    {skillFormData.proficiency ? getProficiencyLabel(skillFormData.proficiency) : 'Select level'}
                                </Typography>
                            </Box>
                        </Box>

                        <TextField
                            label="Years of Experience"
                            type="number"
                            fullWidth
                            InputProps={{ inputProps: { min: 0, max: 50, step: 0.5 } }}
                            value={skillFormData.yearsOfExperience || ''}
                            onChange={(e) => setSkillFormData({ ...skillFormData, yearsOfExperience: parseFloat(e.target.value) })}
                        />
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAddSkillDialogOpen(false)}>Cancel</Button>
                    <Button
                        variant="contained"
                        onClick={handleAddSkill}
                        disabled={!skillFormData.name || !skillFormData.category || !skillFormData.proficiency}
                    >
                        Add Skill
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Edit Skill Dialog */}
            <Dialog open={editSkillDialogOpen} onClose={() => setEditSkillDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Edit Skill</DialogTitle>
                <DialogContent>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
                        <TextField
                            label="Skill Name"
                            fullWidth
                            required
                            value={skillFormData.name}
                            onChange={(e) => setSkillFormData({ ...skillFormData, name: e.target.value })}
                        />

                        <Box>
                            <Typography variant="body2" gutterBottom>
                                Proficiency Level
                            </Typography>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                                <Rating
                                    value={skillFormData.proficiency}
                                    onChange={(_, value) => setSkillFormData({ ...skillFormData, proficiency: value as Skill['proficiency'] })}
                                    max={5}
                                />
                                <Typography variant="body2" color="text.secondary">
                                    {skillFormData.proficiency ? getProficiencyLabel(skillFormData.proficiency) : 'Select level'}
                                </Typography>
                            </Box>
                        </Box>

                        <TextField
                            label="Years of Experience"
                            type="number"
                            fullWidth
                            InputProps={{ inputProps: { min: 0, max: 50, step: 0.5 } }}
                            value={skillFormData.yearsOfExperience || ''}
                            onChange={(e) => setSkillFormData({ ...skillFormData, yearsOfExperience: parseFloat(e.target.value) })}
                        />
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setEditSkillDialogOpen(false)}>Cancel</Button>
                    <Button variant="contained" onClick={handleUpdateSkill}>
                        Save Changes
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};

export default SkillDevelopment;
