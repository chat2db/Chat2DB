import { StoreApi } from 'zustand';
import { UseBoundStoreWithEqualityFn, createWithEqualityFn } from 'zustand/traditional';
import { devtools } from 'zustand/middleware';
import { shallow } from 'zustand/shallow';

export interface IConfigStore {
  curRoute: string;
}

const initConfigStore: IConfigStore = {
  curRoute: '/',
};

/**
 * 配置 store
 */
export const useConfigStore: UseBoundStoreWithEqualityFn<StoreApi<IConfigStore>> = createWithEqualityFn(
  devtools(() => initConfigStore),
  shallow,
);

/**
 *
 * @param curRoute 设置当前路由
 */
export const setCurRoute = (curRoute: string) => {
  useConfigStore.setState({ curRoute });
}

