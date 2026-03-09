import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { PolicyForm } from '../components/PolicyForm';

describe('PolicyForm Component', () => {
  it('should render form fields', () => {
    const mockSubmit = vi.fn();
    const mockCancel = vi.fn();

    render(<PolicyForm onSubmit={mockSubmit} onCancel={mockCancel} />);
    
    expect(screen.getByLabelText('Policy Name')).toBeInTheDocument();
    expect(screen.getByLabelText('Policy Type')).toBeInTheDocument();
    expect(screen.getByText('Create Policy')).toBeInTheDocument();
    expect(screen.getByText('Cancel')).toBeInTheDocument();
  });

  it('should display time limit field when type is time-limit', () => {
    const mockSubmit = vi.fn();
    const mockCancel = vi.fn();

    render(<PolicyForm onSubmit={mockSubmit} onCancel={mockCancel} />);
    
    // Default type is time-limit
    expect(screen.getByLabelText('Maximum Usage (minutes)')).toBeInTheDocument();
  });

  it('should display content filter fields when type is content-filter', () => {
    const mockSubmit = vi.fn();
    const mockCancel = vi.fn();

    render(<PolicyForm onSubmit={mockSubmit} onCancel={mockCancel} />);
    
    const typeSelect = screen.getByLabelText('Policy Type');
    fireEvent.change(typeSelect, { target: { value: 'content-filter' } });
    
    expect(screen.getByLabelText(/Blocked Categories/)).toBeInTheDocument();
  });

  it('should display validation error when name is empty', () => {
    const mockSubmit = vi.fn();
    const mockCancel = vi.fn();

    render(<PolicyForm onSubmit={mockSubmit} onCancel={mockCancel} />);
    
    const submitButton = screen.getByText('Create Policy');
    fireEvent.click(submitButton);
    
    expect(screen.getByText('Policy name is required')).toBeInTheDocument();
    expect(mockSubmit).not.toHaveBeenCalled();
  });
});
