package org.example.codereview.review;

import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(
		String language,
		String context,
		@NotBlank String diff) {
}
