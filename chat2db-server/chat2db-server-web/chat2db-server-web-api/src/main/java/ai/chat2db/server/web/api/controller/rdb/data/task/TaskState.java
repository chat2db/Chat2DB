package ai.chat2db.server.web.api.controller.rdb.data.task;

import lombok.Builder;
import lombok.Data;

/**
 * @author: zgq
 * @date: 2024年06月10日 15:51
 */
@Data
@Builder
public class TaskState {
    private String taskId;
    private String state;
    private int total;
    private int current;


    public String getExportStatus() {
        StringBuilder statusBuilder = new StringBuilder();
        statusBuilder.append("导出状态: ").append(state)
                .append(" 导出进度: ")
                .append(current).append("/")
                .append(total);
        return statusBuilder.toString();
    }

}
