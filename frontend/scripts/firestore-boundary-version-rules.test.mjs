import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const rules = await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8')

test('report drafts require the frozen boundary provenance fields', () => {
  assert.match(rules, /'boundaryDatasetVersion', 'resolutionMethod'/)
  assert.match(
    rules,
    /data\.boundaryDatasetVersion == 'seewik-map-trace-v0\.2'/,
  )
  assert.match(rules, /data\.resolutionMethod is string/)
})

test('legacy drafts may add boundary provenance during an allowed update', () => {
  assert.match(
    rules,
    /affectedKeys\(\)[\s\S]*?'boundaryDatasetVersion',[\s\S]*?'resolutionMethod'/,
  )
})
