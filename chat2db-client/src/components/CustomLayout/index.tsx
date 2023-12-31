import React, { memo } from 'react';
import styles from './index.less';
import classnames from 'classnames';
import { useWorkspaceStore } from '@/pages/main/workspace/store';
import { togglePanelLeft, togglePanelRight } from '@/pages/main/workspace/store/config';

interface IProps {
  className?: string;
}

export default memo<IProps>((props) => {
  const { className } = props;
  const { panelLeft, panelRight} = useWorkspaceStore((state) => {
    return {
      panelLeft: state.layout.panelLeft,
      panelRight: state.layout.panelRight,
    };
  });

  return (
    <div className={classnames(styles.customLayout, className)}>
      <div
        className={classnames(styles.iconPanelLeft, styles.iconPanel, { [styles.iconPanelLeftHidden]: !panelLeft })}
        onClick={togglePanelLeft}
      />
      <div
        className={classnames(styles.iconPanelRight, styles.iconPanel, { [styles.iconPanelRightHidden]: !panelRight })}
        onClick={togglePanelRight}
      />
    </div>
  );
});
