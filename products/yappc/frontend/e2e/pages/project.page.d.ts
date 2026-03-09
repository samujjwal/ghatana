/**
 * Page Object Model - Project Page
 * Encapsulates project-related page interactions
 */
import { Page, Locator } from '@playwright/test';
export declare class ProjectPage {
    readonly page: Page;
    readonly overviewTab: Locator;
    readonly buildsTab: Locator;
    readonly deployTab: Locator;
    readonly monitorTab: Locator;
    readonly versionsTab: Locator;
    readonly settingsTab: Locator;
    readonly projectTitle: Locator;
    readonly projectDescription: Locator;
    readonly healthScore: Locator;
    readonly recentActivity: Locator;
    readonly buildsList: Locator;
    readonly triggerBuildButton: Locator;
    readonly buildProgress: Locator;
    readonly buildLogs: Locator;
    readonly deploymentEnvironments: Locator;
    readonly deployStagingButton: Locator;
    readonly deployProductionButton: Locator;
    readonly confirmDeployButton: Locator;
    readonly rollbackButton: Locator;
    readonly metricsChart: Locator;
    readonly logsViewer: Locator;
    readonly alertsPanel: Locator;
    readonly performanceMetrics: Locator;
    readonly snapshotsList: Locator;
    readonly createSnapshotButton: Locator;
    readonly compareSnapshotsButton: Locator;
    readonly branchSelector: Locator;
    readonly projectSettings: Locator;
    readonly teamManagement: Locator;
    readonly accessControl: Locator;
    readonly apiTokens: Locator;
    constructor(page: Page);
    safeClick(selector: string): Promise<void>;
    clickOverviewTab(): Promise<void>;
    clickBuildsTab(): Promise<void>;
    clickDeployTab(): Promise<void>;
    clickMonitorTab(): Promise<void>;
    clickVersionsTab(): Promise<void>;
    clickSettingsTab(): Promise<void>;
    getHealthScore(): Promise<number>;
    getRecentActivityCount(): Promise<number>;
    triggerBuild(buildType?: 'development' | 'staging' | 'production'): Promise<void>;
    waitForBuildCompletion(timeout?: number): Promise<void>;
    getBuildLogs(): Promise<string>;
    deployToStaging(): Promise<void>;
    deployToProduction(): Promise<void>;
    rollbackDeployment(environment: string): Promise<void>;
    getPerformanceMetrics(): Promise<Record<string, string>>;
    filterLogs(level: 'info' | 'warn' | 'error' | 'debug'): Promise<void>;
    searchLogs(query: string): Promise<void>;
    createSnapshot(name: string, description: string): Promise<void>;
    compareSnapshots(snapshot1: string, snapshot2: string): Promise<void>;
    switchBranch(branchName: string): Promise<void>;
    updateProjectSettings(settings: {
        name?: string;
        description?: string;
        visibility?: string;
    }): Promise<void>;
    addTeamMember(email: string, role: string): Promise<void>;
    removeTeamMember(email: string): Promise<void>;
    generateApiToken(name: string, permissions: string[]): Promise<string | null>;
    revokeApiToken(tokenId: string): Promise<void>;
    waitForPageLoad(): Promise<void>;
    takeScreenshot(name: string): Promise<void>;
    assertNoErrors(): Promise<void>;
}
//# sourceMappingURL=project.page.d.ts.map