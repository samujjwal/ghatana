import React from 'react';

interface Step {
    label: string;
    description?: string;
}

interface StepperProps {
    steps: Step[];
    currentStep?: number;
    className?: string;
}

export const Stepper: React.FC<StepperProps> = ({ steps, currentStep = 0, className = '' }) => {
    return (
        <div className={`flex items-center justify-between ${className}`}>
            {steps.map((step, index) => (
                <div key={index} className="flex items-center flex-1">
                    <div
                        className={`w-8 h-8 rounded-full flex items-center justify-center text-white font-bold ${index <= currentStep ? 'bg-blue-500' : 'bg-gray-300'
                            }`}
                    >
                        {index + 1}
                    </div>
                    {index < steps.length - 1 && (
                        <div className={`flex-1 h-1 mx-2 ${index < currentStep ? 'bg-blue-500' : 'bg-gray-300'}`} />
                    )}
                </div>
            ))}
        </div>
    );
};
