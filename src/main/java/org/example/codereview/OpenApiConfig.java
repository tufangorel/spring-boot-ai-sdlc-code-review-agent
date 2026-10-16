package org.example.codereview;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI codeReviewOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Spring Boot AI Code Review Agent")
				.description("Submit a diff or a source file and get back a structured AI code review.")
				.version("0.0.1"));
	}

}
