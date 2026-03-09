
import { 
  palette, 
  lightColors, 
  darkColors,
  typographyVariants,
  fontFamilies,
  spacing,
  borderRadius,
  lightShadows,
  darkShadows,
  elevationLevels,
  breakpoints,
  durations,
  easings,
  zIndex,
} from '../tokens';

import type { PaletteMode, ThemeOptions } from '../theme/types';

// Common theme settings
const getThemeOptions = (mode: PaletteMode): ThemeOptions => {
  const isDark = mode === 'dark';
  const colors = isDark ? darkColors : lightColors;
  const shadows = isDark ? darkShadows : lightShadows;
  const primary = palette.primary;
  const secondary = palette.secondary;
  const error = palette.error;
  const warning = palette.warning;
  const info = palette.info;
  const success = palette.success;

  return {
    palette: {
      mode,
      primary: {
        light: primary[300],
        main: primary[500],
        dark: primary[700],
        contrastText: primary.contrastText,
      },
      secondary: {
        light: secondary[300],
        main: secondary[500],
        dark: secondary[700],
        contrastText: secondary.contrastText,
      },
      error: {
        light: error.light,
        main: error.main,
        dark: error.dark,
        contrastText: error.contrastText,
      },
      warning: {
        light: warning.light,
        main: warning.main,
        dark: warning.dark,
        contrastText: warning.contrastText,
      },
      info: {
        light: info.light,
        main: info.main,
        dark: info.dark,
        contrastText: info.contrastText,
      },
      success: {
        light: success.light,
        main: success.main,
        dark: success.dark,
        contrastText: success.contrastText,
      },
      background: {
        default: colors.background.default,
        paper: colors.background.paper,
      },
      text: {
        primary: colors.text.primary,
        secondary: colors.text.secondary,
        disabled: colors.text.disabled,
        hint: colors.text.hint,
        icon: colors.text.icon,
      },
      divider: colors.divider,
      action: {
        ...colors.action,
      },
    },
    typography: {
      fontFamily: fontFamilies.primary,
      fontSize: 16, // Base font size (1rem = 16px)
      h1: {
        ...typographyVariants.h1,
        fontWeight: 500,
        letterSpacing: '-0.01562em',
      },
      h2: {
        ...typographyVariants.h2,
        fontWeight: 500,
        letterSpacing: '-0.00833em',
      },
      h3: {
        ...typographyVariants.h3,
        fontWeight: 500,
        letterSpacing: '0em',
      },
      h4: {
        ...typographyVariants.h4,
        fontWeight: 500,
        letterSpacing: '0.00735em',
      },
      h5: {
        ...typographyVariants.h5,
        fontWeight: 500,
        letterSpacing: '0em',
      },
      h6: {
        ...typographyVariants.h6,
        fontWeight: 500,
        letterSpacing: '0.0075em',
      },
      subtitle1: {
        ...typographyVariants.subtitle1,
        lineHeight: 1.5,
      },
      subtitle2: {
        ...typographyVariants.subtitle2,
        fontWeight: 500,
        lineHeight: 1.57,
      },
      body1: {
        ...typographyVariants.body1,
        lineHeight: 1.5,
      },
      body2: {
        ...typographyVariants.body2,
        lineHeight: 1.5,
      },
      button: {
        ...typographyVariants.button,
        fontWeight: 500,
        textTransform: 'none',
        lineHeight: 1.75,
      },
      caption: {
        ...typographyVariants.caption,
        lineHeight: 1.66,
      },
      overline: {
        ...typographyVariants.overline,
        lineHeight: 2.66,
        textTransform: 'uppercase',
      },
    },
    shape: {
      borderRadius: borderRadius.md,
    },
    spacing: (factor: number) => {
      const mappedValue = (spacing as Record<string, number>)[String(factor)];
      return `${mappedValue ?? factor * 4}px`;
    },
    breakpoints: {
      values: {
        xs: breakpoints.xs,
        sm: breakpoints.sm,
        md: breakpoints.md,
        lg: breakpoints.lg,
        xl: breakpoints.xl,
      },
    },
    transitions: {
      duration: {
        shortest: durations.fast,
        shorter: durations.normal,
        short: durations.normal,
        standard: durations.normal,
        complex: durations.slow,
        enteringScreen: durations.normal,
        leavingScreen: durations.fast,
      },
      easing: {
        easeInOut: easings.easeInOut,
        easeOut: easings.easeOut,
        easeIn: easings.easeIn,
        sharp: easings.sharp,
      },
    },
    zIndex: {
      mobileStepper: zIndex.base,
      fab: zIndex.fixed,
      speedDial: zIndex.fixed,
      appBar: zIndex.sticky,
      drawer: zIndex.modal,
      modal: zIndex.modal,
      snackbar: zIndex.toast,
      tooltip: zIndex.tooltip,
    },
    shadows: shadows as unknown, // Cast to any to avoid type issues with MUI's shadow type
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          html: {
            WebkitFontSmoothing: 'auto',
            height: '100%',
          },
          body: {
            height: '100%',
            backgroundColor: colors.background.default,
            color: colors.text.primary,
            '& #root': {
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
            },
          },
          a: {
            color: primary[500],
            textDecoration: 'none',
            '&:hover': {
              textDecoration: 'underline',
            },
          },
          'input:-webkit-autofill, input:-webkit-autofill:hover, input:-webkit-autofill:focus': {
            WebkitBoxShadow: `0 0 0 100px ${colors.background.paper} inset !important`,
            WebkitTextFillColor: colors.text.primary,
          },
        },
      },
      MuiAppBar: {
        defaultProps: {
          elevation: 0,
          color: 'default',
        },
        styleOverrides: {
          root: {
            backgroundColor: isDark ? colors.background.elevated : primary[500],
            color: isDark ? colors.text.primary : primary.contrastText,
            borderBottom: `1px solid ${colors.divider}`,
          },
        },
      },
      MuiButton: {
        defaultProps: {
          disableElevation: true,
        },
        styleOverrides: {
          root: {
            borderRadius: borderRadius.md,
            textTransform: 'none',
            fontWeight: 500,
            '&:hover': {
              boxShadow: 'none',
            },
          },
          sizeSmall: {
            padding: '4px 12px',
            fontSize: '0.8125rem',
          },
          sizeMedium: {
            padding: '6px 16px',
          },
          sizeLarge: {
            padding: '8px 22px',
          },
          containedPrimary: {
            '&:hover': {
              backgroundColor: primary[700],
            },
          },
          containedSecondary: {
            '&:hover': {
              backgroundColor: secondary[700],
            },
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: borderRadius.lg,
            boxShadow: shadows[elevationLevels.card],
            backgroundImage: 'none',
            backgroundColor: colors.background.paper,
            '&.MuiPaper-outlined': {
              borderColor: colors.border,
            },
          },
        },
      },
      MuiCardHeader: {
        styleOverrides: {
          root: {
            padding: '24px',
            '& .MuiCardHeader-action': {
              marginTop: 0,
              marginRight: 0,
            },
          },
          title: {
            fontSize: '1.1rem',
            fontWeight: 600,
          },
          subheader: {
            marginTop: '4px',
          },
        },
      },
      MuiCardContent: {
        styleOverrides: {
          root: {
            padding: '24px',
            '&:last-child': {
              paddingBottom: '24px',
            },
          },
        },
      },
      MuiPaper: {
        defaultProps: {
          elevation: 0,
        },
        styleOverrides: {
          root: {
            backgroundImage: 'none',
          },
          elevation0: {
            boxShadow: 'none',
          },
          elevation1: {
            boxShadow: shadows[elevationLevels.raised],
          },
          elevation2: {
            boxShadow: shadows[elevationLevels.card],
          },
          elevation4: {
            boxShadow: shadows[elevationLevels.dropdown],
          },
          elevation8: {
            boxShadow: shadows[elevationLevels.dialog],
          },
          elevation16: {
            boxShadow: shadows[elevationLevels.popover],
          },
          elevation24: {
            boxShadow: shadows[elevationLevels.modal],
          },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: borderRadius.md,
            '&:hover .MuiOutlinedInput-notchedOutline': {
              borderColor: colors.text.secondary,
            },
            '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
              borderWidth: '1px',
            },
            '&.Mui-error .MuiOutlinedInput-notchedOutline': {
              borderColor: error.main,
            },
          },
          input: {
            padding: '12px 16px',
          },
          notchedOutline: {
            borderColor: colors.divider,
          },
        },
      },
      MuiInputLabel: {
        styleOverrides: {
          root: {
            color: colors.text.secondary,
            '&.Mui-focused': {
              color: colors.text.primary,
            },
            '&.Mui-error': {
              color: error.main,
            },
          },
        },
      },
      MuiFormHelperText: {
        styleOverrides: {
          root: {
            marginTop: '4px',
            marginLeft: 0,
            '&.Mui-error': {
              color: error.main,
            },
          },
        },
      },
      MuiSwitch: {
        styleOverrides: {
          switchBase: {
            color: colors.text.disabled,
            '&.Mui-checked': {
              color: primary[500],
              '& + .MuiSwitch-track': {
                backgroundColor: alpha(primary[500], 0.5),
                opacity: 1,
              },
              '&.Mui-disabled + .MuiSwitch-track': {
                backgroundColor: colors.action.disabledBackground,
              },
            },
            '&.Mui-disabled': {
              color: colors.action.disabled,
              '& + .MuiSwitch-track': {
                opacity: 0.5,
              },
            },
          },
          track: {
            backgroundColor: colors.divider,
            opacity: 1,
          },
          thumb: {
            boxShadow: shadows[1],
          },
        },
      },
      MuiDivider: {
        styleOverrides: {
          root: {
            borderColor: colors.divider,
          },
        },
      },
      MuiList: {
        styleOverrides: {
          root: {
            padding: '8px 0',
          },
        },
      },
      MuiListItemButton: {
        styleOverrides: {
          root: {
            borderRadius: borderRadius.md,
            margin: '0 8px',
            padding: '8px 16px',
            '&.Mui-selected': {
              backgroundColor: isDark ? alpha(primary[500], 0.16) : alpha(primary[500], 0.08),
              color: isDark ? primary[300] : primary[600],
              '&:hover': {
                backgroundColor: isDark ? alpha(primary[500], 0.24) : alpha(primary[500], 0.12),
              },
              '& .MuiListItemIcon-root': {
                color: isDark ? primary[300] : primary[600],
              },
            },
            '&:hover': {
              backgroundColor: colors.action.hover,
            },
          },
        },
      },
      MuiListItemIcon: {
        styleOverrides: {
          root: {
            color: colors.text.secondary,
            minWidth: '40px',
          },
        },
      },
      MuiTooltip: {
        styleOverrides: {
          tooltip: {
            backgroundColor: isDark ? '#424242' : '#212121', // Using dark gray colors that match Material Design
            color: isDark ? 'rgba(255, 255, 255, 0.87)' : '#ffffff',
            fontSize: '0.75rem',
            padding: '4px 8px',
            boxShadow: shadows[elevationLevels.tooltip],
          },
          arrow: {
            color: isDark ? '#424242' : '#212121',
          },
        },
      },
    },
  } as ThemeOptions;
};

// Create themes with proper type assertion
export const lightTheme = createTheme(getThemeOptions('light'));
export const darkTheme = createTheme(getThemeOptions('dark'));

// Default theme (for backward compatibility)
export const theme = lightTheme;
