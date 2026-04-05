import React from 'react';

type HexColorPickerProps = {
  color: string;
  onChange: (value: string) => void;
};

export function HexColorPicker({ color, onChange }: HexColorPickerProps) {
  return (
    <input
      type="color"
      value={color}
      onChange={(event) => onChange(event.target.value)}
      style={{ width: '100%', height: 40, border: 0, background: 'transparent' }}
    />
  );
}
