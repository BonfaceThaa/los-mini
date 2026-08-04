# ADRs

## Table Of Contents
1. [ADR-001: Increase Statement Upload Size Limits in Nginx and Spring Boot](#adr-001-increase-statement-upload-size-limits-in-nginx-and-spring-boot)
2. [ADR-002: Use Spring Config Import for Local .env Property Loading](#adr-002-use-spring-config-import-for-local-env-property-loading)

# ADR-001: Increase Statement Upload Size Limits in Nginx and Spring Boot

## Status
Accepted

## Date
2026-08-01

## Context
The inbound statement upload endpoint `POST /api/v1/service/statements/mpesa/upload` was failing when larger multipart files were sent through the Nginx reverse proxy.

The Nginx error log showed:

```text
client intended to send too large body: 1276582 bytes
```

This meant the request was being rejected by Nginx before it reached the Spring Boot application.

The affected endpoint is implemented in [ServiceStatementUploadController.java](../src/main/java/com/credvenn/lm/statementinbox/ServiceStatementUploadController.java).

## Decision
Increase request body limits at both layers that can reject multipart uploads:

- Nginx proxy layer using `client_max_body_size`
- Spring Boot multipart handling using:
  - `spring.servlet.multipart.max-file-size`
  - `spring.servlet.multipart.max-request-size`

This ensures upload behavior is consistent and avoids a situation where the proxy accepts the request but the application rejects it later.

## Changes Made
### Nginx
Configured a larger body size limit for the API host or upload route:

```nginx
client_max_body_size 10m;
```

### Spring Boot
Configured explicit multipart limits in `application.yaml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

## Rationale
- The failing upload size was approximately `1.22 MB`, which is larger than the common default `1m` Nginx limit.
- Explicit application-level limits make behavior predictable across environments.
- Keeping proxy and application limits aligned reduces operational confusion during troubleshooting.

## Verification
After applying the change:

1. Validate and reload Nginx

```bash
sudo nginx -t
sudo systemctl reload nginx
```

2. Restart the Spring Boot application if `application.yaml` changed

3. Test the upload endpoint through the public Nginx URL with a multipart file larger than the previously failing size

Example:

```bash
curl -i -X POST "https://api.credvenn.com/api/v1/service/statements/mpesa/upload" \
  -H "Authorization: Bearer <SERVICE_TOKEN>" \
  -F "destinationEmail=test@credvenn.com" \
  -F "messageId=test-msg-001" \
  -F "receivedAt=2026-08-01T10:00:00Z" \
  -F "file=@/path/to/test-statement.pdf"
```

Success criteria:

- No `413 Request Entity Too Large`
- No Nginx `client intended to send too large body` error
- Request reaches the backend and returns a normal application response

## Consequences
### Positive
- Mpesa statement uploads larger than the old proxy limit can now reach the backend
- Upload-related failures are easier to diagnose because both proxy and app limits are explicit

### Trade-offs
- Larger uploads increase bandwidth, memory, and storage pressure
- Future size changes must be made in both Nginx and Spring Boot if we want limits to remain aligned

## Operational Notes
- This endpoint is protected by `SERVICE_STATEMENT_UPLOAD`
- It requires a service-authenticated token, not a normal user token
- If upload failures recur, inspect both:
  - Nginx error logs
  - Spring Boot application logs

## References
- Endpoint: `POST /api/v1/service/statements/mpesa/upload`
- Controller: [ServiceStatementUploadController.java](../src/main/java/com/credvenn/lm/statementinbox/ServiceStatementUploadController.java)
- App config: [application.yaml](../src/main/resources/application.yaml)

# ADR-002: Use Spring Config Import for Local .env Property Loading

## Status
Accepted

## Date
2026-08-02

## Context
The application configuration was externalized into a local `.env` file for environment-specific settings such as credentials, URLs, ports, and API keys. For local execution, there was a need to let Spring Boot read those values without requiring every variable to be manually exported into the shell first.

## Decision
Use Spring Boot config import to load the `.env` file as a properties-style configuration source:

```properties
spring.config.import=optional:file:.env[.properties]
```

This allows Spring-managed configuration to resolve values from a local `.env` file when running the application from the project working directory.

## Rationale
- Keeps local environment-specific values out of `application.yaml`
- Reduces the need to manually export variables for ordinary local runs
- Uses Spring Boot's native config import mechanism instead of introducing a separate dotenv library

## Consequences
### Positive
- Local development setup becomes simpler when running from the project root
- Sensitive and environment-specific values can remain outside the committed Spring config

### Trade-offs
- `.env` must be formatted as a `.properties`-style file with plain `KEY=value` lines
- Relative `.env` loading depends on the process working directory
- Complex values are harder to manage safely in `.env` than in YAML

## Operational Notes
- `spring.config.import` makes values available to Spring property binding only
- It does not create real OS environment variables for the Java process
- If non-Spring scripts, libraries, or runtime hooks require true process environment variables, use the service manager to load them, for example with `EnvironmentFile=` in `systemd`
- For deployment, an absolute import path is safer than relying on the working directory

## Verification
- Start the application from the project root with the `.env` file present
- Confirm that datasource, JWT, and integration settings resolve without shell-exporting variables first
- Confirm that missing `.env` does not fail startup when `optional:` is used

## References
- App config: [application.yaml](../src/main/resources/application.yaml)
- Local env file: [.env](../.env)
