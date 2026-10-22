package org.example.codereview.review;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;

class CodeReviewAgentTests {

	private static final String REPLY = """
			{
			  "summary": "SQL injection risk",
			  "verdict": "REQUEST_CHANGES",
			  "findings": [
			    {
			      "file": "UserService.java",
			      "line": 12,
			      "severity": "CRITICAL",
			      "category": "SECURITY",
			      "message": "Query built by string concatenation",
			      "suggestion": "Use a PreparedStatement"
			    }
			  ]
			}
			""";

	private final StubChatModel chatModel = new StubChatModel(REPLY);

	private final CodeReviewAgent agent = new CodeReviewAgent(ChatClient.builder(chatModel));

	@Test
	void reviewMapsModelReplyToReviewResult() {
		ReviewResult result = agent.review(new ReviewRequest("java", "Adds a lookup", "+ String sql = q + name;"));

		assertThat(result.summary()).isEqualTo("SQL injection risk");
		assertThat(result.verdict()).isEqualTo(ReviewResult.Verdict.REQUEST_CHANGES);
		assertThat(result.findings()).singleElement().satisfies(finding -> {
			assertThat(finding.file()).isEqualTo("UserService.java");
			assertThat(finding.line()).isEqualTo(12);
			assertThat(finding.severity()).isEqualTo(ReviewResult.Severity.CRITICAL);
			assertThat(finding.category()).isEqualTo(ReviewResult.Category.SECURITY);
		});
	}

	@Test
	void reviewSendsReviewerSystemPromptAndDiff() {
		agent.review(new ReviewRequest("java", "Adds a lookup", "+ String sql = q + name;"));

		assertThat(chatModel.lastPrompt().getSystemMessage().getText())
				.contains("senior software engineer")
				.contains("REQUEST_CHANGES if any finding is MAJOR or CRITICAL");
		assertThat(chatModel.lastPrompt().getUserMessage().getText())
				.contains("Language: java")
				.contains("Context: Adds a lookup")
				.contains("+ String sql = q + name;");
	}

	@Test
	void reviewUsesUnknownForMissingLanguageAndContext() {
		agent.review(new ReviewRequest(null, " ", "+ x"));

		assertThat(chatModel.lastPrompt().getUserMessage().getText())
				.contains("Language: unknown")
				.contains("Context: unknown");
	}

	@Test
	void reviewFileSendsWholeFilePrompt() {
		agent.reviewFile(new FileReviewRequest("Foo.java", "java", null, "class Foo { }"));

		assertThat(chatModel.lastPrompt().getUserMessage().getText())
				.contains("full contents of the file below, not a diff")
				.contains("Use \"Foo.java\" as the file of every finding")
				.contains("File: Foo.java")
				.contains("Context: unknown")
				.contains("class Foo { }");
	}

	@Test
	void reviewFileKeepsBracesInContent() {
		String content = "class Foo { void run() { if (x) { y(); } } }";

		agent.reviewFile(new FileReviewRequest("Foo.java", "java", null, content));

		assertThat(chatModel.lastPrompt().getUserMessage().getText()).contains(content);
	}

}
