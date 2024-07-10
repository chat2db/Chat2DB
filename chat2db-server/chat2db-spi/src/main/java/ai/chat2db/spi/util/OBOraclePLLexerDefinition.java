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

import com.oceanbase.tools.sqlparser.oboracle.PLLexer;
import org.antlr.v4.runtime.Token;

class OBOraclePLLexerDefinition implements LexerTokenDefinition {

    @Override
    public int DIV() {
        return PLLexer.Div;
    }

    @Override
    public int SPACES() {
        return PLLexer.Blank;
    }

    @Override
    public int ANTLR_SKIP() {
        return PLLexer.ANTLR_SKIP;
    }

    @Override
    public int SINGLE_LINE_COMMENT() {
        return Token.MIN_USER_TOKEN_TYPE;
    }

    @Override
    public int MULTI_LINE_COMMENT() {
        return Token.MIN_USER_TOKEN_TYPE;
    }

    @Override
    public int DECLARE() {
        return PLLexer.DECLARE;
    }

    @Override
    public int BEGIN() {
        return PLLexer.BEGIN_KEY;
    }

    @Override
    public int END() {
        return PLLexer.END_KEY;
    }

    @Override
    public int CREATE() {
        return PLLexer.CREATE;
    }

    @Override
    public int OR() {
        return PLLexer.OR;
    }

    @Override
    public int REPLACE() {
        return PLLexer.REPLACE;
    }

    @Override
    public int EDITIONABLE() {
        return PLLexer.EDITIONABLE;
    }

    @Override
    public int NONEDITIONABLE() {
        return PLLexer.NONEDITIONABLE;
    }

    @Override
    public int PROCEDURE() {
        return PLLexer.PROCEDURE;
    }

    @Override
    public int FUNCTION() {
        return PLLexer.FUNCTION;
    }

    @Override
    public int PACKAGE() {
        return PLLexer.PACKAGE_P;
    }

    @Override
    public int TYPE() {
        return PLLexer.TYPE;
    }

    @Override
    public int TRIGGER() {
        return PLLexer.TRIGGER;
    }

    @Override
    public int BODY() {
        return PLLexer.BODY;
    }

    @Override
    public int IDENT() {
        return PLLexer.IDENT;
    }

    @Override
    public int REGULAR_ID() {
        return Token.MIN_USER_TOKEN_TYPE;
    }

    @Override
    public int DELIMITED_ID() {
        return Token.MIN_USER_TOKEN_TYPE;
    }

    @Override
    public int FOR() {
        return PLLexer.FOR;
    }

    @Override
    public int LOOP() {
        return PLLexer.LOOP;
    }

    @Override
    public int IF() {
        return PLLexer.IF;
    }

    @Override
    public int CASE() {
        return PLLexer.CASE;
    }

    @Override
    public int LANGUAGE() {
        return PLLexer.LANGUAGE;
    }

    @Override
    public int EXTERNAL() {
        return PLLexer.EXTERNAL;
    }

    @Override
    public int IS() {
        return PLLexer.IS;
    }

    @Override
    public int AS() {
        return PLLexer.AS;
    }

    @Override
    public int MEMBER() {
        return PLLexer.MEMBER;
    }

    @Override
    public int STATIC() {
        return PLLexer.STATIC;
    }

    @Override
    public int SEMICOLON() {
        return PLLexer.DELIMITER;
    }

    @Override
    public int ELSE() {
        return PLLexer.ELSE;
    }

    @Override
    public int THEN() {
        return PLLexer.THEN;
    }

    @Override
    public int RIGHTBRACKET() {
        return PLLexer.RightParen;
    }

    @Override
    public int LEFTBRACKET() {
        return PLLexer.LeftParen;
    }

    @Override
    public int GREATER_THAN_OP() {
        return PLLexer.LABEL_RIGHT;
    }

    @Override
    public int WHILE() {
        return PLLexer.WHILE;
    }

}
