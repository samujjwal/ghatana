"use strict";
/**
 * Page Object Model - Navigation Page
 * Encapsulates navigation and common UI interactions
 */
const __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P((resolve) => { resolve(value); }); }
    return new (P || (P = Promise))((resolve, reject) => {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
const __generator = (this && this.__generator) || function (thisArg, body) {
    let _ = { label: 0, sent() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g = Object.create((typeof Iterator === "function" ? Iterator : Object).prototype);
    return g.next = verb(0), g["throw"] = verb(1), g["return"] = verb(2), typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (g && (g = 0, op[0] && (_ = 0)), _) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.NavigationPage = void 0;
const test_1 = require("@playwright/test");
const NavigationPage = /** @class */ (function () {
    function NavigationPage(page) {
        this.page = page;
        // Header elements
        this.logo = page.locator('[data-testid="app-logo"]');
        this.userMenu = page.locator('[data-testid="user-menu"]');
        this.notificationsButton = page.locator('[data-testid="notifications-button"]');
        this.searchButton = page.locator('[data-testid="search-button"]');
        this.themeToggle = page.locator('[data-testid="theme-toggle"]');
        // Main navigation
        this.dashboardNav = page.locator('[data-testid="nav-dashboard"]');
        this.projectsNav = page.locator('[data-testid="nav-projects"]');
        this.buildsNav = page.locator('[data-testid="nav-builds"]');
        this.deploymentsNav = page.locator('[data-testid="nav-deployments"]');
        this.teamsNav = page.locator('[data-testid="nav-teams"]');
        this.settingsNav = page.locator('[data-testid="nav-settings"]');
        // Mobile navigation
        this.mobileMenuButton = page.locator('[data-testid="mobile-menu-button"]');
        this.mobileDrawer = page.locator('[data-testid="mobile-drawer"]');
        this.bottomNavigation = page.locator('[data-testid="bottom-navigation"]');
        // Breadcrumbs and search
        this.breadcrumbs = page.locator('[data-testid="breadcrumbs"]');
        this.globalSearch = page.locator('[data-testid="global-search"]');
        this.quickFilters = page.locator('[data-testid="quick-filters"]');
    }
    // Navigation methods
    NavigationPage.prototype.clickDashboard = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.dashboardNav.click()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.waitForURL('**/dashboard')];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.page.locator('h1')).toContainText(/dashboard/i)];
                    case 3:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.clickProjectsNav = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.projectsNav.click()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.waitForURL('**/projects')];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.page.locator('h1, h2')).toContainText(/projects/i)];
                    case 3:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.clickBuildsNav = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.buildsNav.click()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.waitForURL('**/builds')];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.page.locator('h1, h2')).toContainText(/builds/i)];
                    case 3:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.clickDeploymentsNav = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.deploymentsNav.click()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.waitForURL('**/deployments')];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.page.locator('h1, h2')).toContainText(/deployments/i)];
                    case 3:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.clickTeamsNav = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.teamsNav.click()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.waitForURL('**/teams')];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.page.locator('h1, h2')).toContainText(/teams/i)];
                    case 3:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.clickSettingsNav = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.settingsNav.click()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.waitForURL('**/settings')];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.page.locator('h1, h2')).toContainText(/settings/i)];
                    case 3:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    // Mobile navigation methods
    NavigationPage.prototype.openMobileMenu = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.mobileMenuButton.click()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.mobileDrawer).toBeVisible()];
                    case 2:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.closeMobileMenu = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.page.click('[data-testid="mobile-drawer-close"]')];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.mobileDrawer).not.toBeVisible()];
                    case 2:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.clickBottomNavItem = function (item) {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.page.click("[data-testid=\"bottom-nav-".concat(item, "\"]"))];
                    case 1:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    // User menu methods
    NavigationPage.prototype.openUserMenu = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.userMenu.click()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.page.locator('[data-testid="user-menu-dropdown"]')).toBeVisible()];
                    case 2:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.clickProfile = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.openUserMenu()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.click('[data-testid="user-menu-profile"]')];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, this.page.waitForURL('**/profile')];
                    case 3:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.clickAccountSettings = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.openUserMenu()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.click('[data-testid="user-menu-account"]')];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, this.page.waitForURL('**/account')];
                    case 3:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.logout = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.openUserMenu()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.click('[data-testid="user-menu-logout"]')];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, this.page.waitForURL('**/auth/login')];
                    case 3:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.page.locator('[data-testid="login-form"]')).toBeVisible()];
                    case 4:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    // Theme and preferences
    NavigationPage.prototype.toggleTheme = function () {
        return __awaiter(this, void 0, void 0, function () {
            let currentTheme, newTheme;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.page.evaluate(() => {
                            return document.documentElement.getAttribute('data-theme');
                        })];
                    case 1:
                        currentTheme = _a.sent();
                        return [4 /*yield*/, this.themeToggle.click()];
                    case 2:
                        _a.sent();
                        // Wait for theme to change
                        return [4 /*yield*/, this.page.waitForFunction((prevTheme) => {
                                return document.documentElement.getAttribute('data-theme') !== prevTheme;
                            }, currentTheme)];
                    case 3:
                        // Wait for theme to change
                        _a.sent();
                        return [4 /*yield*/, this.page.evaluate(() => {
                                return document.documentElement.getAttribute('data-theme');
                            })];
                    case 4:
                        newTheme = _a.sent();
                        return [2 /*return*/, newTheme];
                }
            });
        });
    };
    NavigationPage.prototype.setTheme = function (theme) {
        return __awaiter(this, void 0, void 0, function () {
            let currentTheme;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.page.evaluate(() => {
                            return document.documentElement.getAttribute('data-theme');
                        })];
                    case 1:
                        currentTheme = _a.sent();
                        if (!(currentTheme !== theme)) return [3 /*break*/, 4];
                        return [4 /*yield*/, this.themeToggle.click()];
                    case 2:
                        _a.sent();
                        if (!(theme !== 'light')) return [3 /*break*/, 4];
                        // If not light, click again for dark or auto
                        return [4 /*yield*/, this.themeToggle.click()];
                    case 3:
                        // If not light, click again for dark or auto
                        _a.sent();
                        _a.label = 4;
                    case 4: return [2 /*return*/];
                }
            });
        });
    };
    // Search and notifications
    NavigationPage.prototype.openGlobalSearch = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.searchButton.click()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.page.locator('[data-testid="search-dialog"]')).toBeVisible()];
                    case 2:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.searchGlobally = function (query) {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.openGlobalSearch()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.fill('[data-testid="search-input"]', query)];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, this.page.keyboard.press('Enter')];
                    case 3:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.page.locator('[data-testid="search-results"]')).toBeVisible()];
                    case 4:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.openNotifications = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.notificationsButton.click()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.page.locator('[data-testid="notifications-panel"]')).toBeVisible()];
                    case 2:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.getNotificationCount = function () {
        return __awaiter(this, void 0, void 0, function () {
            let badge, text;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        badge = this.page.locator('[data-testid="notifications-badge"]');
                        return [4 /*yield*/, badge.isVisible()];
                    case 1:
                        if (!_a.sent()) return [3 /*break*/, 3];
                        return [4 /*yield*/, badge.textContent()];
                    case 2:
                        text = _a.sent();
                        return [2 /*return*/, parseInt(text || '0')];
                    case 3: return [2 /*return*/, 0];
                }
            });
        });
    };
    NavigationPage.prototype.markNotificationAsRead = function (notificationId) {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.openNotifications()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.click("[data-testid=\"notification-".concat(notificationId, "\"] [data-testid=\"mark-read\"]"))];
                    case 2:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.markAllNotificationsAsRead = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.openNotifications()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.click('[data-testid="mark-all-read"]')];
                    case 2:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    // Breadcrumb navigation
    NavigationPage.prototype.clickBreadcrumb = function (level) {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.breadcrumbs.locator('a').nth(level).click()];
                    case 1:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.getBreadcrumbPath = function () {
        return __awaiter(this, void 0, void 0, function () {
            let items, path, _i, items_1, item, text;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.breadcrumbs.locator('a, span').all()];
                    case 1:
                        items = _a.sent();
                        path = [];
                        _i = 0, items_1 = items;
                        _a.label = 2;
                    case 2:
                        if (!(_i < items_1.length)) return [3 /*break*/, 5];
                        item = items_1[_i];
                        return [4 /*yield*/, item.textContent()];
                    case 3:
                        text = _a.sent();
                        if (text && text.trim()) {
                            path.push(text.trim());
                        }
                        _a.label = 4;
                    case 4:
                        _i++;
                        return [3 /*break*/, 2];
                    case 5: return [2 /*return*/, path];
                }
            });
        });
    };
    // Quick actions and shortcuts
    NavigationPage.prototype.useKeyboardShortcut = function (shortcut) {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.page.keyboard.press(shortcut)];
                    case 1:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.openCommandPalette = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.page.keyboard.press('Control+k')];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, (0, test_1.expect)(this.page.locator('[data-testid="command-palette"]')).toBeVisible()];
                    case 2:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.executeCommand = function (command) {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.openCommandPalette()];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.fill('[data-testid="command-input"]', command)];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, this.page.keyboard.press('Enter')];
                    case 3:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    // Quick filters
    NavigationPage.prototype.applyQuickFilter = function (filter) {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.page.click("[data-testid=\"quick-filter-".concat(filter, "\"]"))];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.waitForTimeout(500)];
                    case 2:
                        _a.sent(); // Wait for filter to apply
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.clearAllFilters = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.page.click('[data-testid="clear-filters"]')];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, this.page.waitForTimeout(500)];
                    case 2:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    // Utility methods
    NavigationPage.prototype.waitForNavigation = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.page.waitForLoadState('networkidle')];
                    case 1:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.assertCurrentPage = function (expectedPath) {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, (0, test_1.expect)(this.page).toHaveURL(new RegExp(expectedPath))];
                    case 1:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.getPageTitle = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.page.title()];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        });
    };
    NavigationPage.prototype.isLoggedIn = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.userMenu.isVisible()];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        });
    };
    NavigationPage.prototype.getCurrentUser = function () {
        return __awaiter(this, void 0, void 0, function () {
            let name, email;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.isLoggedIn()];
                    case 1:
                        if (!(_a.sent())) {
                            return [2 /*return*/, null];
                        }
                        return [4 /*yield*/, this.openUserMenu()];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, this.page
                                .locator('[data-testid="user-name"]')
                                .textContent()];
                    case 3:
                        name = _a.sent();
                        return [4 /*yield*/, this.page
                                .locator('[data-testid="user-email"]')
                                .textContent()];
                    case 4:
                        email = _a.sent();
                        // Close menu
                        return [4 /*yield*/, this.page.keyboard.press('Escape')];
                    case 5:
                        // Close menu
                        _a.sent();
                        return [2 /*return*/, {
                                name: (name === null || name === void 0 ? void 0 : name.trim()) || '',
                                email: (email === null || email === void 0 ? void 0 : email.trim()) || '',
                            }];
                }
            });
        });
    };
    // Error and loading states
    NavigationPage.prototype.waitForLoadingToComplete = function () {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, this.page.waitForSelector('[data-testid="loading"], .loading', {
                            state: 'detached',
                            timeout: 30000,
                        })];
                    case 1:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.assertNoGlobalErrors = function () {
        return __awaiter(this, void 0, void 0, function () {
            let errorElements;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        errorElements = this.page.locator('[data-testid="global-error"], .global-error, [role="alert"][aria-live="assertive"]');
                        return [4 /*yield*/, (0, test_1.expect)(errorElements).toHaveCount(0)];
                    case 1:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.dismissGlobalError = function () {
        return __awaiter(this, void 0, void 0, function () {
            let dismissButton;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        dismissButton = this.page.locator('[data-testid="dismiss-error"], .error-dismiss');
                        return [4 /*yield*/, dismissButton.isVisible()];
                    case 1:
                        if (!_a.sent()) return [3 /*break*/, 3];
                        return [4 /*yield*/, dismissButton.click()];
                    case 2:
                        _a.sent();
                        _a.label = 3;
                    case 3: return [2 /*return*/];
                }
            });
        });
    };
    // Accessibility helpers
    NavigationPage.prototype.checkFocusManagement = function () {
        return __awaiter(this, void 0, void 0, function () {
            let focusedElement;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        focusedElement = this.page.locator(':focus');
                        return [4 /*yield*/, (0, test_1.expect)(focusedElement).toBeVisible()];
                    case 1:
                        _a.sent();
                        return [2 /*return*/, focusedElement];
                }
            });
        });
    };
    NavigationPage.prototype.navigateWithKeyboard = function (direction) {
        return __awaiter(this, void 0, void 0, function () {
            let _a;
            return __generator(this, function (_b) {
                switch (_b.label) {
                    case 0:
                        _a = direction;
                        switch (_a) {
                            case 'next': return [3 /*break*/, 1];
                            case 'previous': return [3 /*break*/, 3];
                            case 'first': return [3 /*break*/, 5];
                            case 'last': return [3 /*break*/, 7];
                        }
                        return [3 /*break*/, 9];
                    case 1: return [4 /*yield*/, this.page.keyboard.press('Tab')];
                    case 2:
                        _b.sent();
                        return [3 /*break*/, 9];
                    case 3: return [4 /*yield*/, this.page.keyboard.press('Shift+Tab')];
                    case 4:
                        _b.sent();
                        return [3 /*break*/, 9];
                    case 5: return [4 /*yield*/, this.page.keyboard.press('Home')];
                    case 6:
                        _b.sent();
                        return [3 /*break*/, 9];
                    case 7: return [4 /*yield*/, this.page.keyboard.press('End')];
                    case 8:
                        _b.sent();
                        return [3 /*break*/, 9];
                    case 9: return [2 /*return*/];
                }
            });
        });
    };
    NavigationPage.prototype.skipToMainContent = function () {
        return __awaiter(this, void 0, void 0, function () {
            let skipLink;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        skipLink = this.page.locator('[data-testid="skip-to-content"]');
                        return [4 /*yield*/, skipLink.isVisible()];
                    case 1:
                        if (!_a.sent()) return [3 /*break*/, 3];
                        return [4 /*yield*/, skipLink.click()];
                    case 2:
                        _a.sent();
                        return [3 /*break*/, 6];
                    case 3: return [4 /*yield*/, this.page.keyboard.press('Tab')];
                    case 4:
                        _a.sent();
                        return [4 /*yield*/, this.page.keyboard.press('Enter')];
                    case 5:
                        _a.sent();
                        _a.label = 6;
                    case 6: return [2 /*return*/];
                }
            });
        });
    };
    return NavigationPage;
}());
exports.NavigationPage = NavigationPage;
