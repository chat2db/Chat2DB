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

import com.oceanbase.tools.sqlparser.oracle.PlSqlLexer;
import org.antlr.v4.runtime.Token;

class OracleLexerDefinition implements LexerTokenDefinition {

    @Override
    public int DIV() {
        return PlSqlLexer.SOLIDUS;
    }

    @Override
    public int SPACES() {
        return PlSqlLexer.SPACES;
    }

    @Override
    public int ANTLR_SKIP() {
        return Token.MIN_USER_TOKEN_TYPE;
    }

    @Override
    public int SINGLE_LINE_COMMENT() {
        return PlSqlLexer.SINGLE_LINE_COMMENT;
    }

    @Override
    public int MULTI_LINE_COMMENT() {
        return PlSqlLexer.MULTI_LINE_COMMENT;
    }

    @Override
    public int DECLARE() {
        return PlSqlLexer.DECLARE;
    }

    @Override
    public int BEGIN() {
        return PlSqlLexer.BEGIN;
    }

    @Override
    public int END() {
        return PlSqlLexer.END;
    }

    @Override
    public int CREATE() {
        return PlSqlLexer.CREATE;
    }

    @Override
    public int OR() {
        return PlSqlLexer.OR;
    }

    @Override
    public int REPLACE() {
        return PlSqlLexer.REPLACE;
    }

    @Override
    public int EDITIONABLE() {
        return PlSqlLexer.EDITIONABLE;
    }

    @Override
    public int NONEDITIONABLE() {
        return PlSqlLexer.NONEDITIONABLE;
    }

    @Override
    public int PROCEDURE() {
        return PlSqlLexer.PROCEDURE;
    }

    @Override
    public int FUNCTION() {
        return PlSqlLexer.FUNCTION;
    }

    @Override
    public int PACKAGE() {
        return PlSqlLexer.PACKAGE;
    }

    @Override
    public int TYPE() {
        return PlSqlLexer.TYPE;
    }

    @Override
    public int TRIGGER() {
        return PlSqlLexer.TRIGGER;
    }

    @Override
    public int BODY() {
        return PlSqlLexer.BODY;
    }

    @Override
    public int IDENT() {
        return Token.MIN_USER_TOKEN_TYPE;
    }

    @Override
    public int REGULAR_ID() {
        return PlSqlLexer.REGULAR_ID;
    }

    @Override
    public int DELIMITED_ID() {
        return PlSqlLexer.DELIMITED_ID;
    }

    @Override
    public int FOR() {
        return PlSqlLexer.FOR;
    }

    @Override
    public int LOOP() {
        return PlSqlLexer.LOOP;
    }

    @Override
    public int IF() {
        return PlSqlLexer.IF;
    }

    @Override
    public int CASE() {
        return PlSqlLexer.CASE;
    }

    @Override
    public int LANGUAGE() {
        return PlSqlLexer.LANGUAGE;
    }

    @Override
    public int EXTERNAL() {
        return PlSqlLexer.EXTERNAL;
    }

    @Override
    public int IS() {
        return PlSqlLexer.IS;
    }

    @Override
    public int AS() {
        return PlSqlLexer.AS;
    }

    @Override
    public int MEMBER() {
        return PlSqlLexer.MEMBER;
    }

    @Override
    public int STATIC() {
        return PlSqlLexer.STATIC;
    }

    @Override
    public int SEMICOLON() {
        return PlSqlLexer.SEMICOLON;
    }

    @Override
    public int ELSE() {
        return PlSqlLexer.ELSE;
    }

    @Override
    public int THEN() {
        return PlSqlLexer.THEN;
    }

    @Override
    public int RIGHTBRACKET() {
        return PlSqlLexer.RIGHT_PAREN;
    }

    @Override
    public int LEFTBRACKET() {
        return PlSqlLexer.LEFT_PAREN;
    }

    @Override
    public int GREATER_THAN_OP() {
        return PlSqlLexer.GREATER_THAN_OP;
    }

    @Override
    public int WHILE() {
        return PlSqlLexer.WHILE;
    }
}
