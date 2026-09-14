import i18n from '@/i18n';
import { useDashboardStore } from '@/store/dashboard/store';
import { Alert, Button, Skeleton } from 'antd';

export default function DashboardListFeedback() {
  const { status, queryDashboardList } = useDashboardStore((state) => ({
    status: state.dashboardListStatus,
    queryDashboardList: state.queryDashboardList,
  }));

  return (
    <div style={{ width: '100%', maxWidth: 320, padding: 12, boxSizing: 'border-box' }}>
      {status === 'error' ? (
        <Alert
          type="error"
          showIcon
          message={i18n('dashboard.list.loadFailed')}
          action={
            <Button size="small" onClick={() => queryDashboardList()}>
              {i18n('dashboard.list.retry')}
            </Button>
          }
        />
      ) : (
        <Skeleton active title={false} paragraph={{ rows: 3 }} />
      )}
    </div>
  );
}
