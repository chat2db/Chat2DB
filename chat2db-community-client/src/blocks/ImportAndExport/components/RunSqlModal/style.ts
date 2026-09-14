import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ css, token }) => {
  return {
    uploadDragger: css`
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 63px 0px;
      p{
        font-size: 14px;
      }
    `,
    uploadDraggerIcon: css`
      color: ${token.colorPrimary};
    `,
    checkboxBody: css`
      .ant-form-item {
        margin-bottom: 0;
      }
      .ant-form-item-control-input{
        min-height: 30px;
      }
    `,
  };
});
