import React from 'react';

export type AuthoringStep = string;

interface AuthoringStepperPanelProps {
  [key: string]: unknown;
}

export function AuthoringStepperPanel(_props: AuthoringStepperPanelProps): React.ReactElement {
  return <div className="authoring-stepper-panel" />;
}
