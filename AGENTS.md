# spring-boot-ai-sdlc-code-review-agent

An agentic AI code review service built with Spring Boot and Spring AI. It accepts a code diff and returns a structured review (summary, verdict, findings).

## Stack

- Java 25, Maven (use the wrapper: `.\mvnw.cmd`)
- Spring Boot 4.1.1 (`spring-boot-starter-webmvc`, validation, actuator)
- Spring AI 2.0.1 with the OpenAI starter (`spring-ai-starter-model-openai`)
- Base package: `org.example.codereview`

## Layout

- `review/CodeReviewAgent` - wraps `ChatClient` with a senior-reviewer system prompt and maps the model response to `ReviewResult` via `.entity(...)`.
- `review/ReviewRequest` - input: `diff` (required), `language`, `context` (optional).
- `review/ReviewResult` - output: `summary`, `verdict` (`APPROVE` / `COMMENT` / `REQUEST_CHANGES`), and `findings` (file, line, severity, category, message, suggestion).
- `review/CodeReviewController` - `POST /api/reviews`.

## Configuration

`application.properties` reads the key from the `OPENAI_API_KEY` environment variable and uses `gpt-4o-mini` at temperature 0.2. The context-load test sets a dummy key, so `.\mvnw.cmd verify` passes without a real one.

OpenAI was chosen as a default; switching provider (Anthropic, Ollama, etc.) means swapping the starter dependency and the `spring.ai.*` properties.

## Running

```powershell
$env:OPENAI_API_KEY="sk-..."
.\mvnw.cmd spring-boot:run
```

```
POST http://localhost:8080/api/reviews
{"language":"java","diff":"<git diff output>"}
```

## Conventions

- Tabs for indentation (Spring Initializr style).
- Records for DTOs; constructor injection, no Lombok.

## Status and next steps

The project has not been initialized as a git repository yet.

The agent currently does a single-pass review. Planned direction for making it agentic is to give it tools via Spring AI `@Tool`, for example:

- fetch a pull request diff from GitHub,
- read full file contents for surrounding context,
- post review comments back to the pull request.
