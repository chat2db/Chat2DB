package ai.chat2db.server.domain.api.param;

import lombok.Data;

import java.io.Serializable;

@Data
public class TaskUpdateParam implements Serializable {


    private static final long serialVersionUID = 1L;

    /**
     * task id
     */
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * task type, such as: DOWNLOAD_DATA, UPLOAD_TABLE_DATA, DOWNLOAD_TABLE_STRUCTURE, UPLOAD_TABLE_STRUCTURE,
     */
    private String taskStatus;

    /**
     * task progress
     */
    private String taskProgress;

    /**
     * task name
     */
    private String taskName;

    /**
     * task description
     */
    private String downloadUrl;

    /**
     * task content
     */
    private byte[] content;
}
