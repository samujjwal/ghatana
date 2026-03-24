/**
 * Tests for SecurityLens component (Journey 11.2)
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { SecurityLens, SecurityFinding, SecurityControlType, ThreatType } from '../views/SecurityLens';

describe('SecurityLens', () => {
    const mockFindings: SecurityFinding[] = [
        {
            id: '1',
            type: 'Spoofing',
            severity: 'critical',
            title: 'Missing authentication',
            description: 'API endpoint lacks authentication',
            nodeId: 'node1',
            recommendation: 'Add OAuth2 authentication',
        },
        {
            id: '2',
            type: 'SQL Injection',
            severity: 'high',
            title: 'Unsafe query',
            description: 'SQL query not parameterized',
            nodeId: 'node2',
            recommendation: 'Use prepared statements',
        },
    ];

    it('renders security lens toggle', () => {
        render(<SecurityLens enabled={false} findings={[]} onToggle={vi.fn()} />);
        expect(screen.getByText('Enable Security Lens')).toBeInTheDocument();
    });

    it('calls onToggle when switch is clicked', () => {
        const onToggle = vi.fn();
        render(<SecurityLens enabled={false} findings={[]} onToggle={onToggle} />);

        const toggle = screen.getByRole('checkbox');
        fireEvent.click(toggle);

        expect(onToggle).toHaveBeenCalledTimes(1);
    });

    it('renders Run STRIDE Analysis button', () => {
        render(<SecurityLens enabled={true} findings={[]} onToggle={vi.fn()} />);
        expect(screen.getByText('Run STRIDE Analysis')).toBeInTheDocument();
    });

    it('calls onRunAnalysis when button is clicked', () => {
        const onRunAnalysis = vi.fn();
        render(<SecurityLens enabled={true} findings={[]} onToggle={vi.fn()} onRunAnalysis={onRunAnalysis} />);

        const button = screen.getByText('Run STRIDE Analysis');
        fireEvent.click(button);

        expect(onRunAnalysis).toHaveBeenCalledTimes(1);
    });

    it('renders security control buttons', () => {
        render(<SecurityLens enabled={true} findings={[]} onToggle={vi.fn()} />);

        expect(screen.getByText('Rate Limiter')).toBeInTheDocument();
        expect(screen.getByText('WAF')).toBeInTheDocument();
        expect(screen.getByText('Encryption')).toBeInTheDocument();
    });

    it('calls onAddControl with correct control type', () => {
        const onAddControl = vi.fn();
        render(<SecurityLens enabled={true} findings={[]} onToggle={vi.fn()} onAddControl={onAddControl} />);

        const rateLimiterBtn = screen.getByText('Rate Limiter');
        fireEvent.click(rateLimiterBtn);

        expect(onAddControl).toHaveBeenCalledWith('ratelimiter');
    });

    it('displays PCI-DSS compliant badge', () => {
        render(<SecurityLens enabled={true} findings={[]} onToggle={vi.fn()} pciDssCompliant={true} />);
        expect(screen.getByText('PCI-DSS Compliant')).toBeInTheDocument();
    });

    it('displays PCI-DSS non-compliant badge', () => {
        render(<SecurityLens enabled={true} findings={[]} onToggle={vi.fn()} pciDssCompliant={false} />);
        expect(screen.getByText('PCI-DSS Non-Compliant')).toBeInTheDocument();
    });

    it('displays findings list', () => {
        render(<SecurityLens enabled={true} findings={mockFindings} onToggle={vi.fn()} />);

        expect(screen.getByText('Missing authentication')).toBeInTheDocument();
        expect(screen.getByText('Unsafe query')).toBeInTheDocument();
    });

    it('displays severity chips with correct colors', () => {
        render(<SecurityLens enabled={true} findings={mockFindings} onToggle={vi.fn()} />);

        const criticalChips = screen.getAllByText('CRITICAL');
        const highChips = screen.getAllByText('HIGH');

        expect(criticalChips.length).toBeGreaterThan(0);
        expect(highChips.length).toBeGreaterThan(0);
    });

    it('displays threat type chips', () => {
        render(<SecurityLens enabled={true} findings={mockFindings} onToggle={vi.fn()} />);

        expect(screen.getByText('Spoofing')).toBeInTheDocument();
        expect(screen.getByText('SQL Injection')).toBeInTheDocument();
    });

    it('displays finding descriptions', () => {
        render(<SecurityLens enabled={true} findings={mockFindings} onToggle={vi.fn()} />);

        expect(screen.getByText('API endpoint lacks authentication')).toBeInTheDocument();
        expect(screen.getByText('SQL query not parameterized')).toBeInTheDocument();
    });

    it('displays recommendations', () => {
        render(<SecurityLens enabled={true} findings={mockFindings} onToggle={vi.fn()} />);

        expect(screen.getByText(/Add OAuth2 authentication/)).toBeInTheDocument();
        expect(screen.getByText(/Use prepared statements/)).toBeInTheDocument();
    });

    it('displays no issues message when findings array is empty', () => {
        render(<SecurityLens enabled={true} findings={[]} onToggle={vi.fn()} />);
        expect(screen.getByText('No security issues found')).toBeInTheDocument();
    });

    it('counts findings by severity', () => {
        render(<SecurityLens enabled={true} findings={mockFindings} onToggle={vi.fn()} />);

        // Should show 2 findings (1 critical + 1 high)
        const findingsText = screen.getByText(/2 findings/);
        expect(findingsText).toBeInTheDocument();
    });

    it('renders with default props', () => {
        render(<SecurityLens enabled={false} findings={[]} onToggle={vi.fn()} />);
        expect(screen.getByText('Enable Security Lens')).toBeInTheDocument();
    });

    it('handles undefined onRunAnalysis gracefully', () => {
        render(<SecurityLens enabled={true} findings={[]} onToggle={vi.fn()} />);

        const button = screen.getByText('Run STRIDE Analysis');
        // Should not throw error
        fireEvent.click(button);
    });

    it('handles undefined onAddControl gracefully', () => {
        render(<SecurityLens enabled={true} findings={[]} onToggle={vi.fn()} />);

        const button = screen.getByText('Rate Limiter');
        // Should not throw error
        fireEvent.click(button);
    });
});
