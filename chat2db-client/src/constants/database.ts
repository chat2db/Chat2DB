import mysqlLogo from '@/assets/img/databaseImg/mysql.png';
import redisLogo from '@/assets/img/databaseImg/redis.png';
import h2Logo from '@/assets/img/databaseImg/h2.png';
import moreDBLogo from '@/assets/img/databaseImg/other.png';
import { IDatabase } from '@/typings';
import { DatabaseTypeCode } from '@/constants'

export enum ConnectionEnvType {
  DAILY = 'DAILY',
  PRODUCT = 'PRODUCT',
}

export const databaseMap: {
  [keys: string]: IDatabase;
} = {
  [DatabaseTypeCode.MYSQL]: {
    name: 'MySQL',
    img: mysqlLogo,
    code: DatabaseTypeCode.MYSQL,
    // port: 3306,
    icon: '\uec6d',
  },
  [DatabaseTypeCode.H2]: {
    name: 'H2',
    img: h2Logo,
    code: DatabaseTypeCode.H2,
    // port: 9092,
    icon: '\ue61c',
  },
  [DatabaseTypeCode.ORACLE]: {
    name: 'Oracle',
    img: moreDBLogo,
    code: DatabaseTypeCode.ORACLE,
    // port: 1521,
    icon: '\uec48',
  },
  [DatabaseTypeCode.POSTGRESQL]: {
    name: 'PostgreSql',
    img: moreDBLogo,
    code: DatabaseTypeCode.POSTGRESQL,
    // port: 5432,
    icon: '\uec5d',
  },
  [DatabaseTypeCode.SQLSERVER]: {
    name: 'SQLServer',
    img: moreDBLogo,
    code: DatabaseTypeCode.SQLSERVER,
    // port: 1521,
    icon: '\ue664',
  },
  [DatabaseTypeCode.SQLITE]: {
    name: 'SQLite',
    img: moreDBLogo,
    code: DatabaseTypeCode.SQLITE,
    // port: 5432,
    icon: '\ue65a',
  },
  [DatabaseTypeCode.MARIADB]: {
    name: 'Mariadb',
    img: moreDBLogo,
    code: DatabaseTypeCode.MARIADB,
    // port: 3306,
    icon: '\ue6f5',
  },
  [DatabaseTypeCode.CLICKHOUSE]: {
    name: 'ClickHouse',
    img: moreDBLogo,
    code: DatabaseTypeCode.CLICKHOUSE,
    // port: 8123,
    icon: '\ue8f4',
  },
  [DatabaseTypeCode.DM]: {
    name: 'DM',
    img: moreDBLogo,
    code: DatabaseTypeCode.DM,
    // port: 5236,
    icon: '\ue655',
  },
  [DatabaseTypeCode.PRESTO]: {
    name: 'Presto',
    img: moreDBLogo,
    code: DatabaseTypeCode.PRESTO,
    //  port: 8080,
    icon: '\ue60b',
  },
  [DatabaseTypeCode.DB2]: {
    name: 'DB2',
    img: moreDBLogo,
    code: DatabaseTypeCode.DB2,
    //  port: 50000,
    icon: '\ue60a',
  },
  [DatabaseTypeCode.OCEANBASE]: {
    name: 'OceanBase',
    img: moreDBLogo,
    code: DatabaseTypeCode.OCEANBASE,
    // port: 2883, 
    icon: '\ue982',
  },
  [DatabaseTypeCode.HIVE]: {
    name: 'Hive',
    img: moreDBLogo,
    code: DatabaseTypeCode.HIVE,
    // port: 10000,
    icon: '\ue60e',
  },
  [DatabaseTypeCode.KINGBASE]: {
    name: 'KingBase',
    img: moreDBLogo,
    code: DatabaseTypeCode.KINGBASE,
    // port: 54321,
    icon: '\ue6a0',
  },
  [DatabaseTypeCode.MONGODB]: {
    name: 'MongoDB',
    img: moreDBLogo,
    code: DatabaseTypeCode.MONGODB,
    // port: 27017,
    icon: '\uec21',
  },
  [DatabaseTypeCode.XUGUDB]: {
    name: 'XuguDB',
    img: moreDBLogo,
    code: DatabaseTypeCode.XUGUDB,
    // port: 5138,
    icon: '\uec21',
  },
  // [DatabaseTypeCode.REDIS]: {
  //   name: 'Redis',
  //   img: moreDBLogo,
  //   code: DatabaseTypeCode.REDIS,
  //   // port: 6379,
  //   icon: '\ue6a2',
  // },
};

export const databaseTypeList = Object.keys(databaseMap).map((keys) => {
  return databaseMap[keys];
});
