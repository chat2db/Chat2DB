import React from 'react';
import { useCommonStore } from './index';
export interface IAppTitleBarConfig {
  appTitleBarRightComponent: React.ReactNode | null;
}

export const initAppTitleBarConfig = {
  appTitleBarRightComponent: null,
};

export const setAppTitleBarRightComponent: (appTitleBarRightComponent: React.ReactNode | null) => void = (
  appTitleBarRightComponent,
) => {
  return useCommonStore.setState({ appTitleBarRightComponent });
};
