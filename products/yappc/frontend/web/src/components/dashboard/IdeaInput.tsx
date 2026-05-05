import React from 'react';
import { Sparkles as AutoAwesomeIcon, ArrowRight as ArrowForwardIcon } from 'lucide-react';
import { Surface as Paper, IconButton, Input as InputBase, Box, Typography, Chip } from '@ghatana/design-system';

interface IdeaInputProps {
    value?: string;
    onChange?: (value: string) => void;
    onSubmit?: () => void;
}

export function IdeaInput({ value, onChange, onSubmit }: IdeaInputProps) {
    const handleKeyPress = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && onSubmit) {
            onSubmit();
        }
    };

    return (
        <Box className="mt-8 max-w-[600px] mx-auto">
            <Paper
                elevation={3}
                className="flex items-center w-full rounded-xl border border-solid border-border dark:border-border p-[2px 4px]"
            >
                <IconButton className="p-[10px]" aria-label="magic">
                    <AutoAwesomeIcon />
                </IconButton>
                <InputBase
                    className="ml-2 flex-1 text-[1.1rem]"
                    placeholder="Describe your idea (e.g., 'A kanban board for marketing teams')..."
                    autoFocus
                    value={value}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => onChange?.(e.target.value)}
                    onKeyDown={handleKeyPress}
                />
                <IconButton
                    type="button"
                    className="p-[10px]"
                    aria-label="search"
                    onClick={onSubmit}
                >
                    <ArrowForwardIcon />
                </IconButton>
            </Paper>

            <Box className="mt-6 flex gap-2 flex-wrap justify-center">
                <Typography as="span" className="text-xs text-fg-muted w-full mb-2" color="text.secondary">Try starting with:</Typography>
                <Chip label="E-commerce Dashboard" onClick={() => { }} clickable size="sm" variant="outlined" />
                <Chip label="SaaS Landing Page" onClick={() => { }} clickable size="sm" variant="outlined" />
                <Chip label="Internal Tool" onClick={() => { }} clickable size="sm" variant="outlined" />
            </Box>
        </Box>
    );
}
