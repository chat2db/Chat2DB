import React, { memo } from 'react';
import styles from './index.less';
import classnames from 'classnames';
import { useWorkspaceStore } from '@/pages/main/workspace/store';
import { togglePanelLeft, togglePanelRight } from '@/pages/main/workspace/store/config';
import Iconfont from '@/components/Iconfont';

interface IProps {
  className?: string;
}

export default memo<IProps>((props) => {
  const { className } = props;
  const { panelLeft, panelRight } = useWorkspaceStore((state) => {
    return {
      panelLeft: state.layout.panelLeft,
      panelRight: state.layout.panelRight,
    };
  });

  // 阻止事件冒泡
  const stopPropagation = (e: React.MouseEvent<HTMLDivElement, MouseEvent>) => {
    e.stopPropagation();
  };

  return (
    <div className={classnames(styles.customLayout, className)}>
      <div className={classnames(styles.iconPanel)} onClick={togglePanelLeft} onDoubleClick={stopPropagation}>
        {panelLeft ? <Iconfont code="&#xe674;" /> : <Iconfont code="&#xe670;" />}
      </div>
      <div className={classnames(styles.iconPanel)} onClick={togglePanelRight} onDoubleClick={stopPropagation}>
        {panelRight ? <Iconfont code="&#xe672;" /> : <Iconfont code="&#xe673;" />}
      </div>
    </div>
  );
});
