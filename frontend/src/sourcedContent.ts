import civicAwarenessDocument from './content/civic-awareness-v0.1.json';
import emergencyInformationDocument from './content/emergency-information-v0.1.json';

export type ContentSource = {
  id: string;
  title: string;
  url: string;
  authority: string;
  sourceDate: string | null;
  lastReviewed: string;
  reviewExpiresOn: string;
};

export type AwarenessSection = {
  label: string;
  text: string;
  highlight?: boolean;
  links?: string[];
};

export type AwarenessTopic = {
  id: string;
  topic: string;
  language: 'en' | 'mr' | 'hi';
  jurisdiction: string;
  status: 'REVIEWED' | 'REVIEW_REQUIRED' | 'ARCHIVED';
  heading: string;
  summary: string;
  sections: AwarenessSection[];
  limitations: string[];
  sources: ContentSource[];
};

export type EmergencyContact = {
  id: string;
  group: string;
  label: string;
  description: string;
  displayNumber: string;
  telephoneNumber: string;
  sourceId: string;
};

export type EmergencyTopic = Omit<AwarenessTopic, 'sections'> & { sections: EmergencyContact[] };

export const civicAwarenessContent = civicAwarenessDocument as {
  schemaVersion: string;
  contentVersion: string;
  defaultLanguage: string;
  reviewedDate: string;
  jurisdiction: string;
  topics: AwarenessTopic[];
};

export const emergencyInformationContent = emergencyInformationDocument as unknown as {
  schemaVersion: string;
  contentVersion: string;
  defaultLanguage: string;
  reviewedDate: string;
  jurisdiction: string;
  topics: EmergencyTopic[];
};

export function isSourceCurrent(source: ContentSource, now: Date = new Date()) {
  const expiresAt = Date.parse(`${source.reviewExpiresOn}T23:59:59Z`);
  return Number.isFinite(expiresAt) && now.getTime() <= expiresAt;
}

export function isTopicCurrent(topic: AwarenessTopic | EmergencyTopic, now: Date = new Date()) {
  return topic.status === 'REVIEWED' && topic.sources.length > 0 && topic.sources.every((source) => isSourceCurrent(source, now));
}

export function emergencyContactIsCallable(topic: EmergencyTopic, contact: EmergencyContact, now: Date = new Date()) {
  const source = topic.sources.find((item) => item.id === contact.sourceId);
  return Boolean(source && isSourceCurrent(source, now) && /^\+?[0-9]{3,15}$/.test(contact.telephoneNumber));
}
