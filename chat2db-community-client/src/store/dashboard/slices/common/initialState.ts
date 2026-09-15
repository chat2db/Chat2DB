import { IPageParams } from '@/typings/common';
import { IDashboardItem } from '@/typings/dashboard';

const defaultPageParam: IPageParams = { pageNo: 1, pageSize: 20, hasNextPage: true };

export interface CommonState {
  /** Currently selected Dashboard */
  currentDashboard?: IDashboardItem | null;
  /** Dashboard list */
  dashboardList: IDashboardItem[];
  dashboardListStatus: 'idle' | 'loading' | 'success' | 'error';
  /** Query parameters for Dashboard list */
  dashboardListParams: IPageParams;
}

export const initCommonState: CommonState = {
  currentDashboard: null,
  dashboardList: [],
  dashboardListStatus: 'idle',
  dashboardListParams: defaultPageParam,
};
