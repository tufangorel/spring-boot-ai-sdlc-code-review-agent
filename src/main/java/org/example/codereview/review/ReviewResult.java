package org.example.codereview.review;

import java.util.List;

public record ReviewResult(
		String summary,
		Verdict verdict,
		List<Finding> findings) {

	public ReviewResult {
		findings = findings == null ? List.of()
				: List.copyOf(findings.stream().filter(finding -> finding != null).toList());
		if (summary != null) {
			summary = summary.isBlank() ? null : summary.trim();
		}
	}

	public static ReviewResult enforce(ReviewResult model) {
		List<Finding> grounded = model == null ? List.of()
				: model.findings().stream().filter(ReviewResult::complete).toList();
		Verdict verdict = decide(grounded);
		String summary = model == null ? null : model.summary();
		if (summary == null || summary.isBlank()) {
			summary = defaultSummary(verdict);
		}
		return new ReviewResult(summary, verdict, grounded);
	}

	private static boolean complete(Finding finding) {
		return finding.severity() != null
				&& finding.message() != null && !finding.message().isBlank()
				&& finding.suggestion() != null && !finding.suggestion().isBlank();
	}

	private static Verdict decide(List<Finding> findings) {
		boolean blocking = findings.stream()
				.anyMatch(finding -> finding.severity() == Severity.MAJOR || finding.severity() == Severity.CRITICAL);
		boolean minor = findings.stream().anyMatch(finding -> finding.severity() == Severity.MINOR);
		if (blocking) {
			return Verdict.REQUEST_CHANGES;
		}
		if (minor) {
			return Verdict.COMMENT;
		}
		return Verdict.APPROVE;
	}

	private static String defaultSummary(Verdict verdict) {
		return switch (verdict) {
			case REQUEST_CHANGES -> "Changes requested.";
			case COMMENT -> "Comments only.";
			case APPROVE -> "No blocking findings.";
		};
	}

	public enum Verdict {
		APPROVE, COMMENT, REQUEST_CHANGES
	}

	public enum Severity {
		INFO, MINOR, MAJOR, CRITICAL
	}

	public enum Category {
		BUG, SECURITY, PERFORMANCE, MAINTAINABILITY, STYLE, TESTING
	}

	public record Finding(
			String file,
			Integer line,
			Severity severity,
			Category category,
			String message,
			String suggestion) {
	}

}
