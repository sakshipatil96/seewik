import assert from 'node:assert/strict';
import test from 'node:test';
import {
  GUEST_COMPLAINT_PREVIEW_KEY,
  readGuestComplaintPreview,
  removeGuestComplaintPreview,
  writeGuestComplaintPreview,
} from '../src/guestComplaintPreview.ts';

function memoryStorage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
}

const preview = {
  schemaVersion: 'guest-complaint-preview-v0.1',
  ownerUid: 'anonymous-1',
  routeId: 'route-1',
  packVersion: 'v0.2',
  prabhagId: 'PRABHAG-01',
  method: 'PRINT',
  language: 'EN',
  draftVersion: 'complaint-draft-v0.1',
  complaintSchemaVersion: 'complaint-draft-v0.1',
  authority: 'Nandurbar Municipal Council',
  authorityLocalName: 'नंदुरबार नगरपालिका',
  subject: 'Road repair request',
  body: 'Please inspect and repair the reported road damage.',
};

const identity = {
  ownerUid: preview.ownerUid,
  routeId: preview.routeId,
  packVersion: preview.packVersion,
  prabhagId: preview.prabhagId,
  method: preview.method,
  language: preview.language,
};

test('guest complaint preview survives a same-owner session refresh', () => {
  const storage = memoryStorage();
  writeGuestComplaintPreview(storage, preview);
  assert.deepEqual(readGuestComplaintPreview(storage, identity), preview);
});

test('guest complaint preview cannot cross owner, route, method, or language boundaries', () => {
  const storage = memoryStorage();
  writeGuestComplaintPreview(storage, preview);
  assert.equal(readGuestComplaintPreview(storage, { ...identity, ownerUid: 'anonymous-2' }), null);
  assert.equal(readGuestComplaintPreview(storage, { ...identity, routeId: 'route-2' }), null);
  assert.equal(readGuestComplaintPreview(storage, { ...identity, method: 'EMAIL' }), null);
  assert.equal(readGuestComplaintPreview(storage, { ...identity, language: 'MR' }), null);
});

test('guest complaint preview rejects old formats and clears explicitly', () => {
  const storage = memoryStorage();
  storage.setItem(GUEST_COMPLAINT_PREVIEW_KEY, JSON.stringify({ ...preview, schemaVersion: 'guest-complaint-preview-v0.0' }));
  assert.equal(readGuestComplaintPreview(storage, identity), null);
  writeGuestComplaintPreview(storage, preview);
  removeGuestComplaintPreview(storage);
  assert.equal(readGuestComplaintPreview(storage, identity), null);
});
