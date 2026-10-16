package org.example.codereview.review;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CodeReviewController.class)
class CodeReviewControllerTests {

	private static final ReviewResult RESULT = new ReviewResult("Looks good", ReviewResult.Verdict.APPROVE, List.of());

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CodeReviewAgent codeReviewAgent;

	@Test
	void reviewsUploadedFile() throws Exception {
		given(codeReviewAgent.reviewFile(any())).willReturn(RESULT);
		MockMultipartFile file = new MockMultipartFile("file", "Foo.java", MediaType.TEXT_PLAIN_VALUE,
				"class Foo {}".getBytes());

		mockMvc.perform(multipart("/api/reviews/file/upload").file(file).param("language", "java"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verdict").value("APPROVE"));

		ArgumentCaptor<FileReviewRequest> captor = ArgumentCaptor.forClass(FileReviewRequest.class);
		verify(codeReviewAgent).reviewFile(captor.capture());
		assertThat(captor.getValue()).isEqualTo(new FileReviewRequest("Foo.java", "java", null, "class Foo {}"));
	}

	@Test
	void rejectsEmptyUploadedFile() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "Foo.java", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

		mockMvc.perform(multipart("/api/reviews/file/upload").file(file))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(codeReviewAgent);
	}

	@Test
	void reviewsFileFromJsonBody() throws Exception {
		given(codeReviewAgent.reviewFile(any())).willReturn(RESULT);

		mockMvc.perform(post("/api/reviews/file")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fileName":"Foo.java","language":"java","content":"class Foo {}"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary").value("Looks good"));

		verify(codeReviewAgent).reviewFile(new FileReviewRequest("Foo.java", "java", null, "class Foo {}"));
	}

	@Test
	void rejectsJsonBodyWithoutContent() throws Exception {
		mockMvc.perform(post("/api/reviews/file")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fileName":"Foo.java"}
								"""))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(codeReviewAgent);
	}

}
