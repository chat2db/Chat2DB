package ai.chat2db.spi;

import ai.chat2db.community.domain.api.model.metadata.Table;
import java.sql.Connection;

/** Optional dialect validation performed by the table-modification flow before generating SQL. */
@FunctionalInterface
public interface ITableModificationValidator {
    /** The caller owns the connection; implementations must not close it. */
    void validate(Connection connection, Table oldTable, Table newTable);
}
