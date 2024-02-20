const { DEV_WEB_URL } = require('./constants');
const path = require('path');
const url = require('url');
const fs = require('fs');

const isLinux = process.platform == 'linux';
const isWin = process.platform == 'win32';
const isMac = process.platform == 'darwin';
const isProd = process.env.NODE_ENV == 'production';

/**
 * 加载主进程前端资源
 * @param {*} mainWindow
 */
function loadMainResource(mainWindow) {
  if (process.env.NODE_ENV === 'development') {
    mainWindow.loadURL(DEV_WEB_URL);
    mainWindow.webContents.openDevTools();
    // 监听应用程序根路径下的所有文件，当文件发生修改时，自动刷新应用程序
    // require('electron-reload')(path.join(__dirname, '..'));
  } else {
    mainWindow.loadURL(
      url.format({
        pathname: path.join(__dirname, '../..', `./versions/${readVersion()}`, `./dist/index.html`),
        protocol: 'file:',
        slashes: true,
      }),
    );
  }
}

/**
 * 应该读取哪个版本的资源
 * @param {*}
 */
function readVersion() {
  let version = '';
  if (process.env.NODE_ENV !== 'development') {
    version = fs.readFileSync(path.join(__dirname, '../..', './versions/version'));
  }
  return version.toString().trim();
}

module.exports = {
  loadMainResource,
  readVersion,
  isLinux,
  isWin,
  isMac,
  isProd,
};
