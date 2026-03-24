import { render, screen } from '@testing-library/react';
import React from 'react';

import { Avatar } from './Avatar.tailwind';

describe('Avatar', () => {
  test('renders without error when alt is empty or undefined and does not throw', () => {
    // Render with empty alt
    const { rerender } = render(<Avatar alt="" />);
  const img = screen.getByRole('img');
  expect(img).not.toBeNull();

    // Render with undefined cast (to simulate runtime mis-usage)
    rerender(<Avatar alt={undefined as unknown as string} />);
  const img2 = screen.getByRole('img');
  expect(img2).not.toBeNull();
  });
});
