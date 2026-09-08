type ClipboardWriter = {
  writeText(value: string): Promise<void>;
};

type CopyPlainTextOptions = {
  clipboard?: ClipboardWriter | null;
  document?: Document;
};

export async function copyPlainText(value: string, options: CopyPlainTextOptions = {}) {
  const clipboard = options.clipboard === undefined ? navigator.clipboard : options.clipboard;
  if (clipboard?.writeText) {
    try {
      await clipboard.writeText(value);
      return 'clipboard' as const;
    } catch {
      // Mobile browsers can reject the modern API even during a direct tap.
    }
  }

  const documentRef = options.document ?? document;
  const textarea = documentRef.createElement('textarea');
  textarea.value = value;
  textarea.setAttribute('readonly', '');
  textarea.style.position = 'fixed';
  textarea.style.inset = '0 auto auto 0';
  textarea.style.opacity = '0';
  documentRef.body.appendChild(textarea);
  textarea.focus();
  textarea.select();
  textarea.setSelectionRange(0, value.length);
  const copied = documentRef.execCommand('copy');
  textarea.remove();
  if (!copied) throw new Error('PLAIN_TEXT_COPY_FAILED');
  return 'fallback' as const;
}
