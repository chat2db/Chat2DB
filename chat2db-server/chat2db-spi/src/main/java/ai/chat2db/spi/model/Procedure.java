
package ai.chat2db.spi.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * @author jipengfei
 * @version : Procedure.java
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Procedure implements Serializable {
    private static final long serialVersionUID = 1L;
    //PROCEDURE_CAT String => procedure catalog (may be null)
    //PROCEDURE_SCHEME String => procedure schema (may be null)
    //PROCEDURE_NAME String => procedure name
    //REMARKS String => explanatory comment on the procedure
    //PROCEDURE_TYPE short => kind of procedure:
    //procedureResultUnknown - Cannot determine if a return value will be returned
    //procedureNoResult - Does not return a return value
    //procedureReturnsResult - Returns a return value
    //SPECIFIC_NAME String => the name which uniquely identifies this procedure within its schema. This is a user specified, or DBMS generated, name that may be different then the PROCEDURE_NAME for example with overload procedures
    //

    @JsonAlias({"PROCEDURE_CAT"})
    private String databaseName;

    @JsonAlias({"PROCEDURE_SCHEM"})

    private String schemaName;

    @JsonAlias({"PROCEDURE_NAME"})
    private String procedureName;

    @JsonAlias({"REMARKS"})
    private String remarks;

    @JsonAlias({"PROCEDURE_TYPE"})

    private Short procedureType;

    @JsonAlias({"SPECIFIC_NAME"})
    private String specificName;

    private String procedureBody;
}