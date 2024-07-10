package ai.chat2db.spi.sql;

import java.util.List;

import com.alibaba.druid.sql.parser.SQLParserUtils;
import com.google.common.collect.Lists;

import lombok.extern.slf4j.Slf4j;

/**
 * @author luojun
 * @version 1.0
 * @description: 用于解析sql，输出语句不会改变关键字的大小写
 * @date 2024/6/3 10:57
 **/
@Slf4j
public class SqlParseUtils {

    public static List<String> parseSql(String sql) {
        List<String> list = Lists.newArrayList();
        try {
            return SQLParserUtils.splitAndRemoveComment(sql, null);
        } catch (Exception e) {
            list.add(SQLParserUtils.removeComment(sql, null));
            log.error("parse sql error", e);
        }
        return list;
    }

}
