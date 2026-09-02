import { useEffect, useState } from 'react';
import { AppIcon } from './AppIcon';
import { createContributionPoster, type ContributionPosterResult } from './civicCardImage';
import { citizenSafeError } from './uiErrors';

type Props = {
  defaultDisplayName: string;
  lifetimePoints: number;
  currentMonthPoints: number;
  monthLabel: string;
  contributionLabels: string[];
  t: (source: string) => string;
};

function downloadPoster(result: ContributionPosterResult) {
  const link = document.createElement('a');
  link.href = URL.createObjectURL(result.blob);
  link.download = result.filename;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(link.href), 1_000);
}

export function ContributionPoster({ defaultDisplayName, lifetimePoints, currentMonthPoints, monthLabel, contributionLabels, t }: Props) {
  const [displayName, setDisplayName] = useState(defaultDisplayName);
  const [result, setResult] = useState<ContributionPosterResult | null>(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState('');

  useEffect(() => {
    if (!displayName.trim() && defaultDisplayName) setDisplayName(defaultDisplayName);
  }, [defaultDisplayName, displayName]);

  useEffect(() => () => { if (previewUrl) URL.revokeObjectURL(previewUrl); }, [previewUrl]);

  async function createPoster() {
    const chosenName = displayName.trim();
    if (chosenName.length < 2 || chosenName.length > 60) {
      setStatus(t('Choose a display name between 2 and 60 characters.'));
      return;
    }
    setBusy(true);
    setStatus(t('Creating your image on this device…'));
    try {
      const created = await createContributionPoster({ displayName: chosenName, lifetimePoints, currentMonthPoints, monthLabel, contributionLabels });
      if (previewUrl) URL.revokeObjectURL(previewUrl);
      setResult(created);
      setPreviewUrl(URL.createObjectURL(created.blob));
      setStatus(t('Your Civic Card image is ready. It has not been uploaded.'));
    } catch (error) {
      setStatus(t(citizenSafeError(error, 'The Civic Card image could not be created.')));
    } finally {
      setBusy(false);
    }
  }

  async function sharePoster() {
    if (!result) return;
    try {
      if (navigator.share && navigator.canShare?.({ files: [result.file] })) {
        await navigator.share({ title: t('My Civic Card'), text: t('My Seewik civic contribution record.'), files: [result.file] });
        setStatus(t('The device share sheet was opened.'));
      } else {
        downloadPoster(result);
        setStatus(t('Direct sharing is unavailable here, so the image was downloaded.'));
      }
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      setStatus(t('Sharing did not finish. You can download the image instead.'));
    }
  }

  return <section className="contribution-poster-panel">
    <div className="recognition-heading"><span className="eyebrow">{t('SHAREABLE CIVIC CARD')}</span><h2>{t('Create a contribution image')}</h2><p>{t('Choose the name that will appear, then create the image only when you are ready.')}</p></div>
    <label htmlFor="poster-display-name">{t('Name shown on this image')}<input id="poster-display-name" type="text" minLength={2} maxLength={60} value={displayName} onChange={(event) => setDisplayName(event.target.value)} /></label>
    <small>{t('The image contains this name, high-level contribution categories and point totals. It never includes email, UID, complaint text, evidence or precise locations.')}</small>
    <button disabled={busy} onClick={() => { void createPoster(); }}>{busy ? t('Creating image…') : t('Create my Civic Card image')}</button>
    {previewUrl && result && <div className="poster-result">
      <img src={previewUrl} alt={t('Preview of your generated Civic Card image')} />
      <div className="poster-actions"><button className="icon-button" onClick={() => { void sharePoster(); }}><AppIcon name="share" />{t('Share Civic Card')}</button><button className="secondary" onClick={() => downloadPoster(result)}>{t('Download image')}</button></div>
    </div>}
    {status && <div className="status-panel" role="status" aria-live="polite">{status}</div>}
    <small>{t('Creating or sharing this image does not change your public-recognition choice. The image is created locally and no public poster URL is made.')}</small>
  </section>;
}
