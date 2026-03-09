/**
 * Material-UI Theme Configuration with Accessibility Enhancements
 * 
 * @doc.type configuration
 * @doc.purpose Provide MUI theme with WCAG-compliant touch targets (44x44px minimum)
 * @doc.layer platform
 * @doc.pattern Configuration
 * 
 * Phase 1 Implementation: Touch Target Remediation (P0 Critical)
 */

import { createTheme as createMuiTheme, type Theme } from '@mui/material/styles';
import { breakpoints, touchTargets } from '@ghatana/tokens';

/**
 * Create Material-UI theme with accessibility enhancements
 * 
 * Key Features:
 * - WCAG 2.1 Level AA compliant touch targets (44x44px minimum)
 * - Unified breakpoint system from @ghatana/tokens
 * - Enhanced button and icon button sizing
 */
export function createAccessibleMuiTheme(): Theme {
    return createMuiTheme({
        // Unified breakpoint system from @ghatana/tokens
        breakpoints: {
            values: {
                xs: breakpoints.xs,
                sm: breakpoints.sm,
                md: breakpoints.md,
                lg: breakpoints.lg,
                xl: breakpoints.xl,
            },
        },

        // Component style overrides for accessibility
        components: {
            // Button touch targets - WCAG 2.1 Level AA (44x44px minimum)
            MuiButton: {
                styleOverrides: {
                    root: {
                        minHeight: touchTargets.minimum, // 44px
                        minWidth: touchTargets.minimum, // 44px
                        padding: '10px 16px', // Maintain visual hierarchy
                    },
                    sizeSmall: {
                        minHeight: touchTargets.minimum, // Still 44px for accessibility
                        minWidth: touchTargets.minimum,
                        padding: '8px 12px',
                    },
                    sizeMedium: {
                        minHeight: touchTargets.minimum,
                        minWidth: touchTargets.minimum,
                        padding: '10px 16px',
                    },
                    sizeLarge: {
                        minHeight: touchTargets.recommended, // 48px
                        minWidth: touchTargets.recommended,
                        padding: '12px 24px',
                    },
                },
            },

            // Icon button touch targets - Enhanced for mobile
            MuiIconButton: {
                styleOverrides: {
                    root: {
                        minHeight: touchTargets.recommended, // 48px (larger for icon-only)
                        minWidth: touchTargets.recommended,
                        padding: '12px', // Visual padding for icon centering
                    },
                    sizeSmall: {
                        minHeight: touchTargets.minimum, // 44px minimum
                        minWidth: touchTargets.minimum,
                        padding: '10px',
                    },
                    sizeMedium: {
                        minHeight: touchTargets.recommended, // 48px
                        minWidth: touchTargets.recommended,
                        padding: '12px',
                    },
                    sizeLarge: {
                        minHeight: 56, // Extra comfortable for important actions
                        minWidth: 56,
                        padding: '16px',
                    },
                },
            },

            // Chip touch targets (interactive chips)
            MuiChip: {
                styleOverrides: {
                    root: {
                        minHeight: touchTargets.minimum, // 44px
                    },
                    deleteIcon: {
                        minHeight: touchTargets.minimum,
                        minWidth: touchTargets.minimum,
                    },
                },
            },

            // Checkbox/Radio touch targets
            MuiCheckbox: {
                styleOverrides: {
                    root: {
                        minHeight: touchTargets.minimum,
                        minWidth: touchTargets.minimum,
                        padding: '9px', // Centers 24px icon in 44px touch target
                    },
                },
            },

            MuiRadio: {
                styleOverrides: {
                    root: {
                        minHeight: touchTargets.minimum,
                        minWidth: touchTargets.minimum,
                        padding: '9px',
                    },
                },
            },

            // Switch touch targets
            MuiSwitch: {
                styleOverrides: {
                    root: {
                        minHeight: touchTargets.minimum,
                        minWidth: touchTargets.minimum * 1.5, // Wider for switch design
                        padding: '8px',
                    },
                },
            },

            // Tab touch targets
            MuiTab: {
                styleOverrides: {
                    root: {
                        minHeight: touchTargets.recommended, // 48px for better mobile UX
                        minWidth: touchTargets.minimum,
                    },
                },
            },

            // List item button touch targets
            MuiListItemButton: {
                styleOverrides: {
                    root: {
                        minHeight: touchTargets.recommended, // 48px for list items
                        padding: '12px 16px',
                    },
                },
            },
        },
    });
}

/**
 * Export default theme instance
 */
export const accessibleMuiTheme = createAccessibleMuiTheme();

/**
 * Usage example:
 * 
 * ```tsx
 * import { ThemeProvider } from '@mui/material/styles';
 * import { accessibleMuiTheme } from '@ghatana/theme';
 * 
 * function App() {
 *   return (
 *     <ThemeProvider theme={accessibleMuiTheme}>
 *       <YourApp />
 *     </ThemeProvider>
 *   );
 * }
 * ```
 */
