import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import ts from 'typescript';
import type { IDashboardItem, IPageParams } from '@/typings';
import type { useDashboardStore } from '../../store';

type DashboardPage = { data: IDashboardItem[]; hasNextPage: boolean };
type FetchPage = (params: IPageParams) => Promise<DashboardPage>;
const clientRoot = process.cwd();
const requirePackage = createRequire(path.join(clientRoot, 'package.json'));
const dashboard = (id: number, name = `Dashboard ${id}`): IDashboardItem => ({ id, name });
const page = (data: IDashboardItem[], hasNextPage = false): DashboardPage => ({ data, hasNextPage });

// Run the production Zustand store, replacing network and UI side effects only.
function createStore(fetchPage: FetchPage) {
  const selectedIds: number[] = [];
  const modules = new Map<string, { exports: unknown }>();
  const load = (filename: string): unknown => {
    const cached = modules.get(filename);
    if (cached) return cached.exports;
    const module = { exports: {} as unknown };
    modules.set(filename, module);
    const code = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, esModuleInterop: true },
      fileName: filename,
    }).outputText;
    const requireSource = (name: string): unknown => {
      if (name === '@/service/dashboard')
        return {
          getDashboardList: fetchPage,
          getDashboardById: async ({ id }: { id: number }) => {
            selectedIds.push(id);
            return dashboard(id);
          },
        };
      if (name === '@/i18n') return (key: string) => key;
      if (name === '@chat2db/ui') return { staticMessage: { success() {}, error() {} } };
      if (name === '@/utils/dashboard') return {};
      if (!name.startsWith('.') && !name.startsWith('@/')) return requirePackage(name);
      const resolved = name.startsWith('@/')
        ? path.join(clientRoot, 'src', name.slice(2))
        : path.resolve(path.dirname(filename), name);
      return load(fs.existsSync(`${resolved}.ts`) ? `${resolved}.ts` : path.join(resolved, 'index.ts'));
    };
    new Function('require', 'module', 'exports', code)(requireSource, module, module.exports);
    return module.exports;
  };
  const store = (
    load(path.join(clientRoot, 'src/store/dashboard/store.ts')) as {
      useDashboardStore: typeof useDashboardStore;
    }
  ).useDashboardStore;
  return { store, selectedIds };
}

test('concurrent list entry points request each page only once', async () => {
  let resolve!: (value: DashboardPage) => void;
  const pending = new Promise<DashboardPage>((done) => {
    resolve = done;
  });
  let requests = 0;
  const { store, selectedIds } = createStore(() => {
    requests += 1;
    return pending;
  });
  const first = store.getState().queryDashboardList();
  const duplicate = store.getState().queryDashboardList();
  assert.equal(store.getState().dashboardListStatus, 'loading');
  assert.equal(requests, 1);
  resolve(page([dashboard(1), dashboard(2)], true));
  await Promise.all([first, duplicate]);
  assert.deepEqual(
    store.getState().dashboardList.map((item) => item.id),
    [1, 2],
  );
  assert.equal(store.getState().dashboardListParams.pageNo, 2);
  assert.equal(store.getState().dashboardListStatus, 'success');
  assert.deepEqual(selectedIds, [1]);
});

test('initial failure settles and retry requests the same first page', async () => {
  const requestedPages: number[] = [];
  const { store } = createStore(async ({ pageNo }) => {
    requestedPages.push(pageNo);
    if (requestedPages.length === 1) throw new Error('HTTP 500');
    return page([dashboard(1)]);
  });
  await assert.doesNotReject(store.getState().queryDashboardList());
  assert.equal(store.getState().dashboardListStatus, 'error');
  assert.equal(store.getState().dashboardListParams.pageNo, 1);
  assert.deepEqual(store.getState().dashboardList, []);
  await store.getState().queryDashboardList();
  assert.deepEqual(requestedPages, [1, 1]);
  assert.equal(store.getState().dashboardListStatus, 'success');
  assert.equal(store.getState().dashboardListParams.hasNextPage, false);
});

test('later page failure preserves existing rows and retries without skipping a page', async () => {
  const requestedPages: number[] = [];
  const { store } = createStore(async ({ pageNo }) => {
    requestedPages.push(pageNo);
    if (requestedPages.length === 2) throw new Error('offline');
    return page([dashboard(pageNo)], pageNo === 1);
  });
  await store.getState().queryDashboardList();
  await store.getState().queryDashboardList();
  assert.equal(store.getState().dashboardListStatus, 'error');
  assert.deepEqual(
    store.getState().dashboardList.map((item) => item.id),
    [1],
  );
  assert.equal(store.getState().dashboardListParams.pageNo, 2);
  assert.equal(store.getState().dashboardListParams.hasNextPage, true);
  await store.getState().queryDashboardList();
  assert.deepEqual(requestedPages, [1, 2, 2]);
  assert.deepEqual(
    store.getState().dashboardList.map((item) => item.id),
    [1, 2],
  );
  assert.equal(store.getState().dashboardListStatus, 'success');
});

test('an empty successful page is settled and does not keep requesting', async () => {
  let requests = 0;
  const { store } = createStore(async () => {
    requests += 1;
    return page([]);
  });
  await store.getState().queryDashboardList();
  await store.getState().queryDashboardList();
  assert.equal(requests, 1);
  assert.equal(store.getState().dashboardListStatus, 'success');
  assert.equal(store.getState().dashboardListParams.hasNextPage, false);
});

test('different dashboards with equal titles remain separate across pages', async () => {
  const { store } = createStore(async ({ pageNo }) => page([dashboard(pageNo, 'Same title')], pageNo === 1));
  await store.getState().queryDashboardList();
  await store.getState().queryDashboardList();
  assert.deepEqual(
    store.getState().dashboardList.map((item) => item.id),
    [1, 2],
  );
});

test('an explicit dashboard route keeps control of its selection', async () => {
  const { store, selectedIds } = createStore(async () => page([dashboard(1)]));
  await store.getState().queryDashboardList(42);
  assert.deepEqual(selectedIds, []);
});
