package ai.chat2db.plugin.informix.validation;

import ai.chat2db.community.domain.api.enums.plugin.EditStatusEnum;
import ai.chat2db.community.domain.api.model.metadata.Table;
import ai.chat2db.community.domain.api.model.metadata.TableColumn;
import ai.chat2db.community.tools.exception.BusinessException;
import ai.chat2db.plugin.informix.InformixMetaData;
import ai.chat2db.plugin.informix.metadata.InformixColumnConstraint;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/** Rejects changes whose native MODIFY semantics would remove constraints or inbound foreign keys. */
public final class InformixTableModificationValidator {
    private static final Set<String> DROPPED_CONSTRAINT_TYPES = Set.of("P", "U", "R", "C");
    private final InformixMetaData metadata;

    public InformixTableModificationValidator(InformixMetaData metadata) {
        this.metadata = metadata;
    }

    public void validate(Connection connection, Table oldTable, Table newTable) {
        for (TableColumn column : newTable.getColumnList()) {
            if (!EditStatusEnum.MODIFY.name().equals(column.getEditStatus())) {
                continue;
            }
            String oldName = column.getOldColumn() == null
                    ? StringUtils.defaultIfBlank(column.getOldName(), column.getName()) : column.getOldColumn().getName();
            String owner = StringUtils.defaultIfBlank(oldTable.getSchemaName(), column.getSchemaName());
            try {
                for (InformixColumnConstraint constraint : metadata.columnConstraints(
                        connection, owner, oldTable.getName(), oldName)) {
                    if (DROPPED_CONSTRAINT_TYPES.contains(constraint.type())) {
                        throw new BusinessException("informix.column.constraintModification",
                                new Object[]{oldName, constraint.name()});
                    }
                }
            } catch (SQLException e) {
                throw new BusinessException("informix.column.constraintInspectionFailed", null, e);
            }
        }
    }
}
