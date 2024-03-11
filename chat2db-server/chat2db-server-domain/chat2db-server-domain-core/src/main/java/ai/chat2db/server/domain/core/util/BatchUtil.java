package ai.chat2db.server.domain.core.util;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 功能描述
 * 集合分批工具类
 *
 * @author: zgq
 * @date: 2024年02月21日 20:39
 */
public class BatchUtil {
    public static <T> List<List<T>> batch(List<T> collection, int batchSize) {
        int collectionSize = collection.size();
        return IntStream.range(0, (collectionSize + batchSize - 1) / batchSize)
                .mapToObj(i -> collection.subList(i * batchSize, Math.min((i + 1) * batchSize, collectionSize)))
                .collect(Collectors.toList());
    }
}
