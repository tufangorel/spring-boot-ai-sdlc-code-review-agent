package org.example.codereview.review;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CodeReviewAgent {

	private static final String SYSTEM_PROMPT = """
			You are a senior software engineer performing a code review.
			You will receive either a diff or the full contents of a single source file.
			Review it for bugs, security issues, performance problems,
			maintainability concerns and missing tests.
			Only report findings that are actionable and grounded in the provided code.
			Reference the file and line when possible.
			Use severity INFO, MINOR, MAJOR, or CRITICAL.
			Use category BUG, SECURITY, PERFORMANCE, MAINTAINABILITY, STYLE, or TESTING.
			Every finding needs a severity, a message, and a suggestion.
			Choose REQUEST_CHANGES if any finding is MAJOR or CRITICAL,
			COMMENT if any finding is MINOR and none are MAJOR or CRITICAL, otherwise APPROVE.
			The service recalculates the verdict from the findings.
			""";

	private final ChatClient chatClient;

	private final int maxInputCharacters;

	public CodeReviewAgent(ChatClient.Builder chatClientBuilder,
			@Value("${review.max-input-characters:1048576}") int maxInputCharacters) {
		this.chatClient = chatClientBuilder
				.defaultSystem(SYSTEM_PROMPT)
				.build();
		this.maxInputCharacters = maxInputCharacters;
	}

	public ReviewResult review(ReviewRequest request) {
		requireWithinLimit(request.diff());
		return complete("""
				Language: {language}
				Context: {context}

				Diff:
				{diff}
				""", Map.of(
				"language", orUnknown(request.language()),
				"context", orUnknown(request.context()),
				"diff", request.diff()));
	}

	public ReviewResult reviewFile(FileReviewRequest request) {
		requireWithinLimit(request.content());
		return complete("""
				Review the full contents of the file below, not a diff.
				Line numbers are 1-based and refer to this file.
				Use "{fileName}" as the file of every finding.

				File: {fileName}
				Language: {language}
				Context: {context}

				Content:
				{content}
				""", Map.of(
				"fileName", request.fileName(),
				"language", orUnknown(request.language()),
				"context", orUnknown(request.context()),
				"content", request.content()));
	}

	private ReviewResult complete(String template, Map<String, String> parameters) {
		ReviewResult modelResult = chatClient.prompt()
				.user(user -> {
					var spec = user.text(template);
					parameters.forEach(spec::param);
				})
				.call()
				.entity(ReviewResult.class);
		return ReviewResult.enforce(modelResult);
	}

	private void requireWithinLimit(String value) {
		if (value != null && value.length() > maxInputCharacters) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Review input exceeds the maximum size");
		}
	}

	private static String orUnknown(String value) {
		if (value == null || value.isBlank()) {
			return "unknown";
		}
		return value.trim();
	}

}
