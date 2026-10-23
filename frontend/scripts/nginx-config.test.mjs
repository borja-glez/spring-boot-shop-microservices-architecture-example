// The API proxy must win over the static-asset rule: the gateway serves JavaScript and CSS under
// /api (the contracts page at /api/docs), and a plain prefix location loses to a regex location.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const config = readFileSync(new URL('../nginx/default.conf.template', import.meta.url), 'utf8');

test('the /api proxy location takes precedence over the static-asset regex', () => {
  assert.match(config, /location \^~ \/api\/ \{/);
});
