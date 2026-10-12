package com.cameraman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableTransactionManagement
public class CameramanPhotoStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(CameramanPhotoStudioApplication.class, args);
    }
}
