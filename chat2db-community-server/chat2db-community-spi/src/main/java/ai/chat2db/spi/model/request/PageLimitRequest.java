package ai.chat2db.spi.model.request;

import java.util.UUID;
import static ai.chat2db.spi.constant.SQLConstants.PAGINATION_ROW_ID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageLimitRequest {

    /** Set by builders only when they append a synthetic result column. */
    private String paginationRowId;

    public String createPaginationRowId() {
        // At most 30 ASCII characters, including on older Oracle versions.
        paginationRowId = PAGINATION_ROW_ID + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return paginationRowId;
    }

    @NotBlank
    private String sql;

    @PositiveOrZero
    private int offset;

    @Positive
    private int pageNo;

    @Positive
    private int pageSize;
}
