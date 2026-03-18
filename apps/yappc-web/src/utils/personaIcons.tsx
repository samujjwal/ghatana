/**
 * Persona Icon Mapping
 * 
 * Maps persona IDs to Material-UI icons.
 * This is the only hardcoded mapping - icons are not stored in backend.
 * 
 * @doc.type utility
 * @doc.purpose Map persona IDs to UI icons
 * @doc.layer product
 */

import { // Execution
    Code as CodeIcon, UserCog as SupervisorIcon, Cloud as CloudIcon, Bug as BugIcon, HeartPulse as MonitorIcon, Hammer as BuildIcon, // Governance
    Security as SecurityIcon, Gavel as GavelIcon, Building2 as ArchitectureIcon, Shield as ShieldIcon, ShieldCheck as VerifiedUserIcon, // Strategic
    TrendingUp as TrendingIcon, ClipboardList as AssignmentIcon, GitBranch as AccountTreeIcon, LineChart as AnalyticsIcon, Users as GroupsIcon, BriefcaseBusiness as BusinessCenterIcon, User as PersonIcon, // Operations
    RocketLaunch as RocketIcon, HardDrive as StorageIcon, Headset as SupportAgentIcon, Headset as HeadsetIcon, // Administrative
    AdminPanelSettings as AdminIcon, Eye as VisibilityIcon, Settings as SettingsIcon } from 'lucide-react';

/**
 * Icon mapping for each persona ID
 */
export const PERSONA_ICONS: Record<string, React.ReactElement> = {
    // Technical
    'developer': <CodeIcon />,
    'tech-lead': <SupervisorIcon />,
    'architect': <ArchitectureIcon />,
    'infrastructure-architect': <StorageIcon />,
    'devops': <CloudIcon />,
    'devops-engineer': <CloudIcon />,
    'sre': <MonitorIcon />,
    'operator': <SettingsIcon />,
    'infrastructure-engineer': <StorageIcon />,
    'qa-engineer': <BugIcon />,
    'quality-engineer': <VerifiedUserIcon />,

    // Security & Governance
    'security': <SecurityIcon />,
    'security-engineer': <ShieldIcon />,
    'compliance-officer': <GavelIcon />,
    'auditor': <VerifiedUserIcon />,
    'ciso': <ShieldIcon />,

    // Product & Management
    'product-manager': <AssignmentIcon />,
    'product-owner': <AssignmentIcon />,
    'business-analyst': <AnalyticsIcon />,
    'program-manager': <AccountTreeIcon />,
    'engineering-manager': <SupervisorIcon />,
    'release-manager': <RocketIcon />,

    // Executive
    'executive': <BusinessCenterIcon />,
    'leadership': <GroupsIcon />,
    'cto': <TrendingIcon />,
};

/**
 * Get icon for a persona ID
 */
export function getPersonaIcon(personaId: string): React.ReactElement {
    return PERSONA_ICONS[personaId] || <PersonIcon />;
}
