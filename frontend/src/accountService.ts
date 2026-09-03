import {
  GoogleAuthProvider,
  linkWithPopup,
  onAuthStateChanged,
  signInAnonymously,
  signInWithCredential,
  signInWithPopup,
  signOut,
  type AuthCredential,
  type User,
} from 'firebase/auth';
import { addDoc, collection, serverTimestamp } from 'firebase/firestore';
import { auth, authPersistenceReady, db } from './firebase';
import { API_URL } from './apiConfig';
import {
  SIGNED_OUT_STORAGE_KEY,
  accountIdentityState,
  collisionAuditData,
  isGoogleLinked,
  type AccountIdentityState,
} from './accountIdentity';

export type AccountProvider = 'GOOGLE';

const providers: Record<AccountProvider, GoogleAuthProvider> = {
  GOOGLE: new GoogleAuthProvider(),
};
providers.GOOGLE.setCustomParameters({ prompt: 'select_account' });

let anonymousSignIn: Promise<User> | null = null;

export type AccountSnapshot = {
  state: AccountIdentityState;
  user: User | null;
};

function signedOutDeliberately() {
  return window.localStorage.getItem(SIGNED_OUT_STORAGE_KEY) === 'true';
}

function snapshotFor(user: User | null): AccountSnapshot {
  return {
    state: accountIdentityState({
      uid: user?.uid ?? null,
      isAnonymous: user?.isAnonymous ?? false,
      providerIds: user?.providerData.map((provider) => provider.providerId) ?? [],
      deliberatelySignedOut: signedOutDeliberately(),
    }),
    user,
  };
}

export function observeAccount(listener: (snapshot: AccountSnapshot) => void) {
  let cancelled = false;
  let unsubscribe: () => void = () => undefined;
  void authPersistenceReady.then(() => {
    if (cancelled) return;
    unsubscribe = onAuthStateChanged(auth, async (user) => {
      if (user) {
        window.localStorage.removeItem(SIGNED_OUT_STORAGE_KEY);
        listener(snapshotFor(user));
        return;
      }
      if (signedOutDeliberately()) {
        listener(snapshotFor(null));
        return;
      }
      listener({ state: 'ANONYMOUS_SESSION', user: null });
      try {
        const anonymousUser = await ensureAnonymousSession();
        listener(snapshotFor(anonymousUser));
      } catch {
        listener({ state: 'ANONYMOUS_SESSION', user: null });
      }
    });
  }).catch(() => {
    if (!cancelled) listener({ state: 'SIGNED_OUT', user: null });
  });
  return () => {
    cancelled = true;
    unsubscribe();
  };
}

export async function ensureAnonymousSession() {
  await authPersistenceReady;
  if (auth.currentUser) return auth.currentUser;
  if (signedOutDeliberately()) throw new Error('ACCOUNT_SIGNED_OUT');
  if (!anonymousSignIn) {
    anonymousSignIn = signInAnonymously(auth)
      .then((credential) => credential.user)
      .finally(() => { anonymousSignIn = null; });
  }
  return anonymousSignIn;
}

export async function sessionToken(forceRefresh = false) {
  const user = auth.currentUser ?? await ensureAnonymousSession();
  return user.getIdToken(forceRefresh);
}

export function accountCredentialFromError(provider: AccountProvider, error: unknown) {
  if (provider !== 'GOOGLE') return null;
  return GoogleAuthProvider.credentialFromError(error as Parameters<typeof GoogleAuthProvider.credentialFromError>[0]);
}

export async function linkCurrentSession(provider: AccountProvider) {
  const user = auth.currentUser ?? await ensureAnonymousSession();
  if (provider === 'GOOGLE' && isGoogleLinked(user.providerData.map((item) => item.providerId))) {
    await user.getIdToken(true);
    await syncPrivateProfile(user);
    return user;
  }
  if (!user.isAnonymous) throw new Error('GOOGLE_LINK_REQUIRED');
  const beforeUid = user.uid;
  const credential = await linkWithPopup(user, providers[provider]);
  if (credential.user.uid !== beforeUid) throw new Error('LINK_CHANGED_UID');
  await credential.user.getIdToken(true);
  await syncPrivateProfile(credential.user);
  return credential.user;
}

export async function signIntoAccount(provider: AccountProvider) {
  await authPersistenceReady;
  const credential = await signInWithPopup(auth, providers[provider]);
  window.localStorage.removeItem(SIGNED_OUT_STORAGE_KEY);
  await credential.user.getIdToken(true);
  await syncPrivateProfile(credential.user);
  return credential.user;
}

export async function continueWithExistingAccount(credential: AuthCredential) {
  await authPersistenceReady;
  const result = await signInWithCredential(auth, credential);
  window.localStorage.removeItem(SIGNED_OUT_STORAGE_KEY);
  await result.user.getIdToken(true);
  return result.user;
}

export async function finalizeExistingAccountCollision(user: User) {
  await syncPrivateProfile(user);
  await addDoc(collection(db, 'accountAuditEvents'), {
    ...collisionAuditData(user.uid),
    occurredAt: serverTimestamp(),
  });
}

export type PrivateProfile = {
  status: string;
  privateGoogleName: string;
  privateGoogleEmail: string;
  schemaVersion: string;
};

export async function syncPrivateProfile(user: User): Promise<PrivateProfile> {
  if (!isGoogleLinked(user.providerData.map((provider) => provider.providerId))) {
    throw new Error('GOOGLE_LINK_REQUIRED');
  }
  const idToken = await user.getIdToken(true);
  const response = await fetch(`${API_URL}/api/profile/sync`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${idToken}` },
  });
  const result = await response.json() as PrivateProfile & { errorCode?: string; message?: string };
  if (!response.ok) {
    const error = new Error(result.message ?? `Profile sync failed (${response.status})`) as Error & { code?: string };
    error.code = response.status >= 500 ? 'unavailable' : result.errorCode ?? 'unknown';
    throw error;
  }
  return result;
}

export async function signOutWithoutStartingAnonymousWork() {
  await authPersistenceReady;
  window.localStorage.setItem(SIGNED_OUT_STORAGE_KEY, 'true');
  await signOut(auth);
}
