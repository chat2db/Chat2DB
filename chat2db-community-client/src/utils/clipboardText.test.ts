import assert from 'node:assert/strict';
import { normalizeClipboardText } from './clipboardText';
import { getInternalResultGridClipboard, setInternalResultGridClipboard, clearInternalClipboard } from './internalClipboard';

const windowsAgents = [
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
  'Mozilla/5.0 (Windows NT 6.1)',
  'Win32',
];

for (const userAgent of windowsAgents) {
  assert.equal(normalizeClipboardText('1001\n1002\n1003', userAgent), '1001\r\n1002\r\n1003');
  assert.equal(normalizeClipboardText('a\r\nb\nc\rd\n', userAgent), 'a\r\nb\r\nc\r\nd\r\n');
  assert.equal(normalizeClipboardText('a\r\nb', userAgent), 'a\r\nb', 'existing CRLF must not acquire extra CR');
  assert.equal(normalizeClipboardText('中文\t001\n\t002', userAgent), '中文\t001\r\n\t002');
  assert.equal(normalizeClipboardText('single value', userAgent), 'single value');
  assert.equal(normalizeClipboardText('', userAgent), '');
}

for (const userAgent of ['Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)', 'Mozilla/5.0 (X11; Linux x86_64)']) {
  const text = '中文\t001\r\nvalue\nnext\rlast';
  assert.equal(normalizeClipboardText(text, userAgent), text, 'non-Windows copy preserves the original text');
}

const rows = [['first\nsecond', 'value\twith tab'], ['CRLF\r\nvalue', 'CR\rvalue']];
setInternalResultGridClipboard(rows);
const windowsText = normalizeClipboardText(rows.map((row) => row.join('\t')).join('\n'), windowsAgents[0]);
assert.deepEqual(getInternalResultGridClipboard(windowsText), rows, 'Windows paste retains original cell boundaries and values');
assert.deepEqual(getInternalResultGridClipboard(windowsText.replace(/\r\n/g, '\n')), rows, 'browser LF normalization retains grid metadata');
assert.equal(getInternalResultGridClipboard(`${windowsText}x`), null, 'different text must not reuse grid metadata');
clearInternalClipboard();
assert.equal(getInternalResultGridClipboard(windowsText), null);

console.log('Clipboard text and Windows result-grid round-trip tests passed');
