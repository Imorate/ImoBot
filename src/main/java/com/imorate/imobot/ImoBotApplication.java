package com.imorate.imobot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.telegram.telegrambots.ApiContextInitializer;

@SpringBootApplication
public class ImoBotApplication {
    public static void main(String[] args) {
        ApiContextInitializer.init();
        SpringApplication.run(ImoBotApplication.class, args);
    }
}
