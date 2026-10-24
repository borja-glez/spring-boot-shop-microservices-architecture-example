// Fails when the generated API types differ from the index, or when a generated file is untracked:
// the types must be regenerated and committed together with the spec. Comparing with the index
// lets `git add` settle the check before the commit; on a clean checkout (CI) the index is HEAD.
import { execFileSync } from 'node:child_process';

const generated = 'src/app/core/api/generated';

function git(...args) {
  return execFileSync('git', args, { encoding: 'utf8' });
}

let changed = '';
try {
  git('diff', '--exit-code', '--', generated);
} catch (error) {
  changed = error.stdout ?? 'the generated API types changed';
}
const untracked = git('ls-files', '--others', '--exclude-standard', '--', generated).trim();

if (changed || untracked) {
  console.error(changed);
  if (untracked) {
    console.error(`Untracked generated files:\n${untracked}`);
  }
  console.error(
    'Generated API types are out of date or not staged: run "npm run gen:api", then stage ' +
      `(git add) and commit ${generated}.`,
  );
  process.exit(1);
}
console.log('Generated API types are up to date.');
