package jp.oidaira.owlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import jp.oidaira.owlog.config.OwlogProperties;

@SpringBootApplication
@EnableConfigurationProperties(OwlogProperties.class)
public class OwlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(OwlogApplication.class, args);
    }
}
