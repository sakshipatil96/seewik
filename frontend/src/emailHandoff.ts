export const MAX_RELIABLE_MAILTO_LENGTH = 1900;

type EmailHandoffInput = {
  recipient: string;
  subject: string;
  body: string;
};

export function buildEmailHandoff({ recipient, subject, body }: EmailHandoffInput) {
  const trimmedRecipient = recipient.trim();
  const trimmedSubject = subject.trim();
  const encodedRecipient = encodeURIComponent(trimmedRecipient);
  const encodedSubject = encodeURIComponent(trimmedSubject);
  const encodedBody = encodeURIComponent(body);

  return {
    url: `mailto:${trimmedRecipient}?subject=${encodedSubject}&body=${encodedBody}`,
    gmailUrl: `https://mail.google.com/mail/u/0/?view=cm&fs=1&tf=1&to=${encodedRecipient}&su=${encodedSubject}&body=${encodedBody}`,
    copyText: `To: ${trimmedRecipient}\nSubject: ${trimmedSubject}\n\n${body}`,
  };
}

export function mailtoIsTooLong(url: string) {
  return url.length > MAX_RELIABLE_MAILTO_LENGTH;
}
