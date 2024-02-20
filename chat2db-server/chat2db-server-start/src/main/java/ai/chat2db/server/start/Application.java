package ai.chat2db.server.start;

import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.tools.common.util.ConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Indexed;


/**
 * 启动类
 *
 * @author Jiaju Zhuang
 */
@SpringBootApplication
@ComponentScan(value = {"ai.chat2db.server"})
@Indexed
@EnableCaching
@EnableScheduling
@EnableAsync
@Slf4j
public class Application {

    public static void main(String[] args) {
        ConfigUtils.initProcess();
        new Thread(() -> {
            Dbutils.init();
        }).start();
        SpringApplication.run(Application.class, args);
    }
}
