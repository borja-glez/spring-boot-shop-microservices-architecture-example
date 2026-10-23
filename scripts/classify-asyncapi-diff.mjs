// Decides whether the JSON output of `asyncapi diff` (bundled documents, so the external payload
// schemas are inlined) contains a breaking change. The diff classifies structural changes of the
// AsyncAPI document itself, but reports every change inside a message payload schema as
// "unclassified", so this script applies one rule to those:
//   - the following unclassified changes are accepted: adding a property to a schema, and editing
//     documentation (description, title, example(s), deprecated, x- extensions);
//   - any other unclassified change is treated as breaking: removing a property, changing a type,
//     format or constraint, adding or removing a required entry, changing an enum.
// Usage: node classify-asyncapi-diff.mjs <diff.json>
import { readFileSync } from "node:fs";

const { changes = [] } = JSON.parse(readFileSync(process.argv[2], "utf8"));

function accepted(change) {
  if (change.type === "non-breaking") {
    return true;
  }
  if (change.type !== "unclassified") {
    return false;
  }
  const segments = change.path.split("/");
  const last = segments.at(-1);
  if (change.action === "add" && segments.at(-2) === "properties") {
    return true;
  }
  return (
    /^(description|title|example|examples|deprecated|externalDocs)$/.test(
      last,
    ) || last.startsWith("x-")
  );
}

const rejected = changes.filter((change) => !accepted(change));
for (const change of rejected) {
  console.error(
    `  ${change.type} ${change.action} ${change.path}` +
      (change.before !== undefined
        ? ` before=${JSON.stringify(change.before)}`
        : "") +
      (change.after !== undefined
        ? ` after=${JSON.stringify(change.after)}`
        : ""),
  );
}
process.exit(rejected.length > 0 ? 1 : 0);
