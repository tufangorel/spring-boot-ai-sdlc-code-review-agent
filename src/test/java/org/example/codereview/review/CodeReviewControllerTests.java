package org.example.codereview.review;

import java.nio.charset.StandardCharsets;
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

	private static final ReviewResult RESULT_WITH_FINDING = new ReviewResult("SQL injection",
			ReviewResult.Verdict.REQUEST_CHANGES,
			List.of(new ReviewResult.Finding("Foo.java", 7, ReviewResult.Severity.CRITICAL,
					ReviewResult.Category.SECURITY, "Concatenated SQL", "Use a PreparedStatement")));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CodeReviewAgent codeReviewAgent;

	@Test
	void reviewsDiff() throws Exception {
		given(codeReviewAgent.review(any())).willReturn(RESULT_WITH_FINDING);

		mockMvc.perform(post("/api/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"language":"java","context":"PR 42","diff":"+ String sql = q + name;"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary").value("SQL injection"))
				.andExpect(jsonPath("$.verdict").value("REQUEST_CHANGES"))
				.andExpect(jsonPath("$.findings[0].file").value("Foo.java"))
				.andExpect(jsonPath("$.findings[0].line").value(7))
				.andExpect(jsonPath("$.findings[0].severity").value("CRITICAL"))
				.andExpect(jsonPath("$.findings[0].category").value("SECURITY"))
				.andExpect(jsonPath("$.findings[0].message").value("Concatenated SQL"))
				.andExpect(jsonPath("$.findings[0].suggestion").value("Use a PreparedStatement"));

		verify(codeReviewAgent).review(new ReviewRequest("java", "PR 42", "+ String sql = q + name;"));
	}

	@Test
	void reviewsDiffWithOnlyRequiredField() throws Exception {
		given(codeReviewAgent.review(any())).willReturn(RESULT);

		mockMvc.perform(post("/api/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"diff":"+ x"}
								"""))
				.andExpect(status().isOk());

		verify(codeReviewAgent).review(new ReviewRequest(null, null, "+ x"));
	}

	@Test
	void rejectsDiffRequestWithoutDiff() throws Exception {
		mockMvc.perform(post("/api/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"language":"java"}
								"""))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(codeReviewAgent);
	}

	@Test
	void rejectsDiffRequestWithWhitespaceDiff() throws Exception {
		mockMvc.perform(post("/api/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"diff":"   "}
								"""))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(codeReviewAgent);
	}

	@Test
	void rejectsMalformedJson() throws Exception {
		mockMvc.perform(post("/api/reviews")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{bad"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(codeReviewAgent);
	}

	@Test
	void rejectsDiffRequestWithWrongContentType() throws Exception {
		mockMvc.perform(post("/api/reviews")
						.contentType(MediaType.TEXT_PLAIN)
						.content("+ x"))
				.andExpect(status().isUnsupportedMediaType());

		verifyNoInteractions(codeReviewAgent);
	}

	@Test
	void reviewsUploadedFile() throws Exception {
		given(codeReviewAgent.reviewFile(any())).willReturn(RESULT);
		MockMultipartFile file = new MockMultipartFile("file", "Foo.java", MediaType.TEXT_PLAIN_VALUE,
				"class Foo {}".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/reviews/file/upload").file(file).param("language", "java"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verdict").value("APPROVE"));

		ArgumentCaptor<FileReviewRequest> captor = ArgumentCaptor.forClass(FileReviewRequest.class);
		verify(codeReviewAgent).reviewFile(captor.capture());
		assertThat(captor.getValue()).isEqualTo(new FileReviewRequest("Foo.java", "java", null, "class Foo {}"));
	}

	@Test
	void uploadPassesContextToAgent() throws Exception {
		given(codeReviewAgent.reviewFile(any())).willReturn(RESULT);
		MockMultipartFile file = new MockMultipartFile("file", "Foo.java", MediaType.TEXT_PLAIN_VALUE,
				"class Foo {}".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/reviews/file/upload").file(file)
						.param("language", "java")
						.param("context", "Security audit"))
				.andExpect(status().isOk());

		verify(codeReviewAgent).reviewFile(new FileReviewRequest("Foo.java", "java", "Security audit", "class Foo {}"));
	}

	@Test
	void uploadStripsDirectoryFromFileName() throws Exception {
		given(codeReviewAgent.reviewFile(any())).willReturn(RESULT);
		MockMultipartFile file = new MockMultipartFile("file", "src/main/java/Foo.java", MediaType.TEXT_PLAIN_VALUE,
				"class Foo {}".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/reviews/file/upload").file(file))
				.andExpect(status().isOk());

		verify(codeReviewAgent).reviewFile(new FileReviewRequest("Foo.java", null, null, "class Foo {}"));
	}

	@Test
	void uploadWithoutFileNameUsesUnknown() throws Exception {
		given(codeReviewAgent.reviewFile(any())).willReturn(RESULT);
		MockMultipartFile file = new MockMultipartFile("file", "", MediaType.TEXT_PLAIN_VALUE,
				"class Foo {}".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/reviews/file/upload").file(file))
				.andExpect(status().isOk());

		verify(codeReviewAgent).reviewFile(new FileReviewRequest("unknown", null, null, "class Foo {}"));
	}

	@Test
	void uploadReadsContentAsUtf8() throws Exception {
		given(codeReviewAgent.reviewFile(any())).willReturn(RESULT);
		String content = "// Çalışma notu: ünlü harfler ✓\nclass Foo {}";
		MockMultipartFile file = new MockMultipartFile("file", "Foo.java", MediaType.TEXT_PLAIN_VALUE,
				content.getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/reviews/file/upload").file(file))
				.andExpect(status().isOk());

		verify(codeReviewAgent).reviewFile(new FileReviewRequest("Foo.java", null, null, content));
	}

	@Test
	void rejectsEmptyUploadedFile() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "Foo.java", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

		mockMvc.perform(multipart("/api/reviews/file/upload").file(file))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(codeReviewAgent);
	}

	@Test
	void rejectsWhitespaceOnlyUploadedFile() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "Foo.java", MediaType.TEXT_PLAIN_VALUE,
				" \n\t \n".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/reviews/file/upload").file(file))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(codeReviewAgent);
	}

	@Test
	void rejectsUploadWithoutFilePart() throws Exception {
		mockMvc.perform(multipart("/api/reviews/file/upload").param("language", "java"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(codeReviewAgent);
	}

	@Test
	void rejectsUploadSentAsJson() throws Exception {
		mockMvc.perform(post("/api/reviews/file/upload")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fileName":"Foo.java","content":"class Foo {}"}
								"""))
				.andExpect(status().isUnsupportedMediaType());

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
	void jsonFileReviewPassesAllFieldsToAgent() throws Exception {
		given(codeReviewAgent.reviewFile(any())).willReturn(RESULT);

		mockMvc.perform(post("/api/reviews/file")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fileName":"app.py","language":"python","context":"Flask route","content":"print('hi')"}
								"""))
				.andExpect(status().isOk());

		verify(codeReviewAgent).reviewFile(new FileReviewRequest("app.py", "python", "Flask route", "print('hi')"));
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

	@Test
	void rejectsJsonBodyWithBlankFileName() throws Exception {
		mockMvc.perform(post("/api/reviews/file")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fileName":" ","content":"class Foo {}"}
								"""))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(codeReviewAgent);
	}

	@Test
	void rejectsJsonBodyWithoutFileName() throws Exception {
		mockMvc.perform(post("/api/reviews/file")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"content":"class Foo {}"}
								"""))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(codeReviewAgent);
	}

	@Test
	void rejectsJsonFileReviewWithWrongContentType() throws Exception {
		mockMvc.perform(post("/api/reviews/file")
						.contentType(MediaType.TEXT_PLAIN)
						.content("class Foo {}"))
				.andExpect(status().isUnsupportedMediaType());

		verifyNoInteractions(codeReviewAgent);
	}

}
