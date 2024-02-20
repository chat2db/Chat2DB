const { shell, app, dialog, BrowserWindow, Menu } = require('electron');
const os = require('os');
const path = require('path');
const { isMac } = require('./utils');

const registerAppMenu = (mainWindow, orgs) => {
  if (!isMac) {
    Menu.setApplicationMenu(null);
    return;
  }
  const menuBar = [
    {
      label: 'Chat2DB',
      submenu: [
        {
          label: '关于Chat2DB',
          click() {
            dialog.showMessageBox({
              title: '关于Chat2DB',
              message: `关于Chat2DB v${orgs?.version || app.getVersion()}`,
              detail:
                // An intelligent database client and smart BI reporting tool with integrated AI capabilities.
                '一个集成AI能力的智能数据库客户端和智能BI报表工具。',
              icon: './logo/icon.png',
            });
          },
        },
        { type: 'separator' },
        {
          label: '重新启动',
          click() {
            // 退出程序
            app.relaunch();
            app.quit();
          },
        },
        {
          label: '退出',
          accelerator: process.platform === 'darwin' ? 'Cmd+Q' : 'Alt+F4',
          click() {
            // 退出程序
            app.quit();
          },
        },
      ],
    },
    {
      label: '编辑',
      submenu: [
        { label: '撤销', role: 'undo' },
        { label: '重做', role: 'redo' },
        { type: 'separator' },
        { label: '剪切', role: 'cut' },
        { label: '复制', role: 'copy' },
        { label: '粘贴', role: 'paste' },
        { label: '全选', role: 'selectAll' },
      ],
    },
    {
      // label: i18n('menu.edit'),
      label: '视图',
      submenu: [
        // {
        //   label: '刷新',
        //   accelerator: 'CmdOrCtrl+Shift+R',
        //   click() {
        //     const focusedWindow = BrowserWindow.getFocusedWindow();
        //     if (focusedWindow) {
        //       focusedWindow.reload();
        //     }
        //   },
        // },
        { type: 'separator' },
        {
          label: '放大',
          accelerator: 'CmdOrCtrl+=',
          role: 'zoomIn',
        },
        {
          label: '缩小',
          accelerator: 'CmdOrCtrl+-',
          role: 'zoomOut',
        },
        {
          label: '重置',
          accelerator: 'CmdOrCtrl+0',
          role: 'resetZoom',
        },
        { type: 'separator' },
        { label: '全屏', role: 'togglefullscreen' },
      ],
    },
    {
      label: '窗口',
      role: 'window',
      submenu: [
        { label: '最小化', role: 'minimize', accelerator: 'Command+W' },
        { label: '关闭', role: 'close' },
      ],
    },
    {
      label: '帮助',
      submenu: [
        {
          label: '打开日志',
          accelerator: process.platform === 'darwin' ? 'Cmd+Shift+T' : 'Ctrl+Shift+T',
          click() {
            const fileName = '.chat2db/logs/application.log';
            const url = path.join(os.homedir(), fileName);
            shell.openPath(url).then((str) => console.log('err:', str));
          },
        },
        {
          label: '打开控制台',
          accelerator: process.platform === 'darwin' ? 'Cmd+Shift+I' : 'Ctrl+Shift+I',
          click() {
            const focusedWindow = BrowserWindow.getFocusedWindow();
            focusedWindow && focusedWindow.toggleDevTools();
          },
        },
        {
          label: '访问官网',
          click() {
            const url = 'https://www.sqlgpt.cn/zh';
            shell.openExternal(url);
          },
        },
        {
          label: '查看文档',
          click() {
            const url = 'https://doc.sqlgpt.cn/zh/';
            shell.openExternal(url);
          },
        },
        {
          label: '查看更新日志',
          click() {
            const url = 'https://doc.sqlgpt.cn/zh/changelog/';
            shell.openExternal(url);
          },
        },
      ],
    },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(menuBar));
};

module.exports = registerAppMenu;
