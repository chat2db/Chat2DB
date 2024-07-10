/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.chat2db.spi.util;

/**
 * SQL 语法 Token 定义。<br>
 * 如果某个 Token 不支持，对应方法返回 1 {@link org.antlr.v4.runtime.Token#MIN_USER_TOKEN_TYPE}
 */
interface LexerTokenDefinition {
    /**
     * 除号
     */
    int DIV();

    /**
     * 空白字符，包括空格、制表符、换行符等
     */
    int SPACES();

    /**
     * OB 语法文件不区分单行注释和多行注释，统一为 ANTLR_SKIP
     */
    int ANTLR_SKIP();

    /**
     * 单行注释
     */
    int SINGLE_LINE_COMMENT();

    /**
     * 多行注释
     */
    int MULTI_LINE_COMMENT();

    /**
     * DECLARE 关键字
     */
    int DECLARE();

    /**
     * BEGIN 关键字
     */
    int BEGIN();

    /**
     * END 关键字
     */
    int END();

    /**
     * CREATE 关键字
     */
    int CREATE();

    /**
     * OR 关键字
     */
    int OR();

    /**
     * REPLACE 关键字
     */
    int REPLACE();

    /**
     * EDITIONABLE 关键字
     */
    int EDITIONABLE();

    /**
     * NONEDITIONABLE 关键字
     */
    int NONEDITIONABLE();

    /**
     * PROCEDURE 关键字
     */
    int PROCEDURE();

    /**
     * FUNCTION 关键字
     */
    int FUNCTION();

    /**
     * PACKAGE 关键字
     */
    int PACKAGE();

    /**
     * TYPE 关键字
     */
    int TYPE();

    /**
     * TRIGGER 关键字
     */
    int TRIGGER();

    /**
     * BODY 关键字
     */
    int BODY();

    /**
     * IDENT, may {@link #REGULAR_ID} OR {@link #DELIMITED_ID}
     */
    int IDENT();

    int REGULAR_ID();

    int DELIMITED_ID();

    int FOR();

    /**
     * LOOP 关键字
     */
    int LOOP();

    /**
     * IF 关键字
     */
    int IF();

    /**
     * CASE 关键字
     */
    int CASE();

    /**
     * LANGUAGE 关键字
     */
    int LANGUAGE();

    /**
     * EXTERNAL 关键字
     */
    int EXTERNAL();

    /**
     * IS 关键字
     */
    int IS();

    /**
     * AS 关键字
     */
    int AS();

    /**
     * MEMBER 关键字
     */
    int MEMBER();

    /**
     * STATIC 关键字
     */
    int STATIC();

    int SEMICOLON();

    int ELSE();

    int THEN();

    int RIGHTBRACKET();

    int LEFTBRACKET();

    int GREATER_THAN_OP();

    int WHILE();
}
