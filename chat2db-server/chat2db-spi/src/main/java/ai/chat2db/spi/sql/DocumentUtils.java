package ai.chat2db.spi.sql;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.ClassUtils;

/**
 * @author luojun
 * @version 1.0
 * @description: 接口定义
 * @date 2024/5/31 19:05
 **/
public class DocumentUtils {

    public static LinkedHashMap<String, Object> convertToMap(Object obj) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        if (obj == null) {
            return map;
        }
        if (ClassUtils.isPrimitiveOrWrapper(obj.getClass()) || String.class.equals(obj.getClass())) {
            map.put("result", obj);
            return map;
        }
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) obj).entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                map.put(entry.getKey(), null);
            } else if (ClassUtils.isPrimitiveOrWrapper(value.getClass()) || String.class.equals(value.getClass())) {
                map.put(entry.getKey(), value);
            } else if (entry.getValue() instanceof Map) {
                LinkedHashMap<String, Object> mmp = convertToMap(entry.getValue());
                map.put(entry.getKey(), mmp);
            } else {
                map.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return map;
    }
}
