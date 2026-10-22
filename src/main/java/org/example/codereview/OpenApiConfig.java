package org.example.codereview;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private final String version;

	public OpenApiConfig(@Value("${app.version}") String version) {
		this.version = version;
	}

	@Bean
	public OpenAPI codeReviewOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Spring Boot AI Code Review Agent")
				.description("Submit a diff or a source file and get back a structured AI code review.")
				.version(version));
	}

}
