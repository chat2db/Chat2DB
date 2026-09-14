import { memo } from 'react';
import RunSqlModal from './components/RunSqlModal';
import ImportFileModal from './components/RunSql';

export default memo(() => {
  return (
    <>
      <RunSqlModal />
      <ImportFileModal />
    </>
  );
});
