import { useEffect, useState, useRef, useCallback } from 'react';
import lodash from 'lodash';

// 多选行 hooks
const useMultipleSelect = (props: {
  curOperationRowNo: Array<string> | null;
  setCurOperationRowNo: (rowNo: Array<string> | null) => void;
  tableData: { [key: string]: string | null }[];
  colNoCode: string;
  setFocusedContent: (content: any[][]) => void;
}) => {
  const { curOperationRowNo, setCurOperationRowNo, tableData, colNoCode,setFocusedContent } = props;
  // 是否按下了shift键
  const isShiftDownRef = useRef<boolean>(false);
  // 是否按下了cmd键
  const isCmdDownRef = useRef<boolean>(false);

  // 如果用useState，因为异步会导致第一次点击失效
  // const [isShiftDown, setIsShiftDown] = useState<boolean>(false);

  // 第一次选中的行号，用于判断是否是连续选中
  const [firstOperationRowNo, setFirstOperationRowNo] = useState<string | null>(null);

  useEffect(()=>{
    if(!curOperationRowNo){
      setFirstOperationRowNo(null)
    }
  },[curOperationRowNo])

  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.keyCode === 16) {
        isShiftDownRef.current = true;
      }
      if (event.keyCode === 91 || event.keyCode === 17) {
        isCmdDownRef.current = true;
      }
    };

    const handleKeyUp = (event) => {
      if (event.keyCode === 16) {
        isShiftDownRef.current = false;
      }
      if (event.keyCode === 91) {
        isCmdDownRef.current = false;
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    document.addEventListener('keyup', handleKeyUp);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('keyup', handleKeyUp);
    };
  }, []);

  const copyTableRowData = (rowIds)=>{
    const newRowDatas = tableData.filter((item) => rowIds.includes(item[colNoCode]!));
    const newRowDatasList = newRowDatas.map((item) => {
      const _item = lodash.cloneDeep(item);
      delete _item[colNoCode];
      return Object.keys(_item).map((i) => _item[i]);
    });
    setFocusedContent(newRowDatasList);
  }

  const multipleSelect = useCallback(
    (newClickRowNo: string | null) => {
      if (newClickRowNo === null) {
        setCurOperationRowNo(null);
        return;
      }

      if (isShiftDownRef.current && firstOperationRowNo) {
        // 1. 在tableData中找到firstOperationRowNo所在的index
        const firstOperationRowIndex = tableData.findIndex((item) => item[colNoCode] === firstOperationRowNo);
        // 2. 在tableData中找到newClickRowNo所在的index
        const newClickRowIndex = tableData.findIndex((item) => item[colNoCode] === newClickRowNo);
        // 3. 从table中截取firstOperationRowIndex到newClickRowIndex的数据
        // 前序号
        let front = 0;
        // 后序号
        let back = 0;
        if (firstOperationRowIndex < newClickRowIndex) {
          front = firstOperationRowIndex;
          back = newClickRowIndex;
        } else {
          front = newClickRowIndex;
          back = firstOperationRowIndex;
        }
        const newCurOperationRowNo = tableData.slice(front, back + 1).map((item) => item[colNoCode]!);
        setCurOperationRowNo(newCurOperationRowNo);
        copyTableRowData(newCurOperationRowNo)
        return;
      }

      if (isCmdDownRef.current) {
        // 如果是cmd键，就是多选
        if (curOperationRowNo) {
          if (curOperationRowNo.includes(newClickRowNo)) {
            // 如果已经选中了，就取消选中
            const newCurOperationRowNo = curOperationRowNo.filter((item) => item !== newClickRowNo);
            setCurOperationRowNo(newCurOperationRowNo);
            copyTableRowData(newCurOperationRowNo)
            return;
          }
          // 如果没有选中，就添加选中
          const newCurOperationRowNo = [...curOperationRowNo, newClickRowNo];
          setCurOperationRowNo(newCurOperationRowNo);
          copyTableRowData(newCurOperationRowNo)
          return;
        }
        // 如果没有选中，就添加选中
        setCurOperationRowNo([newClickRowNo]);
        copyTableRowData([newClickRowNo])
        return;
      }

      setFirstOperationRowNo(newClickRowNo);
      setCurOperationRowNo([newClickRowNo]);
      copyTableRowData([newClickRowNo])
    },
    [isShiftDownRef.current, firstOperationRowNo,curOperationRowNo],
  );

  return {
    multipleSelect
  };
};

export default useMultipleSelect;
