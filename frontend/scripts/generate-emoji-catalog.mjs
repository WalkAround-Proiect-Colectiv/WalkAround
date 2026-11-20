import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

const input = process.argv[2];
if (!input) {
  throw new Error('Usage: node scripts/generate-emoji-catalog.mjs <emoji-test.txt>');
}

const source = readFileSync(input, 'utf8');
const version = source.match(/^# Version: (.+)$/m)?.[1];
if (version !== '18.0') {
  throw new Error(`Expected Unicode Emoji 18.0 data, got ${version ?? 'unknown version'}`);
}

const groups = [];
const emojis = [];
let groupIndex = -1;

for (const line of source.split(/\r?\n/)) {
  const group = line.match(/^# group: (.+)$/);
  if (group) {
    groupIndex = groups.push(group[1]) - 1;
    continue;
  }

  const entry = line.match(/^([0-9A-F ]+)\s*;\s*(fully-qualified|component)\s*#\s*\S+\s+E[\d.]+\s+(.+)$/u);
  if (!entry || groupIndex < 0) continue;

  const glyph = String.fromCodePoint(...entry[1].trim().split(/\s+/).map((point) => parseInt(point, 16)));
  emojis.push([glyph, entry[3], groupIndex]);
}

if (groups.length < 8 || emojis.length < 3500) {
  throw new Error(`Incomplete emoji data: ${groups.length} groups, ${emojis.length} entries`);
}

const output = resolve('src/tripplan/data/emojiCatalog.json');
mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, JSON.stringify({ version, groups, emojis }) + '\n', 'utf8');
console.log(`Wrote ${emojis.length} emojis in ${groups.length} groups to ${output}`);
