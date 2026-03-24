/**
 * Token Picker Component
 *
 * UI component for selecting design tokens from the theme system.
 * Displays available tokens grouped by category with search and preview.
 *
 * @module canvas/properties/TokenPicker
 */

import React, { useState, useMemo } from 'react';

import type { ThemeContext } from '../renderer/ThemeApplicator';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface TokenPickerProps {
  /**
   * Theme context for token resolution
   */
  themeContext: ThemeContext;

  /**
   * Token category to filter (color, spacing, typography, etc.)
   */
  category?: string;

  /**
   * Currently selected token path
   */
  value?: string;

  /**
   * Callback when token is selected
   */
  onChange: (tokenPath: string) => void;

  /**
   * Show token preview
   */
  showPreview?: boolean;

  /**
   * Placeholder text
   */
  placeholder?: string;
}

/**
 *
 */
interface TokenOption {
  path: string;
  value: unknown;
  category: string;
}

// ============================================================================
// Token Picker Component
// ============================================================================

export const TokenPicker: React.FC<TokenPickerProps> = ({
  themeContext,
  category,
  value,
  onChange,
  showPreview = true,
  placeholder = 'Select a token...',
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);

  // Extract all available tokens
  const availableTokens = useMemo(() => {
    const tokens: TokenOption[] = [];
    const activeLayer = themeContext.activeLayer;
    const layerTokens = themeContext.tokens[activeLayer];

    const extractTokens = (obj: Record<string, unknown>, prefix = '') => {
      for (const [key, val] of Object.entries(obj)) {
        const path = prefix ? `${prefix}.${key}` : key;

        if (typeof val === 'object' && !Array.isArray(val) && val !== null) {
          extractTokens(val, path);
        } else {
          const tokenCategory = path.split('.')[0];
          if (!category || tokenCategory === category) {
            tokens.push({
              path,
              value: val,
              category: tokenCategory,
            });
          }
        }
      }
    };

    extractTokens(layerTokens);
    return tokens;
  }, [themeContext, category]);

  // Filter tokens by search query
  const filteredTokens = useMemo(() => {
    if (!searchQuery.trim()) return availableTokens;

    const query = searchQuery.toLowerCase();
    return availableTokens.filter((token) =>
      token.path.toLowerCase().includes(query)
    );
  }, [availableTokens, searchQuery]);

  // Get preview for a token value
  const getPreview = (token: TokenOption): React.ReactNode => {
    if (!showPreview) return null;

    const { category: cat, value: val } = token;

    if (cat === 'color') {
      return (
        <div
          style={{
            width: 20,
            height: 20,
            backgroundColor: val,
            border: '1px solid #ccc',
            borderRadius: 4,
          }}
        />
      );
    }

    if (cat === 'spacing') {
      return (
        <div
          style={{
            width: val,
            height: 8,
            backgroundColor: '#1976d2',
            borderRadius: 2,
          }}
        />
      );
    }

    if (cat === 'typography') {
      return <span style={{ fontSize: 12, color: '#666' }}>{val}</span>;
    }

    return <span style={{ fontSize: 12, color: '#666' }}>{String(val)}</span>;
  };

  // Get display value
  const displayValue = value || placeholder;

  return (
    <div style={{ position: 'relative', width: '100%' }}>
      {/* Trigger Button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        style={{
          width: '100%',
          padding: '8px 12px',
          border: '1px solid #ccc',
          borderRadius: 4,
          backgroundColor: '#fff',
          textAlign: 'left',
          cursor: 'pointer',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <span style={{ color: value ? '#000' : '#999' }}>
          {value ? `$${displayValue}` : displayValue}
        </span>
        <span style={{ fontSize: 12 }}>▼</span>
      </button>

      {/* Dropdown */}
      {isOpen && (
        <div
          style={{
            position: 'absolute',
            top: '100%',
            left: 0,
            right: 0,
            marginTop: 4,
            backgroundColor: '#fff',
            border: '1px solid #ccc',
            borderRadius: 4,
            boxShadow: '0 4px 6px rgba(0,0,0,0.1)',
            zIndex: 1000,
            maxHeight: 300,
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          {/* Search */}
          <div style={{ padding: 8, borderBottom: '1px solid #eee' }}>
            <input
              type="text"
              placeholder="Search tokens..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              style={{
                width: '100%',
                padding: '6px 8px',
                border: '1px solid #ccc',
                borderRadius: 4,
                fontSize: 14,
              }}
            />
          </div>

          {/* Token List */}
          <div style={{ overflowY: 'auto', flex: 1 }}>
            {filteredTokens.length === 0 ? (
              <div style={{ padding: 16, textAlign: 'center', color: '#999' }}>
                No tokens found
              </div>
            ) : (
              filteredTokens.map((token) => (
                <button
                  key={token.path}
                  onClick={() => {
                    onChange(token.path);
                    setIsOpen(false);
                    setSearchQuery('');
                  }}
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    border: 'none',
                    backgroundColor: value === token.path ? '#e3f2fd' : 'transparent',
                    textAlign: 'left',
                    cursor: 'pointer',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    fontSize: 14,
                  }}
                  onMouseEnter={(e) => {
                    if (value !== token.path) {
                      e.currentTarget.style.backgroundColor = '#f5f5f5';
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (value !== token.path) {
                      e.currentTarget.style.backgroundColor = 'transparent';
                    }
                  }}
                >
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 14, fontWeight: 500 }}>
                      ${token.path}
                    </div>
                    <div style={{ fontSize: 11, color: '#666', marginTop: 2 }}>
                      {token.category}
                    </div>
                  </div>
                  {getPreview(token)}
                </button>
              ))
            )}
          </div>

          {/* Footer */}
          <div
            style={{
              padding: 8,
              borderTop: '1px solid #eee',
              fontSize: 12,
              color: '#666',
              textAlign: 'center',
            }}
          >
            {filteredTokens.length} token{filteredTokens.length !== 1 ? 's' : ''}{' '}
            available
          </div>
        </div>
      )}

      {/* Backdrop */}
      {isOpen && (
        <div
          onClick={() => setIsOpen(false)}
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            zIndex: 999,
          }}
        />
      )}
    </div>
  );
};
