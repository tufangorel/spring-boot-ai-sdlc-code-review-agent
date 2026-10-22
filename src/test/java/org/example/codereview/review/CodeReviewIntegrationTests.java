package org.example.codereview.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@AutoConfigureMockMvc
class CodeReviewIntegrationTests {

	private static final String REPLY = """
			{
			  "summary": "One minor issue",
			  "verdict": "COMMENT",
			  "findings": [
			    {
			      "file": "Foo.java",
			      "line": 3,
			      "severity": "MINOR",
			      "category": "STYLE",
			      "message": "Unused import",
			      "suggestion": "Remove it"
			    }
			  ]
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StubChatModel chatModel;

	@BeforeEach
	void clearPrompts() {
		chatModel.prompts().clear();
	}

	@Test
	void reviewsDiffEndToEnd() throws Exception {
		mockMvc.perform(post("/api/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"language":"java","diff":"+ import java.util.List;"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verdict").value("COMMENT"))
				.andExpect(jsonPath("$.findings[0].severity").value("MINOR"))
				.andExpect(jsonPath("$.findings[0].line").value(3));

		assertThat(chatModel.lastPrompt().getUserMessage().getText()).contains("+ import java.util.List;");
	}

	@Test
	void reviewsJsonFileEndToEnd() throws Exception {
		mockMvc.perform(post("/api/reviews/file")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fileName":"Foo.java","content":"class Foo { }"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary").value("One minor issue"))
				.andExpect(jsonPath("$.findings[0].file").value("Foo.java"));

		assertThat(chatModel.lastPrompt().getUserMessage().getText())
				.contains("File: Foo.java")
				.contains("class Foo { }");
	}

	@Test
	void reviewsUploadedFileEndToEnd() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "src/Foo.java", MediaType.TEXT_PLAIN_VALUE,
				"class Foo { }".getBytes());

		mockMvc.perform(multipart("/api/reviews/file/upload").file(file).param("context", "New class"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verdict").value("COMMENT"));

		assertThat(chatModel.lastPrompt().getUserMessage().getText())
				.contains("File: Foo.java")
				.contains("Context: New class")
				.contains("class Foo { }");
	}

	@Test
	void rejectsInvalidRequestWithoutCallingModel() throws Exception {
		mockMvc.perform(post("/api/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"diff":""}
								"""))
				.andExpect(status().isBadRequest());

		assertThat(chatModel.prompts()).isEmpty();
	}

	@TestConfiguration
	static class StubChatModelConfig {

		@Bean
		@Primary
		StubChatModel stubChatModel() {
			return new StubChatModel(REPLY);
		}

	}

}
