import { emergencyContactIsCallable, emergencyInformationContent } from './sourcedContent';
import { AppIcon } from './AppIcon';

type Props = { t: (source: string) => string };

export function EmergencyInformationPage({ t }: Props) {
  const topic = emergencyInformationContent.topics[0];
  const groups = Array.from(new Set(topic.sections.map((contact) => contact.group)));
  return <>
    <section className="hero page-hero emergency-hero">
      <h1 className="page-title">{t('Emergency Information')}</h1>
    </section>
    <section className="emergency-groups" aria-label={t('Emergency telephone numbers')}>
      {groups.map((group) => <div className="emergency-group" key={group}>
        <h2>{t(group)}</h2>
        <div className="emergency-contact-grid">
          {topic.sections.filter((contact) => contact.group === group).map((contact) => {
            const callable = emergencyContactIsCallable(topic, contact);
            return <article className={`emergency-contact ${contact.id === 'national-112' ? 'national-contact' : ''}`} key={contact.id}>
              <div><strong>{t(contact.label)}</strong><span>{t(contact.description)}</span></div>
              <b>{contact.displayNumber}</b>
              {callable
                ? <a className="call-action" href={`tel:${contact.telephoneNumber}`} aria-label={`${t('Call')} ${t(contact.label)} ${contact.displayNumber}`}><AppIcon name="phone" />{t('Call')} {contact.displayNumber}</a>
                : <span className="call-disabled" role="status">{t('Source review expired — call action disabled')}</span>}
            </article>;
          })}
        </div>
      </div>)}
    </section>
    <aside className="offline-emergency-note"><strong>{t('Available without sign-in')}</strong><span>{t('These verified numbers are stored with the app for offline viewing. Placing a call still requires telephone service.')}</span></aside>
    <section className="content-sources emergency-sources" aria-label={t('Emergency sources')}>
      <h2>{t('Sources')}</h2>
      <ol>{topic.sources.map((source) => <li key={source.id}><a href={source.url} target="_blank" rel="noreferrer">{source.title}</a> — {source.authority}. {t('Last reviewed')} {source.lastReviewed}; {t('review by')} {source.reviewExpiresOn}.</li>)}</ol>
    </section>
  </>;
}
