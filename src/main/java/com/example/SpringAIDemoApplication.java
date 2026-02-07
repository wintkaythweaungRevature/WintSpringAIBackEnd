package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
@RestController
@SpringBootApplication
@ComponentScan(basePackages = "com.example")
public class SpringAIDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringAIDemoApplication.class, args);
    }
}