import assert from 'node:assert/strict';
import { readdir, readFile } from 'node:fs/promises';
import test from 'node:test';

async function sourceFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const url = new URL(`${entry.name}${entry.isDirectory() ? '/' : ''}`, directory);
    if (entry.isDirectory()) return sourceFiles(url);
    return /\.(?:ts|tsx)$/.test(entry.name) ? [url] : [];
  }));
  return nested.flat();
}

test('citizen-facing copy avoids vulgar, joke-like, and awkward double-meaning wording', async () => {
  const files = await sourceFiles(new URL('../src/', import.meta.url));
  const copy = (await Promise.all(files.map((url) => readFile(url, 'utf8')))).join('\n');

  assert.doesNotMatch(copy, /\bprivate points?\b/i);
  assert.doesNotMatch(copy, /\b(fuck|shit|bitch|asshole|bastard|dick|cock|pussy|boobs?|sexy|nudes?|balls)\b/i);
  assert.doesNotMatch(copy, /\b(joke|pun|innuendo)\b/i);
});

test('home presents Improve and Initiate as equal primary actions with the requested hierarchy', async () => {
  const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');
  const styles = await readFile(new URL('../src/styles.css', import.meta.url), 'utf8');

  assert.match(source, /home-primary-action improve-action[\s\S]*<h2>\{t\('Improve'\)\}<\/h2>[\s\S]*Something needs fixing/);
  assert.match(source, /home-primary-action initiate-action[\s\S]*<h2>\{t\('Initiate'\)\}<\/h2>[\s\S]*Start something good/);
  assert.match(styles, /\.home-actions \{[^}]*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/);
  assert.match(styles, /\.home-actions \.home-primary-action h2[^}]*font-size: clamp\(2\.25rem/);
  assert.match(styles, /\.improve-action[^}]*background: #f8a536/);
  assert.match(styles, /\.initiate-action[^}]*background: #15918b/);
  assert.match(styles, /\.improve-action \.home-action-subtext[^}]*color: #fff/);
});

test('the points destination is presented as My Civic Card without a redundant contribution-record label', async () => {
  const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');

  assert.match(source, /civic-card-heading[\s\S]*My Civic Card[\s\S]*A record of what you have actually done/);
  assert.doesNotMatch(source, /MY CONTRIBUTION RECORD/);
  assert.match(source, /t\('Civic Card'\)/);
});

test('normal citizen screens hide diagnostic scaffolding and raw browser errors', async () => {
  const source = await readFile(new URL('../src/main.tsx', import.meta.url), 'utf8');

  assert.match(source, /screen === 'home' && DEBUG_MODE/);
  assert.doesNotMatch(source, /set[A-Za-z]+Status\(error\.message\)/);
  assert.doesNotMatch(source, /<button[^>]*>\{t\('Verify cloud services'\)\}<\/button>/);
  assert.doesNotMatch(source, /<dt>\{t\('Route'\)\}<\/dt>/);
  assert.doesNotMatch(source, /<dt>\{t\('Pack'\)\}<\/dt>/);
});
