import { Modal, Space, Switch, Tooltip } from 'antd';
import i18n from '@/i18n';
import type { ImportExecutionMode } from '@/typings/importExport';

interface Props {
  value: ImportExecutionMode;
  onChange: (value: ImportExecutionMode) => void;
  disabled?: boolean;
}

export default function ImportModeControl({ value, onChange, disabled }: Props) {
  const [modal, contextHolder] = Modal.useModal();

  const toggle = (checked: boolean) => {
    if (checked) {
      modal.confirm({
        title: i18n('workspace.importExport.ultraModeConfirmTitle'),
        content: i18n('workspace.importExport.ultraModeAcknowledge'),
        okText: i18n('workspace.importExport.ultraModeConfirm'),
        cancelText: i18n('common.button.cancel'),
        onOk: () => onChange('FAST'),
      });
      return;
    }
    onChange('STANDARD');
  };

  return (
    <Space>
      {contextHolder}
      <Tooltip title={i18n('workspace.importExport.ultraModeHint')}>
        <span>{i18n('workspace.importExport.ultraMode')}</span>
      </Tooltip>
      <Switch
        aria-label={i18n('workspace.importExport.ultraMode')}
        checked={value === 'FAST'}
        disabled={disabled}
        onChange={toggle}
      />
    </Space>
  );
}
