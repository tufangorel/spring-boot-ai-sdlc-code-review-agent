package org.example.codereview.review;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class CodeReviewAgent {

	private static final String SYSTEM_PROMPT = """
			You are a senior software engineer performing a code review.
			You will receive either a diff or the full contents of a single source file.
			Review it for bugs, security issues, performance problems,
			maintainability concerns and missing tests.
			Only report findings that are actionable and grounded in the provided code.
			Reference the file and line when possible.
			Choose REQUEST_CHANGES if any finding is MAJOR or CRITICAL,
			COMMENT if there are only minor findings, otherwise APPROVE.
			""";

	private final ChatClient chatClient;

	public CodeReviewAgent(ChatClient.Builder chatClientBuilder) {
		this.chatClient = chatClientBuilder
				.defaultSystem(SYSTEM_PROMPT)
				.build();
	}

	public ReviewResult review(ReviewRequest request) {
		return chatClient.prompt()
				.user(u -> u.text("""
						Language: {language}
						Context: {context}

						Diff:
						{diff}
						""")
						.param("language", orUnknown(request.language()))
						.param("context", orUnknown(request.context()))
						.param("diff", request.diff()))
				.call()
				.entity(ReviewResult.class);
	}

	public ReviewResult reviewFile(FileReviewRequest request) {
		return chatClient.prompt()
				.user(u -> u.text("""
						Review the full contents of the file below, not a diff.
						Line numbers are 1-based and refer to this file.
						Use "{fileName}" as the file of every finding.

						File: {fileName}
						Language: {language}
						Context: {context}

						Content:
						{content}
						""")
						.param("fileName", request.fileName())
						.param("language", orUnknown(request.language()))
						.param("context", orUnknown(request.context()))
						.param("content", request.content()))
				.call()
				.entity(ReviewResult.class);
	}

	private static String orUnknown(String value) {
		return value == null || value.isBlank() ? "unknown" : value;
	}

}
