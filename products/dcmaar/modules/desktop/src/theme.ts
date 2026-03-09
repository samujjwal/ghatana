import { createTheme } from '@mui/material/styles';
import colors, { withAlpha } from './theme/colors';

export type AppPaletteMode = 'light' | 'dark';

declare module '@mui/material/styles' {
  interface Theme {
    status: {
      danger: string;
    };
  }
  interface ThemeOptions {
    status?: {
      danger?: string;
    };
  }
  interface Palette {
    custom?: {
      light?: string;
      main?: string;
      dark?: string;
    };
  }
  interface PaletteOptions {
    custom?: {
      light?: string;
      main?: string;
      dark?: string;
    };
  }
}

const createBackgroundGradient = (mode: AppPaletteMode) =>
  mode === 'dark'
    ? 'linear-gradient(145deg, rgba(30, 41, 59, 0.92), rgba(15, 23, 42, 0.88))'
    : 'linear-gradient(145deg, rgba(255, 255, 255, 0.94), rgba(241, 245, 249, 0.9))';

export function createAppTheme(mode: AppPaletteMode) {
  const isDark = mode === 'dark';
  const accent = {
    light: colors.info.light,
    main: colors.info.main,
    dark: colors.info.dark,
  };

  return createTheme({
    palette: {
      mode,
      primary: {
        main: colors.primary.main,
        light: colors.primary.light,
        dark: colors.primary.dark,
        contrastText: colors.primary.contrastText,
      },
      secondary: {
        main: colors.secondary.main,
        light: colors.secondary.light,
        dark: colors.secondary.dark,
        contrastText: colors.secondary.contrastText,
      },
      error: {
        main: colors.error.main,
        light: colors.error.light,
        dark: colors.error.dark,
        contrastText: colors.error.contrastText,
      },
      warning: {
        main: colors.warning.main,
        light: colors.warning.light,
        dark: colors.warning.dark,
        contrastText: colors.warning.contrastText,
      },
      info: {
        main: colors.info.main,
        light: colors.info.light,
        dark: colors.info.dark,
        contrastText: colors.info.contrastText,
      },
      success: {
        main: colors.success.main,
        light: colors.success.light,
        dark: colors.success.dark,
        contrastText: colors.success.contrastText,
      },
      background: {
        default: isDark ? colors.background.dark : colors.background.default,
        paper: isDark ? colors.background.darkPaper : colors.background.paper,
      },
      text: {
        primary: isDark ? colors.text.primaryDark : colors.text.primary,
        secondary: isDark ? colors.text.secondaryDark : colors.text.secondary,
        disabled: isDark ? colors.text.disabledDark : colors.text.disabled,
      },
      divider: isDark ? colors.dividerDark : colors.divider,
      custom: accent,
    },
    status: {
      danger: colors.error.main,
    },
    shape: {
      borderRadius: 12,
    },
    ...(isDark
      ? {
          shadows: [
            'none',
            '0px 4px 12px rgba(15, 23, 42, 0.2)',
            '0px 6px 16px rgba(15, 23, 42, 0.24)',
            ...Array(23).fill('0px 6px 18px rgba(15, 23, 42, 0.18)'),
          ] as any,
        }
      : {}),
    typography: {
      fontFamily: [
        '-apple-system',
        'BlinkMacSystemFont',
        '"Segoe UI"',
        'Roboto',
        '"Helvetica Neue"',
        'Arial',
        'sans-serif',
        '"Apple Color Emoji"',
        '"Segoe UI Emoji"',
        '"Segoe UI Symbol"',
      ].join(','),
      h1: {
        fontSize: '2.5rem',
        fontWeight: 600,
      },
      h2: {
        fontSize: '2rem',
        fontWeight: 600,
      },
      h3: {
        fontSize: '1.75rem',
        fontWeight: 600,
      },
      h4: {
        fontSize: '1.5rem',
        fontWeight: 600,
      },
      h5: {
        fontSize: '1.25rem',
        fontWeight: 600,
      },
      h6: {
        fontSize: '0.95rem',
        fontWeight: 600,
      },
      body1: {
        fontSize: '0.95rem',
      },
      subtitle1: {
        fontWeight: 600,
      },
      subtitle2: {
        fontWeight: 600,
        textTransform: 'uppercase',
        letterSpacing: 1,
        fontSize: '0.75rem',
      },
    },
    components: {
      MuiButton: {
        styleOverrides: {
          root: {
            textTransform: 'none',
            borderRadius: 12,
            fontWeight: 600,
            letterSpacing: 0.4,
          },
          containedPrimary: {
            background: `linear-gradient(90deg, ${colors.primary.main}, ${accent.main})`,
            boxShadow: isDark
              ? '0 10px 30px rgba(56, 189, 248, 0.28)'
              : '0 10px 30px rgba(14, 116, 144, 0.18)',
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: 20,
            backgroundImage: createBackgroundGradient(mode),
            border: `1px solid ${withAlpha(isDark ? colors.grey[500] : colors.grey[400], isDark ? 0.16 : 0.2)}`,
            boxShadow: isDark
              ? '0 16px 40px rgba(15, 23, 42, 0.45)'
              : '0 12px 30px rgba(15, 23, 42, 0.12)',
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: {
            fontWeight: 600,
            letterSpacing: 0.4,
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            backgroundImage: createBackgroundGradient(mode),
          },
        },
      },
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            backgroundColor: isDark ? colors.background.dark : colors.background.default,
            color: isDark ? colors.text.primaryDark : colors.text.primary,
            scrollbarColor: `${isDark ? colors.grey[600] : colors.grey[400]} ${
              isDark ? colors.background.dark : colors.background.default
            }`,
            '&::-webkit-scrollbar, & *::-webkit-scrollbar': {
              backgroundColor: isDark ? colors.background.dark : colors.background.paper,
            },
            '&::-webkit-scrollbar-thumb, & *::-webkit-scrollbar-thumb': {
              borderRadius: 8,
              backgroundColor: isDark ? colors.grey[600] : colors.grey[500],
              minHeight: 24,
              border: `3px solid ${isDark ? colors.background.dark : colors.background.paper}`,
            },
          },
        },
      },
      MuiTableContainer: {
        styleOverrides: {
          root: {
            borderRadius: 16,
            border: `1px solid ${withAlpha(isDark ? colors.grey[500] : colors.grey[400], 0.14)}`,
            backgroundColor: isDark ? '#111827' : '#ffffff',
          },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          head: {
            textTransform: 'uppercase',
            letterSpacing: 0.6,
            fontSize: '0.75rem',
            color: isDark ? 'rgba(226, 232, 240, 0.8)' : 'rgba(30, 41, 59, 0.7)',
          },
        },
      },
      MuiDivider: {
        styleOverrides: {
          root: {
            borderColor: isDark ? 'rgba(148, 163, 184, 0.18)' : 'rgba(148, 163, 184, 0.24)',
          },
        },
      },
      MuiTooltip: {
        styleOverrides: {
          tooltip: {
            backgroundColor: isDark ? '#1f2937' : '#f8fafc',
            borderRadius: 10,
            border: `1px solid ${withAlpha(isDark ? colors.grey[500] : colors.grey[400], 0.24)}`,
            color: isDark ? colors.text.primaryDark : colors.text.primary,
          },
        },
      },
    },
  });
}

export default createAppTheme;
