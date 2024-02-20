import React, { useRef, useState, Fragment, useEffect } from 'react';
import { Button, Dropdown } from 'antd';
import classnames from 'classnames';
import i18n from '@/i18n';
// import RefreshLoadingButton from '@/components/RefreshLoadingButton';

// ----- services -----
import connectionService from '@/service/connection';

// ----- constants/typings -----
import { databaseMap } from '@/constants';
import { IConnectionDetails, IConnectionListItem } from '@/typings';

// ----- components -----
import CreateConnection from '@/blocks/CreateConnection';
import Iconfont from '@/components/Iconfont';
import LoadingContent from '@/components/Loading/LoadingContent';
import MenuLabel from '@/components/MenuLabel';

// ----- hooks -----
import useClickAndDoubleClick from '@/hooks/useClickAndDoubleClick';

// ----- store -----
import { useConnectionStore, getConnectionList } from '@/pages/main/store/connection';
import { setMainPageActiveTab } from '@/pages/main/store/main';
import { setCurrentConnectionDetails } from '@/pages/main/workspace/store/common';
import { getOpenConsoleList } from '@/pages/main/workspace/store/console';

import styles from './index.less';

const ConnectionsPage = () => {
  const { connectionList } = useConnectionStore((state) => {
    return {
      connectionList: state.connectionList,
    };
  });
  const volatileRef = useRef<any>();
  const [connectionActiveId, setConnectionActiveId] = useState<IConnectionListItem['id'] | null>(null);
  const [connectionDetail, setConnectionDetail] = useState<IConnectionDetails | null | undefined>(null);

  // 处理列表单击事件
  const handleMenuItemSingleClick = (t: IConnectionListItem) => {
    if (connectionActiveId !== t.id) {
      setConnectionActiveId(t.id);
    }
  };

  // 处理列表双击事件
  const handleMenuItemDoubleClick = (t: IConnectionListItem) => {
    setCurrentConnectionDetails(t);
    setMainPageActiveTab('workspace');
  };

  // 处理列表单击和双击事件
  const handleClickConnectionMenu = useClickAndDoubleClick(handleMenuItemSingleClick, handleMenuItemDoubleClick);

  // 切换连接的详情
  useEffect(() => {
    if (!connectionActiveId) {
      return;
    }
    setConnectionDetail(undefined);
    connectionService
      .getDetails({ id: connectionActiveId })
      .then((res) => {
        setConnectionDetail(res);
      })
      .catch(() => {
        setConnectionActiveId(null);
      });
  }, [connectionActiveId]);

  //
  const createDropdownItems = (t) => {
    const handelDelete = (e) => {
      // 禁止冒泡到menuItem
      e.domEvent?.stopPropagation?.();
      connectionService.remove({ id: t.id }).then(() => {
        getConnectionList().then(() => {
          // 连接删除后需要更新下 consoleList
          getOpenConsoleList();
        });
        if (connectionActiveId === t.id) {
          setConnectionActiveId(null);
          setConnectionDetail(null);
        }
      });
    };

    const enterWorkSpace = (e) => {
      e.domEvent?.stopPropagation?.();
      handleMenuItemDoubleClick(t);
    };

    const copyConnection = (e) => {
      e.domEvent?.stopPropagation?.();
      connectionService.clone({ id: t.id }).then((res) => {
        getConnectionList();
        setConnectionActiveId(res);
      });
    }

    return [
      {
        key: 'enterWorkSpace',
        label: <MenuLabel icon="&#xec57;" label={i18n('connection.button.connect')} />,
        onClick: enterWorkSpace,
      },
      {
        key: 'copyConnection',
        label: <MenuLabel icon="&#xec7a;" label={i18n('common.button.copy')} />,
        onClick: copyConnection,
      },
      {
        key: 'delete',
        label: <MenuLabel icon="&#xe6a7;" label={i18n('connection.button.remove')} />,
        onClick: handelDelete,
      },
    ];
  };

  const renderConnectionMenuList = () => {
    return connectionList?.map((t) => {
      return (
        <Dropdown
          key={t.id}
          trigger={['contextMenu']}
          menu={{
            items: createDropdownItems(t),
          }}
        >
          <div
            className={classnames(styles.menuItem, {
              [styles.menuItemActive]: connectionActiveId === t.id,
            })}
            onClick={() => {
              handleClickConnectionMenu(t);
            }}
          >
            <div className={classnames(styles.menuItemsTitle)}>
              <span className={styles.envTag} style={{ background: t.environment.color.toLocaleLowerCase() }} />
              <span className={styles.databaseTypeIcon}>
                {<Iconfont className={styles.menuItemIcon} code={databaseMap[t.type]?.icon} />}
              </span>
              <span className={styles.name}>{t.alias}</span>
              {/* <Tag color={t.environment.color.toLocaleLowerCase()}>
              {t.environment.shortName}
            </Tag> */}
            </div>
          </div>
        </Dropdown>
      );
    });
  };

  const onSubmit = (data) => {
    return connectionService
      .save({
        ...data,
      })
      .then((res) => {
        getConnectionList();
        setConnectionActiveId(res);
      });
  };

  return (
    <>
      <div className={styles.box}>
        <div ref={volatileRef} className={styles.layoutLeft}>
          <div className={styles.pageTitle}>{i18n('connection.title.connections')}</div>
          <div className={styles.menuBox}>{renderConnectionMenuList()}</div>
          {connectionActiveId && (
            <Button
              type="primary"
              className={styles.addConnection}
              onClick={() => {
                setConnectionActiveId(null);
                setConnectionDetail(null);
              }}
            >
              {i18n('connection.button.addConnection')}
            </Button>
          )}
        </div>
        <LoadingContent
          className={styles.layoutRight}
          isLoading={connectionDetail === undefined && !!connectionActiveId}
        >
          <CreateConnection connectionDetail={connectionDetail} onSubmit={onSubmit} />
        </LoadingContent>
      </div>
    </>
  );
};

export default ConnectionsPage;
