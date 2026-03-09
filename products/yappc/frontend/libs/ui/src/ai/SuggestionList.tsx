import { InteractiveList as List, ListItem, ListItemButton, ListItemIcon, ListItemText, Chip, Divider, Box } from '@ghatana/ui';
import React from 'react';

import { SUGGESTION_LABELS } from './SmartSuggestions/utils';

import type { Suggestion, SuggestionType } from './SmartSuggestions/types';


/**
 *
 */
interface Props {
    suggestions: Suggestion[];
    suggestionTypes: SuggestionType[];
    selectedIndex: number;
    onClick: (s: Suggestion, idx: number) => void;
    showConfidence?: boolean;
}

export const SuggestionList: React.FC<Props> = ({
    suggestions,
    suggestionTypes,
    selectedIndex,
    onClick,
    showConfidence = true,
}) => {
    const getSuggestionLabel = (t: SuggestionType) => SUGGESTION_LABELS[t as SuggestionType] ?? String(t);

    // build groups from suggestions to avoid dynamic object indexing
    const groups = suggestions.reduce((acc: { type: SuggestionType; items: Suggestion[] }[], s) => {
        const grp = acc.find((g) => g.type === s.type);
        if (grp) grp.items.push(s);
        else acc.push({ type: s.type, items: [s] });
        return acc;
    }, []);

    return (
        <List disablePadding>
            {suggestionTypes.map((type, groupIndex) => {
                const group = groups.find((g) => g.type === type);
                const typeSuggestions = group?.items ?? [];
                return (
                    <React.Fragment key={type}>
                        {groupIndex > 0 && <Divider />}
                        <Box className="p-3 pb-1">
                            <Chip component="span" icon={<span />} label={getSuggestionLabel(type)} size="sm" variant="outlined" />
                        </Box>
                        {typeSuggestions.map((suggestion) => {
                            const globalIndex = suggestions.indexOf(suggestion);
                            const isSelected = globalIndex === selectedIndex;
                            return (
                                <ListItem key={suggestion.id} disablePadding>
                                    <ListItemButton data-suggestion-index={globalIndex} component="button" selected={isSelected} onClick={() => onClick(suggestion, globalIndex)}>
                                        <ListItemIcon>{/* icon placeholder */}</ListItemIcon>
                                        <ListItemText
                                            primary={suggestion.text}
                                            secondary={showConfidence && suggestion.confidence && isSelected ? (
                                                <Chip component="span" label={`${Math.round(suggestion.confidence * 100)}% confidence`} size="sm" className="mt-1" />
                                            ) : undefined}
                                        />
                                    </ListItemButton>
                                </ListItem>
                            );
                        })}
                    </React.Fragment>
                );
            })}
        </List>
    );
};

export default SuggestionList;
