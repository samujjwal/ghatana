/**
 * Tests for ConsentManager component
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ConsentManager, useConsent } from '../ConsentManager';

describe('ConsentManager', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('renders children when consent is already granted', () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    render(
      <ConsentManager purpose="voice_processing" onConsentChange={() => {}}>
        <div>Protected Content</div>
      </ConsentManager>
    );

    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('shows consent dialog when requireConsent is true and no consent exists', () => {
    render(
      <ConsentManager purpose="voice_processing" onConsentChange={() => {}} requireConsent>
        <div>Protected Content</div>
      </ConsentManager>
    );

    expect(screen.getByText('Consent Required')).toBeInTheDocument();
    expect(screen.getByText(/This feature requires your consent for voice_processing/)).toBeInTheDocument();
  });

  it('grants consent when Allow button is clicked', async () => {
    const onConsentChange = vi.fn();

    render(
      <ConsentManager purpose="voice_processing" onConsentChange={onConsentChange} requireConsent>
        <div>Protected Content</div>
      </ConsentManager>
    );

    fireEvent.click(screen.getByText('Allow'));

    await waitFor(() => {
      expect(onConsentChange).toHaveBeenCalledWith(true);
    });
  });

  it('denies consent when Deny button is clicked', async () => {
    const onConsentChange = vi.fn();

    render(
      <ConsentManager purpose="voice_processing" onConsentChange={onConsentChange} requireConsent>
        <div>Protected Content</div>
      </ConsentManager>
    );

    fireEvent.click(screen.getByText('Deny'));

    await waitFor(() => {
      expect(onConsentChange).toHaveBeenCalledWith(false);
    });
  });

  it('does not render children when consent is denied and requireConsent is false', () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: false,
      timestamp: new Date().toISOString(),
    }));

    render(
      <ConsentManager purpose="voice_processing" onConsentChange={() => {}}>
        <div>Protected Content</div>
      </ConsentManager>
    );

    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('renders fallback when provided and consent is denied', () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: false,
      timestamp: new Date().toISOString(),
    }));

    render(
      <ConsentManager
        purpose="voice_processing"
        onConsentChange={() => {}}
        fallback={<span>This feature requires your consent</span>}
      >
        <div>Protected Content</div>
      </ConsentManager>
    );

    expect(screen.getByText('This feature requires your consent')).toBeInTheDocument();
  });

  it('handles expired consent correctly', () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() - 1000).toISOString(), // Expired
    }));

    render(
      <ConsentManager purpose="voice_processing" onConsentChange={() => {}} requireConsent>
        <div>Protected Content</div>
      </ConsentManager>
    );

    expect(screen.getByText('Consent Required')).toBeInTheDocument();
  });

  it('uses custom consent message when provided', () => {
    render(
      <ConsentManager
        purpose="voice_processing"
        onConsentChange={() => {}}
        requireConsent
        consentMessage="Custom consent message"
      >
        <div>Protected Content</div>
      </ConsentManager>
    );

    expect(screen.getByText('Custom consent message')).toBeInTheDocument();
  });
});

describe('useConsent hook', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('returns consentGranted as true when consent exists', () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    const TestComponent = (): React.ReactElement => {
      const { consentGranted } = useConsent('voice_processing');
      return <div>{consentGranted ? 'Granted' : 'Denied'}</div>;
    };

    render(<TestComponent />);
    expect(screen.getByText('Granted')).toBeInTheDocument();
  });

  it('grants consent when grantConsent is called', async () => {
    const TestComponent = (): React.ReactElement => {
      const { consentGranted, grantConsent } = useConsent('voice_processing');
      return (
        <div>
          <span>{consentGranted ? 'Granted' : 'Denied'}</span>
          <button onClick={grantConsent}>Grant</button>
        </div>
      );
    };

    render(<TestComponent />);
    fireEvent.click(screen.getByText('Grant'));

    await waitFor(() => {
      expect(screen.getByText('Granted')).toBeInTheDocument();
    });
  });

  it('revokes consent when revokeConsent is called', async () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    const TestComponent = (): React.ReactElement => {
      const { consentGranted, revokeConsent } = useConsent('voice_processing');
      return (
        <div>
          <span>{consentGranted ? 'Granted' : 'Denied'}</span>
          <button onClick={revokeConsent}>Revoke</button>
        </div>
      );
    };

    render(<TestComponent />);
    expect(screen.getByText('Granted')).toBeInTheDocument();

    fireEvent.click(screen.getByText('Revoke'));

    await waitFor(() => {
      expect(screen.getByText('Denied')).toBeInTheDocument();
    });
  });
});
