# P3-2: Canvas AI Suggestions Progressive Disclosure

**Status:** Completed ✅

## Implementation Summary

Modified the SmartSuggestions component to support progressive disclosure, showing only the highest-priority suggestion initially with a "show more" option.

## Changes Made

### 1. Updated Types (SmartSuggestions/types.ts)

- Added `priority?: number` field to `Suggestion` interface
- Added `progressiveDisclosure?: boolean` prop to enable/disable the feature
- Added `disclosureMode?: 'single' | 'all'` prop for user preference

### 2. Enhanced SmartSuggestions Component (SmartSuggestions.tsx)

- Added `ChevronDown` and `ChevronUp` icons for expand/collapse
- Added `showAll` state to track disclosure mode
- Implemented `calculatePriority()` function with heuristic scoring:
  - Confidence score contributes 50% of priority
  - Suggestion type bonuses (completion: +20, improve: +15)
  - Length-based heuristic (shorter = more actionable)
- Added `prioritizedSuggestions` memo to sort by priority
- Added `displayedSuggestions` memo to filter based on disclosure mode
- Updated keyboard navigation to use `displayedSuggestions`
- Added "Show more/less" button when progressive disclosure is enabled

### 3. Priority Calculation Heuristic

```typescript
function calculatePriority(suggestion: Suggestion): number {
  let priority = 0;
  
  // Higher confidence = higher priority
  if (suggestion.confidence) {
    priority += suggestion.confidence * 50;
  }
  
  // Certain types get priority boost
  if (suggestion.type === 'completion') priority += 20;
  if (suggestion.type === 'improve') priority += 15;
  
  // Length-based heuristic (shorter suggestions often more actionable)
  if (suggestion.text.length < 50) priority += 10;
  else if (suggestion.text.length < 100) priority += 5;
  
  return Math.min(100, Math.max(0, priority));
}
```

## Usage Example

```tsx
<SmartSuggestions
  aiService={provider}
  context="User is writing code"
  onSelect={(suggestion) => insertText(suggestion.text)}
  suggestionTypes={['completion', 'improve']}
  progressiveDisclosure={true}  // Enable progressive disclosure
  disclosureMode="single"       // Show only top suggestion initially
/>
```

## Benefits

1. **Reduced Cognitive Load**: Users see only the most relevant suggestion first
2. **Better UX**: Progressive disclosure allows users to drill down when needed
3. **User Preference**: Disclosure mode can be persisted to user settings
4. **Priority-Based**: Suggestions are ranked by confidence, type, and length

## Future Enhancements

- Persist disclosure mode to user preferences
- Allow custom priority calculation functions
- Add "always show" for certain critical suggestion types
- Add priority badges to suggestion items
