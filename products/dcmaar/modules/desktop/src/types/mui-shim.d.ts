import * as React from 'react';

declare module '@mui/material' {
  // Common MUI named exports used across the app — typed permissively to unblock
  // the build until proper @mui types are in place.
  export const Box: React.ComponentType<any>;
  export const Button: React.ComponentType<any>;
  export const IconButton: React.ComponentType<any>;
  export const Typography: React.ComponentType<any>;
  export const Grid: React.ComponentType<any>;
  export const List: React.ComponentType<any>;
  export const ListItem: React.ComponentType<any>;
  export const ListItemText: React.ComponentType<any>;
  export const ListItemButton: React.ComponentType<any>;
  export const ListItemIcon: React.ComponentType<any>;
  export const ListItemSecondaryAction: React.ComponentType<any>;
  export const SvgIcon: React.ComponentType<any>;
  export const Paper: React.ComponentType<any>;
  export const Card: React.ComponentType<any>;
  export const CardContent: React.ComponentType<any>;
  export const CardHeader: React.ComponentType<any>;
  export const Dialog: React.ComponentType<any>;
  export const DialogTitle: React.ComponentType<any>;
  export const DialogContent: React.ComponentType<any>;
  export const DialogActions: React.ComponentType<any>;
  export const TextField: React.ComponentType<any>;
  export const Select: React.ComponentType<any>;
  export const MenuItem: React.ComponentType<any>;
  export const Alert: React.ComponentType<any>;
  export const Snackbar: React.ComponentType<any>;
  export const CircularProgress: React.ComponentType<any>;
  export const LinearProgress: React.ComponentType<any>;
  export const Stack: React.ComponentType<any>;
  export const Toolbar: React.ComponentType<any>;
  export const AppBar: React.ComponentType<any>;
  export const Drawer: React.ComponentType<any>;
  export const Tooltip: React.ComponentType<any>;
  export const Collapse: React.ComponentType<any>;
  export const ToggleButton: React.ComponentType<any>;
  export const ToggleButtonGroup: React.ComponentType<any>;
  export const FormControl: React.ComponentType<any>;
  export const InputLabel: React.ComponentType<any>;
  export const FormGroup: React.ComponentType<any>;
  export const FormControlLabel: React.ComponentType<any>;
  export const Switch: React.ComponentType<any>;
  export const Chip: React.ComponentType<any>;
  export const Accordion: React.ComponentType<any>;
  export const AccordionSummary: React.ComponentType<any>;
  export const AccordionDetails: React.ComponentType<any>;
  export const Autocomplete: React.ComponentType<any>;
  export const Badge: React.ComponentType<any>;
  export const Divider: React.ComponentType<any>;
  export const Table: React.ComponentType<any>;
  export const TableBody: React.ComponentType<any>;
  export const TableCell: React.ComponentType<any>;
  export const TableContainer: React.ComponentType<any>;
  export const TableHead: React.ComponentType<any>;
  export const TableRow: React.ComponentType<any>;
  export const Container: React.ComponentType<any>;
  export const Slide: React.ComponentType<any>;
  export const AlertTitle: React.ComponentType<any>;
  export const DialogContentText: React.ComponentType<any>;
  export type SelectChangeEvent<_T = any> = any;
  export const Tabs: React.ComponentType<any>;
  export const Tab: React.ComponentType<any>;
  export function useTheme(): any;
  export function useMediaQuery(query: any): any;

  const mui: any;
  export default mui;
}

declare module '@mui/material/Grid' {
  const Grid: React.ComponentType<any>;
  export default Grid;
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
