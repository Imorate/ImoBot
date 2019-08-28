package com.imorate.imobot.configuration;

import com.imorate.imobot.properties.BotProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BotProperties.class)
public class AppConfiguration {
}
