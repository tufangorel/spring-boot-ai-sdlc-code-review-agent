package org.example.codereview.review;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewResultTests {

	@Test
	void requestsChangesWhenAFindingIsMajorOrCritical() {
		ReviewResult result = ReviewResult.enforce(model("APPROVE", "Look again", List.of(
				finding(ReviewResult.Severity.MINOR, "Name", "Rename it"),
				finding(ReviewResult.Severity.MAJOR, "Missing check", "Authorize the caller"))));

		assertThat(result.verdict()).isEqualTo(ReviewResult.Verdict.REQUEST_CHANGES);
		assertThat(result.summary()).isEqualTo("Look again");
		assertThat(result.findings()).hasSize(2);
	}

	@Test
	void commentsWhenFindingsAreOnlyMinor() {
		ReviewResult result = ReviewResult.enforce(model("REQUEST_CHANGES", " ", List.of(
				finding(ReviewResult.Severity.INFO, "Note", "Consider a limit"),
				finding(ReviewResult.Severity.MINOR, "Name", "Rename it"))));

		assertThat(result.verdict()).isEqualTo(ReviewResult.Verdict.COMMENT);
		assertThat(result.summary()).isEqualTo("Comments only.");
	}

	@Test
	void approvesInfoOnlyFindings() {
		ReviewResult result = ReviewResult.enforce(model("COMMENT", null, List.of(
				finding(ReviewResult.Severity.INFO, "Note", "Consider a limit"))));

		assertThat(result.verdict()).isEqualTo(ReviewResult.Verdict.APPROVE);
		assertThat(result.summary()).isEqualTo("No blocking findings.");
	}

	@Test
	void dropsFindingsWithoutSeverityMessageOrSuggestion() {
		ReviewResult result = ReviewResult.enforce(new ReviewResult("Kept", ReviewResult.Verdict.REQUEST_CHANGES, List.of(
				new ReviewResult.Finding("A.java", 1, null, ReviewResult.Category.BUG, "Message", "Fix"),
				new ReviewResult.Finding("A.java", 2, ReviewResult.Severity.CRITICAL, ReviewResult.Category.SECURITY, " ", "Fix"),
				new ReviewResult.Finding("A.java", 3, ReviewResult.Severity.CRITICAL, ReviewResult.Category.SECURITY, "Message", null),
				finding(ReviewResult.Severity.MINOR, "Name", "Rename it"))));

		assertThat(result.findings()).singleElement().satisfies(finding -> {
			assertThat(finding.line()).isEqualTo(4);
			assertThat(finding.severity()).isEqualTo(ReviewResult.Severity.MINOR);
		});
		assertThat(result.verdict()).isEqualTo(ReviewResult.Verdict.COMMENT);
	}

	@Test
	void copiesTheFindingsList() {
		ReviewResult result = new ReviewResult("Looks good", ReviewResult.Verdict.APPROVE, List.of());

		assertThatThrownBy(() -> result.findings().add(finding(ReviewResult.Severity.INFO, "Note", "Consider a limit")))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	private static ReviewResult model(String verdict, String summary, List<ReviewResult.Finding> findings) {
		return new ReviewResult(summary, ReviewResult.Verdict.valueOf(verdict), findings);
	}

	private static ReviewResult.Finding finding(ReviewResult.Severity severity, String message, String suggestion) {
		return new ReviewResult.Finding("A.java", 4, severity, ReviewResult.Category.STYLE, message, suggestion);
	}

}
