import React from 'react';
import classnames from 'classnames';
// import desktopStyle from './desktop.less';
import styles from './index.less';

// 只有本地开发时使用cdn，发布线上时要下载iconfont到 /assets/font
if (__ENV__ === 'local') {
  const container = `
  /* 在线链接服务仅供平台体验和调试使用，平台不承诺服务的稳定性，企业客户需下载字体包自行发布使用并做好备份。 */
  @font-face {
    font-family: 'iconfont';  /* Project id 3633546 */
    src: url('//at.alicdn.com/t/c/font_3633546_n88tvocxfkj.woff2?t=1704794450779') format('woff2'),
         url('//at.alicdn.com/t/c/font_3633546_n88tvocxfkj.woff?t=1704794450779') format('woff'),
         url('//at.alicdn.com/t/c/font_3633546_n88tvocxfkj.ttf?t=1704794450779') format('truetype');
  }
  `;
  const style = document.createElement('style');
  style.type = 'text/css';
  document.head.appendChild(style);
  style.appendChild(document.createTextNode(container));
}

interface IProps extends React.HTMLAttributes<HTMLElement> {
  code: string;
  box?: boolean;
  boxSize?: number;
  size?: number;
  className?: string;
  classNameBox?: string;
  active?: boolean;
}

const Iconfont = (props: IProps) => {
  // console.log(active);
  const { box, boxSize = 32, size = 14, className, classNameBox, active, ...args } = props;
  return box ? (
    <div
      {...args}
      style={
        {
          '--icon-box-size': `${boxSize}px`,
          '--icon-size': `${size}px`,
        } as any
      }
      className={classnames(classNameBox, styles.iconBox, { [styles.activeIconBox]: active })}
    >
      <i className={classnames(className, styles.iconfont)}>{props.code}</i>
    </div>
  ) : (
    <i
      style={
        {
          '--icon-size': `${size}px`,
        } as any
      }
      className={classnames(className, styles.iconfont)}
      {...args}
    >
      {props.code}
    </i>
  );
};

export default Iconfont;
