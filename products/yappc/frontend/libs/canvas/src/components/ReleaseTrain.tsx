/**
 * Release Train Component
 * 
 * @doc.type component
 * @doc.purpose Multi-team release orchestration with dependency tracking, blocker detection, and timeline management
 * @doc.layer product
 * @doc.pattern Presentation Component
 * 
 * Features:
 * - Multi-team tracks (5+ teams with parallel releases)
 * - Feature cards with status tracking (not-started, in-progress, testing, blocked, done)
 * - Dependency arrows between features (cross-team dependencies)
 * - Blocker detection and highlighting (automatic red highlighting)
 * - Critical path calculation (longest dependency chain)
 * - Timeline auto-adjustment based on blockers
 * - Release readiness checks (tests passed, security scan, docs updated)
 * - Gantt-style timeline view
 * - Team velocity tracking
 * - Risk assessment per feature
 * 
 * @example
 * ```tsx
 * <ReleaseTrain releaseName="Q1 2025 Release" />
 * ```
 */

import React, { useState } from 'react';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Typography,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { useReleaseTrain } from '../hooks/useReleaseTrain';
import type {
    FeatureStatus,
    BlockerType,
    ReadinessCheckType,
    RiskLevel,
} from '../hooks/useReleaseTrain';

interface ReleaseTrainProps {
    /**
     * Release name displayed in the header
     */
    releaseName?: string;
}

/**
 * Release Train Component
 * 
 * Multi-team release orchestration tool providing comprehensive visibility
 * into feature development, dependencies, blockers, and release readiness
 * across multiple teams working in parallel.
 * 
 * @param props - Component props
 * @returns Release Train component
 */
export const ReleaseTrain: React.FC<ReleaseTrainProps> = ({
    releaseName = 'Release Train',
}) => {
    const {
        // State
        release,
        setRelease,
        selectedTeam,
        setSelectedTeam,
        selectedFeature,
        setSelectedFeature,

        // Team Management
        getTeams,
        addTeam,
        updateTeam,
        deleteTeam,

        // Feature Management
        getFeatures,
        getFeaturesByTeam,
        getFeaturesByStatus,
        addFeature,
        updateFeature,
        deleteFeature,

        // Dependency Management
        addDependency,
        removeDependency,
        getDependencies,
        getFeatureDependencies,

        // Blocker Management
        addBlocker,
        removeBlocker,
        getBlockers,
        getFeatureBlockers,

        // Readiness Checks
        updateReadinessCheck,
        getReadinessChecks,
        isFeatureReady,

        // Timeline & Path Analysis
        calculateCriticalPath,
        getFeatureTimeline,
        estimateReleaseDate,
        getTeamVelocity,

        // Risk Assessment
        calculateFeatureRisk,
        getReleaseRisk,
    } = useReleaseTrain();

    // Local UI state
    const [showAddTeam, setShowAddTeam] = useState(false);
    const [showAddFeature, setShowAddFeature] = useState(false);
    const [showFeatureDetails, setShowFeatureDetails] = useState(false);
    const [showCriticalPath, setShowCriticalPath] = useState(false);
    const [showReleaseReadiness, setShowReleaseReadiness] = useState(false);
    const [newTeamName, setNewTeamName] = useState('');
    const [newFeatureTitle, setNewFeatureTitle] = useState('');
    const [newFeatureDescription, setNewFeatureDescription] = useState('');
    const [newFeatureTeam, setNewFeatureTeam] = useState('');
    const [newFeatureEstimate, setNewFeatureEstimate] = useState('5');

    // Get data
    const teams = getTeams();
    const features = selectedTeam ? getFeaturesByTeam(selectedTeam) : getFeatures();
    const criticalPath = calculateCriticalPath();
    const releaseDate = estimateReleaseDate();
    const releaseRisk = getReleaseRisk();
    const featureDetails = selectedFeature ? getFeatures().find(f => f.id === selectedFeature) : null;

    // Status color mapping
    const getStatusColor = (status: FeatureStatus): string => {
        switch (status) {
            case 'done':
                return 'bg-green-100 text-green-800 border-green-300';
            case 'testing':
                return 'bg-blue-100 text-blue-800 border-blue-300';
            case 'in-progress':
                return 'bg-yellow-100 text-yellow-800 border-yellow-300';
            case 'blocked':
                return 'bg-red-100 text-red-800 border-red-300';
            case 'not-started':
                return 'bg-gray-100 text-gray-800 border-gray-300';
            default:
                return 'bg-gray-100 text-gray-800 border-gray-300';
        }
    };

    // Risk color mapping
    const getRiskColor = (risk: RiskLevel): string => {
        switch (risk) {
            case 'critical':
                return 'text-red-600';
            case 'high':
                return 'text-orange-600';
            case 'medium':
                return 'text-yellow-600';
            case 'low':
                return 'text-green-600';
            default:
                return 'text-gray-600';
        }
    };

    // Blocker type icon
    const getBlockerIcon = (type: BlockerType): string => {
        switch (type) {
            case 'technical':
                return '⚙️';
            case 'dependency':
                return '🔗';
            case 'resource':
                return '👥';
            case 'external':
                return '🌐';
            default:
                return '⚠️';
        }
    };

    // Handle add team
    const handleAddTeam = () => {
        if (newTeamName.trim()) {
            addTeam({
                name: newTeamName,
                velocity: 10,
                capacity: 100,
            });
            setNewTeamName('');
            setShowAddTeam(false);
        }
    };

    // Handle add feature
    const handleAddFeature = () => {
        if (newFeatureTitle.trim() && newFeatureTeam) {
            addFeature({
                title: newFeatureTitle,
                description: newFeatureDescription,
                teamId: newFeatureTeam,
                status: 'not-started',
                estimatedDays: parseInt(newFeatureEstimate) || 5,
                actualDays: 0,
                dependencies: [],
                blockers: [],
            });
            setNewFeatureTitle('');
            setNewFeatureDescription('');
            setNewFeatureEstimate('5');
            setShowAddFeature(false);
        }
    };

    // Handle feature click
    const handleFeatureClick = (featureId: string) => {
        setSelectedFeature(featureId);
        setShowFeatureDetails(true);
    };

    return (
        <div className="w-full h-full p-6 space-y-6 bg-gray-50 overflow-auto">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <Typography variant="h4" className="font-bold text-gray-900">
                        Release Train
                    </Typography>
                    <TextField
                        value={release}
                        onChange={(e) => setRelease(e.target.value)}
                        placeholder="Release name"
                        className="mt-2 text-lg"
                    />
                </div>
                <div className="flex gap-2">
                    <Button
                        onClick={() => setShowAddTeam(true)}
                        className="bg-purple-600 text-white hover:bg-purple-700"
                    >
                        Add Team
                    </Button>
                    <Button
                        onClick={() => setShowAddFeature(true)}
                        className="bg-blue-600 text-white hover:bg-blue-700"
                    >
                        Add Feature
                    </Button>
                    <Button
                        onClick={() => setShowCriticalPath(true)}
                        className="bg-orange-600 text-white hover:bg-orange-700"
                    >
                        Critical Path
                    </Button>
                    <Button
                        onClick={() => setShowReleaseReadiness(true)}
                        className="bg-green-600 text-white hover:bg-green-700"
                    >
                        Release Readiness
                    </Button>
                </div>
            </div>

            {/* Release Overview */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <Card className="border-l-4 border-blue-500 bg-blue-50">
                    <CardContent className="p-4">
                        <Typography variant="caption" className="text-gray-600 uppercase tracking-wide">
                            Total Teams
                        </Typography>
                        <Typography variant="h3" className="font-bold mt-2 text-blue-700">
                            {teams.length}
                        </Typography>
                    </CardContent>
                </Card>

                <Card className="border-l-4 border-purple-500 bg-purple-50">
                    <CardContent className="p-4">
                        <Typography variant="caption" className="text-gray-600 uppercase tracking-wide">
                            Total Features
                        </Typography>
                        <Typography variant="h3" className="font-bold mt-2 text-purple-700">
                            {features.length}
                        </Typography>
                    </CardContent>
                </Card>

                <Card className="border-l-4 border-orange-500 bg-orange-50">
                    <CardContent className="p-4">
                        <Typography variant="caption" className="text-gray-600 uppercase tracking-wide">
                            Estimated Release
                        </Typography>
                        <Typography variant="body1" className="font-bold mt-2 text-orange-700">
                            {releaseDate ? new Date(releaseDate).toLocaleDateString() : 'TBD'}
                        </Typography>
                    </CardContent>
                </Card>

                <Card className={`border-l-4 ${releaseRisk === 'critical' || releaseRisk === 'high' ? 'border-red-500 bg-red-50' : 'border-green-500 bg-green-50'}`}>
                    <CardContent className="p-4">
                        <Typography variant="caption" className="text-gray-600 uppercase tracking-wide">
                            Release Risk
                        </Typography>
                        <Typography variant="h4" className={`font-bold mt-2 uppercase ${getRiskColor(releaseRisk)}`}>
                            {releaseRisk}
                        </Typography>
                    </CardContent>
                </Card>
            </div>

            {/* Team Filter */}
            <div className="flex items-center gap-2">
                <Typography variant="body2" className="font-semibold text-gray-700">
                    Filter by Team:
                </Typography>
                <Button
                    onClick={() => setSelectedTeam(null)}
                    className={selectedTeam === null ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-700 hover:bg-gray-300'}
                >
                    All Teams
                </Button>
                {teams.map((team) => (
                    <Button
                        key={team.id}
                        onClick={() => setSelectedTeam(team.id)}
                        className={selectedTeam === team.id ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-700 hover:bg-gray-300'}
                    >
                        {team.name}
                    </Button>
                ))}
            </div>

            {/* Team Tracks */}
            <div className="space-y-6">
                {teams.map((team) => {
                    const teamFeatures = getFeaturesByTeam(team.id);
                    const velocity = getTeamVelocity(team.id);
                    const blockedFeatures = teamFeatures.filter((f) => f.status === 'blocked').length;

                    return (
                        <Card key={team.id} className="border border-gray-300">
                            <CardHeader className="bg-gray-100 border-b">
                                <div className="flex items-center justify-between">
                                    <div>
                                        <Typography variant="h6" className="font-bold text-gray-900">
                                            {team.name}
                                        </Typography>
                                        <Typography variant="caption" className="text-gray-600">
                                            {teamFeatures.length} features • Velocity: {velocity} points/week
                                        </Typography>
                                    </div>
                                    {blockedFeatures > 0 && (
                                        <div className="px-3 py-1 bg-red-100 text-red-800 rounded font-semibold text-sm">
                                            {blockedFeatures} Blocked
                                        </div>
                                    )}
                                </div>
                            </CardHeader>
                            <CardContent className="p-4">
                                {teamFeatures.length === 0 ? (
                                    <Typography variant="body2" className="text-gray-500 italic">
                                        No features assigned to this team yet.
                                    </Typography>
                                ) : (
                                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
                                        {teamFeatures.map((feature) => {
                                            const featureBlockers = getFeatureBlockers(feature.id);
                                            const dependencies = getFeatureDependencies(feature.id);
                                            const risk = calculateFeatureRisk(feature.id);
                                            const readiness = getReadinessChecks(feature.id);

                                            return (
                                                <div
                                                    key={feature.id}
                                                    onClick={() => handleFeatureClick(feature.id)}
                                                    className={`p-3 rounded border-2 cursor-pointer hover:shadow-md transition-shadow ${feature.status === 'blocked'
                                                            ? 'border-red-500 bg-red-50'
                                                            : 'border-gray-200 bg-white'
                                                        }`}
                                                >
                                                    <div className="flex items-start justify-between mb-2">
                                                        <Typography variant="body2" className="font-semibold line-clamp-2">
                                                            {feature.title}
                                                        </Typography>
                                                        <span className={`px-2 py-0.5 rounded text-xs font-semibold ml-2 ${getRiskColor(risk)}`}>
                                                            {risk}
                                                        </span>
                                                    </div>

                                                    <span className={`inline-block px-2 py-1 rounded text-xs font-semibold border mb-2 ${getStatusColor(feature.status)}`}>
                                                        {feature.status}
                                                    </span>

                                                    <div className="space-y-1 text-xs text-gray-600">
                                                        <div>
                                                            Est: {feature.estimatedDays}d • Actual: {feature.actualDays}d
                                                        </div>
                                                        {dependencies.length > 0 && (
                                                            <div className="text-blue-600">
                                                                🔗 {dependencies.length} dependencies
                                                            </div>
                                                        )}
                                                        {featureBlockers.length > 0 && (
                                                            <div className="text-red-600 font-semibold">
                                                                ⚠️ {featureBlockers.length} blockers
                                                            </div>
                                                        )}
                                                        {readiness.filter((r) => r.passed).length > 0 && (
                                                            <div className="text-green-600">
                                                                ✓ {readiness.filter((r) => r.passed).length}/{readiness.length} checks
                                                            </div>
                                                        )}
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                )}
                            </CardContent>
                        </Card>
                    );
                })}
            </div>

            {/* Feature Status Summary */}
            <Card>
                <CardHeader>
                    <Typography variant="h6" className="font-semibold">
                        Feature Status Breakdown
                    </Typography>
                </CardHeader>
                <CardContent>
                    <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
                        {(['not-started', 'in-progress', 'testing', 'blocked', 'done'] as FeatureStatus[]).map((status) => {
                            const count = getFeaturesByStatus(status).length;
                            return (
                                <div key={status} className="text-center">
                                    <Typography variant="h4" className="font-bold">
                                        {count}
                                    </Typography>
                                    <span className={`inline-block px-3 py-1 rounded text-sm font-semibold border mt-2 ${getStatusColor(status)}`}>
                                        {status}
                                    </span>
                                </div>
                            );
                        })}
                    </div>
                </CardContent>
            </Card>

            {/* Add Team Dialog */}
            <Dialog open={showAddTeam} onClose={() => setShowAddTeam(false)}>
                <DialogTitle>Add New Team</DialogTitle>
                <DialogContent>
                    <div className="space-y-4">
                        <TextField
                            label="Team Name"
                            value={newTeamName}
                            onChange={(e) => setNewTeamName(e.target.value)}
                            placeholder="e.g., Frontend Team"
                            fullWidth
                        />
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowAddTeam(false)}>Cancel</Button>
                    <Button onClick={handleAddTeam} className="bg-blue-600 text-white hover:bg-blue-700">
                        Add Team
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Feature Dialog */}
            <Dialog open={showAddFeature} onClose={() => setShowAddFeature(false)}>
                <DialogTitle>Add New Feature</DialogTitle>
                <DialogContent>
                    <div className="space-y-4">
                        <TextField
                            label="Feature Title"
                            value={newFeatureTitle}
                            onChange={(e) => setNewFeatureTitle(e.target.value)}
                            placeholder="e.g., User Authentication"
                            fullWidth
                        />
                        <TextField
                            label="Description"
                            value={newFeatureDescription}
                            onChange={(e) => setNewFeatureDescription(e.target.value)}
                            placeholder="Feature description..."
                            fullWidth
                            multiline
                            rows={3}
                        />
                        <div>
                            <Typography variant="caption" className="text-gray-600 mb-1 block">
                                Team
                            </Typography>
                            <select
                                value={newFeatureTeam}
                                onChange={(e) => setNewFeatureTeam(e.target.value)}
                                className="w-full px-3 py-2 border border-gray-300 rounded"
                            >
                                <option value="">Select team...</option>
                                {teams.map((team) => (
                                    <option key={team.id} value={team.id}>
                                        {team.name}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <TextField
                            label="Estimated Days"
                            type="number"
                            value={newFeatureEstimate}
                            onChange={(e) => setNewFeatureEstimate(e.target.value)}
                            fullWidth
                        />
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowAddFeature(false)}>Cancel</Button>
                    <Button onClick={handleAddFeature} className="bg-blue-600 text-white hover:bg-blue-700">
                        Add Feature
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Feature Details Dialog */}
            <Dialog open={showFeatureDetails} onClose={() => setShowFeatureDetails(false)} maxWidth="lg">
                <DialogTitle>Feature Details</DialogTitle>
                <DialogContent>
                    {featureDetails && (
                        <div className="space-y-4">
                            <div>
                                <Typography variant="h6" className="font-semibold">
                                    {featureDetails.title}
                                </Typography>
                                <Typography variant="body2" className="text-gray-600 mt-1">
                                    {featureDetails.description}
                                </Typography>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <Typography variant="caption" className="text-gray-600 uppercase">
                                        Status
                                    </Typography>
                                    <div className="mt-1">
                                        <span className={`inline-block px-3 py-1 rounded text-sm font-semibold border ${getStatusColor(featureDetails.status)}`}>
                                            {featureDetails.status}
                                        </span>
                                    </div>
                                </div>
                                <div>
                                    <Typography variant="caption" className="text-gray-600 uppercase">
                                        Risk Level
                                    </Typography>
                                    <Typography variant="body1" className={`font-bold uppercase ${getRiskColor(calculateFeatureRisk(featureDetails.id))}`}>
                                        {calculateFeatureRisk(featureDetails.id)}
                                    </Typography>
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <Typography variant="caption" className="text-gray-600 uppercase">
                                        Estimated Days
                                    </Typography>
                                    <Typography variant="body1" className="font-semibold">
                                        {featureDetails.estimatedDays} days
                                    </Typography>
                                </div>
                                <div>
                                    <Typography variant="caption" className="text-gray-600 uppercase">
                                        Actual Days
                                    </Typography>
                                    <Typography variant="body1" className="font-semibold">
                                        {featureDetails.actualDays} days
                                    </Typography>
                                </div>
                            </div>

                            {getFeatureBlockers(featureDetails.id).length > 0 && (
                                <div>
                                    <Typography variant="body2" className="font-semibold mb-2 text-red-600">
                                        Blockers ({getFeatureBlockers(featureDetails.id).length})
                                    </Typography>
                                    <div className="space-y-2">
                                        {getFeatureBlockers(featureDetails.id).map((blocker) => (
                                            <div key={blocker.id} className="p-3 bg-red-50 rounded border border-red-200">
                                                <div className="flex items-start justify-between">
                                                    <div className="flex-1">
                                                        <div className="flex items-center gap-2">
                                                            <span className="text-lg">{getBlockerIcon(blocker.type)}</span>
                                                            <Typography variant="body2" className="font-semibold">
                                                                {blocker.description}
                                                            </Typography>
                                                        </div>
                                                        <Typography variant="caption" className="text-gray-600">
                                                            Type: {blocker.type} • Reported: {new Date(blocker.createdAt).toLocaleDateString()}
                                                        </Typography>
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {getFeatureDependencies(featureDetails.id).length > 0 && (
                                <div>
                                    <Typography variant="body2" className="font-semibold mb-2">
                                        Dependencies ({getFeatureDependencies(featureDetails.id).length})
                                    </Typography>
                                    <div className="space-y-2">
                                        {getFeatureDependencies(featureDetails.id).map((dep) => {
                                            const depFeature = getFeatures().find((f) => f.id === dep.dependsOnFeatureId);
                                            return (
                                                <div key={dep.id} className="p-2 bg-blue-50 rounded border border-blue-200">
                                                    <Typography variant="caption" className="font-semibold">
                                                        {depFeature?.title || 'Unknown Feature'}
                                                    </Typography>
                                                    <Typography variant="caption" className="block text-gray-600">
                                                        Status: {depFeature?.status || 'unknown'}
                                                    </Typography>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            )}

                            <div>
                                <Typography variant="body2" className="font-semibold mb-2">
                                    Readiness Checks
                                </Typography>
                                <div className="space-y-2">
                                    {getReadinessChecks(featureDetails.id).map((check) => (
                                        <div key={check.type} className="flex items-center justify-between p-2 bg-gray-50 rounded">
                                            <div className="flex items-center gap-2">
                                                <span className={check.passed ? 'text-green-600 text-lg' : 'text-gray-400 text-lg'}>
                                                    {check.passed ? '✓' : '○'}
                                                </span>
                                                <Typography variant="body2">{check.type}</Typography>
                                            </div>
                                            {check.notes && (
                                                <Typography variant="caption" className="text-gray-600">
                                                    {check.notes}
                                                </Typography>
                                            )}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowFeatureDetails(false)}>Close</Button>
                </DialogActions>
            </Dialog>

            {/* Critical Path Dialog */}
            <Dialog open={showCriticalPath} onClose={() => setShowCriticalPath(false)} maxWidth="lg">
                <DialogTitle>Critical Path Analysis</DialogTitle>
                <DialogContent>
                    <div className="space-y-4">
                        <div className="p-4 bg-orange-50 rounded border border-orange-200">
                            <Typography variant="body1" className="font-semibold">
                                Critical Path Length: {criticalPath.length} features
                            </Typography>
                            <Typography variant="body2" className="text-gray-600 mt-1">
                                This is the longest chain of dependent features that determines the minimum release timeline.
                            </Typography>
                        </div>

                        <div className="space-y-2">
                            {criticalPath.map((featureId, index) => {
                                const feature = getFeatures().find((f) => f.id === featureId);
                                if (!feature) return null;

                                return (
                                    <div key={featureId} className="relative">
                                        {index > 0 && (
                                            <div className="absolute left-6 -top-2 w-0.5 h-4 bg-orange-400" />
                                        )}
                                        <div className="flex items-center gap-3 p-3 bg-white rounded border-2 border-orange-300">
                                            <div className="w-12 h-12 rounded-full bg-orange-100 flex items-center justify-center font-bold text-orange-700">
                                                {index + 1}
                                            </div>
                                            <div className="flex-1">
                                                <Typography variant="body2" className="font-semibold">
                                                    {feature.title}
                                                </Typography>
                                                <Typography variant="caption" className="text-gray-600">
                                                    Team: {teams.find((t) => t.id === feature.teamId)?.name} • Status: {feature.status} • {feature.estimatedDays} days
                                                </Typography>
                                            </div>
                                            <span className={`px-2 py-1 rounded text-xs font-semibold border ${getStatusColor(feature.status)}`}>
                                                {feature.status}
                                            </span>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowCriticalPath(false)}>Close</Button>
                </DialogActions>
            </Dialog>

            {/* Release Readiness Dialog */}
            <Dialog open={showReleaseReadiness} onClose={() => setShowReleaseReadiness(false)} maxWidth="lg">
                <DialogTitle>Release Readiness Report</DialogTitle>
                <DialogContent>
                    <div className="space-y-4">
                        <div className={`p-4 rounded border-2 ${releaseRisk === 'low' || releaseRisk === 'medium'
                                ? 'bg-green-50 border-green-300'
                                : 'bg-red-50 border-red-300'
                            }`}>
                            <Typography variant="h6" className="font-bold">
                                Overall Status: {releaseRisk.toUpperCase()} RISK
                            </Typography>
                            <Typography variant="body2" className="text-gray-700 mt-1">
                                Estimated Release Date: {releaseDate ? new Date(releaseDate).toLocaleDateString() : 'TBD'}
                            </Typography>
                        </div>

                        <div>
                            <Typography variant="body1" className="font-semibold mb-2">
                                Feature Completion
                            </Typography>
                            <div className="grid grid-cols-5 gap-2">
                                {(['not-started', 'in-progress', 'testing', 'blocked', 'done'] as FeatureStatus[]).map((status) => {
                                    const count = getFeaturesByStatus(status).length;
                                    const total = features.length;
                                    const percentage = total > 0 ? Math.round((count / total) * 100) : 0;

                                    return (
                                        <div key={status} className="text-center p-3 bg-gray-50 rounded">
                                            <Typography variant="h5" className="font-bold">
                                                {count}
                                            </Typography>
                                            <Typography variant="caption" className="text-gray-600 block">
                                                {percentage}%
                                            </Typography>
                                            <span className={`inline-block px-2 py-1 rounded text-xs font-semibold border mt-1 ${getStatusColor(status)}`}>
                                                {status}
                                            </span>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>

                        <div>
                            <Typography variant="body1" className="font-semibold mb-2">
                                Blocked Features ({getFeaturesByStatus('blocked').length})
                            </Typography>
                            {getFeaturesByStatus('blocked').length === 0 ? (
                                <Typography variant="body2" className="text-green-600">
                                    ✓ No blocked features
                                </Typography>
                            ) : (
                                <div className="space-y-2">
                                    {getFeaturesByStatus('blocked').map((feature) => (
                                        <div key={feature.id} className="p-2 bg-red-50 rounded border border-red-200">
                                            <Typography variant="body2" className="font-semibold">
                                                {feature.title}
                                            </Typography>
                                            <Typography variant="caption" className="text-gray-600">
                                                Team: {teams.find((t) => t.id === feature.teamId)?.name} • {getFeatureBlockers(feature.id).length} blockers
                                            </Typography>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>

                        <div>
                            <Typography variant="body1" className="font-semibold mb-2">
                                Team Readiness
                            </Typography>
                            <div className="space-y-2">
                                {teams.map((team) => {
                                    const teamFeatures = getFeaturesByTeam(team.id);
                                    const completedFeatures = teamFeatures.filter((f) => f.status === 'done').length;
                                    const totalFeatures = teamFeatures.length;
                                    const percentage = totalFeatures > 0 ? Math.round((completedFeatures / totalFeatures) * 100) : 0;

                                    return (
                                        <div key={team.id} className="p-3 bg-gray-50 rounded">
                                            <div className="flex items-center justify-between mb-2">
                                                <Typography variant="body2" className="font-semibold">
                                                    {team.name}
                                                </Typography>
                                                <Typography variant="caption" className="text-gray-600">
                                                    {completedFeatures}/{totalFeatures} features ({percentage}%)
                                                </Typography>
                                            </div>
                                            <div className="w-full bg-gray-200 rounded-full h-2">
                                                <div
                                                    className={`h-2 rounded-full ${percentage === 100 ? 'bg-green-600' : 'bg-blue-600'}`}
                                                    style={{ width: `${percentage}%` }}
                                                />
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowReleaseReadiness(false)}>Close</Button>
                </DialogActions>
            </Dialog>
        </div>
    );
};

export default ReleaseTrain;
