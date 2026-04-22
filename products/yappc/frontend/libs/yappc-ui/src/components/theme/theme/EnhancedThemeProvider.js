import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { CssBaseline } from '@mui/material';
import { createTheme, ThemeProvider as MuiThemeProvider, } from '@mui/material/styles';
import { useMemo } from 'react';
import { LayerPriority, MultiLayerThemeProvider, useAppTheme, useBrandTheme, useMultiLayerTheme, useThemeMode, useWorkspaceTheme, } from './MultiLayerThemeContext';
import { darkTheme, lightTheme } from './theme';
function MuiThemeConnector({ children }) {
    const { mergedThemeOptions, mode } = useMultiLayerTheme();
    const theme = useMemo(() => {
        const baseTheme = mode === 'dark' ? darkTheme : lightTheme;
        return createTheme(baseTheme, mergedThemeOptions);
    }, [mergedThemeOptions, mode]);
    return (_jsxs(MuiThemeProvider, { theme: theme, children: [_jsx(CssBaseline, {}), children] }));
}
export function EnhancedThemeProvider({ children, mode = 'light', brandThemeOptions, workspaceThemeOptions, appThemeOptions, }) {
    const baseTheme = mode === 'dark' ? darkTheme : lightTheme;
    return (_jsx(MultiLayerThemeProvider, { initialMode: mode, baseThemeOptions: baseTheme, brandThemeOptions: brandThemeOptions, workspaceThemeOptions: workspaceThemeOptions, appThemeOptions: appThemeOptions, children: _jsx(MuiThemeConnector, { children: children }) }));
}
export { LayerPriority, useAppTheme, useBrandTheme, useMultiLayerTheme, useThemeMode, useWorkspaceTheme, };
