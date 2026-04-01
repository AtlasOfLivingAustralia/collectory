/**
 * Converts Java .properties i18n files to i18next JSON format.
 *
 * Usage: npx tsx scripts/convert-i18n.ts
 *
 * Reads from: ../../grails-app/i18n/messages*.properties
 * Writes to:  ./src/i18n/locales/{lang}/translation.json
 */
import { readFileSync, writeFileSync, mkdirSync, readdirSync } from 'fs';
import { join, basename } from 'path';

const I18N_SOURCE_DIR = join(__dirname, '../../grails-app/i18n');
const OUTPUT_DIR = join(__dirname, '../public/locales');

function parseProperties(content: string): Record<string, string> {
  const result: Record<string, string> = {};
  const lines = content.split('\n');

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('!')) continue;

    const eqIdx = trimmed.indexOf('=');
    if (eqIdx === -1) continue;

    const key = trimmed.substring(0, eqIdx).trim();
    let value = trimmed.substring(eqIdx + 1).trim();

    // Convert Java MessageFormat {0}, {1} to i18next {{0}}, {{1}}
    value = value.replace(/\{(\d+)\}/g, '{{$1}}');

    result[key] = value;
  }

  return result;
}

function localeFromFilename(filename: string): string {
  // messages.properties -> en
  // messages_pt_BR.properties -> pt-BR
  // messages_zh_CN.properties -> zh-CN
  const match = filename.match(/^messages_?(.*)\.properties$/);
  if (!match || !match[1]) return 'en';
  return match[1]!.replace('_', '-');
}

function main() {
  const files = readdirSync(I18N_SOURCE_DIR).filter((f) => f.startsWith('messages') && f.endsWith('.properties'));

  console.log(`Found ${files.length} i18n files to convert`);

  for (const file of files) {
    const locale = localeFromFilename(file);
    const content = readFileSync(join(I18N_SOURCE_DIR, file), 'utf-8');
    const translations = parseProperties(content);

    const outDir = join(OUTPUT_DIR, locale);
    mkdirSync(outDir, { recursive: true });
    writeFileSync(join(outDir, 'translation.json'), JSON.stringify(translations, null, 2));

    console.log(`  ${file} -> ${locale} (${Object.keys(translations).length} keys)`);
  }

  console.log('Done!');
}

main();
