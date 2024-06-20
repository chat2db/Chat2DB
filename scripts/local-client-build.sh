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

# Use mkdir to create the directory, and use the -p parameter to ensure that no error is reported if the directory already exists
mkdir -p "$JRE_TARGET_DIR"

# Use the cp command to copy the contents of the JAVA_HOME directory to the target directory
# -r Parameter means recursively copy the entire directory
cp -r "${JRE_DIR}/" "$JRE_TARGET_DIR"
chmod -R 777 "$JRE_TARGET_DIR"

# Packaging backend code
mvn clean package -B '-Dmaven.test.skip=true' -f chat2db-server/pom.xml
mkdir -p chat2db-client/versions/99.0.${CURRENT_ID}/static
echo -n 99.0.${CURRENT_ID} > chat2db-client/versions/version
cp chat2db-server/chat2db-server-start/target/chat2db-server-start.jar chat2db-client/versions/99.0.${CURRENT_ID}/static/

# Packaging front-end code
cd chat2db-client
yarn install
yarn run build:web:desktop --app_port=10822
cp -r dist ./versions/99.0.${CURRENT_ID}/
# Packaged client
yarn run build:main:prod -c.productName=Chat2DB-Test -c.extraMetadata.version=99.0.${CURRENT_ID} --mac --arm64
