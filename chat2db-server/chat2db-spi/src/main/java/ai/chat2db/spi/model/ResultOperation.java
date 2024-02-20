package ai.chat2db.spi.model;

import lombok.Data;

import java.util.List;

@Data
public class ResultOperation {

    private String type;

    private List<String> dataList;

    private List<String> oldDataList;
}
