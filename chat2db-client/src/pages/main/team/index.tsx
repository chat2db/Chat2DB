import React, { useMemo, useState } from 'react';
import { ApiOutlined, UserOutlined, TeamOutlined } from '@ant-design/icons';
import { Tabs } from 'antd';
import DataSourceManagement from './datasource-management';
import UserManagement from './user-management';
import TeamManagement from './team-management';
import i18n from '@/i18n';
import styles from './index.less';

const Team = () => {
  const [activeKey, setActiveKey] = useState<string>('0');
  const tabList = useMemo(
    () => [
      {
        label: i18n('team.tab.datasource'),
        icon: <ApiOutlined />,
        children: <DataSourceManagement />,
      },
      {
        label: i18n('team.tab.user'),
        icon: <UserOutlined />,
        children: <UserManagement />,
      },
      {
        label: i18n('team.tab.team'),
        icon: <TeamOutlined />,
        children: <TeamManagement />,
      },
    ],
    [],
  );

  return (
    <div className={styles.teamWrapper}>
      <Tabs
        className={styles.teamTabsBox}
        activeKey={activeKey}
        onChange={(_activeKey) => setActiveKey(_activeKey)}
        items={tabList.map((tab, index) => {
          return {
            key: String(index),
            label: (
              <span>
                {tab.icon}
                {tab.label}
              </span>
            ),
            children: tab.children,
          };
        })}
      />
    </div>
  );
};

export default Team;
