package org.example.codereview.review;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.ai.openai.api-key=test-key")
@AutoConfigureTestRestTemplate
class ErrorResponseIntegrationTests {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private StubChatModel chatModel;

	@BeforeEach
	void clearPrompts() {
		chatModel.prompts().clear();
	}

	@Test
	void modelFailureReturns500WithErrorBody() {
		ResponseEntity<Map<String, Object>> response = post("/api/reviews/file", json("""
				{"fileName":"Foo.java","content":"class Foo {}"}
				"""));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody())
				.containsEntry("status", 500)
				.containsEntry("error", "Internal Server Error")
				.containsEntry("path", "/api/reviews/file")
				.containsKey("timestamp");
		assertThat(chatModel.prompts()).hasSize(1);
	}

	@Test
	void validationFailureReturns400WithErrorBody() {
		ResponseEntity<Map<String, Object>> response = post("/api/reviews", json("""
				{"diff":""}
				"""));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody())
				.containsEntry("status", 400)
				.containsEntry("error", "Bad Request")
				.containsEntry("path", "/api/reviews");
		assertThat(chatModel.prompts()).isEmpty();
	}

	@Test
	void emptyUploadReturns400WithErrorBody() {
		ResponseEntity<Map<String, Object>> response = post("/api/reviews/file/upload",
				upload("Foo.java", new byte[0]));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).containsEntry("path", "/api/reviews/file/upload");
		assertThat(chatModel.prompts()).isEmpty();
	}

	@Test
	void uploadLargerThanOneMegabyteReturns413() {
		byte[] content = "a".repeat(1_100_000).getBytes(StandardCharsets.UTF_8);

		ResponseEntity<String> response = restTemplate.postForEntity("/api/reviews/file/upload",
				upload("Big.java", content), String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(413);
		assertThat(chatModel.prompts()).isEmpty();
	}

	@Test
	void wrongContentTypeReturns415() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.TEXT_PLAIN);

		ResponseEntity<Map<String, Object>> response = post("/api/reviews/file",
				new HttpEntity<>("class Foo {}", headers));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertThat(response.getBody()).containsEntry("status", 415);
	}

	@Test
	void unknownPathReturns404() {
		ResponseEntity<Map<String, Object>> response = post("/api/reviews/unknown", json("{}"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void getOnReviewEndpointReturns405() {
		ResponseEntity<Map<String, Object>> response = restTemplate.exchange("/api/reviews", HttpMethod.GET, null,
				new ParameterizedTypeReference<Map<String, Object>>() {
				});

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
	}

	private ResponseEntity<Map<String, Object>> post(String path, HttpEntity<?> entity) {
		return restTemplate.exchange(path, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {
		});
	}

	private static HttpEntity<String> json(String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(body, headers);
	}

	private static HttpEntity<MultiValueMap<String, Object>> upload(String fileName, byte[] content) {
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		parts.add("file", new ByteArrayResource(content) {
			@Override
			public String getFilename() {
				return fileName;
			}
		});
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		return new HttpEntity<>(parts, headers);
	}

	@TestConfiguration
	static class FailingChatModelConfig {

		@Bean
		@Primary
		StubChatModel failingChatModel() {
			return StubChatModel.failingWith(new IllegalStateException("provider unavailable"));
		}

	}

}
