import React, { memo, useState, useMemo } from 'react';
import styles from './index.less';
import classnames from 'classnames';
import { useCommonStore } from '@/store/common';
import Iconfont from '@/components/Iconfont';
import BrandLogo from '@/components/BrandLogo';

interface IProps {
  className?: string;
}

export default memo<IProps>((props) => {
  const { className } = props;
  const [isMaximized, setIsMaximize] = useState(window.electronApi?.isMaximized());

  const { appTitleBarRightComponent } = useCommonStore((state) => {
    return {
      appTitleBarRightComponent: state.appTitleBarRightComponent,
    };
  });

  const isMac = useMemo(() => {
    return window.electronApi?.getPlatform().isMac;
  }, []);

  // const isMac = false;

  const handleDoubleClick = () => {
    window.electronApi?.setMaximize();
    setIsMaximize(!isMaximized);
  };

  const handelMinimizeWindow = (e) => {
    e.stopPropagation();
    window.electronApi?.minimizeWindow();
  };

  const handelMaximize = (e) => {
    e.stopPropagation();
    window.electronApi?.setMaximize();
    setIsMaximize(!isMaximized);
  };

  const handelCloseWindow = (e) => {
    e.stopPropagation();
    window.electronApi?.closeWindow();
  };

  if(isMac === void 0){
    return false
  }

  return (
    <div className={classnames(styles.appTitleBar, className)} onDoubleClick={handleDoubleClick}>
      <div className={styles.appTitleBarGlobal}>
        <div className={classnames(styles.leftSlot)}>
          <BrandLogo size={20} className={styles.brandLogo} />
        </div>
        <div className={styles.appName}>Chat2DB</div>
        <div className={styles.rightSlot}>{appTitleBarRightComponent}</div>
      </div>
      {(!isMac && isMac !== void 0) && (
        <div className={styles.windowsCloseBar}>
          <div className={styles.windowsCloseBarItem} onClick={handelMinimizeWindow}>
            <Iconfont size={13} code="&#xe671;" />
          </div>
          <div className={styles.windowsCloseBarItem} onClick={handelMaximize}>
            {isMaximized ? <Iconfont size={13} code="&#xe66e;" /> : <Iconfont size={12} code="&#xe66b;" />}
          </div>
          <div className={styles.windowsCloseBarItem} onClick={handelCloseWindow}>
            <Iconfont size={12} code="&#xe66f;" />
          </div>
        </div>
      )}
    </div>
  );
});
