import { civicAwarenessContent, isTopicCurrent, type AwarenessTopic } from './sourcedContent';

type Props = {
  t: (source: string) => string;
  onReportIssue: (issueType: string) => void;
  onStartInitiative: () => void;
};

function DutyLinks({ section, t, onReportIssue, onStartInitiative }: Omit<Props, 'topic'> & { section: AwarenessTopic['sections'][number] }) {
  if (!section.links?.length) return null;
  const handleAction = (action: string) => {
    if (action === 'Start a clean-up or plantation' || action === 'Explore community activities') onStartInitiative();
    else if (section.label === '51A(g)') onReportIssue('GARBAGE_SOLID_WASTE');
    else if (section.label === '51A(i)') onReportIssue('POTHOLE_ROAD_DAMAGE');
  };
  return <small className="duty-links">{section.links.map((action, index) => <span key={action}>{index > 0 ? ' · ' : ''}<button className="text-action" onClick={() => handleAction(action)}>{t(action)}</button></span>)}</small>;
}

export function CivicAwarenessPage({ t, onReportIssue, onStartInitiative }: Props) {
  const sourceIndex = civicAwarenessContent.topics.flatMap((topic) => topic.sources.map((source) => ({ topic: topic.heading, ...source })));
  return <>
    <section className="hero page-hero awareness-hero">
      <h1 className="page-title">{t('Civic Awareness · Did You Know?')}</h1>
      <p>{t('Short, sourced explanations that connect civic duties, municipal work and official programmes to useful action.')}</p>
    </section>
    <section className="awareness-topics" aria-label={t('Civic awareness topics')}>
      {civicAwarenessContent.topics.map((topic, index) => {
        const current = isTopicCurrent(topic);
        return <details className="awareness-topic" key={topic.id} open={index === 0}>
          <summary>
            <span>{String(index + 1).padStart(2, '0')}</span>
            <div><h2>{t(topic.heading)}</h2><p>{t(topic.summary)}</p></div>
            <b aria-hidden="true">＋</b>
          </summary>
          <div className="awareness-topic-body">
            <div className="awareness-facts">
              {topic.sections.map((section) => <article key={`${topic.id}-${section.label}`}>
                <strong>{t(section.label)}</strong><p>{t(section.text)}</p>
                {topic.id === 'article-51a' && <DutyLinks section={section} t={t} onReportIssue={onReportIssue} onStartInitiative={onStartInitiative} />}
              </article>)}
            </div>
            {topic.limitations.length > 0 && <aside className="content-limitation"><strong>{t('Please keep in mind')}</strong>{topic.limitations.map((limitation) => <p key={limitation}>{t(limitation)}</p>)}</aside>}
            <small className={`content-review ${current ? '' : 'stale'}`}>{current ? t('Official sources reviewed') : t('Review required')} · {t('Last reviewed')} {topic.sources[0].lastReviewed} · {topic.sources.map((source, sourceIndex) => <span key={source.id}>{sourceIndex > 0 ? ' · ' : ''}<a href={source.url} target="_blank" rel="noreferrer">{source.title}</a></span>)}</small>
          </div>
        </details>;
      })}
    </section>
    <section className="content-sources" aria-label={t('Sources')}>
      <h2>{t('Sources')}</h2>
      <ol>{sourceIndex.map((source) => <li key={`${source.topic}-${source.id}`}><a href={source.url} target="_blank" rel="noreferrer">{source.title}</a> — {source.authority}. {t('Last reviewed')} {source.lastReviewed}; {t('review by')} {source.reviewExpiresOn}.</li>)}</ol>
      <small>{civicAwarenessContent.schemaVersion} · {civicAwarenessContent.contentVersion}</small>
    </section>
  </>;
}
