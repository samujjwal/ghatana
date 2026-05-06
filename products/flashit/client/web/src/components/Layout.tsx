/**
 * Main Layout Component
 * Composes the shared product shell around FlashIt page content.
 */

import SkipLink from './SkipLink';
import { FlashitProductShell } from './FlashitProductShell';

interface LayoutProps {
  children: React.ReactNode;
}

export default function Layout({ children }: LayoutProps) {
  return (
    <>
      <SkipLink />
      <FlashitProductShell>
        {children}
      </FlashitProductShell>
    </>
  );
}
