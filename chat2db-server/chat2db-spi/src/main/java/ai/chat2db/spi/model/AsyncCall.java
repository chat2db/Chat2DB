package ai.chat2db.spi.model;

import java.util.Map;

public interface AsyncCall {

    void update(Map<String,Object> map);

}
