package com.imorate.imobot.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("imobot")
public class BotProperties {
    private String username;
    private String token;
    private String weatherApi;
}
