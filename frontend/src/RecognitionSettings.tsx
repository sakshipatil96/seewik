import { useEffect, useState } from 'react';
import type { RecognitionSettings as Settings } from './recognitionClient';

type Props = {
  connected: boolean;
  settings: Settings | null;
  busy: boolean;
  status: string;
  t: (source: string) => string;
  onConnect: () => void;
  onSave: (publicDisplayName: string, recognitionActive: boolean) => Promise<void>;
};

export function RecognitionSettings({ connected, settings, busy, status, t, onConnect, onSave }: Props) {
  const [name, setName] = useState('');
  const [confirmed, setConfirmed] = useState(false);

  useEffect(() => {
    setName(settings?.publicDisplayName ?? '');
    setConfirmed(false);
  }, [settings?.publicDisplayName, settings?.recognitionActive]);

  if (!connected) return <section className="recognition-settings">
    <h2>{t('Public recognition is optional')}</h2>
    <p>{t('Connect Google to choose a separate public display name and decide whether it may appear in the monthly thank-you.')}</p>
    <button onClick={onConnect}>{t('Continue with Google')}</button>
  </section>;

  if (!settings) return <section className="recognition-settings" aria-live="polite">
    <h2>{t('Your public recognition choice')}</h2>
    <p>{t(status || 'Loading your private recognition settings…')}</p>
  </section>;

  return <section className="recognition-settings" aria-labelledby="recognition-settings-title">
    <span className="eyebrow">{t('YOUR PRIVACY CHOICE')}</span>
    <h2 id="recognition-settings-title">{settings?.recognitionActive ? t('Public recognition is active') : t('You are private by default')}</h2>
    <p>{t('Your Google name and email remain private account details. Only the public display name previewed below can appear, and only after you opt in.')}</p>
    <label>{t('Public display name')}<input type="text" minLength={2} maxLength={60} value={name} onChange={(event) => { setName(event.target.value); setConfirmed(false); }} /></label>
    <div className="recognition-preview"><small>{t('Exact public preview')}</small><strong>{name.trim() || t('Enter a display name')}</strong></div>
    {!settings?.recognitionActive && <label className="review-check"><input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} /><span>{t('I choose to make this display name eligible for Seewik’s public monthly recognition.')}</span></label>}
    <div className="recognition-form-actions">
      {settings?.recognitionActive
        ? <><button className="secondary" disabled={busy || name.trim().length < 2} onClick={() => { void onSave(name, true); }}>{t('Save public name')}</button><button disabled={busy} onClick={() => { void onSave(settings.publicDisplayName, false); }}>{t('Withdraw from public recognition')}</button></>
        : <button disabled={busy || !confirmed || name.trim().length < 2} onClick={() => { void onSave(name, true); }}>{t('Opt in with this public name')}</button>}
    </div>
    <small>{t('Withdrawing removes your name from the public panel without deleting or changing your points.')}</small>
    {status && <div className="status-panel state-warning" role="status" aria-live="polite">{t(status)}</div>}
  </section>;
}
