// Generates src/app/core/api/generated/<spec>.d.ts for every OpenAPI contract of a service or of
// service-support, with openapi-typescript. The component libraries in platform/api-contracts
// are referenced by those contracts and are not generated on their own.
import { execFileSync } from 'node:child_process';
import { mkdirSync, readdirSync, rmSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repository = resolve(frontend, '..');
const output = join(frontend, 'src/app/core/api/generated');

function specs(directory) {
  try {
    return readdirSync(directory, { withFileTypes: true })
      .filter((entry) => entry.isFile() && entry.name.endsWith('.yaml'))
      .map((entry) => join(directory, entry.name));
  } catch (error) {
    if (error.code === 'ENOENT') {
      return [];
    }
    throw error;
  }
}

const serviceDirectories = readdirSync(join(repository, 'services'), { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .map((entry) => join(repository, 'services', entry.name, 'src/main/openapi'));
const found = [
  ...serviceDirectories,
  join(repository, 'platform/service-support/src/main/openapi'),
]
  .flatMap(specs)
  .sort();

// Start from an empty directory so the types of a removed spec disappear too.
rmSync(output, { recursive: true, force: true });
mkdirSync(output, { recursive: true });

const cli = join(frontend, 'node_modules/openapi-typescript/bin/cli.js');
for (const spec of found) {
  const name = basename(spec, '.yaml');
  execFileSync(process.execPath, [cli, spec, '--output', join(output, `${name}.d.ts`)], {
    stdio: 'inherit',
  });
}
console.log(`Generated types for ${found.length} OpenAPI contract(s).`);
