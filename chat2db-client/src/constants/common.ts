export enum DatabaseTypeCode {
  MYSQL = 'MYSQL',
  ORACLE = 'ORACLE',
  DB2 = 'DB2',
  MONGODB = 'MONGODB',
  REDIS = 'REDIS',
  H2 = 'H2',
  POSTGRESQL = 'POSTGRESQL',
  SQLSERVER = 'SQLSERVER',
  SQLITE = 'SQLITE',
  MARIADB = 'MARIADB',
  CLICKHOUSE = 'CLICKHOUSE',
  DM = "DM",
  OCEANBASE = "OCEANBASE",
  PRESTO = "PRESTO",
  HIVE = "HIVE",
  KINGBASE = "KINGBASE",
  XUGUDB = "XUGUDB",
}

export enum ConsoleStatus {
  DRAFT = 'DRAFT',
  RELEASE = 'RELEASE',
}

export enum OSType {
  WIN = 'Win',
  MAC = 'Mac',
  RESTS = 'rests',
}

export enum ConnectionKind {
  Private = 'PRIVATE',
  Shared = 'SHARED'
}

// 通用的增删改查枚举
export enum CRUD {
  CREATE = 'CREATE',
  READ = 'READ',
  UPDATE = 'UPDATE',
  DELETE = 'DELETE',
  UPDATE_COPY = 'UPDATE_COPY',
}
