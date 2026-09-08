import assert from 'node:assert/strict';
import test from 'node:test';
import { copyPlainText } from '../src/plainTextClipboard.ts';

const multilingualText = 'Issue: Pothole & road damage | Prabhag 8\nमराठी मजकूर\nहिंदी पाठ';

test('modern clipboard receives exact plain text with spaces, punctuation, line breaks and Devanagari', async () => {
  let copied = '';
  const method = await copyPlainText(multilingualText, {
    clipboard: { writeText: async (value) => { copied = value; } },
  });

  assert.equal(method, 'clipboard');
  assert.equal(copied, multilingualText);
  assert.doesNotMatch(copied, /%(?:20|7C|0A|25)/i);
});

test('fallback clipboard receives the same exact plain text when the modern API fails', async () => {
  let textarea;
  let copied = '';
  const document = {
    createElement() {
      textarea = {
        value: '',
        style: {},
        setAttribute() {},
        focus() {},
        select() {},
        setSelectionRange() {},
        remove() {},
      };
      return textarea;
    },
    body: { appendChild() {} },
    execCommand(command) {
      assert.equal(command, 'copy');
      copied = textarea.value;
      return true;
    },
  };

  const method = await copyPlainText(multilingualText, {
    clipboard: { writeText: async () => { throw new Error('blocked'); } },
    document,
  });

  assert.equal(method, 'fallback');
  assert.equal(copied, multilingualText);
  assert.doesNotMatch(copied, /%(?:20|7C|0A|25)/i);
});

test('fallback copy reports failure instead of claiming success', async () => {
  const textarea = {
    value: '',
    style: {},
    setAttribute() {},
    focus() {},
    select() {},
    setSelectionRange() {},
    remove() {},
  };
  const document = {
    createElement: () => textarea,
    body: { appendChild() {} },
    execCommand: () => false,
  };

  await assert.rejects(
    copyPlainText('plain text', { clipboard: null, document }),
    /PLAIN_TEXT_COPY_FAILED/,
  );
});
