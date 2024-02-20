import { defineConfig } from 'umi';
import { extractYarnConfig, transitionTimezoneTimestamp } from './src/utils/webpack';

const MonacoWebpackPlugin = require('monaco-editor-webpack-plugin');

// yarn run build --app_port=xx 获取打包时命令行传入的参数
const yarn_config = extractYarnConfig(process.argv);

const chainWebpack = (config: any, { webpack }: any) => {
  config.plugin('monaco-editor').use(MonacoWebpackPlugin, [
    {
      languages: ['mysql', 'pgsql', 'sql'],
    },
  ]);
};

export default defineConfig({
  title: 'Chat2DB',
  base: '/',
  publicPath: '/',
  hash: true,
  routes: [
    {
      path: '/',
      component: '@/layouts/GlobalLayout',
      routes: [
        {
          path: '/login',
          component: '@/pages/login',
        },
        {
          path: '/demo',
          component: '@/pages/demo',
        },
        {
          path: '/connections',
          component: 'main',
        },
        {
          path: '/dashboard',
          component: 'main',
        },
        {
          path: '/team',
          component: 'main',
        },
        {
          path: '/workspace',
          component: 'main',
        },
        {
          path: '/',
          component: 'main',
        },
      ],
    },
  ],

  npmClient: 'yarn',
  dva: {},
  plugins: ['@umijs/plugins/dist/dva'],
  chainWebpack,
  proxy: {
    '/api': {
      target: 'http://127.0.0.1:10821',
      changeOrigin: true,
    },
    '/client/remaininguses/': {
      target: 'http://127.0.0.1:1889',
      changeOrigin: true,
    },
  },
  targets: {
    chrome: 80,
  },
  // links: [{
  //   rel: 'manifest',
  //   href: 'manifest.json',
  // }],
  links: [{ rel: 'icon', type: 'image/ico', sizes: '32x32', href: '/static/front/logo.ico' }],
  headScripts: [
    `if (localStorage.getItem('app-local-storage-versions') !== 'v4') {
      localStorage.clear();
      localStorage.setItem('app-local-storage-versions', 'v4');
    }`,
    // `if (window.electronApi) { window.electronApi.startServerForSpawn() }`,
    // `if ("serviceWorker" in navigator) {
    //   window.addEventListener("load", function () {
    //     navigator.serviceWorker
    //       .register("sw.js")
    //       .then(res => console.log("service worker registered"))
    //       .catch(err => console.log("service worker not registered", err));
    //   })
    // }`,
    // `var deferredPrompt = null;
    // window.addEventListener("beforeinstallprompt", e => {
    //   e.preventDefault();
    //   deferredPrompt = e;
    // });
    // window.addEventListener("appinstalled", () => {
    //   deferredPrompt = null;
    // })`,
    {
      src: 'https://www.googletagmanager.com/gtag/js?id=G-V8M4E5SF61',
      async: true,
    },
    // `window.dataLayer = window.dataLayer || [];
    // function gtag() {
    //   window.dataLayer.push(arguments);
    // }
    // gtag('js', new Date());
    // gtag('config', 'G-V8M4E5SF61', {
    //   platform: 'WEB',
    //   version: '1.0.0'
    // });`,
  ],
  favicons: ['logo.ico'],
  define: {
    __ENV__: process.env.UMI_ENV,
    __BUILD_TIME__: transitionTimezoneTimestamp(new Date().getTime()),
    __APP_VERSION__: yarn_config.app_version || '0.0.0',
    __APP_PORT__: yarn_config.app_port,
  },
  esbuildMinifyIIFE: true,
});
