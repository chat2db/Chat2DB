import type { FileUrl } from '@/components/UploadLocalFile';
import type { ImportTaskParams } from '@/service/importExport';
import { stageSelectedImportFile } from '../ImportMappingContent/fileStaging';

type UploadBrowserFile = (params: { file: File }) => Promise<string>;
type StageDesktopFile = (params: { sourceFile: string; originalFileName: string }) => Promise<string>;

export const prepareImportParams = async (
  params: ImportTaskParams,
  file: FileUrl,
  uploadBrowserFile: UploadBrowserFile,
  stageDesktopFile: StageDesktopFile,
): Promise<ImportTaskParams> => ({
  ...params,
  sourceFile: undefined,
  fileId: await stageSelectedImportFile(file, uploadBrowserFile, stageDesktopFile),
  displayFileName: file.fileName || file.file?.name,
});
