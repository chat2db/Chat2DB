package ai.chat2db.server.tools.common.model.export.data.option;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TableOption {
    @NotNull
    private String tableName;
    @NotEmpty
    @NotNull
    private List<String> filedNames;

}