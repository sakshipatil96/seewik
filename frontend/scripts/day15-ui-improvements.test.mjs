import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const app = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8');
const accountControl = await readFile(new URL('../src/AccountControl.tsx', import.meta.url), 'utf8');
const backend = await readFile(new URL('../../backend/src/main/java/com/seewik/api/InitiativeService.java', import.meta.url), 'utf8');
const civicPack = await readFile(new URL('../../backend/src/main/resources/civic-pack-v0.2.json', import.meta.url), 'utf8');

test('desktop header controls share a stable height and emergency copy cannot wrap vertically', () => {
  assert.match(styles, /\.desktop-nav button[^}]*height: 48px/);
  assert.match(styles, /\.header-emergency[^}]*height: 48px[^}]*white-space: nowrap/);
  assert.match(styles, /\.account-button[^}]*height: 48px/);
  assert.match(styles, /\.language-switcher[^}]*height: 48px/);
  assert.match(styles, /\.language-option[^}]*min-height: 44px/);
  assert.match(app, /role="group" aria-label=\{t\('Language'\)\}/);
  assert.match(styles, /@media \(max-width: 1080px\)/);
});

test('compact header keeps language, emergency, and profile controls on the Seewik row', () => {
  const compactHeader = styles.slice(styles.indexOf('@media (max-width: 760px)'), styles.indexOf('@media (max-width: 520px)'));
  assert.match(app, /compactLabel: 'E'/);
  assert.match(app, /compactLabel: 'म'/);
  assert.match(app, /compactLabel: 'ह'/);
  assert.match(app, /AppIcon name="phone" className="header-emergency-icon"/);
  assert.match(accountControl, /AppIcon name="user" className="account-button-icon"/);
  assert.match(compactHeader, /\.app-header \{ flex-wrap: nowrap;/);
  assert.match(compactHeader, /\.header-actions \{[^}]*flex-wrap: nowrap;/);
  assert.match(compactHeader, /\.language-switcher \{ height: 40px;/);
  assert.match(compactHeader, /\.header-emergency, \.account-button \{[^}]*width: 40px;[^}]*height: 40px;/);
});

test('home removes the large report title while retaining concise civic guidance', () => {
  assert.doesNotMatch(app, /<h1>\{t\('Report a problem\. Get it to the right office\.'\)\}<\/h1>/);
  assert.match(app, /home-greeting/);
});

test('report flow provides camera, editable confirmation, and three honest filing choices', () => {
  assert.match(app, /photo-upload-label.*AppIcon name="camera"/s);
  assert.match(app, /Take or add a photo/);
  assert.match(app, /Camera or photo library/);
  assert.match(app, /evidencePreviewUrl/);
  assert.doesNotMatch(app, /Take a photo|Choose a photo/);
  assert.match(app, /<TemplatePicker id="issue-category"/);
  assert.match(app, /ISSUE_TYPES\.map\(\(\[value\]\) => \(\{ value, icon:/);
  assert.match(app, /scheduleEvidenceClassification\(evidenceImage, nextText, 650\)/);
  assert.doesNotMatch(app, /Confirm this category|Suggest from my location|Find the right office/);
  assert.match(app, /Find the right route/);
  assert.doesNotMatch(app, /Prefill report details/);
  assert.match(app, /Recipient email/);
  assert.match(app, /mailto:/);
  assert.match(app, /window\.open\(action\.url, '_blank', 'noopener,noreferrer'\)/);
  assert.match(app, /Open Gmail in browser/);
  assert.match(app, /filing-email-copy-actions/);
  assert.match(app, /filing-email-open-actions/);
  assert.doesNotMatch(app, /window\.location\.href = mailtoUrl/);
  assert.match(app, /Open official DMA form/);
  assert.match(app, /Share letter/);
  assert.match(app, /Print or save as PDF/);
  assert.match(app, /Seewik did not send it/);
  assert.match(civicPack, /complaint-2\/\?dma_tab=regional/);
  assert.doesNotMatch(app, /Saved reports are not deleted by Start over\./);
  assert.match(app, /report-page-heading[^>]*><h1[^>]*>.*New Report.*flow-start-over/s);
});

test('initiative templates and backend categories stay aligned', () => {
  for (const category of [
    'BIRTHDAY_DONATION', 'PLANTATION_DRIVE', 'AWARENESS_SESSION',
    'COMMUNITY_YOGA', 'MEDITATION_WORKSHOP', 'HEALTH_ACTIVITY', 'BOOK_SUPPLY_DRIVE',
    'DONATION', 'CLEANUP', 'OTHER_CIVIC_ACTIVITY',
  ]) {
    assert.match(app, new RegExp(category));
    assert.match(backend, new RegExp(category));
  }
  assert.match(app, /What is needed \(optional\)/);
  assert.match(app, /Message from the organiser \(optional\)/);
  assert.match(app, /Your name, as the city will see it/);
  assert.match(app, /setInitiativePublicOrganiserName\(\(currentName\)/);
  assert.match(app, /<TemplatePicker id="initiative-type"/);
  assert.match(app, /INITIATIVE_TEMPLATES\.map\(\(template\) => \(\{ value: template\.value, icon:/);
  assert.match(app, /onChange=\{applyInitiativeTemplate\}/);
  assert.match(styles, /\.template-picker-panel[^}]*position: absolute/);
  assert.match(styles, /\.template-picker-backdrop[^}]*background:/);
  assert.match(styles, /\.report-flow-card button:not\(\.secondary\):not\(\.icon-choice-card\):not\(\.template-picker-trigger\):not\(\.template-picker-option\):not\(\.template-picker-clear\)/);
  assert.match(app, /initiative-page-heading[^>]*><h1[^>]*>.*New Initiative.*startInitiativeOver/s);
  assert.match(app, /APPROVAL_REQUIRED/);
  assert.match(app, /Review activity/);
  assert.match(app, /PRIVATE REVIEW/);
  assert.match(app, /Happening in your community/);
  assert.match(styles, /\.community-feed[^}]*grid-template-columns: 1fr/);
});

test('My Initiatives uses the full available width and point rules are last on Civic Card', () => {
  assert.match(styles, /\.initiative-memberships > \.compact-list[^}]*grid-template-columns: 1fr/);
  const recognitionIndex = app.lastIndexOf('<RecognitionPanel');
  const pointsRulesIndex = app.lastIndexOf('points-rules-card');
  assert.ok(pointsRulesIndex > recognitionIndex);
});
