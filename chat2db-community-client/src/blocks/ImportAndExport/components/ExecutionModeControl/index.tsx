import { Modal, Space, Switch, Tooltip } from 'antd';
import i18n from '@/i18n';
import type { ImportExecutionMode } from '@/typings/importExport';

interface Props {
  value: ImportExecutionMode;
  onChange: (value: ImportExecutionMode) => void;
  disabled?: boolean;
  confirmImport?: boolean;
}

export default function ExecutionModeControl({ value, onChange, disabled, confirmImport }: Props) {
  const [modal, contextHolder] = Modal.useModal();

  const toggle = (checked: boolean) => {
    if (checked && confirmImport) {
      modal.confirm({
        title: i18n('workspace.importExport.ultraModeConfirmTitle'),
        content: i18n('workspace.importExport.ultraModeAcknowledge'),
        okText: i18n('workspace.importExport.ultraModeConfirm'),
        cancelText: i18n('common.button.cancel'),
        onOk: () => onChange('ULTRA_FAST'),
      });
      return;
    }
    onChange(checked ? 'ULTRA_FAST' : 'STANDARD');
  };

  return (
    <Space>
      {contextHolder}
      <Tooltip title={i18n('workspace.importExport.ultraModeHint')}>
        <span>{i18n('workspace.importExport.ultraMode')}</span>
      </Tooltip>
      <Switch
        aria-label={i18n('workspace.importExport.ultraMode')}
        checked={value === 'ULTRA_FAST'}
        disabled={disabled}
        onChange={toggle}
      />
    </Space>
  );
}
