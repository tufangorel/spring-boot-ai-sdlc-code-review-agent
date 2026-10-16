# Spring Boot AI Code Review Agent

An AI-powered code review service built with Spring Boot and Spring AI. Send it a code diff and it returns a structured review: a summary, an overall verdict, and a list of actionable findings.

## Features

- Reviews diffs for bugs, security issues, performance problems, maintainability concerns and missing tests
- Returns structured JSON (not free text), mapped directly to Java records
- Verdict logic: `REQUEST_CHANGES` if any finding is `MAJOR` or `CRITICAL`, `COMMENT` for minor findings only, otherwise `APPROVE`
- Simple REST API with request validation
- Health and info endpoints via Spring Boot Actuator

## Tech stack

- Java 25
- Spring Boot 4.1.1 (Web MVC, Validation, Actuator)
- Spring AI 2.0.1 with the OpenAI model starter
- Maven (wrapper included)

## Getting started

### Prerequisites

- JDK 25
- An OpenAI API key

### Run

Windows (PowerShell):

```powershell
$env:OPENAI_API_KEY="sk-..."
.\mvnw.cmd spring-boot:run
```

macOS / Linux:

```bash
export OPENAI_API_KEY="sk-..."
./mvnw spring-boot:run
```

The service starts on `http://localhost:8080`.

### Build and test

```bash
./mvnw verify
```

The tests use a dummy API key, so no real key is needed to build.

## API

### `POST /api/reviews`

Request body:

| Field      | Required | Description                                         |
|------------|----------|-----------------------------------------------------|
| `diff`     | yes      | The code diff to review (for example `git diff` output) |
| `language` | no       | Programming language of the code                    |
| `context`  | no       | Extra context about the change                      |

Example:

```bash
curl -X POST http://localhost:8080/api/reviews \
  -H "Content-Type: application/json" \
  -d '{
        "language": "java",
        "context": "Adds a user lookup endpoint",
        "diff": "diff --git a/UserService.java b/UserService.java\n+ String sql = \"SELECT * FROM users WHERE name = '\''\" + name + \"'\''\";"
      }'
```

Example response:

```json
{
  "summary": "The change builds a SQL query via string concatenation, which is vulnerable to SQL injection.",
  "verdict": "REQUEST_CHANGES",
  "findings": [
    {
      "file": "UserService.java",
      "line": 1,
      "severity": "CRITICAL",
      "category": "SECURITY",
      "message": "User input is concatenated directly into a SQL query.",
      "suggestion": "Use a parameterized query or PreparedStatement."
    }
  ]
}
```

Possible values:

- `verdict`: `APPROVE`, `COMMENT`, `REQUEST_CHANGES`
- `severity`: `INFO`, `MINOR`, `MAJOR`, `CRITICAL`
- `category`: `BUG`, `SECURITY`, `PERFORMANCE`, `MAINTAINABILITY`, `STYLE`, `TESTING`

## Configuration

Settings live in `src/main/resources/application.properties`:

| Property                                   | Default             |
|--------------------------------------------|---------------------|
| `spring.ai.openai.api-key`                 | `${OPENAI_API_KEY}` |
| `spring.ai.openai.chat.options.model`      | `gpt-4o-mini`       |
| `spring.ai.openai.chat.options.temperature`| `0.2`               |

To use a different provider (Anthropic, Ollama, etc.), swap the Spring AI starter dependency in `pom.xml` and update the `spring.ai.*` properties.

## Project structure

```
src/main/java/org/example/codereview
├── SpringBootAiSdlcCodeReviewAgentApplication.java
└── review
    ├── CodeReviewAgent.java       # ChatClient with a senior-reviewer system prompt
    ├── CodeReviewController.java  # POST /api/reviews
    ├── ReviewRequest.java         # Input DTO
    └── ReviewResult.java          # Output DTO (summary, verdict, findings)
```

## Roadmap

The agent currently performs a single-pass review. Planned work is to make it agentic using Spring AI tools (`@Tool`):

- Fetch pull request diffs from GitHub
- Read full file contents for surrounding context
- Post review comments back to the pull request
