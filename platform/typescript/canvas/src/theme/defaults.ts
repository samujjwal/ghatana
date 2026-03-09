/**
 * Theme defaults for canvas elements
 * Provides consistent color schemes, typography, and spacing
 */

export interface ThemeColors {
    shapeFillColor: string;
    shapeStrokeColor: string;
    shapeTextColor: string;
    connectorColor: string;
    selectionColor: string;
    gridColor: string;
    backgroundColor: string;
}

export interface ThemeTypography {
    fontFamily: string;
    fontSize: number;
    lineHeight: number;
    fontWeight: string;
}

export interface ThemeSpacing {
    padding: [number, number]; // [vertical, horizontal]
    elementGap: number;
    handleSize: number;
}

export interface Theme {
    colors: ThemeColors;
    typography: ThemeTypography;
    spacing: ThemeSpacing;
}

export const lightTheme: Theme = {
    colors: {
        shapeFillColor: "#10b981",
        shapeStrokeColor: "#065f46",
        shapeTextColor: "#111827",
        connectorColor: "#6366f1",
        selectionColor: "#0066cc",
        gridColor: "#e0e0e0",
        backgroundColor: "#ffffff"
    },
    typography: {
        fontFamily: "Inter, system-ui, -apple-system, sans-serif",
        fontSize: 16,
        lineHeight: 1.5,
        fontWeight: "400"
    },
    spacing: {
        padding: [8, 12],
        elementGap: 20,
        handleSize: 8
    }
};

export const darkTheme: Theme = {
    colors: {
        shapeFillColor: "#059669",
        shapeStrokeColor: "#10b981",
        shapeTextColor: "#f9fafb",
        connectorColor: "#818cf8",
        selectionColor: "#3b82f6",
        gridColor: "#374151",
        backgroundColor: "#1f2937"
    },
    typography: {
        fontFamily: "Inter, system-ui, -apple-system, sans-serif",
        fontSize: 16,
        lineHeight: 1.5,
        fontWeight: "400"
    },
    spacing: {
        padding: [8, 12],
        elementGap: 20,
        handleSize: 8
    }
};

export function getTheme(scheme: "light" | "dark" = "light"): Theme {
    return scheme === "dark" ? darkTheme : lightTheme;
}

export function getColorValue(
    color: string | undefined,
    fallback: string,
    theme?: Theme
): string {
    if (color) return color;
    return theme ? fallback : lightTheme.colors.shapeFillColor;
}
