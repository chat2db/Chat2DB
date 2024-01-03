#!/bin/bash

##
## 本地客户构建
## - 考虑将脚本统一起来管理
#
#


# JRE_DIR="${JAVA_HOME}/jre"
JRE_DIR="~/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home/"
JRE_TARGET_DIR="chat2db-client/static/jre"
CURRENT_ID="123"

# Clean
echo "Clean"
rm -rf chat2db-client/static
rm -rf chat2db-client/versions
rm -rf chat2db-client/release

# 使用 mkdir 创建目录，并使用 -p 参数确保如果目录已存在不会报错
mkdir -p "$JRE_TARGET_DIR"

# 使用 cp 命令复制 JAVA_HOME 目录内容到目标目录
# -r 参数表示递归复制整个目录
cp -r "${JRE_DIR}/" "$JRE_TARGET_DIR"
chmod -R 777 "$JRE_TARGET_DIR"

# 打包后端代码
mvn clean package -B '-Dmaven.test.skip=true' -f chat2db-server/pom.xml
mkdir -p chat2db-client/versions/99.0.${CURRENT_ID}/static
echo -n 99.0.${CURRENT_ID} > chat2db-client/versions/version
cp chat2db-server/chat2db-server-start/target/chat2db-server-start.jar chat2db-client/versions/99.0.${CURRENT_ID}/static/

# 打包前端代码
cd chat2db-client
yarn install
yarn run build:web:desktop --app_port=10822
cp -r dist ./versions/99.0.${CURRENT_ID}/
# 打包客户端
yarn run build:main:prod -c.productName=Chat2DB-Test -c.extraMetadata.version=99.0.${CURRENT_ID} --mac --arm64
