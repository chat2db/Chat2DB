import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { setImmediate } from 'node:timers/promises';
import { test } from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';
import en from '../../i18n/en-US/connection';
import zh from '../../i18n/zh-CN/connection';
import ja from '../../i18n/ja-JP/connection';
import ko from '../../i18n/ko-KR/connection';
import es from '../../i18n/es-ES/connection';

const source = ts.createSourceFile(
  'index.tsx',
  readFileSync(path.join(__dirname, 'index.tsx'), 'utf8'),
  ts.ScriptTarget.Latest,
  true,
  ts.ScriptKind.TSX,
);
const functions: string[] = [];
function visit(node: ts.Node) {
  if (ts.isFunctionDeclaration(node) && ['testSSH', 'getConnectionErrorMessage'].includes(node.name?.text || '')) {
    functions.push(node.getText(source));
  }
  ts.forEachChild(node, visit);
}
visit(source);
assert.equal(functions.length, 2);
const code = ts.transpileModule(functions.join('\n') + '\ntestSSH();', {
  compilerOptions: { target: ts.ScriptTarget.ES2022 },
}).outputText;

async function invoke(error: unknown, locale = zh, successful = false) {
  const errors: string[] = [];
  const successes: string[] = [];
  const loading: boolean[] = [];
  let requests = 0;
  vm.runInNewContext(code, {
    Error,
    sshForm: { getFieldsValue: () => ({ host: 'review.invalid' }) },
    connectionService: {
      testSSH: (params: { host: string }) => {
        assert.equal(params.host, 'review.invalid');
        requests += 1;
        return successful ? Promise.resolve(true) : Promise.reject(error);
      },
    },
    loadings: { sshTestLoading: false },
    setLoading: (state: { sshTestLoading: boolean }) => loading.push(state.sshTestLoading),
    staticMessage: {
      error: (message: string) => errors.push(message),
      success: (message: string) => successes.push(message),
    },
    i18n: (key: keyof typeof zh) => locale[key] || key,
  });
  await setImmediate();
  assert.equal(requests, 1);
  assert.deepEqual(loading, [true, false]);
  assert.equal(successes.length, successful ? 1 : 0);
  return errors;
}

test('successful SSH tests do not show an error', async () => {
  assert.deepEqual(await invoke(undefined, zh, true), []);
});

test('business errors keep the existing global notification without a second toast', async () => {
  assert.deepEqual(await invoke({ errorCode: 'ssh.error', errorMessage: 'Authentication failed' }), []);
});

test('desktop bridge failures keep the existing alert without a second toast', async () => {
  assert.deepEqual(await invoke('Bridge request failed'), []);
});

test('network and HTTP errors show their cause once', async () => {
  for (const message of ['Failed to fetch', 'Request failed with status code 503']) {
    assert.deepEqual(await invoke(new Error(message)), [message]);
  }
});

test('SSH timeouts use the current locale and do not expose the internal request URL', async () => {
  const timeout = 'timeout_error:/api/connection/ssh/pre_connect';
  for (const locale of [zh, en, ja, ko, es, zh]) {
    const message = locale['connection.message.testSshTimeout'];
    assert.ok(message.trim());
    assert.deepEqual(await invoke(timeout, locale), [message]);
    assert.ok(!message.includes('/api/'));
  }
  assert.equal(new Set([zh, en, ja, ko, es].map(locale => locale['connection.message.testSshTimeout'])).size, 5);
});
