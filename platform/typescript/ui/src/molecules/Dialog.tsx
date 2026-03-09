import * as React from 'react';

import { Modal, type ModalProps } from './Modal';

export interface DialogProps extends Omit<ModalProps, 'title' | 'description'> {
    /** MUI-style open prop. */
    open?: boolean;
    /** Legacy alias for open. */
    isOpen?: boolean;
    /** MUI-like sizing props (accepted for compatibility; currently ignored). */
    maxWidth?: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | string | false;
    fullWidth?: boolean;
    fullScreen?: boolean;
    children?: React.ReactNode;
}

export const DialogTitle: React.FC<React.HTMLAttributes<HTMLDivElement>> = (props) => (
    <div {...props} />
);
DialogTitle.displayName = 'DialogTitle';

export const DialogContent: React.FC<React.HTMLAttributes<HTMLDivElement>> = (props) => (
    <div {...props} />
);
DialogContent.displayName = 'DialogContent';

export const DialogActions: React.FC<React.HTMLAttributes<HTMLDivElement>> = (props) => (
    <div {...props} />
);
DialogActions.displayName = 'DialogActions';

function isElementOfType<TProps>(
    node: React.ReactNode,
    type: React.ComponentType<TProps>
): node is React.ReactElement<TProps> {
    return React.isValidElement(node) && node.type === type;
}

export const Dialog: React.FC<DialogProps> = ({
    open,
    isOpen,
    onClose,
    children,
    ...rest
}) => {
    const resolvedOpen = isOpen ?? open ?? false;

    const nodes = React.Children.toArray(children);

    const titleNode = nodes.find((n) => isElementOfType(n, DialogTitle));
    const contentNodes = nodes.filter((n) => !isElementOfType(n, DialogTitle) && !isElementOfType(n, DialogActions));
    const actionsNode = nodes.find((n) => isElementOfType(n, DialogActions));

    const title = isElementOfType(titleNode, DialogTitle) ? titleNode.props.children : undefined;

    return (
        <Modal open={resolvedOpen} onClose={onClose} title={title} {...rest}>
            <div style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                {contentNodes}
                {isElementOfType(actionsNode, DialogActions) ? (
                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>{actionsNode.props.children}</div>
                ) : null}
            </div>
        </Modal>
    );
};

Dialog.displayName = 'Dialog';
