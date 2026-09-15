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

    /** Candidate alias supplied to builders; only emitted helper columns are hidden. */
    @Builder.Default
    private String paginationRowId = PAGINATION_ROW_ID + "_"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    @NotBlank
    private String sql;

    @PositiveOrZero
    private int offset;

    @Positive
    private int pageNo;

    @Positive
    private int pageSize;
}
