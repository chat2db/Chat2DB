import 'umi/typings';
import { IVersionResponse } from '@/typings';

declare module 'monaco-editor/esm/vs/basic-languages/sql/sql';
declare module 'monaco-editor/esm/vs/language/typescript/ts.worker.js';
declare module 'monaco-editor/esm/vs/editor/editor.worker.js';
declare namespace NodeJS {
  interface ProcessEnv {
    readonly NODE_ENV: 'development' | 'production'
    readonly UMI_ENV: string
    readonly __ENV: string;
  }
}

declare global {
  interface Window {
    _Lang: string;
    _APP_PORT: string;
    _BUILD_TIME: string;
    _BaseURL: string;
    _AppThemePack: { [key in string]: string };
    _appGatewayParams: IVersionResponse;
    _notificationApi: any;
    _indexedDB: any;
    electronApi?: {
      startServerForSpawn: () => void;
      quitApp: () => void;
      setBaseURL: (baseUrl: string) => void;
      registerAppMenu: (data: any) => void;
      setForceQuitCode: (code: boolean) => void;
      setMaximize: () => void;
      getPlatform: () => {
        isLinux: boolean,
        isWin: boolean,
        isMac: boolean,
      };
      minimizeWindow: () => void;
      closeWindow: () => void;
      isMaximized: () => boolean;
    };
  }
  const __APP_VERSION__: string;
  const __BUILD_TIME__: string;
  const __ENV__: string;
  const __APP_PORT__: string;
}
