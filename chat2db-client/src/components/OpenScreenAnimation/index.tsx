import React, { memo } from 'react';
import styles from './index.less';
import classnames from 'classnames';
import BrandLogo from '@/components/BrandLogo'

interface IProps {
  className?: string;
}

export default memo<IProps>((props) => {
  const { className } = props;
  return <div className={classnames(styles.openScreenAnimation, className)}>
    <BrandLogo size={80} className={styles.brandLogo} />
    <div className={styles.brandName}>
      <div className={styles.textImg}>
        Chat2DB
      </div>
    </div>
  </div>
});
