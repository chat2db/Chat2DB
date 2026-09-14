import assert from 'node:assert/strict';
import { ImportExportFileType, ImportExportTaskType } from '@/constants/importExport';
import { prepareImportParams } from './submission';

async function run() {
  const file = { name: 'users.json' } as File;
  const params: import('@/service/importExport').ImportTaskParams = {
    dataSourceId: 1,
    databaseName: 'app',
    tableName: 'users',
    taskType: ImportExportTaskType.DATA_FILE_IMPORT,
    format: ImportExportFileType.JSON,
    sourceFile: 'untrusted-path',
  };
  const browser = await prepareImportParams(params, { file }, async ({ file: uploaded }) => {
    assert.equal(uploaded, file);
    return 'browser-file-id';
  }, async () => { throw new Error('Browser uploads must not stage local paths'); });
  assert.deepEqual(browser, {
    ...params, sourceFile: undefined, fileId: 'browser-file-id', displayFileName: 'users.json',
  });

  const desktop = await prepareImportParams(params,
    { filePath: '/imports/users.json', fileName: 'users.json' },
    async () => { throw new Error('Desktop selections must not upload a browser File'); },
    async (request) => {
      assert.deepEqual(request, { sourceFile: '/imports/users.json', originalFileName: 'users.json' });
      return 'desktop-file-id';
    });
  assert.deepEqual(desktop, {
    ...params, sourceFile: undefined, fileId: 'desktop-file-id', displayFileName: 'users.json',
  });
  await assert.rejects(prepareImportParams(params, {},
    async () => { throw new Error('Unexpected upload'); },
    async () => { throw new Error('Unexpected local staging'); }), /selection is incomplete/);
}

run().catch((error) => { console.error(error); process.exitCode = 1; });
