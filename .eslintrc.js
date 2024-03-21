module.exports = {
  parser: '@typescript-eslint/parser',
  env: {
    browser: true,
    es2021: true,
  },
  plugins: ['@typescript-eslint', 'babel', 'react-hooks', 'react'],
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:react/recommended',
    // 'airbnb-base', // airbnb-base中已经包含了eslint-plugin-import
    // 'prettier', // 使得eslint中的样式规范失效，遵循prettier中的样式规范
    // 'prettier/@typescript-eslint', // 使得@typescript-eslint中的样式规范失效，遵循prettier中的样式规范
  ],
  overrides: [
    {
      env: {
        node: true,
      },
      files: ['.eslintrc.{js,cjs}'], //
      parserOptions: {
        sourceType: 'script',
      },
    },
  ],
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
  },
  ignorePatterns: ['chat2db-client/src/main'],
  rules: {
    'func-names': 0, // 函数表达式必须有名字
    'one-var': [1, 'never'], // 连续声明
    'prefer-const': 1, // 首选const
    'no-unused-expressions': 0, // 禁止无用的表达式
    'new-cap': 2, // 构造函数首字母大写
    'prefer-arrow-callback': 2, // 首选箭头函数
    'arrow-body-style': 0, // 箭头函数体使用大括号
    'max-len': [
      // 一行最大长度
      1,
      {
        code: 120,
        ignoreStrings: true,
        ignoreUrls: true,
        ignoreRegExpLiterals: true,
      },
    ],
    'consistent-return': 'off', // return 后面是否允许省略
    'default-case': 2, // switch 语句必须有 default
    'prefer-rest-params': 2, // 必须使用解构 ...args 来代替 arguments
    'no-script-url': 0, // 禁止使用 javascript:void(0)
    // 'no-console': [ // 禁止使用 console
    //   2,
    //   {
    //     allow: ['info', 'error', 'warn'],
    //   },
    // ],
    'no-duplicate-imports': [2], // 禁止重复 import
    'newline-per-chained-call': 2, // 链式调用必须换行
    // 'no-underscore-dangle': 2, // 禁止标识符中有悬空下划线
    'eol-last': 2, // 文件以单一的换行符结束
    'no-useless-rename': 2, // 禁止无用的重命名
    'no-undef': 0, // 禁止使用未定义的变量
    'class-methods-use-this': 0, // class 的非静态方法必须包含 this
    'prefer-destructuring': 0, // 优先使用数组和对象解构
    'no-unused-vars': 0, // 禁止未使用过的变量
    '@typescript-eslint/no-unused-vars': 1, // 禁止未使用过的变量
    'react/self-closing-comp': 2, // 非单行 JSX 必须使用括号包裹
    'react/jsx-indent-props': [2, 2], // jsx props 缩进
    'no-plusplus': 0, // 禁止使用 ++，--
    'react/jsx-uses-vars': 1, // jsx 文件中禁止使用变量
    // 'react/no-multi-comp': [ // 禁止一个文件中定义多个组件
    //   2,
    //   {
    //     ignoreStateless: true,
    //   },
    // ],
    'react/jsx-uses-react': 2, // jsx 文件中禁止使用 React
    'react/react-in-jsx-scope': 2, // jsx 文件中禁止使用 React
    'react/sort-comp': 1, // 组件内方法顺序
    'react/jsx-tag-spacing': 2, // jsx 中的属性禁止使用空格
    'react/jsx-no-bind': 0, // jsx 中禁止使用 bind
    'react/jsx-closing-bracket-location': 2, // jsx 中的右括号必须换行
    'react/prefer-stateless-function': 0, // 优先使用无状态组件
    'react/display-name': 0, // 组件必须写 displayName
    'react/prop-types': 0, // 组件必须写 propTypes
    'import/prefer-default-export': 0, // 优先使用 export default
    '@typescript-eslint/no-var-requires': 2, // 禁止 require() 使用表达式
    'no-use-before-define': 0, // 禁止定义前使用
    '@typescript-eslint/no-use-before-define': [
      // 禁止定义前使用
      0,
      // {
      //   functions: false,
      // },
    ],
    '@typescript-eslint/explicit-function-return-type': 0, // 函数必须有返回值
    '@typescript-eslint/interface-name-prefix': 0, // 接口名称必须以 I 开头
    '@typescript-eslint/explicit-module-boundary-types': 0, // 导出函数和类的公共方法必须声明返回类型
    'no-shadow': 0, // 禁止变量名与上层作用域内的定义过的变量重复
    '@typescript-eslint/no-shadow': 1, // 禁止变量名与上层作用域内的定义过的变量重复TODO: 为2是不是好点？
    'no-invalid-this': 0, // 禁止 this 关键字出现在类和类对象之外
    'no-await-in-loop': 'off', // 禁止在循环中出现 await
    'array-callback-return': 'off', // 数组方法的回调函数中必须有 return 语句
    'no-restricted-syntax': 'off', // 禁止使用特定的语法
    '@typescript-eslint/no-explicit-any': 0, // 禁止使用 any
    'import/no-extraneous-dependencies': 0, // 禁止使用无关的 package
    'import/no-unresolved': 0, // 禁止使用无关的 package
    '@typescript-eslint/explicit-member-accessibility': 0, // 类的成员之间是否需要空行
    '@typescript-eslint/no-object-literal-type-assertion': 0, // 禁止使用 as Type
    'react/no-find-dom-node': 0, // 禁止使用 findDOMNode
    'no-param-reassign': [
      // 禁止对函数参数再赋值
      2,
      {
        props: false,
      },
    ],
    'arrow-parens': 0, // 箭头函数参数括号
    indent: 0, // 缩进
    'operator-linebreak': [0], // 换行符位置
    'max-classes-per-file': [2, 10], // 一个文件最多定义几个类
    '@typescript-eslint/no-empty-function': [0], // 禁止空函数
    'import/extensions': 0, // 禁止导入文件时带上文件后缀
  },
};
