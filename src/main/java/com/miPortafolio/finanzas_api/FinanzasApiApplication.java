package com.miPortafolio.finanzas_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinanzasApiApplication {
	public static void main(String[] args) {
		SpringApplication.run(FinanzasApiApplication.class, args);
	}
}
