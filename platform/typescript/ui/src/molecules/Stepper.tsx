import React from 'react';
import { tokens } from '@ghatana/tokens';
import { sxToStyle, type SxProps } from '../utils/sx';

export interface StepLabelProps extends React.HTMLAttributes<HTMLSpanElement> {
  children?: React.ReactNode;
  optional?: React.ReactNode;
  /** MUI-compatible prop (ignored; provided for typing compatibility). */
  StepIconComponent?: React.ElementType;
  /** MUI-compatible prop (ignored). */
  error?: boolean;
}

export const StepLabel: React.FC<StepLabelProps> = ({ children, optional, StepIconComponent: _StepIconComponent, error: _error, ...props }) => (
  <span {...props}>
    {children}
    {optional ? <span style={{ marginLeft: tokens.spacing[2], color: tokens.colors.neutral[500] }}>{optional}</span> : null}
  </span>
);
StepLabel.displayName = 'StepLabel';

export interface StepContentProps extends React.HTMLAttributes<HTMLDivElement> {
  children?: React.ReactNode;
}

export const StepContent: React.FC<StepContentProps> = ({ children, ...props }) => (
  <div {...props}>{children}</div>
);

StepContent.displayName = 'StepContent';

export interface StepComponentProps {
  children?: React.ReactNode;
  /** MUI-compatible prop (ignored). */
  completed?: boolean;
  /** MUI-compatible prop (ignored). */
  disabled?: boolean;
}

export const Step: React.FC<StepComponentProps> = ({ children }) => <>{children}</>;
Step.displayName = 'Step';

export interface Step {
  key: string;
  label: React.ReactNode;
  description?: string;
  icon?: React.ReactNode;
  optional?: boolean;
}

export interface StepperProps {
  /** Steps configuration */
  steps?: Step[];
  /** MUI-like API: allow composing <Stepper><Step><StepLabel /></Step></Stepper>. */
  children?: React.ReactNode;
  /** Active step index (0-based) */
  activeStep?: number;
  /** Orientation */
  orientation?: 'horizontal' | 'vertical';
  /** Variant */
  variant?: 'default' | 'dots' | 'progress';
  /** Allow clicking on steps */
  interactive?: boolean;
  /** Step click handler */
  onStepClick?: (index: number) => void;
  /** Show step numbers */
  showNumbers?: boolean;
  /** Additional class name */
  className?: string;

  /** MUI-like sx prop (limited support). */
  sx?: SxProps;
}

export const Stepper: React.FC<StepperProps> = ({
  steps: stepsProp,
  children,
  activeStep = 0,
  orientation = 'horizontal',
  variant = 'default',
  interactive = false,
  onStepClick,
  showNumbers = true,
  className,
  sx,
}) => {
  const isElementOfType = <TProps,>(
    node: React.ReactNode,
    type: React.ComponentType<TProps>
  ): node is React.ReactElement<TProps> => React.isValidElement(node) && node.type === type;

  const derivedSteps = React.useMemo<Step[]>(() => {
    if (!children) return [];
    const nodes = React.Children.toArray(children);
    const stepNodes = nodes.filter((n): n is React.ReactElement<StepComponentProps> => isElementOfType(n, Step));

    return stepNodes.map((stepNode, index) => {
      const inner = React.Children.toArray(stepNode.props.children);
      const labelNode = inner.find((n): n is React.ReactElement<StepLabelProps> => isElementOfType(n, StepLabel));
      const label = labelNode?.props.children ?? `Step ${index + 1}`;

      return {
        key: String(stepNode.key ?? index),
        label,
      };
    });
  }, [children]);

  const steps = stepsProp && stepsProp.length > 0 ? stepsProp : derivedSteps;

  if (!steps || steps.length === 0) {
    return <div className={className} />;
  }
  const handleStepClick = (index: number) => {
    if (interactive && onStepClick) {
      onStepClick(index);
    }
  };

  const getStepStatus = (index: number): 'completed' | 'active' | 'pending' => {
    if (index < activeStep) return 'completed';
    if (index === activeStep) return 'active';
    return 'pending';
  };

  const containerStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: orientation === 'vertical' ? 'column' : 'row',
    alignItems: orientation === 'vertical' ? 'flex-start' : 'center',
    gap: orientation === 'vertical' ? 0 : tokens.spacing[4],
    width: '100%',
    ...sxToStyle(sx),
  };

  const stepContainerStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: orientation === 'vertical' ? 'row' : 'column',
    alignItems: orientation === 'vertical' ? 'flex-start' : 'center',
    flex: orientation === 'horizontal' ? 1 : 'none',
    gap: tokens.spacing[2],
    cursor: interactive ? 'pointer' : 'default',
  };

  const getIconContainerStyles = (status: 'completed' | 'active' | 'pending'): React.CSSProperties => {
    const baseStyles: React.CSSProperties = {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: variant === 'dots' ? '12px' : '32px',
      height: variant === 'dots' ? '12px' : '32px',
      borderRadius: tokens.borderRadius.full,
      fontFamily: tokens.typography.fontFamily.sans,
      fontSize: tokens.typography.fontSize.sm,
      fontWeight: tokens.typography.fontWeight.semibold,
      transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
    };

    if (status === 'completed') {
      return {
        ...baseStyles,
        backgroundColor: tokens.colors.primary[600],
        color: tokens.colors.white,
        border: `2px solid ${tokens.colors.primary[600]}`,
      };
    }

    if (status === 'active') {
      return {
        ...baseStyles,
        backgroundColor: tokens.colors.primary[100],
        color: tokens.colors.primary[700],
        border: `2px solid ${tokens.colors.primary[600]}`,
      };
    }

    return {
      ...baseStyles,
      backgroundColor: tokens.colors.neutral[100],
      color: tokens.colors.neutral[600],
      border: `2px solid ${tokens.colors.neutral[300]}`,
    };
  };

  const getLabelStyles = (status: 'completed' | 'active' | 'pending'): React.CSSProperties => ({
    fontFamily: tokens.typography.fontFamily.sans,
    fontSize: tokens.typography.fontSize.sm,
    fontWeight: status === 'active' ? tokens.typography.fontWeight.semibold : tokens.typography.fontWeight.medium,
    color:
      status === 'completed'
        ? tokens.colors.primary[700]
        : status === 'active'
          ? tokens.colors.neutral[900]
          : tokens.colors.neutral[600],
  });

  const getDescriptionStyles = (): React.CSSProperties => ({
    fontFamily: tokens.typography.fontFamily.sans,
    fontSize: tokens.typography.fontSize.xs,
    color: tokens.colors.neutral[600],
    marginTop: tokens.spacing[1],
  });

  const connectorStyles = (isCompleted: boolean): React.CSSProperties => {
    if (orientation === 'vertical') {
      return {
        width: '2px',
        height: tokens.spacing[8],
        backgroundColor: isCompleted ? tokens.colors.primary[600] : tokens.colors.neutral[300],
        marginLeft: variant === 'dots' ? '5px' : '15px',
        transition: `background-color ${tokens.transitions.duration.normal} ${tokens.transitions.easing.easeInOut}`,
      };
    }

    return {
      flex: 1,
      height: '2px',
      backgroundColor: isCompleted ? tokens.colors.primary[600] : tokens.colors.neutral[300],
      transition: `background-color ${tokens.transitions.duration.normal} ${tokens.transitions.easing.easeInOut}`,
    };
  };

  const renderStepIcon = (step: Step, index: number, status: 'completed' | 'active' | 'pending') => {
    if (variant === 'dots') {
      return <div style={getIconContainerStyles(status)} />;
    }

    if (step.icon) {
      return <div style={getIconContainerStyles(status)}>{step.icon}</div>;
    }

    if (status === 'completed') {
      return (
        <div style={getIconContainerStyles(status)}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
            <polyline points="20 6 9 17 4 12" />
          </svg>
        </div>
      );
    }

    return (
      <div style={getIconContainerStyles(status)}>
        {showNumbers && index + 1}
      </div>
    );
  };

  if (variant === 'progress') {
    const progress = ((activeStep + 1) / steps.length) * 100;

    return (
      <div style={{ width: '100%' }} className={className}>
        <div
          style={{
            width: '100%',
            height: '8px',
            backgroundColor: tokens.colors.neutral[200],
            borderRadius: tokens.borderRadius.full,
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              width: `${progress}%`,
              height: '100%',
              backgroundColor: tokens.colors.primary[600],
              transition: `width ${tokens.transitions.duration.normal} ${tokens.transitions.easing.easeInOut}`,
            }}
          />
        </div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            marginTop: tokens.spacing[2],
          }}
        >
          {steps.map((step, index) => {
            const status = getStepStatus(index);
            return (
              <div
                key={step.key}
                style={{
                  ...getLabelStyles(status),
                  textAlign: 'center',
                  flex: 1,
                }}
              >
                {step.label}
              </div>
            );
          })}
        </div>
      </div>
    );
  }

  return (
    <div style={containerStyles} className={className}>
      {steps.map((step, index) => {
        const status = getStepStatus(index);
        const isLast = index === steps.length - 1;

        return (
          <React.Fragment key={step.key}>
            <div
              style={stepContainerStyles}
              onClick={() => handleStepClick(index)}
              role="button"
              tabIndex={interactive ? 0 : -1}
              aria-current={status === 'active' ? 'step' : undefined}
            >
              <div
                style={{
                  display: 'flex',
                  flexDirection: orientation === 'vertical' ? 'column' : 'row',
                  alignItems: orientation === 'vertical' ? 'flex-start' : 'center',
                  gap: tokens.spacing[2],
                  width: '100%',
                }}
              >
                {renderStepIcon(step, index, status)}
                {orientation === 'vertical' && (
                  <div style={{ flex: 1 }}>
                    <div style={getLabelStyles(status)}>
                      {step.label}
                      {step.optional && (
                        <span style={{ fontSize: tokens.typography.fontSize.xs, marginLeft: tokens.spacing[1] }}>
                          (Optional)
                        </span>
                      )}
                    </div>
                    {step.description && <div style={getDescriptionStyles()}>{step.description}</div>}
                  </div>
                )}
              </div>
              {orientation === 'horizontal' && (
                <div style={{ textAlign: 'center' }}>
                  <div style={getLabelStyles(status)}>
                    {step.label}
                    {step.optional && (
                      <span style={{ fontSize: tokens.typography.fontSize.xs, display: 'block' }}>
                        (Optional)
                      </span>
                    )}
                  </div>
                  {step.description && <div style={getDescriptionStyles()}>{step.description}</div>}
                </div>
              )}
            </div>
            {!isLast && <div style={connectorStyles(status === 'completed')} />}
          </React.Fragment>
        );
      })}
    </div>
  );
};

Stepper.displayName = 'Stepper';
