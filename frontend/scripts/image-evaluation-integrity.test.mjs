import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const caseSetUrl = new URL('../../data/eval/classification-image-cases-v0.1-draft.json', import.meta.url);
const run1Url = new URL('../../data/eval/results/classification-image-results-2026-08-28-day9-set5-run1.ndjson', import.meta.url);
const run2Url = new URL('../../data/eval/results/classification-image-results-2026-08-28-day9-set5-run2.ndjson', import.meta.url);
const repeatabilitySummaryUrl = new URL('../../data/eval/results/classification-image-repeatability-summary-2026-08-28-day9-set5.json', import.meta.url);
const backendSourceUrl = new URL('../../backend/src/main/java/com/seewik/api/CivicClassificationService.java', import.meta.url);
const frontendSourceUrl = new URL('../src/main.tsx', import.meta.url);
const caseSet = JSON.parse(await readFile(caseSetUrl, 'utf8'));
const run1 = (await readFile(run1Url, 'utf8')).trim().split('\n').map(JSON.parse);
const run2 = (await readFile(run2Url, 'utf8')).trim().split('\n').map(JSON.parse);
const repeatabilitySummary = JSON.parse(await readFile(repeatabilitySummaryUrl, 'utf8'));
const backendSource = await readFile(backendSourceUrl, 'utf8');
const frontendSource = await readFile(frontendSourceUrl, 'utf8');

function canonicalJson(value) {
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  if (value !== null && typeof value === 'object') {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

test('Track B image cases were frozen separately before scored calls', () => {
  assert.equal(caseSet.case_set_version, 'classification-image-cases-v0.1-draft');
  assert.equal(caseSet.track, 'TRACK_B_REAL_PRIVATE_IMAGES');
  assert.equal(caseSet.status, 'FROZEN_BEFORE_SCORED_RUN');
  assert.equal(caseSet.prompt_version, 'classification-prompt-v0.1');
  assert.equal(caseSet.schema_version, 'classification-v0.1');
  assert.equal(caseSet.pack_version, 'v0.2');
  assert.equal(caseSet.policy_version, 'evaluation-policy-v0.1');
  assert.equal(caseSet.model_version, 'gemini-3.7-flash');
  assert.equal(caseSet.scoring_version, 'image-classification-scoring-v0.1');
  assert.equal(caseSet.input_contract.image_only, true);
  assert.equal(caseSet.input_contract.text_hints, false);
  assert.equal(caseSet.input_contract.silent_retries, false);
  assert.equal(caseSet.cases.length, 8);

  const digest = createHash('sha256').update(canonicalJson(caseSet.cases)).digest('hex');
  assert.equal(digest, caseSet.cases_sha256);
});

test('private image metadata is complete, sanitized, resolved, and safe to commit', () => {
  assert.equal(caseSet.privacy_contract.raw_photographs_in_git, false);
  assert.equal(caseSet.privacy_contract.sanitized_photographs_in_git, false);
  assert.equal(caseSet.privacy_contract.private_identifiers_in_git, false);
  assert.equal(caseSet.provenance.location, 'Nandurbar');
  assert.equal(caseSet.provenance.permission, 'CONFIRMED_FOR_PRIVATE_EVALUATION_AND_PRIVACY_SANITIZATION');
  assert.equal(caseSet.provenance.prior_model_test_status, 'UNTOUCHED_BEFORE_THIS_SCORED_SET');
  assert.equal(caseSet.label_review.status, 'CONFIRMED_BY_3_TO_4_PEOPLE');
  assert.equal(caseSet.label_review.unresolved_cases, 0);

  const ids = new Set();
  const privateRefs = new Set();
  const categoryCounts = new Map();
  for (const imageCase of caseSet.cases) {
    assert.equal(ids.has(imageCase.case_id), false);
    ids.add(imageCase.case_id);
    assert.match(imageCase.case_id, /^TB-IMG-\d{3}$/);
    assert.equal(privateRefs.has(imageCase.private_image_ref), false);
    privateRefs.add(imageCase.private_image_ref);
    assert.match(imageCase.private_image_ref, /^TB-IMG-\d{3}\.jpg$/);
    assert.match(imageCase.sanitized_image_sha256, /^[a-f0-9]{64}$/);
    assert.ok(imageCase.sanitized_bytes > 0 && imageCase.sanitized_bytes <= 5 * 1024 * 1024);
    assert.equal(imageCase.mime_type, 'image/jpeg');
    assert.ok(imageCase.pixel_width > 0 && imageCase.pixel_height > 0);
    assert.equal(imageCase.provenance_status, 'CONFIRMED_NANDURBAR');
    assert.equal(imageCase.permission_status, 'CONFIRMED');
    assert.match(imageCase.privacy_review_status, /^PASSED_/);
    assert.equal(imageCase.label_review_status, 'CONFIRMED_BY_3_TO_4_PEOPLE');
    assert.equal(imageCase.scored, true);
    assert.equal(Object.hasOwn(imageCase, 'input_text'), false);
    assert.equal(Object.hasOwn(imageCase, 'original_filename'), false);
    categoryCounts.set(imageCase.expected_issueType, (categoryCounts.get(imageCase.expected_issueType) ?? 0) + 1);
  }

  assert.deepEqual(Object.fromEntries(categoryCounts), {
    GARBAGE_SOLID_WASTE: 3,
    POTHOLE_ROAD_DAMAGE: 2,
    DRAINAGE_SEWAGE: 3,
  });
});

test('application input contract supports the frozen image formats and size ceiling', () => {
  assert.deepEqual(caseSet.input_contract.allowed_mime_types, ['image/jpeg', 'image/png', 'image/webp']);
  assert.equal(caseSet.input_contract.max_bytes, 5 * 1024 * 1024);
  assert.match(backendSource, /MAX_IMAGE_BYTES = 5 \* 1024 \* 1024/);
  assert.match(backendSource, /Set\.of\("image\/jpeg", "image\/png", "image\/webp"\)/);
  assert.match(frontendSource, /accept="image\/jpeg,image\/png,image\/webp"/);
});

test('Track B reports category correctness separately from image-path schema validity', () => {
  const allResults = [...run1, ...run2];
  const validResponses = allResults.filter(({ failureClass }) => failureClass === 'NONE');
  const schemaFailures = allResults.filter(({ failureClass }) => failureClass === 'SCHEMA');

  assert.equal(allResults.length, 16);
  assert.equal(validResponses.length, 12);
  assert.equal(validResponses.every(({ correct }) => correct), true);
  assert.equal(schemaFailures.length, 4);
  assert.deepEqual(schemaFailures.map(({ case_id }) => case_id), [
    'TB-IMG-002',
    'TB-IMG-006',
    'TB-IMG-004',
    'TB-IMG-006',
  ]);

  assert.equal(repeatabilitySummary.categoryCorrectOnValidResponses, 12);
  assert.equal(repeatabilitySummary.categoryAccuracyOnValidResponses, 1);
  assert.equal(repeatabilitySummary.schemaValidity, 0.75);
  assert.equal(repeatabilitySummary.misclassifications, 0);
  assert.deepEqual(repeatabilitySummary.confusionPairs, []);
  assert.equal(repeatabilitySummary.namedFailureMode.code, 'IMAGE_PATH_SCHEMA_VALIDATION_INSTABILITY');
  assert.deepEqual(repeatabilitySummary.namedFailureMode.repeatedCases, [
    { caseId: 'TB-IMG-006', occurrences: 2 },
  ]);
  assert.equal(repeatabilitySummary.namedFailureMode.rejectedProviderPayloadAvailable, false);
});
