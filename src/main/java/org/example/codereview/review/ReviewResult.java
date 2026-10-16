package org.example.codereview.review;

import java.util.List;

public record ReviewResult(
		String summary,
		Verdict verdict,
		List<Finding> findings) {

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
