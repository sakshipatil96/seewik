import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const pickerSource = readFileSync(new URL('../src/TemplatePicker.tsx', import.meta.url), 'utf8');
const stylesSource = readFileSync(new URL('../src/styles.css', import.meta.url), 'utf8');

test('template picker keeps selection immediate and all dismissal paths available', () => {
  assert.match(pickerSource, /document\.addEventListener\(['"](?:pointerdown|mousedown)['"]/);
  assert.match(pickerSource, /Escape/);
  assert.match(pickerSource, /closePicker\(true\)/);
  assert.match(pickerSource, /template-picker-backdrop/);
  assert.doesNotMatch(pickerSource, />\s*Done\s*</);
  assert.match(stylesSource, /\.template-picker-scroll-region\s*\{[^}]*overflow-y:\s*auto/);
});
