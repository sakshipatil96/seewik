const NETWORK_ERROR = /failed to fetch|networkerror|network request failed|load failed|connection/i;
const TIMEOUT_ERROR = /timeout|timed out|aborterror/i;

export function citizenSafeError(error: unknown, fallback: string) {
  const message = error instanceof Error ? error.message : '';
  if (TIMEOUT_ERROR.test(message)) return 'This request took too long. Check your connection and try again.';
  if (NETWORK_ERROR.test(message)) return 'Seewik could not reach the service. Check your connection and try again.';
  return fallback;
}
