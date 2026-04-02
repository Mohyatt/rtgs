package fr.betuf.rtgs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RtgsApplication {
    public static void main(String[] args) {
        SpringApplication.run(RtgsApplication.class, args);
    }
}
