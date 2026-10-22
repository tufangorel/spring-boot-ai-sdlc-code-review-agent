package org.example.codereview;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

class ApplicationMainTests {

	@Test
	void mainStartsApplication() {
		assertThatNoException().isThrownBy(() -> SpringBootAiSdlcCodeReviewAgentApplication.main(new String[] {
				"--spring.main.web-application-type=none",
				"--spring.ai.openai.api-key=test-key" }));
	}

	@Test
	void mainStartsApplicationWithAnthropic() {
		assertThatNoException().isThrownBy(() -> SpringBootAiSdlcCodeReviewAgentApplication.main(new String[] {
				"--spring.main.web-application-type=none",
				"--AI_PROVIDER=anthropic",
				"--spring.ai.anthropic.api-key=test-key" }));
	}

}
