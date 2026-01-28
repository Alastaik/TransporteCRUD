package org.Alastaik.logistica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;

@SpringBootApplication(exclude = { OpenAiAutoConfiguration.class })
public class LogisticaBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticaBotApplication.class, args);
    }
}