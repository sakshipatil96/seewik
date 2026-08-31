import { useState } from 'react';
import type { PublicRecognitionPanel } from './recognitionClient';

type Props = {
  panel: PublicRecognitionPanel | null;
  loading: boolean;
  status: string;
  t: (source: string) => string;
  onReport: (position: number, targetDisplayName: string, reason: string, details: string) => Promise<void>;
};

export function RecognitionPanel({ panel, loading, status, t, onReport }: Props) {
  const [reporting, setReporting] = useState<number | null>(null);
  const [reason, setReason] = useState('IMPERSONATION');
  const [details, setDetails] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (reporting === null) return;
    setBusy(true);
    try {
      const targetDisplayName = panel?.names[reporting];
      if (!targetDisplayName) return;
      await onReport(reporting, targetDisplayName, reason, details);
      setReporting(null);
      setDetails('');
    } finally {
      setBusy(false);
    }
  }

  return <section className="recognition-panel" aria-labelledby="recognition-title">
    <div className="recognition-heading">
      <span className="eyebrow">{t('MONTHLY THANK-YOU')}</span>
      <h2 id="recognition-title">{t('Thanks to Our Top Three Citizens of the Month')}</h2>
      <p>{t('A Seewik thank-you for recorded civic contributions.')}</p>
      {panel?.monthLabel && <small>{panel.monthLabel}</small>}
    </div>
    {loading && <div className="recognition-empty" role="status">{t('Loading this month’s recognition…')}</div>}
    {!loading && panel && panel.names.length === 0 && <div className="recognition-empty">{t('No citizens have opted in and qualified for public recognition this month.')}</div>}
    {!loading && panel && panel.names.length > 0 && <div className="recognition-names">
      {panel.names.map((name, position) => <article className="recognition-name-card" key={`${position}-${name}`}>
        <strong>{name}</strong>
        <button className="text-action" onClick={() => setReporting(position)}>{t('Report this displayed name')}</button>
      </article>)}
    </div>}
    {reporting !== null && <div className="recognition-report-form">
      <h3>{t('Report a concern about this displayed name')}</h3>
      <label>{t('Reason')}<select value={reason} onChange={(event) => setReason(event.target.value)}><option value="IMPERSONATION">{t('Possible impersonation')}</option><option value="OFFICIAL_TITLE">{t('Misleading official title')}</option><option value="OTHER">{t('Other concern')}</option></select></label>
      <label>{t('Details (optional)')}<textarea maxLength={300} value={details} onChange={(event) => setDetails(event.target.value)} /></label>
      <div className="recognition-form-actions"><button className="secondary" disabled={busy} onClick={() => setReporting(null)}>{t('Cancel')}</button><button disabled={busy} onClick={() => { void submit(); }}>{busy ? t('Sending…') : t('Send report')}</button></div>
    </div>}
    {status && <div className="status-panel state-warning" role="status" aria-live="polite">{t(status)}</div>}
  </section>;
}
