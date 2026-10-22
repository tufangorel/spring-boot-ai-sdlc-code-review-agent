package org.example.codereview;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@AutoConfigureMockMvc
class OpenApiDocsTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void documentsReviewEndpoints() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/reviews'].post").exists())
				.andExpect(jsonPath("$.paths['/api/reviews/file'].post").exists())
				.andExpect(jsonPath("$.paths['/api/reviews/file/upload'].post.requestBody.content['multipart/form-data']").exists());
	}

	@Test
	void documentsApiInfo() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Spring Boot AI Code Review Agent"))
				.andExpect(jsonPath("$.info.version").value("0.0.1-SNAPSHOT"))
				.andExpect(jsonPath("$.info.description").isNotEmpty());
	}

	@Test
	void documentsOperationsUnderCodeReviewsTag() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/reviews'].post.summary").value("Review a diff"))
				.andExpect(jsonPath("$.paths['/api/reviews/file'].post.summary").value("Review a file sent as JSON"))
				.andExpect(jsonPath("$.paths['/api/reviews/file/upload'].post.summary").value("Review an uploaded file"))
				.andExpect(jsonPath("$.paths['/api/reviews/file'].post.tags[0]").value("Code reviews"));
	}

	@Test
	void documentsUploadFilePartAsBinary() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/reviews/file/upload'].post.requestBody.content['multipart/form-data'].schema.properties.file.format")
						.value("binary"))
				.andExpect(jsonPath("$.paths['/api/reviews/file/upload'].post.requestBody.content['multipart/form-data'].schema.required[0]")
						.value("file"));
	}

	@Test
	void documentsRequiredRequestFields() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.schemas.ReviewRequest.required").value(contains("diff")))
				.andExpect(jsonPath("$.components.schemas.FileReviewRequest.required")
						.value(containsInAnyOrder("fileName", "content")));
	}

	@Test
	void documentsReviewResultEnums() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.schemas.ReviewResult.properties.verdict.enum")
						.value(contains("APPROVE", "COMMENT", "REQUEST_CHANGES")))
				.andExpect(jsonPath("$.components.schemas.Finding.properties.severity.enum")
						.value(contains("INFO", "MINOR", "MAJOR", "CRITICAL")))
				.andExpect(jsonPath("$.components.schemas.Finding.properties.category.enum")
						.value(contains("BUG", "SECURITY", "PERFORMANCE", "MAINTAINABILITY", "STYLE", "TESTING")));
	}

	@Test
	void servesSwaggerUi() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk());
	}

	@Test
	void redirectsSwaggerShortcutToUi() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection());
	}

}
