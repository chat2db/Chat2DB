const { contextBridge, ipcRenderer } = require('electron');
const { spawn } = require('child_process');
const { JAVA_APP_NAME, JAVA_PATH } = require('./constants');
const path = require('path');
const { readVersion, isLinux, isWin, isMac } = require('./utils');

contextBridge.exposeInMainWorld('electronApi', {
  startServerForSpawn: async () => {
    const appVersion = readVersion();
    const javaPath = path.join(__dirname, '../..', `./versions/${appVersion}`, `./static/${JAVA_APP_NAME}`);
    const libPath = path.join(__dirname, '../..', `./versions/${appVersion}`, './static/lib');

    const productName = await ipcRenderer.invoke('get-product-name');

    const isTest = productName.match(/test$/i) !== null;

    console.log('productName:', productName, isTest);

    const child = spawn(path.join(__dirname, '../..', `./static/${JAVA_PATH}`), [
      '-noverify',
      `-Dspring.profiles.active=${isTest ? 'test' : 'release'}`,
      '-Dserver.address=127.0.0.1',
      '-Dchat2db.mode=DESKTOP',
      `-Dproject.path=${javaPath}`,
      `-Dloader.path=${libPath}`,
      `-Dclient.version=${appVersion}`,
      '-Xmx1024M',
      '-jar',
      javaPath,
    ]);

    child.stdout.on('data', (buffer) => {
      console.log(buffer.toString('utf8'));
      const data = buffer.toString('utf8');
      if (data.toString().indexOf('Started Application') !== -1) {
        console.log('load success');
      }
    });
    child.stderr.on('data', (data) => {
      console.error(`stderr: ${data}`);
    });
    child.on('close', (code) => {
      console.log(`child process exited with code ${code}`);
    });
  },
  quitApp: () => {
    ipcRenderer.send('quit-app');
  },
  setBaseURL: (baseUrl) => {
    ipcRenderer.send('set-base-url', baseUrl);
  },
  setForceQuitCode: (code) => {
    ipcRenderer.send('set-force-quit-code', !code);
  },
  registerAppMenu: (menuProps) => {
    ipcRenderer.send('register-app-menu', menuProps);
  },
  setMaximize: () => {
    ipcRenderer.send('set-maximize');
  },
  // 获取当前窗口是否是最大化
  isMaximized: () => {
    ipcRenderer.send('is-maximized');
  },
  closeWindow: () => {
    ipcRenderer.send('close-window');
  },
  // 最小化窗口
  minimizeWindow: () => {
    ipcRenderer.send('minimize-window');
  },
  // 获取环境是mac还是windows还是linux
  getPlatform: () => {
    return {
      isLinux,
      isWin,
      isMac,
    };
  },
});
