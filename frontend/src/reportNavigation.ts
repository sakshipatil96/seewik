export type AppScreen = 'home' | 'new-report' | 'review' | 'reports' | 'report-detail' | 'points';

const SCREEN_PATHS: Record<AppScreen, string> = {
  home: '/',
  'new-report': '/report/new',
  review: '/report/review',
  reports: '/reports',
  'report-detail': '/reports/current',
  points: '/points',
};

export function pathForScreen(screen: AppScreen, reportId?: string) {
  if (screen === 'report-detail' && reportId) return `/reports/${encodeURIComponent(reportId)}`;
  if (screen === 'review' && reportId) return `/report/review?report=${encodeURIComponent(reportId)}`;
  return SCREEN_PATHS[screen];
}

export function screenFromPath(pathname: string): AppScreen {
  if (/^\/reports\/[^/]+$/.test(pathname) && pathname !== SCREEN_PATHS['report-detail']) return 'report-detail';
  return (Object.entries(SCREEN_PATHS).find(([, path]) => path === pathname)?.[0] as AppScreen | undefined) ?? 'home';
}

export function reportIdFromPath(pathname: string) {
  const match = pathname.match(/^\/reports\/([^/]+)$/);
  return match && match[1] !== 'current' ? decodeURIComponent(match[1]) : null;
}

export function reportIdFromReviewSearch(search: string) {
  return new URLSearchParams(search).get('report');
}

export function canEditReport(status: string) {
  return status === 'DRAFT';
}

export function canResumeReport(status: string) {
  return status === 'DRAFT';
}

export type DraftRouteIdentity = { issueType: string; prabhagId: string; routeId: string; packVersion: string };

export function draftRouteIsCurrent(draft: DraftRouteIdentity, current: DraftRouteIdentity) {
  return draft.issueType === current.issueType
    && draft.prabhagId === current.prabhagId
    && draft.routeId === current.routeId
    && draft.packVersion === current.packVersion;
}
