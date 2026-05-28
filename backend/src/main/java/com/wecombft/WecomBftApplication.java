package com.wecombft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WecomBftApplication {

    public static void main(String[] args) {
        SpringApplication.run(WecomBftApplication.class, args);
    }
}
