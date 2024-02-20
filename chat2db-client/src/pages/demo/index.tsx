import React, { useEffect } from 'react';
import sqlService from '@/service/sql';

function Test() {
  const sql = "INSERT INTO `big_data_table` (`name1`,`name2`,`name3`,`name4`,`name5`,`name6`,`name7`,`name8`,`name9`,`name10`,`name11`,`name21`,`name31`,`name41`,`name51`,`name61`,`name71`,`name81`,`name91`,`name12`,`name22`,`name32`,`name42`,`name52`,`name62`,`name72`,`name82`,`name92`,`name13`,`name223`,`name323`,`name423`,`name523`,`name623`,`name723`,`name823`,`name923`) VALUES ('牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛','牛牛牛牛牛牛牛牛牛牛牛牛');";
  const sqls:any = [];
  for (let i = 30; i < 1030; i++) {
    sqls.push(sql)
  }
  const a = () =>{
    const executeSQLParams = {
      sql: sqls.join(''),
      dataSourceId:2,
      databaseName: "e-commerc",
    };
    // 获取当前SQL的查询结果
    return sqlService.executeSql(executeSQLParams).finally(()=>{
      a()
    });
  }
  useEffect(() => {
    a()
  },[])
  return 11111;
}

export default Test;
