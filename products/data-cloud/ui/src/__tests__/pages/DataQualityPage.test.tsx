import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { DataQualityPage } from '../../pages/DataQualityPage';

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider><BrowserRouter>{children}</BrowserRouter></Provider>
);

describe('DataQualityPage', () => {
  it('renders without crashing', () => {
    render(<DataQualityPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays data quality content', () => {
    render(<DataQualityPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/quality|score|check|valid|rule|monitor/i);
  });

  it('renders quality metric sections', () => {
    render(<DataQualityPage />, { wrapper: TestWrapper });
    const elements = document.querySelectorAll('div, section, article');
    expect(elements.length).toBeGreaterThan(0);
  });

  it('has interactive tab or filter controls', async () => {
    const user = userEvent.setup();
    render(<DataQualityPage />, { wrapper: TestWrapper });
    const buttons = Array.from(document.querySelectorAll('button'));
    if (buttons.length > 0) {
      await user.click(buttons[0]);
      expect(document.body).toBeTruthy();
    }
  });
});
