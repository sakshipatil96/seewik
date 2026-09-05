export type LinkableUser = {
  uid: string;
  getIdToken(forceRefresh?: boolean): Promise<string>;
};

export async function finalizeLinkedUser<T extends LinkableUser>(
  beforeUid: string,
  user: T,
  syncProfile: (linkedUser: T) => Promise<unknown>,
) {
  if (user.uid !== beforeUid) throw new Error('LINK_CHANGED_UID');
  await user.getIdToken(true);
  await syncProfile(user);
  return user;
}

export function anonymousSessionDecision(hasCurrentUser: boolean, deliberatelySignedOut: boolean) {
  if (hasCurrentUser) return 'REUSE' as const;
  if (deliberatelySignedOut) return 'REJECT' as const;
  return 'CREATE' as const;
}
