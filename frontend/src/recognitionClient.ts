import { API_URL } from './apiConfig';

export type PublicRecognitionPanel = {
  status: string;
  monthKey: string;
  monthLabel: string;
  names: string[];
  message: string;
  schemaVersion: string;
};

export type RecognitionSettings = {
  status: string;
  publicDisplayName: string;
  recognitionStatus: 'PRIVATE' | 'OPTED_IN' | 'WITHDRAWN';
  recognitionActive: boolean;
  schemaVersion: string;
};

export type ContributionBreakdown = {
  contributionType: string;
  lifetimePoints: number;
  currentMonthPoints: number;
  lifetimeAwards: number;
};

export type PrivatePointsSummary = {
  status: string;
  lifetimePoints: number;
  currentMonthPoints: number;
  monthLabel: string;
  breakdown: ContributionBreakdown[];
  ledgerSchemaVersion: string;
  rewardPolicyVersion: string;
};

type ApiError = { errorCode?: string; message?: string };

async function json<T>(response: Response): Promise<T> {
  const body = await response.json() as T & ApiError;
  if (!response.ok) throw new Error(body.message ?? `Request failed (${response.status})`);
  return body;
}

const authorization = (token: string) => ({ Authorization: `Bearer ${token}` });

export async function fetchCurrentRecognition() {
  return json<PublicRecognitionPanel>(await fetch(`${API_URL}/api/recognition/current`));
}

export async function fetchPrivatePoints(token: string) {
  return json<PrivatePointsSummary>(await fetch(`${API_URL}/api/recognition/me/points`, {
    headers: authorization(token),
  }));
}

export async function fetchRecognitionSettings(token: string) {
  return json<RecognitionSettings>(await fetch(`${API_URL}/api/recognition/me/settings`, {
    headers: authorization(token),
  }));
}

export async function saveRecognitionSettings(
  token: string,
  publicDisplayName: string,
  recognitionActive: boolean,
) {
  return json<RecognitionSettings>(await fetch(`${API_URL}/api/recognition/me/settings`, {
    method: 'PUT',
    headers: { ...authorization(token), 'Content-Type': 'application/json' },
    body: JSON.stringify({ publicDisplayName, recognitionActive }),
  }));
}

export async function reportRecognitionName(
  token: string,
  targetPosition: number,
  targetDisplayName: string,
  reason: string,
  details: string,
) {
  return json<{ status: string; message: string }>(await fetch(`${API_URL}/api/recognition/reports`, {
    method: 'POST',
    headers: { ...authorization(token), 'Content-Type': 'application/json' },
    body: JSON.stringify({ targetPosition, targetDisplayName, reason, details }),
  }));
}
