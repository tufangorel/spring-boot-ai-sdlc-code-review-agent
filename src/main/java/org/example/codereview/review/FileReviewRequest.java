package org.example.codereview.review;

import jakarta.validation.constraints.NotBlank;

public record FileReviewRequest(
		@NotBlank String fileName,
		String language,
		String context,
		@NotBlank String content) {
}
