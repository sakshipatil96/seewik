import type { RewardOverview } from './recognitionClient';
import type { InterfaceLanguage } from './i18n';
import { formatDateTime } from './i18n';

type Props = {
  language: InterfaceLanguage;
  overview: RewardOverview | null;
  loading: boolean;
  busyId: string;
  confirmUseId: string;
  status: string;
  t: (source: string) => string;
  onClaim: (couponId: string) => void;
  onBeginUse: (claimId: string) => void;
  onCancelUse: () => void;
  onConfirmUse: (claimId: string) => void;
  onRefresh: () => void;
};

const statusLabel = (status: string, t: Props['t']) => ({
  LOCKED: t('Locked'),
  UNLOCKED: t('Unlocked'),
  CLAIMED: t('Claimed'),
  USED: t('Used in simulation'),
  EXPIRED: t('Expired'),
}[status] ?? status);

export function RewardCatalogue({
  language,
  overview,
  loading,
  busyId,
  confirmUseId,
  status,
  t,
  onClaim,
  onBeginUse,
  onCancelUse,
  onConfirmUse,
  onRefresh,
}: Props) {
  if (!overview && loading) {
    return <section className="reward-catalogue" aria-busy="true"><p>{t('Loading example rewards…')}</p></section>;
  }
  if (!overview) {
    return <section className="reward-catalogue"><div className="empty-state"><p>{t('Example rewards could not be loaded.')}</p><button className="secondary" onClick={onRefresh}>{t('Try again')}</button></div>{status && <div role="status" aria-live="polite" className="status-panel state-warning">{t(status)}</div>}</section>;
  }

  const progressMaximum = overview.nextTier || overview.tiers.at(-1) || 250;
  const progressValue = Math.min(overview.lifetimePoints, progressMaximum);
  const tierCopy = overview.currentTier
    ? `${overview.currentTier}-${t('point tier unlocked')}`
    : t('No reward tier unlocked yet');
  const nextTierCopy = language === 'mr'
    ? `पुढील स्तरासाठी ${overview.pointsToNextTier} गुण बाकी`
    : language === 'hi'
      ? `अगले स्तर के लिए ${overview.pointsToNextTier} अंक बाकी`
      : `${overview.pointsToNextTier} points to the next tier`;

  return <section className="reward-catalogue" aria-labelledby="reward-heading">
    <div className="reward-heading">
      <div><span className="eyebrow">{t('EXAMPLE REWARDS')}</span><h2 id="reward-heading">{t('Contribution rewards')}</h2></div>
      <span className="reward-tier-chip">{tierCopy}</span>
    </div>
    <p>{t('Your lifetime points unlock these examples permanently. Claiming never reduces your points.')}</p>
    <div className="reward-progress">
      <div><strong>{overview.lifetimePoints} {t('points')}</strong><span>{overview.nextTier ? nextTierCopy : t('All example tiers unlocked')}</span></div>
      <progress max={progressMaximum} value={progressValue} aria-label={t('Progress to the next reward tier')} />
    </div>
    <div className="reward-grid">
      {overview.coupons.map((coupon) => {
        const isBusy = busyId === coupon.couponId || busyId === coupon.claimId;
        const confirming = Boolean(coupon.claimId && confirmUseId === coupon.claimId);
        const pointsNeeded = Math.max(0, coupon.tierRequired - overview.lifetimePoints);
        const lockedCopy = language === 'mr'
          ? `${coupon.tierRequired} गुण आवश्यक · ${pointsNeeded} गुण बाकी`
          : language === 'hi'
            ? `${coupon.tierRequired} अंक चाहिए · ${pointsNeeded} अंक बाकी`
            : `Needs ${coupon.tierRequired} points · ${pointsNeeded} to go`;
        return <article className={`reward-card reward-${coupon.claimStatus.toLowerCase()}`} key={coupon.couponId}>
          <div className="reward-card-top"><span className="example-reward-label">{t('Example local reward')}</span><span className="reward-state">{statusLabel(coupon.claimStatus, t)}</span></div>
          <h3>{t(coupon.description)}</h3>
          <p className="reward-business">{coupon.businessName} · {t(coupon.category)}</p>
          <p className="reward-threshold">{coupon.tierRequired} {t('points')}</p>
          {coupon.claimStatus === 'LOCKED' && <p className="reward-needed">{lockedCopy}</p>}
          {coupon.claimStatus === 'UNLOCKED' && <button disabled={isBusy} onClick={() => onClaim(coupon.couponId)}>{isBusy ? t('Creating example code…') : t('Claim example reward')}</button>}
          {coupon.code && <div className="reward-code"><small>{t('Your example code')}</small><strong>{coupon.code}</strong>{coupon.expiresAt && <span>{coupon.claimStatus === 'EXPIRED' ? t('Expired') : t('Expires')} · {formatDateTime(language, Date.parse(coupon.expiresAt))}</span>}</div>}
          {coupon.claimStatus === 'CLAIMED' && !confirming && <button disabled={isBusy} onClick={() => coupon.claimId && onBeginUse(coupon.claimId)}>{t('Simulate using this reward')}</button>}
          {coupon.claimStatus === 'CLAIMED' && confirming && <div className="reward-confirm" role="group" aria-label={t('Confirm simulated use')}>
            <p><strong>{t('Mark this example code as used?')}</strong> {t('This cannot be undone.')}</p>
            <div><button disabled={isBusy} onClick={() => coupon.claimId && onConfirmUse(coupon.claimId)}>{isBusy ? t('Marking as used…') : t('Yes, simulate use')}</button><button className="secondary" disabled={isBusy} onClick={onCancelUse}>{t('Cancel')}</button></div>
          </div>}
          {coupon.claimStatus === 'USED' && <p className="reward-used-note">{t('Used in simulation')} · {coupon.usedAt ? formatDateTime(language, Date.parse(coupon.usedAt)) : ''}</p>}
          {(coupon.claimStatus === 'CLAIMED' || coupon.claimStatus === 'USED') && <p className="reward-demo-notice">{t('This is a demonstration. No shop has verified or accepted this code.')}</p>}
        </article>;
      })}
    </div>
    {status && <div role="status" aria-live="polite" className="status-panel state-warning">{t(status)}</div>}
  </section>;
}
