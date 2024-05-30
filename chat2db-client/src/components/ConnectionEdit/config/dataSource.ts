import { DatabaseTypeCode, OperationColumn } from '@/constants';
import { IConnectionConfig } from './types';
import { InputType, AuthenticationType } from './enum';
import i18n from '@/i18n';

export const sshConfig: IConnectionConfig['ssh'] = {
  items: [
    {
      defaultValue: false,
      inputType: InputType.SELECT,
      labelNameCN: '使用SSH',
      labelNameEN: 'USE SSH',
      name: 'use',
      required: false,
      selects: [
        {
          label: 'false',
          value: false,
        },
        {
          label: 'true',
          value: true,
        },
      ],

    },
    {
      defaultValue: '',
      inputType: InputType.INPUT,
      labelNameCN: 'SSH 主机',
      labelNameEN: 'SSH Hostname',
      name: 'hostName',
      required: false,
      styles: {
        width: '70%',
      }
    },
    {
      defaultValue: '22',
      inputType: InputType.INPUT,
      labelNameCN: 'SSH 端口',
      labelNameEN: 'Port',
      name: 'port',
      required: false,
      styles: {
        width: '30%',
        labelWidthEN: '40px',
        labelWidthCN: '70px',
        labelAlign: 'right'
      }
    },
    {
      defaultValue: '',
      inputType: InputType.INPUT,
      labelNameCN: '用户名',
      labelNameEN: 'SSH UserName',
      name: 'userName',
      required: false,
      styles: {
        width: '70%',
      }
    },
    {
      defaultValue: '',
      inputType: InputType.INPUT,
      labelNameCN: '本地端口',
      labelNameEN: 'LocalPort',
      name: 'localPort',
      placeholder: '不必填',
      placeholderEN: 'Need not fill in',
      required: false,
      styles: {
        width: '30%',
        labelWidthEN: '70px',
        labelWidthCN: '70px',
        labelAlign: 'right'
      }
    },
    {
      defaultValue: 'password',
      inputType: InputType.SELECT,
      labelNameCN: '身份验证',
      labelNameEN: 'Authentication',
      name: 'authenticationType',
      required: true,
      selects: [
        {
          items: [
            {
              defaultValue: '',
              inputType: InputType.PASSWORD,
              labelNameCN: '密码',
              labelNameEN: 'Password',
              name: 'password',
              required: true,
            },
          ],
          label: 'password',
          value: 'password',
        },
        {
          items: [
            {
              defaultValue: '',
              inputType: InputType.INPUT,
              labelNameCN: '密钥文件',
              labelNameEN: 'Private key file',
              name: 'keyFile',
              required: true,
              placeholder: '/user/userName/.ssh/xxxx',
              placeholderEN: '/user/userName/.ssh/xxxx',
            },
            {
              defaultValue: '',
              inputType: InputType.PASSWORD,
              labelNameCN: '密码短语',
              labelNameEN: 'Passphrase',
              name: 'passphrase',
              required: true,
            },
          ],
          label: 'Private key',
          value: 'keyFile',
        },
      ],
      styles: {
        width: '50%',
      }
    },

  ]
}

const  envItem = {
  defaultValue: '',
  inputType: InputType.SELECT,
  labelNameCN: '环境',
  labelNameEN: 'Env',
  name: 'environmentId',
  required: true,
  selects: [],
  styles: {
    width: '50%',
  }
}

export const dataSourceFormConfigs: IConnectionConfig[] = [
  // MYSQL
  {
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,
        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',
          }
        },
        {
          defaultValue: '3306',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,
                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,
                },
              ],
              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {
              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],

            },
          ],
          styles: {
            width: '50%',
          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,
        },
        {
          defaultValue: 'jdbc:mysql://localhost:3306',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,
        },
      ],
      pattern: /jdbc:mysql:\/\/(.*):(\d+)(\/(\w+))?/,
      template: 'jdbc:mysql://{host}:{port}/{database}',
    },
    ssh: sshConfig,
    extendInfo: [
      {
        "key": "zeroDateTimeBehavior",
        "value": "convertToNull"
      },
      {
        "key": "useInformationSchema",
        "value": "true"
      },
      {
        "key": "tinyInt1isBit",
        "value": "false"
      }
    ],
    type: DatabaseTypeCode.MYSQL,
  },
  // POSTGRESQL
  {
    type: DatabaseTypeCode.POSTGRESQL,
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,
        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',

          }
        },
        {
          defaultValue: '5432',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,

                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,

                },
              ],
              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {

              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],

            },
          ],
          styles: {
            width: '50%',

          }
        },
        {
          defaultValue: 'postgres',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,

        },
        {
          defaultValue: 'jdbc:postgresql://localhost:5432',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,

        },
      ],
      pattern: /jdbc:postgresql:\/\/(.*):(\d+)(\/(\w+))?/,
      template: 'jdbc:postgresql://{host}:{port}/{database}',
    },
    ssh: sshConfig,
  },
  // ORACLE
  {
    type: DatabaseTypeCode.ORACLE,
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,
        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',

          }
        },
        {
          defaultValue: '1521',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: 'sid',
          inputType: InputType.SELECT,
          labelNameCN: '链接类型',
          labelNameEN: 'Service type',
          name: 'serviceType',
          required: true,
          selects: [
            {
              label: 'SID',
              value: 'sid',
              items: [
                {
                  defaultValue: 'XE',
                  inputType: InputType.INPUT,
                  labelNameCN: 'SID',
                  labelNameEN: 'SID',
                  name: 'sid',
                  required: true,
                  styles: {
                    width: '70%',
                  }
                },
              ],
              onChange: (data: IConnectionConfig) => {
                data.baseInfo.pattern = /jdbc:oracle:(.*):@(.*):(\d+):(.*)/;
                data.baseInfo.template = 'jdbc:oracle:{driver}:@{host}:{port}:{sid}';
                return data
              }
            },
            {
              label: 'Service',
              value: 'service',
              items: [{
                defaultValue: 'XE',
                inputType: InputType.INPUT,
                labelNameCN: '服务名',
                labelNameEN: 'Service name',
                name: 'serviceName',
                required: true,
                styles: {
                  width: '70%',
                },
              }],
              onChange: (data: IConnectionConfig) => {
                data.baseInfo.pattern = /jdbc:oracle:(.*):@\/\/(.*):(\d+)\/(.*)/;
                data.baseInfo.template = 'jdbc:oracle:{driver}:@//{host}:{port}/{serviceName}';
                return data
              }
            },
          ],
          styles: {
            width: '50%',
          }
        },
        {
          defaultValue: 'thin',
          inputType: InputType.SELECT,
          labelNameCN: '驱动',
          labelNameEN: 'Driver',
          name: 'driver',
          required: true,
          labelTextAlign: 'right',
          selects: [
            {
              value: 'thin',
              label: 'thin',
            },
            {
              value: 'oci',
              label: 'oci',
            },
            {

              value: 'oci8',
              label: 'oci8',
            },
          ],
          styles: {
            width: '30%',
            labelWidthEN: '70px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,

                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,

                },
              ],
              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {

              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],

            },
          ],
          styles: {
            width: '50%',

          }
        },
        {
          defaultValue: 'jdbc:oracle:thin:@localhost:1521:XE',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,
        },
      ],
      pattern: /jdbc:oracle:(.*):@(.*):(\d+):(.*)/,
      template: 'jdbc:oracle:{driver}:@{host}:{port}:{sid}',
    },
    ssh: sshConfig,
  },
  // H2
  {
    type: DatabaseTypeCode.H2,
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,
        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',
          }
        },
        {
          defaultValue: '9092',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: 'TCP',
          inputType: InputType.SELECT,
          labelNameCN: '链接类型',
          labelNameEN: 'Service type',
          name: 'serviceType',
          required: true,
          selects: [
            {
              label: i18n('common.label.tcp'),
              value: 'TCP',
              items: [],
              onChange: (data: IConnectionConfig) => {
                data.baseInfo.pattern = /jdbc:h2:tcp:\/\/(.*):(\d+)(\/(\w+))?/;
                data.baseInfo.template = 'jdbc:h2:tcp://{host}:{port}/{database}';
                return data
              }
            },
            {
              label: i18n('common.label.LocalFile'),
              value: 'LocalFile',
              items: [
                {
                  defaultValue: '',
                  inputType: InputType.INPUT,
                  labelNameCN: 'File',
                  labelNameEN: 'File',
                  name: 'file',
                  required: true,
                },
              ],
              onChange: (data: IConnectionConfig) => {
                data.baseInfo.pattern = /jdbc:h2:(.*)?/;
                data.baseInfo.template = 'jdbc:h2:{file}';
                return data
              }
            },
          ],
          styles: {
            width: '70%',
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,

                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,

                },
              ],

              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {

              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],
            },
          ],
          styles: {
            width: '50%',

          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,
        },
        {
          defaultValue: 'jdbc:h2:tcp://localhost:9092',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,

        },
      ],
      pattern: /jdbc:h2:tcp:\/\/(.*):(\d+)(\/(\w+))?/,
      template: 'jdbc:h2:tcp://{host}:{port}/{database}',
    },
    ssh: sshConfig,
  },
  // SQLSERVER encrypt=true;trustServerCertificate=true;integratedSecurity=false;Trusted_Connection=yes
  {
    type: DatabaseTypeCode.SQLSERVER,
    extendInfo: [
      {
        "key": "encrypt",
        "value": "false"
      },
      {
        "key": "trustServerCertificate",
        "value": "true"
      },
      {
        "key": "integratedSecurity",
        "value": "false"
      },
      {
        "key": "Trusted_Connection",
        "value": "yes"
      },
    ],
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,
        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',
          }
        },
        {
          defaultValue: '1433',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: 'Instance',
          labelNameEN: 'Instance',
          name: 'instance',
          required: false,
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,

                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,

                },
              ],

              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {

              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],

            },
          ],
          styles: {
            width: '50%',

          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,
        },
        {
          defaultValue: 'jdbc:sqlserver://localhost:1433',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,
        },
      ],
      pattern: /jdbc:sqlserver:\/\/(.*):(\d+)(;database=(\w+))?/,
      template: 'jdbc:sqlserver://{host}:{port};database={database}',
    },
    ssh: sshConfig,
  },
  // SQLITE
  {
    type: DatabaseTypeCode.SQLITE,
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,

        },
        envItem,
        {
          defaultValue: 'identifier.sqlite',
          inputType: InputType.INPUT,
          labelNameCN: 'File',
          labelNameEN: 'File',
          name: 'file',
          required: true,
        },
        {
          defaultValue: 'jdbc:sqlite:identifier.sqlite',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,

        },
      ],
      pattern: /jdbc:sqlite:(.*)?/,
      template: 'jdbc:sqlite:{file}',
    },
    ssh: sshConfig,
  },
  // MARIADB
  {
    type: DatabaseTypeCode.MARIADB,
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,

        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',

          }
        },
        {
          defaultValue: '3306',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,

                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,

                },
              ],

              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {

              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],

            },
          ],
          styles: {
            width: '50%',

          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,

        },
        {
          defaultValue: 'jdbc:mariadb://localhost:3306',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,

        },
      ],
      pattern: /jdbc:mariadb:\/\/(.*):(\d+)(\/(\w+))?/,
      template: 'jdbc:mariadb://{host}:{port}/{database}',
    },
    ssh: sshConfig,
  },
  // CLICKHOUSE
  {
    type: DatabaseTypeCode.CLICKHOUSE,
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,

        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',

          }
        },
        {
          defaultValue: '8123',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,

                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,

                },
              ],

              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {

              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],

            },
          ],
          styles: {
            width: '50%',

          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,

        },
        {
          defaultValue: 'jdbc:clickhouse://localhost:8123',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,

        },
      ],
      pattern: /jdbc:clickhouse:\/\/(.*):(\d+)(\/(\w+))?/,
      template: 'jdbc:clickhouse://{host}:{port}/{database}',
      excludes: [OperationColumn.ViewDDL, OperationColumn.CreateTable,OperationColumn.EditTable]
      //排除掉导出ddl 和 创建表功能 支持的功能见 ./enum.ts => OperationColumn
    },
    ssh: sshConfig,
  },
  // DM
  {
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,

        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',

          }
        },
        {
          defaultValue: '5236',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,

                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,
                },
              ],
              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {
              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],
            },
          ],
          styles: {
            width: '50%',

          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,

        },
        {
          defaultValue: 'jdbc:dm://localhost:5236',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,

        },
      ],
      pattern: /jdbc:dm:\/\/(.*):(\d+)(\/(\w+))?/,
      template: 'jdbc:dm://{host}:{port}/{database}',
      // excludes: [OperationColumn.EditTable]

    },
    ssh: sshConfig,
    extendInfo: [
      {
        "key": "zeroDateTimeBehavior",
        "value": "convertToNull"
      },
    ],
    type: DatabaseTypeCode.DM
  },
  //DB2
  {
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,
          styles: {
            width: '100%',
          }
        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',
          }
        },
        {
          defaultValue: '50000',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
              ],
              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {
              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],

            },
          ],
          styles: {
            width: '50%',
          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,
          styles: {
            width: '100%',
          }
        },
        {
          defaultValue: 'jdbc:db2://localhost:50000',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,
          styles: {
            width: '100%',
          }
        },

      ],
      pattern: /jdbc:db2:\/\/(.*):(\d+)(\/(\w+))?/,
      template: 'jdbc:db2://{host}:{port}/{database}',
      // excludes: [OperationColumn.EditTable]

    },
    ssh: sshConfig,
    extendInfo: [

    ],
    type: DatabaseTypeCode.DB2
  },
  //presto
  {
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,
          styles: {
            width: '100%',
          }
        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',
          }
        },
        {
          defaultValue: '8080',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
              ],
              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {
              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],

            },
          ],
          styles: {
            width: '50%',
          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,
          styles: {
            width: '100%',
          }
        },
        {
          defaultValue: 'jdbc:presto://localhost:8080',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,
          styles: {
            width: '100%',
          }
        },

      ],
      pattern: /jdbc:presto:\/\/(.*):(\d+)(\/(\w+))?/,
      template: 'jdbc:presto://{host}:{port}/{database}',
      // excludes: [OperationColumn.EditTable]

    },
    ssh: sshConfig,
    extendInfo: [

    ],
    type: DatabaseTypeCode.PRESTO
  },
  //oceanbase
  {
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,
          styles: {
            width: '100%',
          }
        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',
          }
        },
        {
          defaultValue: '2883',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
              ],
              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {
              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],

            },
          ],
          styles: {
            width: '50%',
          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,
          styles: {
            width: '100%',
          }
        },
        {
          defaultValue: 'jdbc:oceanbase://localhost:2883',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,
          styles: {
            width: '100%',
          }
        },

      ],
      pattern: /jdbc:oceanbase:\/\/(.*):(\d+)(\/(\w+))?/,
      template: 'jdbc:oceanbase://{host}:{port}/{database}',
      // excludes: [OperationColumn.EditTable]

    },
    ssh: sshConfig,
    extendInfo: [

    ],
    type: DatabaseTypeCode.OCEANBASE
  },
  //redis
  {
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,
          styles: {
            width: '100%',
          }
        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',
          }
        },
        {
          defaultValue: '6379',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
              ],
              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {
              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],

            },
          ],
          styles: {
            width: '50%',
          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,
          styles: {
            width: '100%',
          }
        },
        {
          defaultValue: 'jdbc:redis://localhost:6379',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,
          styles: {
            width: '100%',
          }
        },

      ],
      pattern: /jdbc:redis:\/\/(.*):(\d+)(\/(\w+))?/,
      template: 'jdbc:redis://{host}:{port}/{database}',
    },
    ssh: sshConfig,
    extendInfo: [

    ],
    type: DatabaseTypeCode.REDIS
  },
  //hive
  {
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,
          styles: {
            width: '100%',
          }
        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',
          }
        },
        {
          defaultValue: '10000',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
              ],
              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {
              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],

            },
          ],
          styles: {
            width: '50%',
          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,
          styles: {
            width: '100%',
          }
        },
        {
          defaultValue: 'jdbc:hive2://localhost:10000',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,
          styles: {
            width: '100%',
          }
        },

      ],
      pattern: /jdbc:hive2:\/\/(.*):(\d+)(\/(\w+))?/,
      template: 'jdbc:hive2://{host}:{port}/{database}',
      // excludes: [OperationColumn.EditTable]

    },
    ssh: sshConfig,
    extendInfo: [

    ],
    type: DatabaseTypeCode.HIVE
  },
  //KINGBASE
  {
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,
          styles: {
            width: '100%',
          }
        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',
          }
        },
        {
          defaultValue: '54321',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
              ],
              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {
              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],

            },
          ],
          styles: {
            width: '50%',
          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,
          styles: {
            width: '100%',
          }
        },
        {
          defaultValue: 'jdbc:kingbase8://127.0.0.1:54321',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,
          styles: {
            width: '100%',
          }
        },

      ],
      pattern: /jdbc:kingbase8:\/\/(.*):(\d+)(\/(\w+))?/,
      template: 'jdbc:kingbase8://{host}:{port}/{database}',
      // excludes: [OperationColumn.EditTable]

    },
    ssh: sshConfig,
    extendInfo: [

    ],
    type: DatabaseTypeCode.KINGBASE
  },
  //MONGODB
  {
    baseInfo: {
      items: [
        {
          defaultValue: '@localhost',
          inputType: InputType.INPUT,
          labelNameCN: '名称',
          labelNameEN: 'Name',
          name: 'alias',
          required: true,
          styles: {
            width: '100%',
          }
        },
        envItem,
        {
          defaultValue: 'localhost',
          inputType: InputType.INPUT,
          labelNameCN: '主机',
          labelNameEN: 'Host',
          name: 'host',
          required: true,
          styles: {
            width: '70%',
          }
        },
        {
          defaultValue: '27017',
          inputType: InputType.INPUT,
          labelNameCN: '端口',
          labelNameEN: 'Port',
          name: 'port',
          labelTextAlign: 'right',
          required: true,
          styles: {
            width: '30%',
            labelWidthEN: '40px',
            labelWidthCN: '40px',
            labelAlign: 'right'
          }
        },
        {
          defaultValue: AuthenticationType.USERANDPASSWORD,
          inputType: InputType.SELECT,
          labelNameCN: '身份验证',
          labelNameEN: 'Authentication',
          name: 'authenticationType',
          required: true,
          selects: [
            {
              items: [
                {
                  defaultValue: 'root',
                  inputType: InputType.INPUT,
                  labelNameCN: '用户名',
                  labelNameEN: 'User',
                  name: 'user',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
                {
                  defaultValue: '',
                  inputType: InputType.PASSWORD,
                  labelNameCN: '密码',
                  labelNameEN: 'Password',
                  name: 'password',
                  required: true,
                  styles: {
                    width: '100%',
                  }
                },
              ],
              label: 'User&Password',
              value: AuthenticationType.USERANDPASSWORD,
            },
            {
              label: 'NONE',
              value: AuthenticationType.NONE,
              items: [],

            },
          ],
          styles: {
            width: '50%',
          }
        },
        {
          defaultValue: '',
          inputType: InputType.INPUT,
          labelNameCN: '数据库',
          labelNameEN: 'Database',
          name: 'database',
          required: false,
          styles: {
            width: '100%',
          }
        },
        {
          defaultValue: 'mongodb://localhost:27017',
          inputType: InputType.INPUT,
          labelNameCN: 'URL',
          labelNameEN: 'URL',
          name: 'url',
          required: true,
          styles: {
            width: '100%',
          }
        },

      ],
      pattern: /mongodb:\/\/(.*):(\d+)(\/(\w+))?/,
      template: 'mongodb://{host}:{port}/{database}',
      excludes: [OperationColumn.ViewDDL, OperationColumn.CreateTable,OperationColumn.EditTable]
    },
    ssh: sshConfig,
    extendInfo: [],
    type: DatabaseTypeCode.MONGODB
  },
];
