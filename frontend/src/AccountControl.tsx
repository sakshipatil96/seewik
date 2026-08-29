import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import type { AccountIdentityState } from './accountIdentity';

export type AccountDialog = 'CLOSED' | 'LINK' | 'PROFILE' | 'COLLISION';

type AccountControlProps = {
  state: AccountIdentityState;
  dialog: AccountDialog;
  email: string | null;
  busy: boolean;
  error: string;
  t: (source: string) => string;
  onOpen: () => void;
  onClose: () => void;
  onGoogle: () => void;
  onCollisionContinue: () => void;
  onSignOut: () => void;
};

export function AccountControl({
  state,
  dialog,
  email,
  busy,
  error,
  t,
  onOpen,
  onClose,
  onGoogle,
  onCollisionContinue,
  onSignOut,
}: AccountControlProps) {
  const [collisionConfirmed, setCollisionConfirmed] = useState(false);
  const dialogRef = useRef<HTMLElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const previousDialog = useRef<AccountDialog>('CLOSED');

  useEffect(() => {
    if (dialog !== 'COLLISION') setCollisionConfirmed(false);
  }, [dialog]);

  useEffect(() => {
    const wasOpen = previousDialog.current !== 'CLOSED';
    previousDialog.current = dialog;
    if (dialog === 'CLOSED') {
      if (wasOpen) triggerRef.current?.focus();
      return;
    }
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const frame = window.requestAnimationFrame(() => {
      const preferred = dialogRef.current?.querySelector<HTMLElement>('[autofocus], button:not(:disabled), input:not(:disabled)');
      preferred?.focus();
    });
    return () => {
      window.cancelAnimationFrame(frame);
      document.body.style.overflow = previousOverflow;
    };
  }, [dialog]);

  const linked = state === 'GOOGLE_LINKED';
  const signedOut = state === 'SIGNED_OUT';
  const buttonLabel = linked ? t('Profile') : signedOut ? t('Sign in') : t('Save my work');

  return <>
    <button
      ref={triggerRef}
      className={`account-button ${linked ? 'linked' : ''}`}
      onClick={onOpen}
      aria-haspopup="dialog"
      aria-label={linked ? t('Open recoverable profile') : t('Connect a Google account')}
    >
      <span aria-hidden="true">{linked ? '✓' : '○'}</span>{buttonLabel}
    </button>

    {dialog !== 'CLOSED' && createPortal(<div className="account-overlay" role="presentation">
      <section
        ref={dialogRef}
        className="account-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="account-dialog-title"
        onKeyDown={(event) => {
          if (event.key === 'Escape' && !busy) onClose();
          if (event.key === 'Tab') {
            const focusable = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), [href], [tabindex]:not([tabindex="-1"])') ?? []);
            if (!focusable.length) return;
            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            if (event.shiftKey && document.activeElement === first) {
              event.preventDefault();
              last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
              event.preventDefault();
              first.focus();
            }
          }
        }}
      >
        <button className="account-close" onClick={onClose} disabled={busy} aria-label={t('Close')}>×</button>

        {dialog === 'LINK' && <>
          <span className="eyebrow">{t('RECOVERABLE PROFILE')}</span>
          <h2 id="account-dialog-title">{signedOut ? t('Sign in to your Seewik profile') : t('Save your civic work')}</h2>
          <p>{signedOut
            ? t('Use the same Google account to recover your reports, points and Initiative activity.')
            : t('Connect Google before Seewik saves your first change. Your current account ID and ownership stay the same.')}</p>
          <ul className="account-benefits">
            <li>{t('Keep reports and drafts after clearing this browser')}</li>
            <li>{t('Recover points and Initiative memberships on another device')}</li>
            <li>{t('One Google tap; no Seewik password')}</li>
          </ul>
          <button autoFocus disabled={busy} onClick={onGoogle}>{busy ? t('Connecting…') : t('Continue with Google')}</button>
          <button className="secondary" disabled={busy} onClick={onClose}>{t('Not now')}</button>
          <small>{t('Seewik stores only a provider marker and your existing account ID in its profile record. It does not copy your Google email, name or photo into that record.')}</small>
        </>}

        {dialog === 'COLLISION' && <>
          <span className="eyebrow">{t('ACCOUNT ALREADY EXISTS')}</span>
          <h2 id="account-dialog-title">{t('Choose the existing Google-linked profile')}</h2>
          <p>{t('This Google account already has Seewik data. If you continue, that existing account will open.')}</p>
          <div className="account-warning">
            <strong>{t('Current-device data will not transfer automatically.')}</strong>
            <span>{t('Reports, drafts, points, organiser rights and Initiative memberships attached to this temporary account will remain untouched, but they will not appear in the existing account.')}</span>
          </div>
          <label className="review-check">
            <input type="checkbox" checked={collisionConfirmed} onChange={(event) => setCollisionConfirmed(event.target.checked)} />
            <span>{t('I understand that these two accounts will not be merged.')}</span>
          </label>
          <button autoFocus={false} disabled={busy || !collisionConfirmed} onClick={onCollisionContinue}>{busy ? t('Switching account…') : t('Open the existing account')}</button>
          <button className="secondary" autoFocus disabled={busy} onClick={onClose}>{t('Cancel and keep this account')}</button>
        </>}

        {dialog === 'PROFILE' && <>
          <span className="eyebrow">{t('RECOVERABLE PROFILE')}</span>
          <h2 id="account-dialog-title">{t('Google account connected')}</h2>
          <div className="account-status"><span aria-hidden="true">✓</span><div><strong>{t('Your Seewik work is recoverable')}</strong>{email && <small>{email}</small>}</div></div>
          <p>{t('Use this same Google account on another browser or device to open records owned by this profile.')}</p>
          <p className="account-privacy">{t('Your Google email is shown here from Firebase Authentication. Seewik does not copy it into the civic profile record.')}</p>
          <button className="secondary" disabled={busy} onClick={onSignOut}>{busy ? t('Signing out…') : t('Sign out')}</button>
          <small>{t('Signing out does not delete reports, drafts, points or Initiative activity. Seewik will not silently create a new temporary working account after sign-out.')}</small>
        </>}

        {error && <div className="status-panel state-warning" role="status" aria-live="polite">{t(error)}</div>}
      </section>
    </div>, document.body)}
  </>;
}
