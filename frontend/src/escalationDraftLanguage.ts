export type EscalationDraftLanguage = 'MR' | 'EN';

export function automaticEscalationLanguageTransition(
  interfaceLanguage: 'en' | 'mr' | 'hi',
  manuallySelected: boolean,
  currentLanguage: EscalationDraftLanguage,
) {
  const nextLanguage: EscalationDraftLanguage = manuallySelected
    ? currentLanguage
    : interfaceLanguage === 'en' ? 'EN' : 'MR';
  return {
    nextLanguage,
    shouldSwitch: nextLanguage !== currentLanguage,
  };
}
