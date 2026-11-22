package com.texos.texos_backend;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class TexosBackendApplication {

	public TexosBackendApplication() {
		super();
	}

	public static void main(String[] args) {
		SpringApplication.run(TexosBackendApplication.class, args);
	}

}
