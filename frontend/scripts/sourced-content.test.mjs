import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const awareness = JSON.parse(await readFile(new URL('../src/content/civic-awareness-v0.1.json', import.meta.url), 'utf8'));
const emergency = JSON.parse(await readFile(new URL('../src/content/emergency-information-v0.1.json', import.meta.url), 'utf8'));
const awarenessUi = await readFile(new URL('../src/CivicAwarenessPage.tsx', import.meta.url), 'utf8');
const emergencyUi = await readFile(new URL('../src/EmergencyInformationPage.tsx', import.meta.url), 'utf8');
const helpers = await readFile(new URL('../src/sourcedContent.ts', import.meta.url), 'utf8');
const serviceWorker = await readFile(new URL('../public/sw.js', import.meta.url), 'utf8');
const reviewedOn = Date.parse('2026-08-31T00:00:00Z');
const allowedAuthorities = new Set(['indiacode.nic.in', 'www.indiacode.nic.in', 'mahadma.maharashtra.gov.in', 'nandurbar.gov.in', 'www.pib.gov.in', 'pib.gov.in', 'morth.gov.in', 'www.morth.gov.in', 'mybharat.gov.in', 'www.mybharat.gov.in', '112.gov.in', 'www.112.gov.in']);

function validateDocument(document) {
  assert.equal(document.schemaVersion, 'sourced-content-v0.1');
  assert.match(document.contentVersion, /^[-a-z0-9.]+$/i);
  assert.equal(document.defaultLanguage, 'en');
  assert.ok(document.topics.length > 0);
  for (const topic of document.topics) {
    assert.equal(topic.status, 'REVIEWED', `${topic.id} must not ship as current without review`);
    assert.equal(topic.language, 'en');
    assert.ok(topic.jurisdiction);
    assert.ok(topic.sources.length > 0, `${topic.id} must be sourced`);
    for (const source of topic.sources) {
      assert.ok(source.title && source.authority && source.url);
      assert.equal(allowedAuthorities.has(new URL(source.url).hostname), true, `${source.url} is not an approved official host`);
      assert.ok(Date.parse(`${source.lastReviewed}T00:00:00Z`) <= reviewedOn);
      assert.ok(Date.parse(`${source.reviewExpiresOn}T23:59:59Z`) >= reviewedOn, `${source.id} is stale`);
      assert.ok(Object.hasOwn(source, 'sourceDate'), `${source.id} must define sourceDate, including null for an undated live page`);
    }
  }
}

test('awareness and emergency documents use the versioned, current, official-source contract', () => {
  validateDocument(awareness);
  validateDocument(emergency);
});

test('the explicitly approved awareness set is present without generated legal guidance', () => {
  assert.deepEqual(awareness.topics.map((topic) => topic.id), [
    'article-51a',
    'municipal-complaint-follow-up',
    'civic-recognition-programmes',
    'nandurbar-whos-who',
    'nagar-parishad-work',
  ]);
  const article = awareness.topics[0];
  assert.equal(article.sections.length, 11);
  assert.deepEqual(article.sections.filter((section) => section.highlight).map((section) => section.label), ['51A(g)', '51A(h)', '51A(i)', '51A(j)']);
  assert.doesNotMatch(JSON.stringify(awareness), /gemini|personalized legal advice/i);
  assert.match(JSON.stringify(article.sections), /Report waste or drainage/);
  assert.match(JSON.stringify(article.sections), /Start a clean-up or plantation/);
  assert.match(awarenessUi, /className="duty-links"/);
  assert.doesNotMatch(awarenessUi, /className="awareness-actions"/);
  assert.match(awarenessUi, /content-sources/);
  assert.match(awarenessUi, /t\(topic\.heading\)/);
  assert.match(awarenessUi, /t\(topic\.summary\)/);
  assert.match(awarenessUi, /t\(section\.text\)/);
  assert.match(awarenessUi, /t\(limitation\)/);
});

test('all callable emergency contacts map to a current source and normalized telephone number', () => {
  const topic = emergency.topics[0];
  const sources = new Map(topic.sources.map((source) => [source.id, source]));
  assert.equal(topic.sections[0].displayNumber, '112');
  assert.equal(topic.sections[0].sourceId, 'erss-112');
  for (const contact of topic.sections) {
    assert.match(contact.telephoneNumber, /^\+?[0-9]{3,15}$/);
    assert.ok(sources.has(contact.sourceId), `${contact.id} has no matching source`);
  }
  assert.match(helpers, /reviewExpiresOn/);
  assert.match(helpers, /emergencyContactIsCallable/);
  assert.match(emergencyUi, /href={`tel:\${contact\.telephoneNumber}`}/);
  assert.match(emergencyUi, /Source review expired — call action disabled/);
  assert.match(emergencyUi, /not an emergency response service/i);
  assert.match(emergencyUi, /t\(topic\.summary\)/);
  assert.match(emergencyUi, /t\(group\)/);
  assert.match(emergencyUi, /t\(contact\.label\)/);
  assert.match(emergencyUi, /t\(contact\.description\)/);
});

test('emergency information stays independent of accounts, reports and location', () => {
  assert.doesNotMatch(emergencyUi, /accountState|accountName|authService|report history|privatePoints|geolocation|coordinates|navigator\.geolocation/i);
  assert.match(emergencyUi, /Available without sign-in/);
  assert.match(emergencyUi, /offline viewing/);
  assert.match(serviceWorker, /cache\.put\(event\.request,response\.clone\(\)\)/);
  assert.match(serviceWorker, /event\.request\.mode==='navigate'\?caches\.match\('\/'\)/);
});
