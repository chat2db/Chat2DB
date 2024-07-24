import { IChartItem, IChartType, IConnectionDetails } from '@/typings';
import React, { useEffect, useRef, useState, useMemo } from 'react';
import styles from './index.less';
import addImage from '@/assets/img/add.svg';
import cs from 'classnames';
import Line from '../chart/line';
import Pie from '../chart/pie';
import Bar from '../chart/bar';
import { MoreOutlined } from '@ant-design/icons';
import { Button, Dropdown, Form, message, Select, Spin } from 'antd';
import { deleteChart, getChartById, updateChart } from '@/service/dashboard';
import ConsoleEditor from '@/components/ConsoleEditor';
import Iconfont from '@/components/Iconfont';
import sqlService, { IExecuteSqlParams } from '@/service/sql';
import { Option } from '@/typings/common';
import { handleDatabaseAndSchema } from '@/utils/database';
import i18n from '@/i18n';
import { isValid } from '@/utils/check';

const handleSQLResult2ChartData = (data) => {
  const { headerList, dataList } = data;
  const mockData = headerList?.reduce((acc, cur, index) => {
    acc[cur.name] = {
      ...cur,
      data: dataList?.map((i: any) => i[index]),
    };
    return acc;
  }, {});
  return mockData;
};

function countArrayElements<T>(arr: T[]): { name: T; value: number }[] {
  const counts = new Map<T, number>();
  // 统计每个元素出现的次数
  arr.forEach((item) => {
    if (counts.has(item)) {
      counts.set(item, counts.get(item)! + 1);
    } else {
      counts.set(item, 1);
    }
  });
  // 转换为数组形式
  const result: { name: T; value: number }[] = [];
  for (const [key, value] of counts.entries()) {
    result.push({ name: key, value });
  }
  return result;
}

interface IChartItemProps {
  id: number;
  isEditing?: boolean;
  canAddRowItem: boolean;
  connectionList: any[];
  remainingUse: any;
  onDelete?: (id: number) => void;
  addChartTop?: () => void;
  addChartBottom?: () => void;
  addChartLeft?: () => void;
  addChartRight?: () => void;
}

function ChartItem(props: IChartItemProps) {
  const { connectionList, id } = props;
  const [cascaderOption, setCascaderOption] = useState<Option[]>([]);
  const [curConnection, setCurConnection] = useState<IConnectionDetails>();
  const [chartData, setChartData] = useState<IChartItem>({});
  const [chartMetaData, setChartMetaData] = useState<any>();
  const [, setCascaderValue] = useState<(string | number)[]>([]);
  const [isEditing, setIsEditing] = useState<boolean>(props.isEditing ?? false);
  const [isLoading, setIsLoading] = useState(false);
  const [initDDL, setInitDDL] = useState('');
  const [form] = Form.useForm(); // 创建一个表单实例
  const chartRef = useRef<any>();

  useEffect(() => {
    if (id !== undefined) {
      queryChartData();
    }
  }, [id]);

  useEffect(() => {
    if (connectionList && connectionList.length > 0) {
      setCurConnection(connectionList[0]);
      setCascaderOption(
        (connectionList || []).map((c) => ({
          value: c.id,
          label: c.alias,
          isLeaf: false,
        })),
      );
    }
  }, [connectionList]);

  useEffect(() => {
    if (!curConnection) {
      return;
    }
    setChartData({
      ...chartData,
      dataSourceId: curConnection.id,
      type: curConnection.type,
    });

    queryDatabaseAndSchemaList(curConnection.id);
  }, [curConnection]);

  useEffect(() => {
    handleChartConfigChange();
  }, [chartData.sqlData]);

  const queryDatabaseAndSchemaList = async (dataSourceId: number) => {
    const res = await sqlService.getDatabaseSchemaList({ dataSourceId });
    const dataSource = (cascaderOption || []).find((c) => c.value === dataSourceId);
    if (!dataSource) return;

    dataSource.children = handleDatabaseAndSchema(res);
    setCascaderOption([...cascaderOption]);
  };

  const handleExecuteSQL = async (sql: string, _chartData: IChartItem) => {
    const { dataSourceId, databaseName, schemaName } = _chartData;
    if (!isValid(dataSourceId)) {
      message.success(i18n('dashboard.editor.execute.noDataSource'));
      return;
    }
    setIsLoading(true);
    try {
      const executeSQLParams: IExecuteSqlParams = {
        sql,
        dataSourceId,
        databaseName,
        schemaName
      };
      // 获取当前SQL的查询结果
      const sqlResult = await sqlService.executeSql(executeSQLParams);

      let sqlData;
      if (sqlResult && sqlResult[0]) {
        sqlData = handleSQLResult2ChartData(sqlResult[0]);
      }
      setChartData({
        ..._chartData,
        ddl: sql,
        sqlData,
      });
      message.success(i18n('dashboard.editor.execute.success'));
    } finally {
      setIsLoading(false);
    }
  };

  /** 根据id请求Chart数据 */
  const queryChartData = async () => {
    setIsLoading(true);

    const res = await getChartById({ id: props.id });
    if (!res.dataSourceId) {
      res.connectable = undefined;
    }
    setChartData(res);

    // 设置级联value
    const cascaderKey = ['dataSourceId', 'databaseName', 'schemaName'];
    const _cascaderValue = cascaderKey.map((k: string) => res[k]).filter((i) => !!i);
    setCascaderValue(_cascaderValue);

    // 设置Chart参数，eg ChartType、xAxis、yAxis
    const formValue = JSON.parse(res.schema || '{}');
    form.setFieldsValue(formValue);

    if (res.ddl && res.connectable) {
      setInitDDL(res.ddl);
      handleExecuteSQL(res.ddl, res);
      // let p: IExecuteSqlParams = {
      //   sql: res?.ddl ?? '',
      //   dataSourceId,
      //   databaseName,
      // };
      // sqlService.executeSql(p).then((result) => {
      //   let sqlData;
      //   if (result && result[0]) {
      //     sqlData = handleSQLResult2ChartData(result[0]);
      //   }

      //   setChartData({
      //     ...res,
      //     ...chartData,
      //     sqlData,
      //   });
      // });
    }
    setIsLoading(false);
  };

  const onExport2Image = () => {
    const echartInstance = chartRef.current.getEchartsInstance();
    const img = new Image();
    img.src = echartInstance.getDataURL({
      type: 'png',
      devicePixelRatio: 4,
      backgroundColor: '#FFF',
    });
    img.onload = function () {
      const canvas = document.createElement('canvas');
      canvas.width = img.width;
      canvas.height = img.height;
      const ctx = canvas.getContext('2d');
      ctx?.drawImage(img, 0, 0);
      const dataURL = canvas.toDataURL('image/png');

      const a = document.createElement('a');
      const event = new MouseEvent('click');
      a.download = 'image.png';
      a.href = dataURL;
      a.dispatchEvent(event);
    };
  };

  const onDeleteChart = () => {
    deleteChart({ id: props.id });
    props.onDelete && props.onDelete(props.id);
  };

  const handleSaveChart = async () => {
    const params: IChartItem = {
      id: props.id,
      ...chartData,
      schema: JSON.stringify(form.getFieldsValue(true)),
    };
    await updateChart(params);
    setIsEditing(false);
    message.success(i18n('common.tips.saveSuccessfully'));
  };

  const handleChartConfigChange = () => {
    const { sqlData = {} } = chartData || {};
    const { chartType, xAxis, yAxis } = form.getFieldsValue(true);
    // let xAxisOptions: Array<{ label: string; value: string }> = [];
    // let yAxisOptions: Array<{ label: string; value: string }> = [];

    if (chartType === IChartType.Pie) {
      const dimension = sqlData[xAxis];
      const { data = [] } = dimension || {};
      const finallyData = countArrayElements(data);
      setChartMetaData(finallyData);
    } else if (chartType === IChartType.Line) {
      const dimensionX = sqlData[xAxis]?.data;
      const dimensionY = sqlData[yAxis]?.data;
      setChartMetaData({
        xAxis: dimensionX,
        yAxis: dimensionY,
      });
    } else if (chartType === IChartType.Column) {
      const dimensionX = sqlData[xAxis]?.data;
      const dimensionY = sqlData[yAxis]?.data;
      setChartMetaData({
        xAxis: dimensionX,
        yAxis: dimensionY,
      });
    }
  };

  const renderPlusIcon = () => {
    return (
      <>
        {props.canAddRowItem && (
          <div onClick={props.addChartLeft} className={styles.left_overlay_add}>
            <div className={styles.add_chart_icon}>
              <img className={styles.add_chart_plus_icon} src={addImage} alt="Add chart" />
            </div>
          </div>
        )}
        {props.canAddRowItem && (
          <div onClick={props.addChartRight} className={styles.right_overlay_add}>
            <div className={styles.add_chart_icon}>
              <img className={styles.add_chart_plus_icon} src={addImage} alt="Add chart" />
            </div>
          </div>
        )}
        <div onClick={props.addChartTop} className={styles.top_overlay_add}>
          <div className={cs(styles.add_chart_icon, styles.add_chart_icon_y)}>
            <img className={styles.add_chart_plus_icon} src={addImage} alt="Add chart" />
          </div>
        </div>
        <div onClick={props.addChartBottom} className={styles.bottom_overlay_add}>
          <div className={cs(styles.add_chart_icon, styles.add_chart_icon_y)}>
            <img className={styles.add_chart_plus_icon} src={addImage} alt="Add chart" />
          </div>
        </div>
      </>
    );
  };

  const renderChart = () => {
    // const { chartType } = chartData || {};
    // const { chartType } = JSON.parse(schema) || {};
    const { chartType } = form.getFieldsValue(true);
    switch (chartType) {
      case IChartType.Pie:
        return <Pie ref={chartRef} data={chartMetaData} />;
      case IChartType.Line:
        return <Line ref={chartRef} data={chartMetaData} />;
      case IChartType.Column:
        return <Bar ref={chartRef} data={chartMetaData} />;
      default:
        return (
          <div style={{ height: '120px', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
            <Iconfont code="&#xe638;" className={styles.emptyDataImage} />
          </div>
        );
    }
  };

  const renderEmptyBlock = () => {
    return (
      <div className={styles.emptyChartBlock}>
        <Iconfont code="&#xe638;" className={styles.emptyDataImage} />

        <div className={styles.emptyDataText}>No data selected</div>
        <Button
          type="primary"
          onClick={() => {
            setIsEditing(true);
          }}
        >
          Add Data
        </Button>
      </div>
    );
  };

  const initDDLMemo = useMemo(() => {
    return {
      text: initDDL,
      range: 'front',
    };
  }, [initDDL]);

  const setBoundInfo = (boundInfo) => {
    setChartData({
      ...chartData,
      ...boundInfo,
    });
  };

  const renderEditorBlock = () => {
    const { sqlData = {} } = chartData || {};
    const options = Object.keys(sqlData).map((i) => ({ label: i, value: i }));

    return (
      <div className={styles.editBlock}>
        <div className={styles.editorBlock}>
          <div className={styles.editor}>
            <ConsoleEditor
              defaultValue={initDDLMemo.text}
              boundInfo={chartData as any}
              setBoundInfo={setBoundInfo}
              hasAiChat={true}
              hasAi2Lang={false}
              hasSaveBtn={false}
              value={chartData?.ddl}
              onExecuteSQL={(sql: string) => handleExecuteSQL(sql, chartData)}
              editorOptions={{
                lineNumbers: 'off',
              }}
              isActive={true}
            />
            {/* <Cascader
              options={cascaderOption}
              value={cascaderValue}
              loadData={loadData}
              onChange={(value, selectedOptions) => {
                const p: any = {
                  dataSourceId: '',
                };
                //包含了dataSourceId、databaseName、schemaName
                (selectedOptions || []).forEach((o: any) => {
                  if (o.type) {
                    p[`${o.type}Name`] = o.value;
                  } else {
                    p.dataSourceId = o.value;
                  }
                });
                setCascaderValue(value);
                setChartData({
                  ...chartData,
                  ...p,
                });
              }}
              className={styles.dataSourceSelect}
              placeholder={i18n('dashboard.editor.cascader.placeholder')}
              // style={{ width: '100%' }}
            /> */}
          </div>
          <div className={styles.chartParamsForm}>
            <div className={styles.chartParamsFormTitle}>Charts:</div>
            <Form
              form={form}
              // labelCol={{ span: 24 }}
              // wrapperCol={{ span: 24 }}
              layout="vertical"
              autoComplete="off"
              onValuesChange={handleChartConfigChange}
              style={{ width: '100%' }}
            >
              <Form.Item label={'Chart Type'} name={'chartType'}>
                <Select
                  options={[
                    { label: 'Line', value: 'Line' },
                    { label: 'Pie', value: 'Pie' },
                    { label: 'Column', value: 'Column' },
                  ]}
                />
              </Form.Item>
              <Form.Item label={'xAxis'} name={'xAxis'}>
                <Select options={options} />
              </Form.Item>
              <Form.Item label={'yAxis'} name={'yAxis'} hidden={form.getFieldValue('chartType') === IChartType.Pie}>
                <Select options={options} />
              </Form.Item>
            </Form>
          </div>
        </div>
        <div className={styles.editorOptionBlock}>
          <Button type="primary" style={{ marginRight: '8px' }} onClick={handleSaveChart}>
            {i18n('common.button.confirm')}
          </Button>
          <Button
            onClick={() => {
              setIsEditing(false);
            }}
          >
            {i18n('common.button.cancel')}
          </Button>
        </div>
      </div>
    );
  };

  return (
    <Spin spinning={isLoading}>
      <div className={styles.container}>
        {renderPlusIcon()}
        <div className={styles.titleBar}>
          <div className={styles.title}>{chartData?.name}</div>
          <div>
            <Dropdown
              menu={{
                items: [
                  {
                    key: 'Edit',
                    label: i18n('dashboard.edit'),
                    onClick: () => {
                      setIsEditing(true);
                    },
                  },
                  {
                    key: 'Export',
                    label: i18n('dashboard.export2image'),
                    onClick: onExport2Image,
                  },
                  {
                    key: 'delete',
                    label: i18n('dashboard.delete'),
                    onClick: onDeleteChart,
                  },
                ],
              }}
              placement="bottomLeft"
            >
              <MoreOutlined className={styles.edit} />
            </Dropdown>
          </div>
        </div>
        {chartData?.sqlData || isEditing ? renderChart() : renderEmptyBlock()}
        {isEditing && renderEditorBlock()}
      </div>
    </Spin>
  );
}

export default ChartItem;
