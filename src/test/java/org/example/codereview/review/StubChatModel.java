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

	private final RuntimeException failure;

	private final List<Prompt> prompts = new ArrayList<>();

	StubChatModel(String reply) {
		this(reply, null);
	}

	private StubChatModel(String reply, RuntimeException failure) {
		this.reply = reply;
		this.failure = failure;
	}

	static StubChatModel failingWith(RuntimeException failure) {
		return new StubChatModel(null, failure);
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		prompts.add(prompt);
		if (failure != null) {
			throw failure;
		}
		return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
	}

	List<Prompt> prompts() {
		return prompts;
	}

	Prompt lastPrompt() {
		return prompts.getLast();
	}

}
