// Minimal ambient declarations for @mui/material used in the desktop app.
// These are intentionally permissive (any/ComponentType<any>) to unblock
// type-checking and JSX usage until proper MUI types are available.
// Placed under src/types so it's picked up via `typeRoots` in tsconfig.json.

import * as React from 'react';

declare module '@mui/material' {
    // Common components used across the app
    export const Box: React.ComponentType<any>;
    export const Button: React.ComponentType<any>;
    export const IconButton: React.ComponentType<any>;
    export const Typography: React.ComponentType<any>;
    export const Grid: React.ComponentType<any>;
    export const List: React.ComponentType<any>;
    export const ListItem: React.ComponentType<any>;
    export const ListItemText: React.ComponentType<any>;
    export const ListItemAvatar: React.ComponentType<any>;
    export const Avatar: React.ComponentType<any>;
    export const TextField: React.ComponentType<any>;
    export const InputAdornment: React.ComponentType<any>;
    export const CircularProgress: React.ComponentType<any>;
    export const Stack: React.ComponentType<any>;
    export const Chip: React.ComponentType<any>;
    export const Paper: React.ComponentType<any>;
    export const Card: React.ComponentType<any>;
    export const CardContent: React.ComponentType<any>;
    export const CardActions: React.ComponentType<any>;
    export const Table: React.ComponentType<any>;
    export const TableBody: React.ComponentType<any>;
    export const TableCell: React.ComponentType<any>;
    export const TableContainer: React.ComponentType<any>;
    export const TableHead: React.ComponentType<any>;
    export const TableRow: React.ComponentType<any>;
    export const Container: React.ComponentType<any>;
    export const Dialog: React.ComponentType<any>;
    export const DialogTitle: React.ComponentType<any>;
    export const DialogContent: React.ComponentType<any>;
    export const DialogActions: React.ComponentType<any>;
    export const Tabs: React.ComponentType<any>;
    export const Tab: React.ComponentType<any>;
    export const Select: React.ComponentType<any>;
    export const MenuItem: React.ComponentType<any>;
    export const FormControl: React.ComponentType<any>;
    export const InputLabel: React.ComponentType<any>;
    export const Accordion: React.ComponentType<any>;
    export const AccordionSummary: React.ComponentType<any>;
    export const AccordionDetails: React.ComponentType<any>;

    // Hooks/utilities
    export function useTheme(): any;
    export function useMediaQuery(query: any): any;

    // Event type used by some MUI components
    export type SelectChangeEvent<T = any> = any;

    const mui: any;
    export default mui;
}

declare module '@mui/material/styles' {
    export function styled(component: any, options?: any): <T = any>(arg: any) => any;
    export function createTheme(...args: any[]): any;
    export const ThemeProvider: React.ComponentType<any>;
    export function useTheme(): any;
    export function useMediaQuery(query: any): any;
}

declare module '@mui/material/colors' {
    export const blue: any;
    export const grey: any;
}

declare module '@mui/material/Grid' {
    const Grid: React.ComponentType<any>;
    export default Grid;
}
