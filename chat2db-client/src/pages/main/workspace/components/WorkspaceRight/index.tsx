import React, { memo, useRef, useCallback } from 'react';
import styles from './index.less';
// import classnames from 'classnames';
import WorkspaceExtendBody from '../WorkspaceExtend/WorkspaceExtendBody';
import WorkspaceExtendNav from '../WorkspaceExtend/WorkspaceExtendNav';
import { useWorkspaceStore } from '@/pages/main/workspace/store';
import { setPanelRightWidth } from '@/pages/main/workspace/store/config';

// ----- components -----
import WorkspaceTabs from '../WorkspaceTabs';
import DraggableContainer from '@/components/DraggableContainer';

const WorkspaceRight = memo(() => {
  const draggableRef = useRef<any>();

  const { currentWorkspaceExtend, panelRight, panelRightWidth } = useWorkspaceStore((state) => {
    return {
      currentWorkspaceExtend: state.currentWorkspaceExtend,
      panelRight: state.layout.panelRight,
      panelRightWidth: state.layout.panelRightWidth,
    };
  });

  const draggableContainerResize = useCallback((data: number) => {
    setPanelRightWidth(data);
  }, []);

  return (
    <div className={styles.workspaceRight}>
      <DraggableContainer
        onResize={draggableContainerResize}
        showLine={!!currentWorkspaceExtend}
        min={200}
        className={styles.draggableContainer}
      >
        <WorkspaceTabs />
        <div
          ref={draggableRef}
          className={styles.workspaceExtendBody}
          style={{
            display: currentWorkspaceExtend && panelRight ? 'block' : 'none',
            width: `${panelRightWidth || 0}px`,
          }}
        >
          <WorkspaceExtendBody />
        </div>
      </DraggableContainer>
      {panelRight && <WorkspaceExtendNav className={styles.workspaceExtendNav} />}
    </div>
  );
});

export default WorkspaceRight;
