# Cook-Copilot Backend

## Prerequisites

- **Java 17+**
- **PostgreSQL** running on the configured host/port
- **Maven** (or use the included `mvnw` wrapper)

## Setup

1. **Start PostgreSQL**
   ```bash
   pg_ctl.exe -D ..\data start
   ```

2. **Configure environment variables**
   Copy `.env.example` to `.env` and fill in your values:
   ```bash
   cp .env.example .env
   ```

## Running the Server

### Option 1: Using Maven Wrapper (recommended)
```bash
.\mvnw.cmd spring-boot:run
```

### Option 2: Using Maven
```bash
mvn spring-boot:run
```

### Option 3: Build JAR and run
```bash
./mvnw clean package -DskipTests
java -jar target/Cook-Copilot-0.0.1-SNAPSHOT.jar
```

> **Note:** Spring Boot does not load `.env` files natively. Either:
> - Load variables via your IDE run configuration
> - Use [spring-dotenv](https://github.com/paulschwarz/spring-dotenv) library
> - Or export them in your shell before running:
>   ```powershell
>   # PowerShell - load all .env vars into current session
>   Get-Content .env | ForEach-Object {
>       if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
>           [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
>       }
>   }
>   ./mvnw spring-boot:run
>   ```

## API Documentation (Swagger UI)

Once the server is running:

- **Swagger UI** → [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON** → [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Click the 🔒 **Authorize** button and paste your JWT token to test authenticated endpoints.
