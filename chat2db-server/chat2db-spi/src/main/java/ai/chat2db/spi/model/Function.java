
package ai.chat2db.spi.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * @author jipengfei
 * @version : Function.java
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Function  implements Serializable {
    private static final long serialVersionUID = 1L;
    //FUNCTION_CAT String => function catalog (may be null)
    //FUNCTION_SCHEME String => function schema (may be null)
    //FUNCTION_NAME String => function name. This is the name used to invoke the function
    //REMARKS String => explanatory comment on the function
    //FUNCTION_TYPE short => kind of function:
    //functionResultUnknown - Cannot determine if a return value or table will be returned
    //functionNoTable- Does not return a table
    //functionReturnsTable - Returns a table
    //SPECIFIC_NAME String => the name which uniquely identifies this function within its schema. This is a user specified, or DBMS generated, name that may be different then the FUNCTION_NAME for example with overload functions
    //

    @JsonAlias({"FUNCTION_CAT"})
    private String databaseName;

    @JsonAlias({"FUNCTION_SCHEM"})
    private String schemaName;

    @JsonAlias({"FUNCTION_NAME"})
    private String functionName;

    @JsonAlias({"REMARKS"})
    private String remarks;

    @JsonAlias({"FUNCTION_TYPE"})
    private Short functionType;

    @JsonAlias({"SPECIFIC_NAME"})
    private String specificName;

    private String functionBody;

}