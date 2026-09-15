package ai.chat2db.plugin.informix;

import ai.chat2db.plugin.generic.GenericMetaData;
import ai.chat2db.plugin.informix.builder.InformixSqlBuilder;
import ai.chat2db.plugin.informix.metadata.InformixColumnConstraint;
import ai.chat2db.spi.ICommandExecutor;
import ai.chat2db.spi.IDbMetaData;
import ai.chat2db.spi.ISqlBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static ai.chat2db.plugin.informix.constant.InformixMetaDataConstants.COLUMN_CONSTRAINTS_SQL;
import static ai.chat2db.plugin.informix.constant.InformixMetaDataConstants.CURRENT_USER_COLUMN_CONSTRAINTS_SQL;

@Slf4j
public class InformixMetaData extends GenericMetaData implements IDbMetaData {

    public List<InformixColumnConstraint> columnConstraints(Connection connection, String schemaName,
                                                            String tableName, String columnName) throws SQLException {
        boolean hasOwner = StringUtils.isNotBlank(schemaName);
        String sql = hasOwner ? COLUMN_CONSTRAINTS_SQL : CURRENT_USER_COLUMN_CONSTRAINTS_SQL;
        List<InformixColumnConstraint> constraints = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, tableName);
            if (hasOwner) {
                statement.setString(index++, schemaName);
            }
            statement.setString(index, columnName);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    constraints.add(new InformixColumnConstraint(result.getString(1).trim(), result.getString(2).trim()));
                }
            }
        }
        return constraints;
    }

    @Override
    public ICommandExecutor getCommandExecutor() {
        return InformixCommandExecutor.INSTANCE;
    }

    @Override
    public ISqlBuilder getSqlBuilder() {
        // Informix rejects the default MySQL/PG-style RENAME/MODIFY/EXPLAIN syntax.
        return new InformixSqlBuilder();
    }

    @Override
    public String getMetaDataName(String... names) {
        List<String> validNames = Arrays.stream(names)
                .filter(name -> StringUtils.isNotBlank(name))
                .collect(Collectors.toList());

        int size = validNames.size();
        if (size == 0) return "";
        if (size == 1) return validNames.get(0);

        return validNames.get(size - 2) + "." + validNames.get(size - 1);
    }
}
