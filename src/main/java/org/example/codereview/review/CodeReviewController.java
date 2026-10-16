package org.example.codereview.review;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class CodeReviewController {

	private final CodeReviewAgent codeReviewAgent;

	public CodeReviewController(CodeReviewAgent codeReviewAgent) {
		this.codeReviewAgent = codeReviewAgent;
	}

	@PostMapping
	public ReviewResult review(@Valid @RequestBody ReviewRequest request) {
		return codeReviewAgent.review(request);
	}

}
