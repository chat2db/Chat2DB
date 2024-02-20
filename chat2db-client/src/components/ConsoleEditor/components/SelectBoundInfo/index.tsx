import React, { useEffect, useMemo, useState, memo, useContext } from 'react';
import { IntelligentEditorContext } from '../../index';
import { Dropdown } from 'antd';
import { useConnectionStore } from '@/pages/main/store/connection';
import connectionService from '@/service/connection';
import historyService from '@/service/history';
import Iconfont from '@/components/Iconfont';
import { databaseMap } from '@/constants/database';
import styles from './index.less';
import sqlService from '@/service/sql';
import { IBoundInfo } from '@/typings';

import {
  registerIntelliSenseField,
  registerIntelliSenseKeyword,
  registerIntelliSenseTable,
  registerIntelliSenseDatabase,
  resetSenseKeyword,
  resetSenseTable,
  resetSenseDatabase,
  resetSenseField,
} from '@/utils/IntelliSense';

interface IProps {
  boundInfo: IBoundInfo;
  setBoundInfo: (params: IBoundInfo) => void;
}

interface IOption<T> {
  key: string;
  label: string;
  value: T;
}

const emptyOption = {
  key: '',
  label: '',
  value: '',
};

const SelectBoundInfo = memo((props: IProps) => {
  const { boundInfo, setBoundInfo } = props;
  const { setSelectedTables, setTableNameList, isActive } = useContext(IntelligentEditorContext);
  const connectionList = useConnectionStore((state) => state.connectionList);
  const [databaseNameList, setDatabaseNameList] = useState<IOption<string>[]>([emptyOption]);
  const [schemaList, setSchemaList] = useState<IOption<string>[]>([emptyOption]);
  const [allTableList, setAllTableList] = useState<any>([]);

  useEffect(() => {
    if(!isActive){
      resetSenseKeyword();
      resetSenseTable();
      resetSenseDatabase();
      resetSenseField();
    }
  }, [isActive]);

  const dataSourceList = useMemo(() => {
    return (
      connectionList?.map((item) => ({
        key: item.id.toString(),
        label: item.alias,
        value: item.id,
        type: item.type,
      })) || []
    );
  }, [connectionList]);

  const supportDatabase = useMemo(() => {
    return connectionList?.find((item) => item.id === boundInfo.dataSourceId)?.supportDatabase;
  }, [boundInfo.dataSourceId, connectionList]);

  const supportSchema = useMemo(() => {
    return connectionList?.find((item) => item.id === boundInfo.dataSourceId)?.supportSchema;
  }, [boundInfo.dataSourceId, connectionList]);

  // 编辑器绑定的数据库类型变化时，重新注册智能提示
  useEffect(() => {
    if(!isActive){
      return
    }
    registerIntelliSenseKeyword(boundInfo.databaseType);
  }, [boundInfo.dataSourceId, isActive]);

  // 当数据源变化时，重新获取数据库列表
  useEffect(() => {
    if (!isActive || boundInfo.connectable === false) {
      return;
    }
    if (supportDatabase) {
      setSchemaList([]);
      setDatabaseNameList([]);
      getDatabaseList();
    }
  }, [boundInfo.dataSourceId, isActive]);

  // 当数据库名变化时，重新获取schema列表
  useEffect(() => {
    if (!isActive || boundInfo.connectable === false) {
      return;
    }
    if (supportSchema) {
      setSchemaList([]);
      getSchemaList();
    }
    if (!supportSchema && boundInfo.databaseName) {
      getAllTableNameList(boundInfo.dataSourceId, boundInfo.databaseName);
    }
  }, [boundInfo.databaseName, isActive, supportSchema]);

  useEffect(() => {
    if (!isActive || boundInfo.connectable === false) {
      return;
    }
    if (supportSchema && boundInfo.schemaName) {
      getAllTableNameList(boundInfo.dataSourceId, boundInfo.databaseName, boundInfo.schemaName);
    }
  }, [boundInfo.schemaName, isActive, supportSchema]);

  // 获取数据库列表
  const getDatabaseList = () => {
    if (boundInfo.dataSourceId === undefined || boundInfo.dataSourceId === null) {
      return;
    }
    connectionService
      .getDatabaseList({
        dataSourceId: boundInfo.dataSourceId,
      })
      .then((res) => {
        const editorDatabaseTips: any = [];
        const _databaseNameList = res.map((item) => {
          editorDatabaseTips.push({
            name: item.name,
            dataSourceName: boundInfo.dataSourceName,
          });
          return {
            key: item.name,
            label: item.name,
            value: item.name,
          };
        });
        if (!_databaseNameList.length) {
          getSchemaList();
        }
        setDatabaseNameList([emptyOption, ..._databaseNameList]);
      });
  };

  // 获取schema列表
  const getSchemaList = () => {
    if (boundInfo.dataSourceId === undefined || boundInfo.dataSourceId === null) {
      return;
    }
    connectionService
      .getSchemaList({
        dataSourceId: boundInfo.dataSourceId!,
        databaseName: boundInfo?.databaseName,
      })
      .then((res: any) => {
        const _schemaList = res.map((item) => ({
          key: item.name,
          label: item.name,
          value: item.name,
        }));
        setSchemaList([emptyOption, ..._schemaList]);
      });
  };

  // 注册表名
  useEffect(() => {
    if (isActive) {
      const tableNameListTemp = allTableList.map((t) => t.name);
      setTableNameList(tableNameListTemp);
      registerIntelliSenseTable(
        allTableList,
        boundInfo.databaseType,
        boundInfo.dataSourceId,
        boundInfo.databaseName,
        boundInfo.schemaName,
      );
      registerIntelliSenseField(
        tableNameListTemp,
        boundInfo.dataSourceId,
        boundInfo.databaseName,
        boundInfo.schemaName,
      );
      setSelectedTables(tableNameListTemp.slice(0, 1));
    }
  }, [allTableList, isActive]);

  // 注册数据库名
  useEffect(() => {
    const editorDatabaseTips = databaseNameList.map((item) => ({
      name: item.value,
      dataSourceName: boundInfo.dataSourceName,
    }));
    registerIntelliSenseDatabase(editorDatabaseTips);
  }, [databaseNameList]);

  // 选择数据源
  const changeDataSource = (item) => {
    const currentData = dataSourceList.find((i) => i.key === item.key)!;
    setBoundInfo({
      ...boundInfo,
      dataSourceId: currentData.value,
      dataSourceName: currentData.label,
      databaseType: currentData.type,
      databaseName: void 0,
      schemaName: void 0,
    });
    if (boundInfo.consoleId) {
      historyService.updateSavedConsole({
        id: boundInfo.consoleId,
        dataSourceId: currentData.value,
        dataSourceName: currentData.label,
        type: currentData.type,
      });
    }
  };

  // 选择数据库
  const changeDataBase = (item) => {
    const _databaseName = databaseNameList?.find((i) => i.key === item.key)?.value;

    setBoundInfo({
      ...boundInfo,
      databaseName: _databaseName,
      schemaName: void 0,
    });

    if (boundInfo.consoleId) {
      historyService.updateSavedConsole({
        id: boundInfo.consoleId,
        databaseName: _databaseName,
      });
    }
  };

  // 选择schema
  const changeSchema = (item) => {
    const _schemaName = schemaList?.find((i) => i.key === item.key)?.value;
    setBoundInfo({
      ...boundInfo,
      schemaName: _schemaName,
    });

    if (boundInfo.consoleId) {
      historyService.updateSavedConsole({
        id: boundInfo.consoleId,
        schemaName: _schemaName,
      });
    }
  };

  const getAllTableNameList = (dataSourceId, databaseName, schemaName?) => {
    sqlService
      .getAllTableList({
        dataSourceId,
        databaseName,
        schemaName,
      })
      .then((data) => {
        setAllTableList(data);
      });
  };

  return (
    <div className={styles.consoleOptionsRight}>
      <div className={styles.boundInfoBoxSpacer} />
      <Dropdown
        menu={{
          items: dataSourceList,
          onClick: changeDataSource,
        }}
        trigger={['click']}
      >
        <div className={styles.boundInfoBox}>
          <Iconfont code={databaseMap[boundInfo.databaseType!]?.icon} />
          <div className={styles.boundInfoName}>{boundInfo.dataSourceName || `<${'dataSource'}>`}</div>
          <Iconfont code="&#x100be;" />
        </div>
      </Dropdown>

      {supportDatabase && (
        <Dropdown
          menu={{
            items: databaseNameList,
            onClick: changeDataBase,
          }}
          trigger={['click']}
        >
          <div className={styles.boundInfoBox}>
            <Iconfont code="&#xe669;" />
            <div className={styles.boundInfoName}>{boundInfo.databaseName || `<${'database'}>`}</div>
            <Iconfont code="&#x100be;" />
          </div>
        </Dropdown>
      )}

      {supportSchema && (
        <Dropdown
          menu={{
            items: schemaList,
            onClick: changeSchema,
          }}
          trigger={['click']}
        >
          <div className={styles.boundInfoBox}>
            <Iconfont code="&#xe663;" />
            <div className={styles.boundInfoName}>{boundInfo.schemaName || `<${'schema'}>`}</div>
            <Iconfont code="&#x100be;" />
          </div>
        </Dropdown>
      )}
    </div>
  );
});

export default SelectBoundInfo;
