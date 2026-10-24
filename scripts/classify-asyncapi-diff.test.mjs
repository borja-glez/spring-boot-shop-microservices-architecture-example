// Run with `node --test scripts/`. The fixtures in scripts/fixtures/asyncapi-diff are outputs of
// `asyncapi diff --type all --format json` (AsyncAPI CLI 6.2.0) for a payload schema edited in the
// way the file name says; the last two are hand-written malformed outputs.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

import { locate } from './classify-asyncapi-diff.mjs';

const script = fileURLToPath(new URL('./classify-asyncapi-diff.mjs', import.meta.url));
const fixture = (name) =>
  fileURLToPath(new URL(`./fixtures/asyncapi-diff/${name}.json`, import.meta.url));

function verdict(name) {
  return spawnSync(process.execPath, [script, fixture(name)], { encoding: 'utf8' }).status;
}

for (const name of [
  'remove-property',
  'remove-property-named-title',
  'change-type',
  'add-required',
  'narrow-enum',
]) {
  test(`${name} is rejected`, () => assert.equal(verdict(name), 1));
}

for (const name of ['add-optional-property', 'add-property-named-required', 'edit-description']) {
  test(`${name} is accepted`, () => assert.equal(verdict(name), 0));
}

for (const name of ['unexpected-shape', 'change-without-path', 'does-not-exist']) {
  test(`${name} fails closed`, () => assert.equal(verdict(name), 1));
}

test('a property named like a keyword is a property, not a keyword', () => {
  const base = '/channels/ch/messages/m/payload';
  assert.deepEqual(locate(`${base}/properties/required`), { kind: 'property', name: 'required' });
  assert.deepEqual(locate(`${base}/properties/properties`), {
    kind: 'property',
    name: 'properties',
  });
  assert.deepEqual(locate(`${base}/properties/properties/required`), {
    kind: 'keyword',
    keyword: 'required',
  });
  assert.deepEqual(locate(`${base}/required/2`), { kind: 'keyword', keyword: 'required' });
  assert.deepEqual(locate(`${base}/items/properties/id`), { kind: 'property', name: 'id' });
  assert.equal(locate('/info/title'), null);
});
