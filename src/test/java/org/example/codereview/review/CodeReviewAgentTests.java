package org.example.codereview.review;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	private static final int MAX_INPUT = 1_048_576;

	private final StubChatModel chatModel = new StubChatModel(REPLY);

	private final CodeReviewAgent agent = new CodeReviewAgent(ChatClient.builder(chatModel), MAX_INPUT);

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
				.contains("INFO, MINOR, MAJOR, or CRITICAL")
				.contains("BUG, SECURITY, PERFORMANCE, MAINTAINABILITY, STYLE, or TESTING")
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
	void reviewTrimsLanguageAndContext() {
		agent.review(new ReviewRequest("  java  ", "  PR 42  ", "+ x"));

		assertThat(chatModel.lastPrompt().getUserMessage().getText())
				.contains("Language: java")
				.contains("Context: PR 42");
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

	@Test
	void reviewFileUsesGivenLanguageAndContext() {
		agent.reviewFile(new FileReviewRequest("app.py", "python", "Flask route", "print('hi')"));

		assertThat(chatModel.lastPrompt().getUserMessage().getText())
				.contains("Language: python")
				.contains("Context: Flask route");
	}

	@Test
	void reviewFileMapsModelReplyToReviewResult() {
		ReviewResult result = agent.reviewFile(new FileReviewRequest("Foo.java", null, null, "class Foo { }"));

		assertThat(result.verdict()).isEqualTo(ReviewResult.Verdict.REQUEST_CHANGES);
		assertThat(result.findings()).hasSize(1);
	}

	@Test
	void promptAsksForJsonMatchingReviewResult() {
		agent.review(new ReviewRequest("java", null, "+ x"));

		String userText = chatModel.lastPrompt().getUserMessage().getText();
		assertThat(userText).contains("\"verdict\"").contains("\"findings\"").contains("\"severity\"");
	}

	@Test
	void callsModelOncePerReview() {
		agent.review(new ReviewRequest(null, null, "+ x"));
		agent.reviewFile(new FileReviewRequest("Foo.java", null, null, "class Foo { }"));

		assertThat(chatModel.prompts()).hasSize(2);
	}

	@Test
	void mapsApproveWithNoFindings() {
		CodeReviewAgent approvingAgent = agentReplying("""
				{"summary": "Looks good", "verdict": "APPROVE", "findings": []}
				""");

		ReviewResult result = approvingAgent.review(new ReviewRequest(null, null, "+ x"));

		assertThat(result.verdict()).isEqualTo(ReviewResult.Verdict.APPROVE);
		assertThat(result.findings()).isEmpty();
	}

	@Test
	void mapsFindingWithoutLineNumber() {
		CodeReviewAgent commentingAgent = agentReplying("""
				{
				  "summary": "Minor naming issue",
				  "verdict": "COMMENT",
				  "findings": [
				    {"file": "Foo.java", "line": null, "severity": "MINOR", "category": "STYLE",
				     "message": "Unclear name", "suggestion": "Rename it"}
				  ]
				}
				""");

		ReviewResult result = commentingAgent.review(new ReviewRequest(null, null, "+ x"));

		assertThat(result.verdict()).isEqualTo(ReviewResult.Verdict.COMMENT);
		assertThat(result.findings()).singleElement().satisfies(finding -> {
			assertThat(finding.line()).isNull();
			assertThat(finding.category()).isEqualTo(ReviewResult.Category.STYLE);
		});
	}

	@Test
	void mapsReplyWrappedInMarkdownCodeFence() {
		CodeReviewAgent fencedAgent = agentReplying("""
				```json
				{"summary": "Looks good", "verdict": "APPROVE", "findings": []}
				```
				""");

		ReviewResult result = fencedAgent.review(new ReviewRequest(null, null, "+ x"));

		assertThat(result.verdict()).isEqualTo(ReviewResult.Verdict.APPROVE);
	}

	@Test
	void failsWhenModelReplyIsNotJson() {
		CodeReviewAgent confusedAgent = agentReplying("Sorry, I cannot review this code.");

		assertThatThrownBy(() -> confusedAgent.review(new ReviewRequest(null, null, "+ x")))
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	void failsWhenModelReplyHasUnknownVerdict() {
		CodeReviewAgent confusedAgent = agentReplying("""
				{"summary": "?", "verdict": "MAYBE", "findings": []}
				""");

		assertThatThrownBy(() -> confusedAgent.review(new ReviewRequest(null, null, "+ x")))
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	void recalculatesVerdictFromFindings() {
		CodeReviewAgent approvingAgent = agentReplying("""
				{
				  "summary": "SQL injection risk",
				  "verdict": "APPROVE",
				  "findings": [
				    {"file": "UserService.java", "line": 12, "severity": "CRITICAL", "category": "SECURITY",
				     "message": "Query built by string concatenation", "suggestion": "Use a PreparedStatement"}
				  ]
				}
				""");

		ReviewResult result = approvingAgent.review(new ReviewRequest(null, null, "+ x"));

		assertThat(result.verdict()).isEqualTo(ReviewResult.Verdict.REQUEST_CHANGES);
		assertThat(result.summary()).isEqualTo("SQL injection risk");
		assertThatThrownBy(() -> result.findings().add(null)).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void dropsIncompleteFindingsAndFillsABlankSummary() {
		CodeReviewAgent incomplete = agentReplying("""
				{
				  "summary": " ",
				  "verdict": "REQUEST_CHANGES",
				  "findings": [
				    {"file": "A.java", "line": 1, "severity": "CRITICAL", "category": "SECURITY",
				     "message": "Missing suggestion", "suggestion": " "}
				  ]
				}
				""");

		ReviewResult result = incomplete.review(new ReviewRequest(null, null, "+ x"));

		assertThat(result.verdict()).isEqualTo(ReviewResult.Verdict.APPROVE);
		assertThat(result.summary()).isEqualTo("No blocking findings.");
		assertThat(result.findings()).isEmpty();
	}

	@Test
	void rejectsInputLargerThanTheLimitWithoutCallingTheModel() {
		CodeReviewAgent limited = new CodeReviewAgent(ChatClient.builder(chatModel), 10);

		assertThatThrownBy(() -> limited.review(new ReviewRequest(null, null, "12345678901")))
				.isInstanceOf(ResponseStatusException.class)
				.extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
		assertThat(chatModel.prompts()).isEmpty();
	}

	@Test
	void propagatesModelFailure() {
		IllegalStateException failure = new IllegalStateException("provider unavailable");
		CodeReviewAgent failingAgent = new CodeReviewAgent(ChatClient.builder(StubChatModel.failingWith(failure)), MAX_INPUT);

		assertThatThrownBy(() -> failingAgent.reviewFile(new FileReviewRequest("Foo.java", null, null, "class Foo { }")))
				.isSameAs(failure);
	}

	private static CodeReviewAgent agentReplying(String reply) {
		return new CodeReviewAgent(ChatClient.builder(new StubChatModel(reply)), MAX_INPUT);
	}

}
