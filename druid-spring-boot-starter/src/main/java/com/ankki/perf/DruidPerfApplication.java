package com.ankki.perf;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.ankki.perf"})
@MapperScan("com.ankki.perf.mapper")
public class DruidPerfApplication {

    public static void main(String[] args) {
        SpringApplication.run(DruidPerfApplication.class, args);
    }
}