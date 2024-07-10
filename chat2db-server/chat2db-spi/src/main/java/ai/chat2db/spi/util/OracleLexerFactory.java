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
import com.oceanbase.tools.sqlparser.util.CaseChangingCharStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;

public class OracleLexerFactory implements LexerFactory {
    @Override
    public Lexer create(CharStream input) {
        CaseChangingCharStream caseChangingCharStream = new CaseChangingCharStream(input, true);
        return new PlSqlLexer(caseChangingCharStream);
    }
}
