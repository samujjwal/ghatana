import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { AssessmentEditor } from './AssessmentEditor';
import type { AssessmentConfig } from '@tutorputor/contracts/v1/learning-unit';

describe('AssessmentEditor publish readiness', () => {
    it('shows exact assessment fix actions when confidence, scoring, and viva readiness are incomplete', () => {
        const assessment = {
            model: 'cbm',
            confidenceLevels: [],
            scoring: undefined,
        } as unknown as AssessmentConfig;

        render(<AssessmentEditor assessment={assessment} onChange={vi.fn()} />);

        expect(screen.getByText('Assessment Publish Readiness')).toBeInTheDocument();
        expect(screen.getByText('1/4 ready')).toBeInTheDocument();
        expect(screen.getByText('Add low, medium, and high confidence levels before publishing.')).toBeInTheDocument();
        expect(screen.getByText('Configure the backend-owned CBM scoring matrix.')).toBeInTheDocument();
        expect(screen.getByText('Enable viva triggers for overconfidence or process anomalies.')).toBeInTheDocument();
    });
});
