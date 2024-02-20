import React, { useEffect, useLayoutEffect, useState } from 'react';
import usePollRequestService, { ServiceStatus } from '@/hooks/usePollRequestService';
import i18n, { isEn } from '@/i18n';
import { Button, ConfigProvider, Spin, Tooltip } from 'antd';
import antdEnUS from 'antd/locale/en_US';
import antdZhCN from 'antd/locale/zh_CN';
import service from '@/service/misc';
import useCopyFocusData from '@/hooks/useFocusData';
import { useTheme } from '@/hooks/useTheme';
import { getAntdThemeConfig } from '@/theme';
import { Outlet } from 'umi';
import init from '../init/init';
import { GithubOutlined, SyncOutlined, WechatOutlined } from '@ant-design/icons';
import { ThemeType } from '@/constants';
import GlobalComponent from '../init/GlobalComponent';
import styles from './index.less';
import { useUserStore, queryCurUser } from '@/store/user';
import AppTitleBar from '@/blocks/AppTitleBar';
import OpenScreenAnimation from '@/components/OpenScreenAnimation';

const GlobalLayout = () => {
  const [appTheme, setAppTheme] = useTheme();
  const [antdTheme, setAntdTheme] = useState<any>({});
  const { curUser } = useUserStore((state) => {
    return {
      curUser: state.curUser,
    };
  });

  const { serviceStatus, restartPolling } = usePollRequestService({
    loopService: service.testService,
  });

  useCopyFocusData();

  useLayoutEffect(() => {
    setAntdTheme(getAntdThemeConfig(appTheme));
  }, [appTheme]);

  useLayoutEffect(() => {
    init();
    monitorOsTheme();
  }, []);

  useEffect(() => {
    if (serviceStatus === ServiceStatus.SUCCESS) {
      queryCurUser();
    }
  }, [serviceStatus]);

  // 监听系统(OS)主题变化
  const monitorOsTheme = () => {
    function change(e: any) {
      if (appTheme.backgroundColor === ThemeType.FollowOs) {
        setAppTheme({
          ...appTheme,
          backgroundColor: e.matches ? ThemeType.Dark : ThemeType.Light,
        });
      }
    }
    const matchMedia = window.matchMedia('(prefers-color-scheme: dark)');
    matchMedia.onchange = change;
  };

  // 等待状态页面
  // if (serviceStatus === ServiceStatus.PENDING || curUser === null) {
  //   return (
  //     <div className={styles.app}>
  //       <AppTitleBar className={styles.appTitleBar} />
  //       <Spin className={styles.loadingBox} size="large" />
  //     </div>
  //   );
  // }

  // 错误状态页面
  if (serviceStatus === ServiceStatus.FAILURE) {
    return (
      <div className={styles.app}>
        <AppTitleBar className={styles.appTitleBar} />
        <div className={styles.loadingBox}>
          <Button type="primary" onClick={restartPolling} style={{ marginBottom: 20 }}>
            <SyncOutlined />
            {i18n('common.text.tryToRestart')}
          </Button>
          <div className={styles.contact}>
            {i18n('common.text.contactUs')}：
            <GithubOutlined className={styles.icon} onClick={() => window.open('https://github.com/chat2db/Chat2DB')} />
            <Tooltip
              placement="bottom"
              title={<img style={{ width: 200, height: 200 }} src="https://sqlgpt.cn/_static/img/chat2db_wechat.png" />}
            >
              <WechatOutlined className={styles.icon} />
            </Tooltip>
          </div>
        </div>
      </div>
    );
  }

  return (
    <ConfigProvider locale={isEn ? antdEnUS : antdZhCN} theme={antdTheme}>
      <div className={styles.app}>
        {/* Open screen animation  */}
        {(serviceStatus === ServiceStatus.PENDING || curUser === null) && <OpenScreenAnimation />}
        <AppTitleBar className={styles.appTitleBar} />
        <div className={styles.appBody}>
          <Outlet />
        </div>
      </div>
      <GlobalComponent />
    </ConfigProvider>
  );
};

export default GlobalLayout;
