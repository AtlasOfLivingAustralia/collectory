/**
 * Converts collectory-style markup to HTML, matching the Grails cl:formattedText TagLib.
 * - _text_ → <em>text</em>
 * - +text+ → <strong>text</strong>
 * - URLs → <a href="url" target="_blank" rel="noopener noreferrer">url</a>
 * - Lines starting with * → <ul><li>...</li></ul>
 * - [[url|text]] → <a href="url">text</a>
 */
export function formatText(text: string | undefined | null): string {
  if (!text) return '';

  let result = text;

  // Wiki links: [[url|text]] or [[url]]
  result = result.replace(/\[\[([^|\]]+)\|([^\]]+)\]\]/g, '<a href="$1" target="_blank" rel="noopener noreferrer">$2</a>');
  result = result.replace(/\[\[([^\]]+)\]\]/g, '<a href="$1" target="_blank" rel="noopener noreferrer">$1</a>');

  // Bold: +text+
  result = result.replace(/\+([^+]+)\+/g, '<strong>$1</strong>');

  // Italic: _text_
  result = result.replace(/(?<!\w)_([^_]+)_(?!\w)/g, '<em>$1</em>');

  // Auto-link URLs (but not already inside href="...")
  result = result.replace(/(?<!="|'>)(https?:\/\/[^\s<]+)/g, '<a href="$1" target="_blank" rel="noopener noreferrer">$1</a>');

  // List items: lines starting with *
  const lines = result.split('\n');
  let inList = false;
  const processed: string[] = [];
  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed.startsWith('*')) {
      if (!inList) {
        processed.push('<ul>');
        inList = true;
      }
      processed.push(`<li>${trimmed.substring(1).trim()}</li>`);
    } else {
      if (inList) {
        processed.push('</ul>');
        inList = false;
      }
      if (trimmed) {
        processed.push(`<p>${trimmed}</p>`);
      }
    }
  }
  if (inList) processed.push('</ul>');

  return processed.join('\n');
}
