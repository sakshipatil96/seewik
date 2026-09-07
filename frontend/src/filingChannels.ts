export type FilingChannel = {
  type: string;
  value?: string | null;
};

export function resolveFilingRecipientEmail(
  citizenOverride: string,
  channels: readonly FilingChannel[] | null | undefined,
) {
  const override = citizenOverride.trim();
  if (override) return override;
  return channels?.find((channel) => channel.type === 'EMAIL')?.value?.trim() ?? '';
}
