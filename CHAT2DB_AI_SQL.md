# Chat2DB AI SQL功能使用说明
Chat2DB包含一系列基于ChatGPT的AI SQL使用功能，主要包括自然语言转SQL、SQL解释、SQL优化和SQL转换。 使用这些AI功能，可以将自然语言转换成本地查询SQL，而不仅仅是SQL查询伪代码；可以将SQL解释成自然语言，帮助用户理解复杂的SQL；可以针对慢SQL提供全方位的优化建议，提升查询效率；可以转换不同数据库类型的SQL语言，降低数据库迁移难度等等。
## 使用配置
### 点击设置【UI旧】
<a><img src="https://img.alicdn.com/imgextra/i2/O1CN01hecdtO1acLegtiP9k_!!6000000003350-2-tps-2400-1600.png" width="100%"/></a>
### 配置AI
#### 配置OPENAI【UI旧】
使用OPENAI的ChatSql功能需要满足两个条件
- 配置OPENAI_API_KEY，如没有OPENAI_API_KEY可加入答疑群根据群公告指引获取chat2db自定义key
- 客户端网络可以连接到OPENAI官网，如果本地VPN未能全局生效，可以通过在客户端中设置网络代理HOST和PORT来保证网络连通性
<a><img src="https://img.alicdn.com/imgextra/i2/O1CN01anrJMI1FEtSBbmTau_!!6000000000456-0-tps-1594-964.jpg" width="100%"/></a>
#### 配置自定义AI【UI旧】
- 自定义AI可以是用户自己部署的任意AI模型，例如ChatGLM、ChatGPT、文心一言、通义千问等等，但是自定义的接口输入和输出需要符合自定义的协议规范才可快速使用，否则可能需要二次开发。代码中提供了两个DEMO，只需要配置自定义AI接口地址，以及接口是否流式输出即可查看。具体使用中可以参考DEMO接口来编写自定义接口，或者直接在DEMO接口中进行二次开发，封装自己的自定义接口
- 自定义的流式输出接口配置DEMO
<a><img src="https://img.alicdn.com/imgextra/i1/O1CN01xMqnRH1DlkdSekvSF_!!6000000000257-0-tps-591-508.jpg" width="100%"/></a>
- 自定义的非流式输出接口配置DEMO
<a><img src="https://img.alicdn.com/imgextra/i1/O1CN01JqmbGo1fW0GAQhRu4_!!6000000004013-0-tps-587-489.jpg" width="100%"/></a>
## 自然语言转SQL【UI旧】
### 功能描述
输入自然语言，选中执行并传入查询相关的表结构信息，则会返回相应的SQL
- 输入样例：查询学生小明的各科目成绩，并传入关联数据表course,score,student,student_course
- 输出样例：
```sql
SELECT score.score
FROM score
INNER JOIN student_course ON score.course_id = student_course.course_id
INNER JOIN student ON student_course.student_id = student.id
WHERE student.name = '小明'
```
## SQL解释
### 功能描述
输入SQL，选中执行并传入附加信息，附加信息可以添加希望解释《输入SQL》哪方面的信息，如SQL执行目标，则会返回此条SQL附加信息相关的解释
- 输入样例：SELECT department_name FROM departments WHERE department_id IN (SELECT department_id FROM employees GROUP BY department_id HAVING COUNT(*) > 10)
- 输出样例：这个SQL查询语句的意思是：从departments表中选择department_name列，其中department_id列的值在employees表中department_id列的值分组后，如果该组中的记录数大于10，则返回该department_id。换句话说，这个查询语句返回的是在employees表中有超过10个员工的部门名称。
## SQL优化
### 功能描述
输入SQL，选中执行并传入优化附加信息，附加信息可以添加希望具体优化方面和要求，则会返回此条SQL附加信息相关的优化建议
- 输入样例：SELECT * FROM department ORDER BY gmt_create DESC
- 输出样例：
  1. 尽量避免使用SELECT *，应该明确指定需要查询的列，以减少不必要的数据传输和提高查询效率。
  2. 对于ORDER BY语句，如果查询的结果集比较大，可以考虑添加LIMIT限制返回的行数，以减少排序的开销。
  3. 对于gmt_create字段，可以考虑添加索引以提高排序的效率。
  4. 如果查询的结果集经常被访问，可以考虑使用缓存技术，如Redis等，以减少数据库的访问次数。
## SQL转换
### 功能描述
输入SQL，选中执行并传入目标SQL类型和附加信息，附加信息中可以添加转换的自定义要求，则会返回目标SQL类型且符合附加要求的SQL语言
- 输入样例：SELECT IFNULL(NULL, "W3Schools.com")，目标SQL类型为Oracle SQL
- 输出样例：SELECT NVL(NULL, 'W3Schools.com') FROM dual;
## CHATBOT
### 功能描述
输入任意prompt，点击ChatRobot按钮即可根据prompt返回相应的结果，此处聊天对话默认支持上下文，默认上下文长度为4，可在application.yml文件中修改上下文长度
- 输入样例：针对第二条优化建议给出具体实现方法
- 输出样例：针对第二条优化建议，可以考虑在student表中添加一个索引，以加快查询速度。具体实现方法如下：

1. 查看student表中是否已经存在索引。可以使用以下命令查看：

   ```sql
   SHOW INDEX FROM student;
   ```

   如果已经存在索引，则可以跳过下一步。

2. 在student表中添加索引。可以使用以下命令添加：

   ```sql
   ALTER TABLE student ADD INDEX name_index (name);
   ```

   这里的name_index是索引的名称，name是需要建立索引的列名。

   注意：如果student表中的数据量很大，添加索引可能需要一些时间。

3. 重新执行原始SQL语句，查看查询速度是否有所提升。

   ```sql
   SELECT score.score FROM score INNER JOIN student ON score.student_id = student.id WHERE student.name = '小明';
   ```

   如果查询速度有所提升，则说明索引建立成功。
