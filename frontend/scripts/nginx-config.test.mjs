// Checks the location blocks of the real nginx template. The gateway serves JavaScript and CSS under
// /api (the contracts page at /api/docs), so the API proxy must win over the static-asset rule: a
// plain prefix location loses to a regex location, a `^~` prefix location does not.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const template = readFileSync(new URL('../nginx/default.conf.template', import.meta.url), 'utf8');

/** The top-level `location` blocks of a server block, in order: modifier, pattern and body. */
function locations(config) {
  const found = [];
  const header = /location\s+(=|\^~|~\*|~)?\s*(\S+)\s*\{/g;
  let match;
  while ((match = header.exec(config)) !== null) {
    let depth = 1;
    let end = header.lastIndex;
    while (depth > 0 && end < config.length) {
      if (config[end] === '{') depth++;
      if (config[end] === '}') depth--;
      end++;
    }
    assert.equal(depth, 0, `unbalanced braces in location ${match[2]}`);
    found.push({
      modifier: match[1] ?? '',
      pattern: match[2],
      body: config.slice(header.lastIndex, end - 1),
    });
    header.lastIndex = end;
  }
  return found;
}

/**
 * The location nginx picks for a URI: an exact match; otherwise the longest matching prefix if it
 * is `^~`; otherwise the first matching regex, in the order of the file; otherwise the longest
 * matching prefix.
 */
function select(blocks, uri) {
  const exact = blocks.find((block) => block.modifier === '=' && block.pattern === uri);
  if (exact) return exact;
  const prefix = blocks
    .filter(
      (block) =>
        (block.modifier === '' || block.modifier === '^~') && uri.startsWith(block.pattern),
    )
    .sort((a, b) => b.pattern.length - a.pattern.length)[0];
  if (prefix?.modifier === '^~') return prefix;
  const regex = blocks.find(
    (block) =>
      (block.modifier === '~' || block.modifier === '~*') &&
      new RegExp(block.pattern, block.modifier === '~*' ? 'i' : '').test(uri),
  );
  return regex ?? prefix;
}

const blocks = locations(template);
const api = blocks.find((block) => block.pattern === '/api/');
const staticAssets = blocks.find((block) => block.modifier === '~*');

test('the /api/ location is a ^~ prefix that proxies to the gateway', () => {
  assert.ok(api, 'no location for /api/');
  assert.equal(api.modifier, '^~');
  assert.match(api.body, /proxy_pass\s+\$\{GATEWAY_URL\};/);
});

test('the static-asset regex location serves files and proxies nothing', () => {
  assert.ok(staticAssets, 'no regex location for static assets');
  assert.doesNotMatch(staticAssets.body, /proxy_pass/);
});

test('assets under /api go to the gateway, other assets and routes stay in the SPA', () => {
  assert.equal(select(blocks, '/api/docs/webjars/x.js'), api);
  assert.equal(select(blocks, '/api/v1/catalog/products'), api);
  assert.equal(select(blocks, '/main-4f3a.js'), staticAssets);
  assert.equal(select(blocks, '/healthz').pattern, '/healthz');
  assert.equal(select(blocks, '/orders/42').pattern, '/');
});

test('without ^~ the static-asset regex would take the assets under /api', () => {
  const plainPrefix = locations(template.replace('location ^~ /api/', 'location /api/'));

  assert.equal(select(plainPrefix, '/api/docs/webjars/x.js').modifier, '~*');
});
