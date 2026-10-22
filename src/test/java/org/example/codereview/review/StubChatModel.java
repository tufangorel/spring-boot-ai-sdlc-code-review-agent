package org.example.codereview.review;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class StubChatModel implements ChatModel {

	private final String reply;

	private final List<Prompt> prompts = new ArrayList<>();

	StubChatModel(String reply) {
		this.reply = reply;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		prompts.add(prompt);
		return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
	}

	List<Prompt> prompts() {
		return prompts;
	}

	Prompt lastPrompt() {
		return prompts.getLast();
	}

}
