// Fails when the generated API types differ from the last commit (HEAD), staged or not, or when a
// generated file is untracked: the types must be regenerated and committed together with the spec.
import { execFileSync } from 'node:child_process';

const generated = 'src/app/core/api/generated';

function git(...args) {
  return execFileSync('git', args, { encoding: 'utf8' });
}

let changed = '';
try {
  git('diff', '--exit-code', 'HEAD', '--', generated);
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
    'Generated API types are out of date or not committed: run "npm run gen:api" and commit ' +
      `${generated}.`,
  );
  process.exit(1);
}
console.log('Generated API types are up to date.');
