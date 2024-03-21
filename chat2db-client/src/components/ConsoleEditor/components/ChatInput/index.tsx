import React, { useState } from 'react';
import styles from './index.less';
import AIImg from '@/assets/img/ai.svg';
import { Button, Input, Popover, Select, Radio, Space } from 'antd';
import i18n from '@/i18n/';
import Iconfont from '@/components/Iconfont';
import { AIType } from '@/typings/ai';

export const enum SyncModelType {
  AUTO = 0,
  MANUAL = 1,
}

interface IProps {
  value?: string;
  result?: string;
  tables?: string[];
  syncTableModel: number;
  selectedTables?: string[];
  aiType: AIType;
  disabled?: boolean;
  isStream?: boolean;
  onPressEnter: (value: string) => void;
  onSelectTableSyncModel: (model: number) => void;
  onSelectTables?: (tables: string[]) => void;
  // onClickRemainBtn: Function;
  onCancelStream: () => void;
}

const ChatInput = (props: IProps) => {
  const [value, setValue] = useState(props.value);

  const onPressEnter = (e: any) => {
    if (!e.target.value) {
      return;
    }
    if (e.nativeEvent.isComposing && e.key === 'Enter') {
      e.preventDefault();
      return;
    }
    props.onPressEnter && props.onPressEnter(e.target.value);
  };

  const renderSelectTable = () => {
    const { tables, onSelectTableSyncModel, selectedTables, onSelectTables,syncTableModel } = props;
    const options = (tables || []).map((t) => ({ value: t, label: t }));
    return (
      <div className={styles.aiSelectedTable}>
        <Radio.Group
          onChange={(v) => onSelectTableSyncModel(v.target.value)}
          value={syncTableModel}
          style={{ marginBottom: '8px' }}
        >
          <Space direction="horizontal">
            <Radio value={SyncModelType.AUTO}>自动</Radio>
            <Radio value={SyncModelType.MANUAL}>手动</Radio>
          </Space>
        </Radio.Group>
        {/* {syncTableModel === 0 ? (
          i18n('chat.input.syncTable.tips')
        ) : (
        )} */}
        <>
          <span className={styles.aiSelectedTableTips}>{i18n('chat.input.remain.tooltip')}</span>
          <Select
            showSearch
            mode="multiple"
            allowClear
            options={options}
            placeholder={i18n('chat.input.tableSelect.placeholder')}
            value={selectedTables}
            onChange={(v) => {
              onSelectTables && onSelectTables(v);
            }}
          />
        </>
      </div>
    );
  };

  const renderSuffix = () => {
    return (
      <div className={styles.suffixBlock}>
        {props.isStream ? (
          <Iconfont
            onClick={() => {
              props.onCancelStream && props.onCancelStream();
            }}
            code="&#xe652;"
            className={styles.stop}
          />
        ) : (
          <Button
            type="primary"
            className={styles.enter}
            onClick={() => {
              if (value) {
                props.onPressEnter && props.onPressEnter(value);
              }
            }}
          >
            <Iconfont code="&#xe643;" className={styles.enterIcon} />
          </Button>
        )}
        {/* <Tooltip
          title={<span style={{ color: window._AppThemePack.colorText }}>{i18n('chat.input.syncTable.tempTips')}</span>}
          defaultOpen={!hasBubble}
          color={window._AppThemePack.colorBgBase}
          trigger={'contextMenu'}
          onOpenChange={() => {
            localStorage.setItem('syncTableBubble', 'true');
          }}
        >
        </Tooltip> */}
        <div className={styles.tableSelectBlock}>
          <Popover content={renderSelectTable()} placement="bottomLeft">
            <Iconfont code="&#xe618;" />
          </Popover>
        </div>
      </div>
    );
  };

  return (
    <div className={styles.chatWrapper}>
      <img className={styles.chatAi} src={AIImg} />
      <Input
        disabled={props.disabled}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        bordered={false}
        placeholder={i18n('workspace.ai.input.placeholder')}
        onPressEnter={onPressEnter}
        suffix={renderSuffix()}
      />
    </div>
  );
};

export default ChatInput;
