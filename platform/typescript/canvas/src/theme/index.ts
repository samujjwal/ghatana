export interface YAPPCTheme {
  name: string;
  colors: {
    primary: string;
    secondary: string;
    accent: string;
    background: string;
    surface: string;
    text: {
      primary: string;
      secondary: string;
      muted: string;
    };
    border: {
      light: string;
      medium: string;
      dark: string;
    };
    canvas: {
      grid: string;
      selection: string;
      handles: string;
    };
  };
  typography: {
    fontFamily: string;
    fontSize: {
      small: number;
      medium: number;
      large: number;
      xlarge: number;
    };
    fontWeight: {
      normal: number;
      medium: number;
      bold: number;
    };
  };
  spacing: {
    xs: number;
    sm: number;
    md: number;
    lg: number;
    xl: number;
  };
  borderRadius: {
    small: number;
    medium: number;
    large: number;
  };
}

export const YAPPCDefaultTheme: YAPPCTheme = {
  name: "default",
  colors: {
    primary: "#3b82f6",
    secondary: "#64748b",
    accent: "#f59e0b",
    background: "#ffffff",
    surface: "#f8fafc",
    text: {
      primary: "#1e293b",
      secondary: "#475569",
      muted: "#94a3b8",
    },
    border: {
      light: "#e2e8f0",
      medium: "#cbd5e1",
      dark: "#94a3b8",
    },
    canvas: {
      grid: "#e5e7eb",
      selection: "#3b82f6",
      handles: "#ffffff",
    },
  },
  typography: {
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", "Roboto", sans-serif',
    fontSize: {
      small: 12,
      medium: 14,
      large: 16,
      xlarge: 18,
    },
    fontWeight: {
      normal: 400,
      medium: 500,
      bold: 700,
    },
  },
  spacing: {
    xs: 4,
    sm: 8,
    md: 16,
    lg: 24,
    xl: 32,
  },
  borderRadius: {
    small: 4,
    medium: 8,
    large: 12,
  },
};

export const YAPPCDarkTheme: YAPPCTheme = {
  ...YAPPCDefaultTheme,
  name: "dark",
  colors: {
    primary: "#60a5fa",
    secondary: "#94a3b8",
    accent: "#fbbf24",
    background: "#0f172a",
    surface: "#1e293b",
    text: {
      primary: "#f8fafc",
      secondary: "#cbd5e1",
      muted: "#64748b",
    },
    border: {
      light: "#334155",
      medium: "#475569",
      dark: "#64748b",
    },
    canvas: {
      grid: "#334155",
      selection: "#60a5fa",
      handles: "#1e293b",
    },
  },
};

export class ThemeManager {
  private currentTheme: YAPPCTheme = YAPPCDefaultTheme;
  private listeners: Set<(theme: YAPPCTheme) => void> = new Set();

  setTheme(theme: YAPPCTheme): void {
    this.currentTheme = theme;
    this.notifyListeners();
  }

  getTheme(): YAPPCTheme {
    return this.currentTheme;
  }

  subscribe(listener: (theme: YAPPCTheme) => void): () => void {
    this.listeners.add(listener);
    listener(this.currentTheme);

    return () => {
      this.listeners.delete(listener);
    };
  }

  private notifyListeners(): void {
    for (const listener of this.listeners) {
      listener(this.currentTheme);
    }
  }

  getColor(path: string): string {
    const parts = path.split(".");
    let value: unknown = this.currentTheme;

    for (const part of parts) {
      value = (value as Record<string, unknown>)[part];
    }

    return value as string;
  }

  getSpacing(size: keyof YAPPCTheme["spacing"]): number {
    return this.currentTheme.spacing[size];
  }

  getFontSize(size: keyof YAPPCTheme["typography"]["fontSize"]): number {
    return this.currentTheme.typography.fontSize[size];
  }

  getBorderRadius(size: keyof YAPPCTheme["borderRadius"]): number {
    return this.currentTheme.borderRadius[size];
  }
}

export const themeManager = new ThemeManager();
