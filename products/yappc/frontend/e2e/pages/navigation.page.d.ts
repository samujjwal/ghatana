/**
 * Page Object Model - Navigation Page
 * Encapsulates navigation and common UI interactions
 */
import { Page, Locator } from '@playwright/test';
export declare class NavigationPage {
    readonly page: Page;
    readonly logo: Locator;
    readonly userMenu: Locator;
    readonly notificationsButton: Locator;
    readonly searchButton: Locator;
    readonly themeToggle: Locator;
    readonly dashboardNav: Locator;
    readonly projectsNav: Locator;
    readonly buildsNav: Locator;
    readonly deploymentsNav: Locator;
    readonly teamsNav: Locator;
    readonly settingsNav: Locator;
    readonly mobileMenuButton: Locator;
    readonly mobileDrawer: Locator;
    readonly bottomNavigation: Locator;
    readonly breadcrumbs: Locator;
    readonly globalSearch: Locator;
    readonly quickFilters: Locator;
    constructor(page: Page);
    clickDashboard(): Promise<void>;
    clickProjectsNav(): Promise<void>;
    clickBuildsNav(): Promise<void>;
    clickDeploymentsNav(): Promise<void>;
    clickTeamsNav(): Promise<void>;
    clickSettingsNav(): Promise<void>;
    openMobileMenu(): Promise<void>;
    closeMobileMenu(): Promise<void>;
    clickBottomNavItem(item: 'projects' | 'dashboard' | 'notifications' | 'profile'): Promise<void>;
    openUserMenu(): Promise<void>;
    clickProfile(): Promise<void>;
    clickAccountSettings(): Promise<void>;
    logout(): Promise<void>;
    toggleTheme(): Promise<string | null>;
    setTheme(theme: 'light' | 'dark' | 'auto'): Promise<void>;
    openGlobalSearch(): Promise<void>;
    searchGlobally(query: string): Promise<void>;
    openNotifications(): Promise<void>;
    getNotificationCount(): Promise<number>;
    markNotificationAsRead(notificationId: string): Promise<void>;
    markAllNotificationsAsRead(): Promise<void>;
    clickBreadcrumb(level: number): Promise<void>;
    getBreadcrumbPath(): Promise<string[]>;
    useKeyboardShortcut(shortcut: string): Promise<void>;
    openCommandPalette(): Promise<void>;
    executeCommand(command: string): Promise<void>;
    applyQuickFilter(filter: string): Promise<void>;
    clearAllFilters(): Promise<void>;
    waitForNavigation(): Promise<void>;
    assertCurrentPage(expectedPath: string): Promise<void>;
    getPageTitle(): Promise<string>;
    isLoggedIn(): Promise<boolean>;
    getCurrentUser(): Promise<{
        name: string;
        email: string;
    } | null>;
    waitForLoadingToComplete(): Promise<void>;
    assertNoGlobalErrors(): Promise<void>;
    dismissGlobalError(): Promise<void>;
    checkFocusManagement(): Promise<Locator>;
    navigateWithKeyboard(direction: 'next' | 'previous' | 'first' | 'last'): Promise<void>;
    skipToMainContent(): Promise<void>;
}
//# sourceMappingURL=navigation.page.d.ts.map