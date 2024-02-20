/**
 * 树的store
 */
import { create, UseBoundStore, StoreApi } from 'zustand';
import { devtools } from 'zustand/middleware';

export interface ITreeStore {
  focusId: number | string | null;
  focusTreeNode: {
    dataSourceId: number;
    dataSourceName: string;
    databaseType: string,
    databaseName?: string;
    schemaName?: string,
    tableName?: string,
  } | null;
}

const treeStore = {
  focusId: null,
  focusTreeNode: null,
}

export const useTreeStore: UseBoundStore<StoreApi<ITreeStore>> = create(
  devtools(() => (treeStore)),
);

export const setFocusId = (focusId: ITreeStore['focusId']) => {
  useTreeStore.setState({ focusId });
}

export const setFocusTreeNode = (focusTreeNode: ITreeStore['focusTreeNode']) => {
  useTreeStore.setState({ focusTreeNode });
}

// 清除treeStore
export const clearTreeStore = () => {
  useTreeStore.setState(treeStore);
}
