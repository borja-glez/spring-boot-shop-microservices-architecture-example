// Fails when the generated API types differ from the ones in the index or when a generated
// file is not tracked by git: run `npm run gen:api` and commit the result.
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
  console.error(`${generated} is out of date: run "npm run gen:api" and commit the result.`);
  process.exit(1);
}
console.log('Generated API types are up to date.');
