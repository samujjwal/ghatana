import React, { useState, useEffect } from 'react';
import { tokens } from '@ghatana/tokens';

export interface CommandItem {
  id: string;
  label: string;
  description?: string;
  icon?: React.ReactNode;
  shortcut?: string;
  category?: string;
  onSelect: () => void;
}

export interface CommandPaletteProps {
  commands: CommandItem[];
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  placeholder?: string;
  className?: string;
}

/**
 * CommandPalette component for quick actions
 */
export const CommandPalette: React.FC<CommandPaletteProps> = ({
  commands,
  open: controlledOpen,
  onOpenChange,
  placeholder = 'Type a command...',
  className,
}) => {
  const [internalOpen, setInternalOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);

  const open = controlledOpen !== undefined ? controlledOpen : internalOpen;

  const filteredCommands = commands.filter((cmd) =>
    cmd.label.toLowerCase().includes(search.toLowerCase()) ||
    cmd.description?.toLowerCase().includes(search.toLowerCase())
  );

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        const newOpen = !open;
        setInternalOpen(newOpen);
        onOpenChange?.(newOpen);
        setSearch('');
        setSelectedIndex(0);
      }

      if (!open) return;

      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault();
          setSelectedIndex((prev) => (prev + 1) % filteredCommands.length);
          break;
        case 'ArrowUp':
          e.preventDefault();
          setSelectedIndex((prev) => (prev - 1 + filteredCommands.length) % filteredCommands.length);
          break;
        case 'Enter':
          e.preventDefault();
          if (filteredCommands[selectedIndex]) {
            filteredCommands[selectedIndex].onSelect();
            setInternalOpen(false);
            onOpenChange?.(false);
          }
          break;
        case 'Escape':
          e.preventDefault();
          setInternalOpen(false);
          onOpenChange?.(false);
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, filteredCommands, selectedIndex, onOpenChange]);

  if (!open) return null;

  const overlayStyles: React.CSSProperties = {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    display: 'flex',
    alignItems: 'flex-start',
    justifyContent: 'center',
    paddingTop: '20vh',
    zIndex: 1000,
  };

  const paletteStyles: React.CSSProperties = {
    backgroundColor: tokens.colors.neutral[0],
    borderRadius: tokens.borderRadius.lg,
    boxShadow: tokens.colors.neutral[900] + '33',
    width: '90%',
    maxWidth: '600px',
    maxHeight: '400px',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  };

  const inputStyles: React.CSSProperties = {
    padding: tokens.spacing[3],
    borderBottom: `1px solid ${tokens.colors.neutral[200]}`,
    fontSize: tokens.typography.fontSize.base,
    border: 'none',
    outline: 'none',
    width: '100%',
  };

  const listStyles: React.CSSProperties = {
    overflowY: 'auto',
    flex: 1,
  };

  const itemStyles = (isSelected: boolean): React.CSSProperties => ({
    padding: `${tokens.spacing[2]} ${tokens.spacing[3]}`,
    cursor: 'pointer',
    backgroundColor: isSelected ? tokens.colors.primary[50] : 'transparent',
    borderLeft: isSelected ? `4px solid ${tokens.colors.primary[600]}` : '4px solid transparent',
    display: 'flex',
    alignItems: 'center',
    gap: tokens.spacing[2],
    transition: `all ${tokens.transitions.duration.fast}`,
  });

  const groupedCommands = filteredCommands.reduce(
    (acc, cmd) => {
      const category = cmd.category || 'General';
      if (!acc[category]) acc[category] = [];
      acc[category].push(cmd);
      return acc;
    },
    {} as Record<string, CommandItem[]>
  );

  return (
    <div style={overlayStyles} onClick={() => setInternalOpen(false)} className={className}>
      <div style={paletteStyles} onClick={(e) => e.stopPropagation()}>
        <input
          type="text"
          placeholder={placeholder}
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setSelectedIndex(0);
          }}
          style={inputStyles}
          autoFocus
        />
        <div style={listStyles}>
          {Object.entries(groupedCommands).map(([category, items]) => (
            <div key={category}>
              <div
                style={{
                  padding: `${tokens.spacing[2]} ${tokens.spacing[3]}`,
                  fontSize: tokens.typography.fontSize.xs,
                  fontWeight: 600,
                  color: tokens.colors.neutral[500],
                  textTransform: 'uppercase',
                  letterSpacing: '0.5px',
                }}
              >
                {category}
              </div>
              {items.map((cmd, idx) => {
                const globalIndex = Object.values(groupedCommands)
                  .slice(0, Object.keys(groupedCommands).indexOf(category))
                  .reduce((sum, arr) => sum + arr.length, 0) + idx;

                return (
                  <div
                    key={cmd.id}
                    style={itemStyles(globalIndex === selectedIndex)}
                    onClick={() => {
                      cmd.onSelect();
                      setInternalOpen(false);
                      onOpenChange?.(false);
                    }}
                    onMouseEnter={() => setSelectedIndex(globalIndex)}
                  >
                    {cmd.icon && <span style={{ display: 'flex', flexShrink: 0 }}>{cmd.icon}</span>}
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 500 }}>{cmd.label}</div>
                      {cmd.description && (
                        <div style={{ fontSize: tokens.typography.fontSize.sm, color: tokens.colors.neutral[500] }}>
                          {cmd.description}
                        </div>
                      )}
                    </div>
                    {cmd.shortcut && (
                      <div
                        style={{
                          fontSize: tokens.typography.fontSize.xs,
                          color: tokens.colors.neutral[500],
                          fontFamily: 'monospace',
                        }}
                      >
                        {cmd.shortcut}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

CommandPalette.displayName = 'CommandPalette';
