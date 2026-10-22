package org.example.codereview.review;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"spring.ai.openai.api-key=test-key",
		"review.max-input-characters=5" })
@AutoConfigureMockMvc
class ReviewInputSizeTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void jsonDiffOverTheLimitReturns413() throws Exception {
		mockMvc.perform(post("/api/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"diff":"123456"}
								"""))
				.andExpect(status().isPayloadTooLarge());
	}

	@Test
	void jsonFileOverTheLimitReturns413() throws Exception {
		mockMvc.perform(post("/api/reviews/file")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fileName":"Foo.java","content":"123456"}
								"""))
				.andExpect(status().isPayloadTooLarge());
	}

	@Test
	void uploadOverTheLimitReturns413() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "Foo.java", MediaType.TEXT_PLAIN_VALUE,
				"123456".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/reviews/file/upload").file(file))
				.andExpect(status().isPayloadTooLarge());
	}

}
