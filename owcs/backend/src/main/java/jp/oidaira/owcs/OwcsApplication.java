package jp.oidaira.owcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OwcsProperties.class)
public class OwcsApplication {
    public static void main(String[] args) {
        SpringApplication.run(OwcsApplication.class, args);
    }
}
