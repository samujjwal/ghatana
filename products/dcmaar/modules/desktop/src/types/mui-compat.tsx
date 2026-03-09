import React from 'react';

// Minimal compatibility layer for MUI imports used in the app. This file exports common components as simple React wrappers.
export const Box: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;

// Lightweight Grid shim: strips MUI-specific props and maps a few common props
// to safe DOM styles so the app renders during migration without React warnings.
export const Grid: React.FC<any> = (props: any): React.ReactElement | null => {
    const {
        children,
        container,
        item: _item,
        spacing,
        xs,
        sm,
        md,
        lg,
        xl,
        style,
        sx,
        ...rest
    } = props;

    // avoid unused variable lint
    void _item;

    // Build a safe style object, merging style and sx if provided
    const combinedStyle: React.CSSProperties = { ...(style || {}) };
    if (sx && typeof sx === 'object') Object.assign(combinedStyle, sx as any);

    if (container) {
        combinedStyle.display = combinedStyle.display ?? 'flex';
        combinedStyle.flexWrap = combinedStyle.flexWrap ?? 'wrap';
        if (spacing != null) {
            const gapPx = Number(spacing) * 8;
            combinedStyle.gap = combinedStyle.gap ?? `${gapPx}px`;
        }
    }

    // If this Grid is an item with an xs/sm/md/lg/xl numeric prop, map to width
    const size = xs ?? sm ?? md ?? lg ?? xl;
    if (!container && typeof size === 'number') {
        const pct = (size / 12) * 100;
        combinedStyle.flex = combinedStyle.flex ?? `0 0 ${pct}%`;
        combinedStyle.maxWidth = combinedStyle.maxWidth ?? `${pct}%`;
    }

    // Do not forward MUI-specific props (container, item, sizing props) to DOM.
    return (
        <div {...rest} style={combinedStyle}>
            {children}
        </div>
    );
};
export const List: React.FC<any> = ({ children: _children, ...props }) => <ul {...props}>{_children}</ul>;
export const ListItem: React.FC<any> = ({ children: _children, ...props }) => <li {...props}>{_children}</li>;
export const ListItemText: React.FC<any> = ({ children: _children, ...props }) => <span {...props}>{_children}</span>;
export const ListItemButton: React.FC<any> = ({ children: _children, ...props }) => <button {...props}>{_children}</button>;
export const ListItemIcon: React.FC<any> = ({ children: _children, ...props }) => <span {...props}>{_children}</span>;
export const Paper: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const Typography: React.FC<any> = ({ children: _children, ...props }) => <p {...props}>{_children}</p>;
export const Button: React.FC<any> = ({ children: _children, ...props }) => <button {...props}>{_children}</button>;
export const IconButton: React.FC<any> = ({ children: _children, ...props }) => <button {...props}>{_children}</button>;
export const Card: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const CardContent: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const Dialog: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const DialogTitle: React.FC<any> = ({ children: _children, ...props }) => <h3 {...props}>{_children}</h3>;
export const DialogContent: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const DialogActions: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const TextField: React.FC<any> = (props) => <input {...props} />;
export const Select: React.FC<any> = (props) => <select {...props} />;
export const MenuItem: React.FC<any> = ({ children: _children, ...props }) => <option {...props}>{_children}</option>;
export const Alert: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const Snackbar: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const CircularProgress: React.FC<any> = () => <span />;
export const LinearProgress: React.FC<any> = () => <span />;
export const Stack: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const Toolbar: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const AppBar: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const Drawer: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const Tooltip: React.FC<any> = ({ children: _children, ...props }) => <span {...props}>{_children}</span>;
export const Collapse: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const ToggleButton: React.FC<any> = ({ children: _children, ...props }) => <button {...props}>{_children}</button>;
export const ToggleButtonGroup: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const FormControl: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const InputLabel: React.FC<any> = ({ children: _children, ...props }) => <label {...props}>{_children}</label>;
export const FormGroup: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const FormControlLabel: React.FC<any> = ({ children: _children, ...props }) => <label {...props}>{_children}</label>;
export const Switch: React.FC<any> = (props) => <input type="checkbox" {...props} />;
export const Chip: React.FC<any> = ({ children: _children, ...props }) => <span {...props}>{_children}</span>;
export const Accordion: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const AccordionSummary: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const AccordionDetails: React.FC<any> = ({ children: _children, ...props }) => <div {...props}>{_children}</div>;
export const Autocomplete: React.FC<any> = ({ children: _children, ...props }) => <input {...props} />;

// Provide a default export that maps to the Grid component so
// default imports (e.g. `import Grid from '@mui/material/Unstable_Grid2'`)
// resolve to a renderable component during development.
export default (Grid as any);
