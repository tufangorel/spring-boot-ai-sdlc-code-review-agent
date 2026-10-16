# Spring Boot AI Code Review Agent

An AI-powered code review service built with Spring Boot and Spring AI. Send it a code diff or a source file and it returns a structured review: a summary, an overall verdict, and a list of actionable findings.

## Features

- Reviews diffs or whole files for bugs, security issues, performance problems, maintainability concerns and missing tests
- Returns structured JSON (not free text), mapped directly to Java records
- Verdict logic: `REQUEST_CHANGES` if any finding is `MAJOR` or `CRITICAL`, `COMMENT` for minor findings only, otherwise `APPROVE`
- REST API with request validation, accepting JSON or multipart file uploads
- Health and info endpoints via Spring Boot Actuator
- Swagger UI for trying out the API in the browser

## Tech stack

- Java 25
- Spring Boot 4.1.1 (Web MVC, Validation, Actuator)
- Spring AI 2.0.1 with the OpenAI model starter
- springdoc-openapi 3.1.1 (OpenAPI spec and Swagger UI)
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

### Swagger UI

Interactive API docs are available once the app is running:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec (JSON): http://localhost:8080/v3/api-docs

Use **Try it out** on any endpoint to send a request. The `/api/reviews/file/upload` endpoint shows a file picker for the upload.

### Build and test

```bash
./mvnw verify
```

The tests use a dummy API key, so no real key is needed to build.

## API reference

### Overview

| Endpoint                         | Content type          | Reviews                  | Typical SDLC stage                         |
|----------------------------------|-----------------------|--------------------------|--------------------------------------------|
| `POST /api/reviews`              | `application/json`    | A diff (changed lines)   | Pre-commit, pull request, CI pipeline      |
| `POST /api/reviews/file`         | `application/json`    | One full source file     | Development, IDE or script integration     |
| `POST /api/reviews/file/upload`  | `multipart/form-data` | One uploaded source file | Manual review, Swagger UI, quick checks    |

- **Base URL:** `http://localhost:8080`
- **Authentication:** none. Put the service behind a gateway or add Spring Security before exposing it outside a trusted network.
- **Behavior:** every call is synchronous and makes one request to the language model. Expect a response in a few seconds to tens of seconds, depending on input size.
- **Determinism:** results come from an LLM (temperature `0.2`), so the same input can produce slightly different findings between calls.
- **Data handling:** the submitted code is sent to the configured model provider (OpenAI by default). Do not submit code you are not allowed to share with that provider.

### How a review works

```mermaid
sequenceDiagram
    participant Client as Client (CI, IDE, curl, Swagger UI)
    participant API as CodeReviewController
    participant Agent as CodeReviewAgent
    participant LLM as LLM (OpenAI)

    Client->>API: POST diff or file
    API->>API: Validate request (400 / 413 / 415 on failure)
    API->>Agent: review(...) or reviewFile(...)
    Agent->>LLM: System prompt (senior reviewer rules) + user prompt (code)
    LLM-->>Agent: JSON matching the ReviewResult schema
    Agent-->>API: ReviewResult
    API-->>Client: 200 OK with summary, verdict, findings
```

1. The controller validates the request and, for uploads, reads the file as UTF-8 text.
2. `CodeReviewAgent` sends the code to the model with a system prompt that sets the reviewer's role, what to look for, and the verdict rules.
3. Spring AI asks the model for output that matches the `ReviewResult` JSON schema and converts the reply into Java records.
4. The structured result is returned unchanged to the client.

### Response: `ReviewResult`

All three endpoints return `200 OK` with this JSON body.

| Field      | Type               | Description                                              |
|------------|--------------------|----------------------------------------------------------|
| `summary`  | string             | Short overall assessment of the code                     |
| `verdict`  | enum               | `APPROVE`, `COMMENT` or `REQUEST_CHANGES` (see below)    |
| `findings` | array of `Finding` | Individual issues found; empty when nothing is reported  |

`Finding`:

| Field        | Type            | Description                                                         |
|--------------|-----------------|---------------------------------------------------------------------|
| `file`       | string          | File the finding refers to                                          |
| `line`       | integer or null | Line number when the model can point to one, otherwise `null`       |
| `severity`   | enum            | `INFO`, `MINOR`, `MAJOR`, `CRITICAL`                                |
| `category`   | enum            | `BUG`, `SECURITY`, `PERFORMANCE`, `MAINTAINABILITY`, `STYLE`, `TESTING` |
| `message`    | string          | What the problem is                                                 |
| `suggestion` | string          | How to fix it                                                       |

Verdict meaning:

| Verdict           | Meaning                                         | Suggested action in a pipeline |
|-------------------|-------------------------------------------------|--------------------------------|
| `APPROVE`         | No actionable findings                          | Pass                           |
| `COMMENT`         | Only `INFO` or `MINOR` findings                 | Pass, surface the comments     |
| `REQUEST_CHANGES` | At least one `MAJOR` or `CRITICAL` finding      | Fail or block the merge        |

The verdict rules are given to the model in its system prompt. They are not re-checked in code, so treat the verdict as the model's judgment.

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

---

### `POST /api/reviews` — review a diff

Reviews only the changed lines in a diff, such as `git diff` output. Best suited for pull requests and CI, where you care about what changed rather than the whole file.

- **Content type:** `application/json`
- **Operation:** `CodeReviewController.review` → `CodeReviewAgent.review`

Request body (`ReviewRequest`):

| Field      | Type   | Required | Constraints  | Description                                              |
|------------|--------|----------|--------------|----------------------------------------------------------|
| `diff`     | string | yes      | Not blank    | The diff to review, in unified diff format               |
| `language` | string | no       | —            | Programming language, for example `java`. Sent as `unknown` if omitted |
| `context`  | string | no       | —            | What the change is for, ticket text, or reviewer hints. Sent as `unknown` if omitted |

Example with curl:

```bash
curl -X POST http://localhost:8080/api/reviews \
  -H "Content-Type: application/json" \
  -d '{
        "language": "java",
        "context": "Adds a user lookup endpoint",
        "diff": "diff --git a/UserService.java b/UserService.java\n+ String sql = \"SELECT * FROM users WHERE name = '\''\" + name + \"'\''\";"
      }'
```

Example with PowerShell, reviewing your uncommitted changes:

```powershell
$body = @{
    language = "java"
    context  = "Refactor of the review controller"
    diff     = git diff | Out-String
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/reviews -ContentType "application/json" -Body $body
```

Responses: `200` with a `ReviewResult`, `400` if `diff` is missing or blank or the JSON is malformed, `415` if the content type is not JSON, `500` if the model call fails.

---

### `POST /api/reviews/file` — review a file sent as JSON

Reviews the entire contents of one source file. The model is told it is looking at a full file (not a diff), that line numbers are 1-based, and to use `fileName` as the file of every finding. Best suited for scripts and IDE integrations that already have the file contents in memory.

- **Content type:** `application/json`
- **Operation:** `CodeReviewController.reviewFile` → `CodeReviewAgent.reviewFile`

Request body (`FileReviewRequest`):

| Field      | Type   | Required | Constraints | Description                                                  |
|------------|--------|----------|-------------|--------------------------------------------------------------|
| `fileName` | string | yes      | Not blank   | File name or path, used in the `file` field of each finding  |
| `content`  | string | yes      | Not blank   | Full contents of the file                                    |
| `language` | string | no       | —           | Programming language. Sent as `unknown` if omitted           |
| `context`  | string | no       | —           | What the file does or what to focus on. Sent as `unknown` if omitted |

Example with curl:

```bash
curl -X POST http://localhost:8080/api/reviews/file \
  -H "Content-Type: application/json" \
  -d '{"fileName":"Foo.java","language":"java","content":"public class Foo { }"}'
```

Example with PowerShell, reviewing a file from disk:

```powershell
$path = ".\src\main\java\org\example\codereview\review\CodeReviewAgent.java"
$body = @{
    fileName = Split-Path $path -Leaf
    language = "java"
    context  = "Focus on prompt construction and error handling"
    content  = Get-Content -Raw $path
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/reviews/file -ContentType "application/json" -Body $body
```

Responses: `200` with a `ReviewResult`, `400` if `fileName` or `content` is missing or blank or the JSON is malformed, `415` if the content type is not JSON, `500` if the model call fails.

---

### `POST /api/reviews/file/upload` — review an uploaded file

Same review as `POST /api/reviews/file`, but the file is sent as a multipart upload. The file name is taken from the upload (any directory part is stripped), and the bytes are read as UTF-8. Best suited for manual checks and Swagger UI.

- **Content type:** `multipart/form-data`
- **Operation:** `CodeReviewController.reviewUploadedFile` → `CodeReviewAgent.reviewFile`

Form parts:

| Part       | Type   | Required | Constraints                     | Description                         |
|------------|--------|----------|---------------------------------|-------------------------------------|
| `file`     | file   | yes      | Not empty, UTF-8 text, max 1 MB | The source file to review           |
| `language` | string | no       | —                               | Programming language                |
| `context`  | string | no       | —                               | What the file does or what to focus on |

Example with curl:

```bash
curl -X POST http://localhost:8080/api/reviews/file/upload \
  -F "file=@src/main/java/org/example/codereview/review/CodeReviewAgent.java" \
  -F "language=java" \
  -F "context=Focus on prompt construction"
```

On Windows, use `curl.exe` (not the `curl` alias of `Invoke-WebRequest`) with the same arguments.

Responses: `200` with a `ReviewResult`, `400` if the `file` part is missing or the file is empty or only whitespace, `413` if the file is larger than 1 MB, `415` if the request is not multipart, `500` if the model call fails.

---

### Error responses

Errors use Spring Boot's default error format:

```json
{
  "timestamp": "2026-10-16T08:58:19.129Z",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/reviews"
}
```

| Status | When it happens                                                                 |
|--------|---------------------------------------------------------------------------------|
| `400`  | A required field is missing or blank, the JSON is malformed, the `file` part is missing, or the uploaded file is empty |
| `413`  | The uploaded file is larger than the multipart limit (1 MB by default). The body is empty |
| `415`  | The `Content-Type` does not match the endpoint (JSON vs multipart)              |
| `500`  | The model call failed, for example an invalid or missing API key, a provider outage, or a reply that could not be parsed into `ReviewResult` |

Validation details are not included in the body by default. To include them, set `server.error.include-message=always` and `server.error.include-binding-errors=always`. Validation failures are also logged by the server.

### Using the API in the SDLC

**Pre-push or pre-commit check.** Review your local changes before pushing:

```bash
jq -n --rawfile diff <(git diff --cached) '{language: "java", diff: $diff}' \
  | curl -s -X POST http://localhost:8080/api/reviews -H "Content-Type: application/json" -d @- \
  | jq .
```

**CI quality gate.** Fail the pipeline when the verdict is `REQUEST_CHANGES`:

```bash
git diff origin/main...HEAD > change.diff
jq -n --rawfile diff change.diff '{diff: $diff, context: "Pull request against main"}' > request.json

curl -sf -X POST "$REVIEW_URL/api/reviews" \
  -H "Content-Type: application/json" -d @request.json > review.json

jq -r '.findings[] | "\(.severity) \(.category) \(.file):\(.line // "-") \(.message)"' review.json
jq -e '.verdict != "REQUEST_CHANGES"' review.json > /dev/null
```

**Single-file deep review.** Use `POST /api/reviews/file` or `/api/reviews/file/upload` when a file is new, was heavily rewritten, or needs a full audit rather than a look at the latest change.

### Limits and tips

- Keep inputs reasonably small. Very large diffs or files can exceed the model's context window and produce a `500`, and they make reviews slower and less focused. Split large changes and review them per file.
- Raise the upload limit with `spring.servlet.multipart.max-file-size` and `spring.servlet.multipart.max-request-size` if needed.
- Use `context` to steer the review, for example "security-sensitive code" or "ignore style issues".
- In Windows PowerShell 5.1, prefer `Invoke-RestMethod` with `ConvertTo-Json` for JSON bodies. Passing inline JSON to `curl.exe` loses the embedded double quotes and results in a `400`.

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
├── OpenApiConfig.java             # OpenAPI / Swagger UI metadata
└── review
    ├── CodeReviewAgent.java       # ChatClient with a senior-reviewer system prompt
    ├── CodeReviewController.java  # POST /api/reviews, /api/reviews/file, /api/reviews/file/upload
    ├── FileReviewRequest.java     # Input DTO for whole-file reviews
    ├── ReviewRequest.java         # Input DTO for diff reviews
    └── ReviewResult.java          # Output DTO (summary, verdict, findings)
```

## Roadmap

The agent currently performs a single-pass review: one request, one model call, no tools. Planned work is to make it agentic using Spring AI tools (`@Tool`), so the model can gather what it needs during a review:

- Fetch pull request diffs from GitHub
- Read full file contents for surrounding context
- Post review comments back to the pull request
