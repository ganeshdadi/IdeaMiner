# IdeaMiner

**AI Opportunity Discovery Tool for Banking Codebases**

---

## Overview

IdeaMiner is a **Java/Spring Boot** console application that ingests multiple Java (Spring Boot) repositories, builds a **hybrid index** (SQLite metadata + vector embeddings), and uses **OpenAI GPT‑4o** to automatically surface AI/ML or workflow‑automation opportunities that can improve the customer experience.

It was built to satisfy the following high‑level requirement:
> *"Code is the real source of truth – we need a tool that can understand the whole code‑base across many repositories and surface business opportunities where AI/ML could make customers’ lives easier or improve banking operations.*"

---

## Key Features

- **One‑time class‑level indexing** – parses every `.java` file with **JavaParser**, extracts annotations (`@RestController`, `@Service`, etc.) and computes cyclomatic complexity.
- **Hybrid Index** – stores structured metadata in a local **SQLite** DB and semantic embeddings (via the **all‑MiniLM‑L6‑v2** model) in a JSON‑backed **LangChain4j** vector store.
- **Map‑Reduce analysis** – LLM‑driven brainstorming (Map) followed by vector‑store validation (Deep Dive) and final refined output (Reduce).
- **Customer‑centric Opportunity Cards** – each card lists a title, benefit, current code references, and a high‑level AI/ML solution.
- **CLI interface** – easy to invoke `index` and `analyze` commands.

---

## Architecture Diagram

```
+------------------------+        +--------------------------+        +-------------------+
|   GitHub Repos (.java) |  -->   |   Ingestion Service      |  -->   |   SQLite Metadata |
| (any number of repos)  |        | (JavaParser + embeddings) |        +-------------------+
+------------------------+        +--------------------------+        |
                                                                     |
                                                                     v
                                                            +-------------------+
                                                            | Vector Store (JSON |
                                                            |  backed by Lang   |
                                                            +-------------------+
                                                                   |
                                                                   v
+------------------------+        +--------------------------+        +-------------------+
|   LLM (GPT‑4o)         |  <--   |  Analysis Service        |  <--   |  Query Engine      |
+------------------------+        +--------------------------+        +-------------------+
```

---

## Prerequisites

- **Java 17** (or newer)
- **Maven** (to build the project)
- **OpenAI API key** (set as environment variable `OPENAI_API_KEY`)
- (Optional) **Git** – to clone repositories you want to index

---

## Installation & Build

```bash
# Clone the repository (if you haven't already)
git clone https://github.com/your-org/IdeaMiner.git
cd IdeaMiner

# Build the project
mvn clean package
```

The build will download the required Maven dependencies, including:
- `JavaParser`
- `LangChain4j` with the `all‑MiniLM‑L6‑v2` embedding model
- `OpenAI` client for GPT‑4o

---

## Usage

### 1. Index a repository

```bash
# Example: index a local clone of a Banking service repository
java -jar target/ideaminer-0.0.1-SNAPSHOT.jar index /path/to/your/repo
```

The command will:
1. Walk every `.java` file.
2. Extract class metadata and store it in `ideaminer_metadata.db`.
3. Generate embeddings and persist them to `ideaminer_vectors.json`.

### 2. Run the AI analysis

```bash
java -jar target/ideaminer-0.0.1-SNAPSHOT.jar analyze
```

The tool will:
1. Query SQLite for the most complex services/batch jobs.
2. Ask GPT‑4o to brainstorm AI/ML opportunities.
3. Validate each idea against the vector store.
4. Produce a markdown report `AI_Opportunity_Report.md` with ranked Opportunity Cards.

---

## Example Report (Excerpt)

> **Opportunity Card**
> - **Title**: Real‑time Fraud Detection
> - **Customer Benefit**: Prevents unauthorized transactions instantly rather than nightly batch checks.
> - **Current State**: `com.bank.batch.NightlyReconciliationJob`
> - **Proposed AI Solution**: Replace the nightly rule‑engine with an online ML scoring model.

---

## Configuration

All configuration lives in `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:sqlite:ideaminer_metadata.db
spring.datasource.driver-class-name=org.sqlite.JDBC
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.model-name=gpt-4o
```

You can change the SQLite file location or the vector store file (`ideaminer_vectors.json`) by editing `VectorStoreConfig.java`.

---

## Extending the Tool

- **Incremental updates** – add a Git watcher to ingest only changed files.
- **Additional languages** – plug in `tree‑sitter` parsers for Kotlin, Python, etc.
- **Different LLMs** – swap `OpenAiChatModel` for Anthropic or a self‑hosted model.

---

## License

MIT License – feel free to adapt, extend, and integrate into internal tooling.

---

## Support

For questions, open an issue on the repository or contact the internal tooling team.
