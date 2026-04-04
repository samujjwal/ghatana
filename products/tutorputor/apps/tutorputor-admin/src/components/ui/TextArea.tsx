import React from 'react';

interface TextAreaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> { }

export const TextArea = React.forwardRef<HTMLTextAreaElement, TextAreaProps>(
    ({ className, ...props }, ref) => (
        <textarea
            ref={ref}
            className={`px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring focus:ring-blue-500 ${className || ''}`}
            {...props}
        />
    )
);

TextArea.displayName = 'TextArea';
