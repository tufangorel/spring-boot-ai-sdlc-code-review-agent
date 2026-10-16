package org.example.codereview.review;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reviews")
@Tag(name = "Code reviews", description = "AI-powered code review of diffs and source files")
public class CodeReviewController {

	private final CodeReviewAgent codeReviewAgent;

	public CodeReviewController(CodeReviewAgent codeReviewAgent) {
		this.codeReviewAgent = codeReviewAgent;
	}

	@Operation(summary = "Review a diff", description = "Reviews a code diff such as `git diff` output.")
	@PostMapping
	public ReviewResult review(@Valid @RequestBody ReviewRequest request) {
		return codeReviewAgent.review(request);
	}

	@Operation(summary = "Review an uploaded file", description = "Reviews the full contents of a single uploaded source file.")
	@PostMapping(path = "/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ReviewResult reviewUploadedFile(
			@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) String language,
			@RequestParam(required = false) String context) throws IOException {
		String content = new String(file.getBytes(), StandardCharsets.UTF_8);
		if (content.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
		}
		String fileName = StringUtils.getFilename(file.getOriginalFilename());
		return codeReviewAgent.reviewFile(new FileReviewRequest(
				StringUtils.hasText(fileName) ? fileName : "unknown", language, context, content));
	}

	@Operation(summary = "Review a file sent as JSON", description = "Reviews the full contents of a single source file sent in a JSON body.")
	@PostMapping(path = "/file", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ReviewResult reviewFile(@Valid @RequestBody FileReviewRequest request) {
		return codeReviewAgent.reviewFile(request);
	}

}
