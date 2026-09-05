export function routeSnapshotHashAfterTransition(
  isFiling: boolean,
  responseHash: string | undefined,
  existingHash: string | undefined,
) {
  return isFiling ? responseHash : existingHash;
}
