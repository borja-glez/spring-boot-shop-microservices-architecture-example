// Decides whether the JSON output of `asyncapi diff` (run on bundled documents, so the external
// payload schemas are inlined) contains a breaking change. The CLI classifies structural changes of
// the AsyncAPI document itself, but reports every change inside a message payload schema as
// "unclassified". This script applies one rule to those:
//   - accepted: a new property added directly under a schema's `properties`, and edits of
//     documentation keywords (description, title, example(s), deprecated, externalDocs, x-*);
//   - rejected, as breaking: anything else, such as a removed property, a changed type, format or
//     constraint, a changed enum, or an added or removed `required` entry.
// Known caveat, by decision: adding a property is accepted even when the schema is closed with
// `additionalProperties: false`, although a consumer that validates strictly would reject the new
// property. Evolve closed payloads with a new message version when consumers validate strictly.
// The classifier fails closed: output that is not a list of changes with an action, a type and a
// path is an error, not an empty diff.
// Usage: node classify-asyncapi-diff.mjs <diff.json>
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

// Keywords whose value is a map from a name to a schema, followed by the name.
const SCHEMA_MAPS = new Set([
  'properties',
  'patternProperties',
  '$defs',
  'definitions',
  'dependentSchemas',
]);
// Keywords whose value is a list of schemas, followed by an index.
const SCHEMA_LISTS = new Set(['allOf', 'anyOf', 'oneOf', 'prefixItems']);
// Keywords whose value is a schema.
const SCHEMA_KEYWORDS = new Set([
  'items',
  'additionalProperties',
  'not',
  'if',
  'then',
  'else',
  'contains',
  'propertyNames',
]);
const DOCUMENTATION = new Set([
  'description',
  'title',
  'example',
  'examples',
  'deprecated',
  'externalDocs',
]);

/**
 * Locates a change inside a message schema. Returns `{ kind: 'property', name }` when the path ends
 * on a property name directly under `properties`, `{ kind: 'keyword', keyword }` when it ends on a
 * keyword of a schema (or deeper inside the value of a keyword), and `null` when the path is not
 * the schema of a message payload or headers.
 */
export function locate(path) {
  const segments = path.split('/').slice(1);
  const messages = segments.indexOf('messages');
  if (messages < 0 || !['payload', 'headers'].includes(segments[messages + 2])) {
    return null;
  }
  let index = messages + 3;
  while (index < segments.length) {
    const keyword = segments[index];
    index += 1;
    if (SCHEMA_MAPS.has(keyword)) {
      if (index >= segments.length) {
        return { kind: 'keyword', keyword };
      }
      const name = segments[index];
      index += 1;
      if (index >= segments.length) {
        return keyword === 'properties' ? { kind: 'property', name } : { kind: 'keyword', keyword };
      }
    } else if (SCHEMA_LISTS.has(keyword)) {
      index += 1;
    } else if (!SCHEMA_KEYWORDS.has(keyword)) {
      return { kind: 'keyword', keyword };
    }
  }
  return { kind: 'keyword', keyword: segments.at(-1) };
}

function isDocumentation(keyword) {
  return DOCUMENTATION.has(keyword) || keyword.startsWith('x-');
}

export function accepted(change) {
  if (change.type === 'non-breaking') {
    return true;
  }
  if (change.type !== 'unclassified') {
    return false;
  }
  const where = locate(change.path);
  if (where === null) {
    return isDocumentation(change.path.split('/').at(-1));
  }
  if (where.kind === 'property') {
    // A new property under `properties`; see the caveat about closed schemas in the header.
    return change.action === 'add';
  }
  return isDocumentation(where.keyword);
}

export function rejectedChanges(diff) {
  if (
    diff === null ||
    typeof diff !== 'object' ||
    !Array.isArray(diff.changes) ||
    !diff.changes.every(
      (change) =>
        change !== null &&
        typeof change === 'object' &&
        typeof change.action === 'string' &&
        typeof change.type === 'string' &&
        typeof change.path === 'string',
    )
  ) {
    throw new Error(
      'unexpected output of asyncapi diff: expected {"changes": [{action, type, path}]}',
    );
  }
  return diff.changes.filter((change) => !accepted(change));
}

function main(file) {
  let rejected;
  try {
    rejected = rejectedChanges(JSON.parse(readFileSync(file, 'utf8')));
  } catch (error) {
    console.error(`  ${error.message}`);
    return 1;
  }
  for (const change of rejected) {
    console.error(
      `  ${change.type} ${change.action} ${change.path}` +
        (change.before !== undefined ? ` before=${JSON.stringify(change.before)}` : '') +
        (change.after !== undefined ? ` after=${JSON.stringify(change.after)}` : ''),
    );
  }
  return rejected.length > 0 ? 1 : 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exit(main(process.argv[2]));
}
