export const GOOGLE_PROVIDER_ID = 'google.com';
export const PROFILE_SCHEMA_VERSION = 'citizen-profile-v0.1';
export const ACCOUNT_AUDIT_SCHEMA_VERSION = 'account-audit-v0.1';
export const SIGNED_OUT_STORAGE_KEY = 'seewik.account.signed-out.v1';

export type AccountIdentityState =
  | 'ANONYMOUS_SESSION'
  | 'GOOGLE_LINK_REQUIRED'
  | 'GOOGLE_LINKED'
  | 'SIGNED_OUT';

export type IdentitySnapshot = {
  uid: string | null;
  isAnonymous: boolean;
  providerIds: string[];
  deliberatelySignedOut?: boolean;
};

export function isGoogleLinked(providerIds: readonly string[]) {
  return providerIds.includes(GOOGLE_PROVIDER_ID);
}

export function accountIdentityState(snapshot: IdentitySnapshot): AccountIdentityState {
  if (snapshot.deliberatelySignedOut && !snapshot.uid) return 'SIGNED_OUT';
  if (!snapshot.uid) return 'ANONYMOUS_SESSION';
  if (isGoogleLinked(snapshot.providerIds)) return 'GOOGLE_LINKED';
  return 'GOOGLE_LINK_REQUIRED';
}

export function isCredentialCollisionCode(code: string | undefined) {
  return code === 'auth/credential-already-in-use'
    || code === 'auth/account-exists-with-different-credential';
}

export function accountErrorMessage(code: string | undefined) {
  switch (code) {
    case 'auth/popup-blocked':
      return 'The Google window was blocked. Allow pop-ups for Seewik and try again.';
    case 'auth/popup-closed-by-user':
    case 'auth/cancelled-popup-request':
      return 'Google sign-in was cancelled. Your form is still here.';
    case 'auth/network-request-failed':
      return 'The network interrupted Google sign-in. Check your connection and try again.';
    case 'auth/invalid-credential':
    case 'auth/user-token-expired':
      return 'The Google sign-in expired. Please try again.';
    case 'auth/operation-not-allowed':
      return 'Google sign-in is temporarily unavailable. Your form has not been cleared.';
    case 'auth/unauthorized-domain':
      return 'Google sign-in is not authorized on this website yet.';
    case 'permission-denied':
      return 'Your Google account connected, but Seewik could not finish the recoverable profile. Please try again after the update completes.';
    default:
      return 'Google sign-in could not be completed. Your form has not been cleared.';
  }
}

export function safeAccountErrorCode(code: string | undefined) {
  if (code && /^auth\/[a-z0-9-]+$/.test(code)) return code;
  if (code && ['permission-denied', 'failed-precondition', 'internal', 'unavailable', 'unknown'].includes(code)) return code;
  return 'auth/unknown';
}

export function minimalProfileData(uid: string) {
  return {
    ownerUid: uid,
    authProvider: 'GOOGLE',
    recoverable: true,
    schemaVersion: PROFILE_SCHEMA_VERSION,
  } as const;
}

export function collisionAuditData(ownerUid: string) {
  return {
    ownerUid,
    eventType: 'ACCOUNT_COLLISION_EXISTING_ACCOUNT_WON',
    outcome: 'EXISTING_ACCOUNT_SELECTED',
    schemaVersion: ACCOUNT_AUDIT_SCHEMA_VERSION,
  } as const;
}
