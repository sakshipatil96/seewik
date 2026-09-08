import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import {
  MAX_RELIABLE_MAILTO_LENGTH,
  buildEmailHandoff,
  mailtoIsTooLong,
} from '../src/emailHandoff.ts';

test('email and Gmail links encode raw multilingual fields exactly once', () => {
  const recipient = 'citizen+route@example.com';
  const subject = 'Road & drainage | रस्ता';
  const body = 'A large pothole | प्रभाग 8\nकृपया तपासा';
  const action = buildEmailHandoff({ recipient, subject, body });

  const mailtoQuery = new URLSearchParams(action.url.slice(action.url.indexOf('?') + 1));
  const gmail = new URL(action.gmailUrl);

  assert.equal(mailtoQuery.get('subject'), subject);
  assert.equal(mailtoQuery.get('body'), body);
  assert.equal(gmail.pathname, '/mail/u/0/');
  assert.equal(gmail.searchParams.get('view'), 'cm');
  assert.equal(gmail.searchParams.get('to'), recipient);
  assert.equal(gmail.searchParams.get('su'), subject);
  assert.equal(gmail.searchParams.get('body'), body);
  assert.doesNotMatch(action.url, /%2520|%257C|%25E[0-9A-F]/i);
  assert.doesNotMatch(action.gmailUrl, /%2520|%257C|%25E[0-9A-F]/i);
  assert.equal(action.copyText, `To: ${recipient}\nSubject: ${subject}\n\n${body}`);
});

test('mailto length guard falls back only above the reliable ceiling', () => {
  assert.equal(mailtoIsTooLong('x'.repeat(MAX_RELIABLE_MAILTO_LENGTH)), false);
  assert.equal(mailtoIsTooLong('x'.repeat(MAX_RELIABLE_MAILTO_LENGTH + 1)), true);
});

test('default email navigation stays synchronous and long messages copy instead', async () => {
  const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');

  assert.match(source, /if \(mailtoIsTooLong\(action\.url\)\) \{[\s\S]*await copyPlainText\(action\.copyText\)/);
  assert.match(source, /window\.location\.href = action\.url/);
  assert.doesNotMatch(source, /window\.open\(action\.url/);
  assert.match(source, /window\.open\(action\.gmailUrl, '_blank', 'noopener,noreferrer'\)/);
});
