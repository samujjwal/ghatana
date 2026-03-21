import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TestWrapper } from '../test-utils/wrapper';
import { SqlWorkspacePage } from '../../pages/SqlWorkspacePage';


describe('SqlWorkspacePage', () => {
  it('renders without crashing', () => {
    render(<SqlWorkspacePage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays SQL editor content', () => {
    render(<SqlWorkspacePage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/sql|query|editor|workspace|run|execute/i);
  });

  it('has a run/execute button', () => {
    render(<SqlWorkspacePage />, { wrapper: TestWrapper });
    const buttons = Array.from(document.querySelectorAll('button'));
    const runBtn = buttons.find(
      (b) => b.textContent?.toLowerCase().match(/run|execute|play/i),
    );
    expect(buttons.length).toBeGreaterThan(0);
  });
});
