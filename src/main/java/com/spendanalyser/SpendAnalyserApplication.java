package com.spendanalyser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpendAnalyserApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpendAnalyserApplication.class, args);
    }
}
