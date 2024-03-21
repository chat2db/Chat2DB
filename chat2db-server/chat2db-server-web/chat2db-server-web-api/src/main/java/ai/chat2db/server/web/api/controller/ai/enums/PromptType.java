package ai.chat2db.server.web.api.controller.ai.enums;

import ai.chat2db.server.tools.base.enums.BaseEnum;

import lombok.Getter;

/**
 * 提示类型
 *
 * @author moji
 * @version PromptType.java, v 0.1 2023年04月09日 15:36 moji Exp $
 * @date 2023/04/09
 */
@Getter
public enum PromptType implements BaseEnum<String> {

    /**
     * 自然语言转换成SQL
     */
    NL_2_SQL("将自然语言转换成SQL查询"),

    /**
     * 解释SQL
     */
    SQL_EXPLAIN("解释SQL"),

    /**
     * SQL优化
     */
    SQL_OPTIMIZER("提供优化建议"),

    /**
     * SQL转换
     */
    SQL_2_SQL("进行SQL转换"),

    /**
     * text generation
     */
    TEXT_GENERATION("文本生成"),


    /**
     * GET_TABLE_COLUMNS
     */
    GET_TABLE_COLUMNS("获取指定表的属性"),
    ;

    final String description;

    PromptType(String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return this.name();
    }
}
