import React from 'react';

// Minimal compatibility layer for MUI imports used in the app. This file exports common components as simple React wrappers.
// Implemented without JSX so it compiles as .ts.
type AnyProps = Record<string, any>;

export const Box: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const Grid: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const List: React.FC<AnyProps> = (props) => React.createElement('ul', props, props.children);
export const ListItem: React.FC<AnyProps> = (props) => React.createElement('li', props, props.children);
export const ListItemText: React.FC<AnyProps> = (props) => React.createElement('span', props, props.children);
export const ListItemButton: React.FC<AnyProps> = (props) => React.createElement('button', props, props.children);
export const ListItemIcon: React.FC<AnyProps> = (props) => React.createElement('span', props, props.children);
export const Paper: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const Typography: React.FC<AnyProps> = (props) => React.createElement('p', props, props.children);
export const Button: React.FC<AnyProps> = (props) => React.createElement('button', props, props.children);
export const IconButton: React.FC<AnyProps> = (props) => React.createElement('button', props, props.children);
export const Card: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const CardContent: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const Dialog: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const DialogTitle: React.FC<AnyProps> = (props) => React.createElement('h3', props, props.children);
export const DialogContent: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const DialogActions: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const TextField: React.FC<AnyProps> = (props) => React.createElement('input', props);
export const Select: React.FC<AnyProps> = (props) => React.createElement('select', props, props.children);
export const MenuItem: React.FC<AnyProps> = (props) => React.createElement('option', props, props.children);
export const Alert: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const Snackbar: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const CircularProgress: React.FC<AnyProps> = () => React.createElement('span', null);
export const LinearProgress: React.FC<AnyProps> = () => React.createElement('span', null);
export const Stack: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const Toolbar: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const AppBar: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const Drawer: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const Tooltip: React.FC<AnyProps> = (props) => React.createElement('span', props, props.children);
export const Collapse: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const ToggleButton: React.FC<AnyProps> = (props) => React.createElement('button', props, props.children);
export const ToggleButtonGroup: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const FormControl: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const InputLabel: React.FC<AnyProps> = (props) => React.createElement('label', props, props.children);
export const FormGroup: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const FormControlLabel: React.FC<AnyProps> = (props) => React.createElement('label', props, props.children);
export const Switch: React.FC<AnyProps> = (props) => React.createElement('input', { type: 'checkbox', ...(props as any) });
export const Chip: React.FC<AnyProps> = (props) => React.createElement('span', props, props.children);
export const Accordion: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const AccordionSummary: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const AccordionDetails: React.FC<AnyProps> = (props) => React.createElement('div', props, props.children);
export const Autocomplete: React.FC<AnyProps> = (props) => React.createElement('input', props);

export default {} as any;
