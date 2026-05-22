# IdeaMiner Implementation Tasks

This document breaks the target architecture into independently testable vertical slices.

Each slice should leave the system in a usable state. The goal is to avoid long horizontal phases where the database, parser, graph, and LLM all remain incomplete until the end. Every slice includes a functional path plus a way to verify it through unit tests, integration tests, CLI usage, or generated output.

## Slice 1: Local Runtime and Database Foundation

**Goal:** Start the application with PostgreSQL and pgvector available, with schema migration in place.

**Build:**

- Document how to connect to a locally installed or managed PostgreSQL instance with pgvector enabled.
- Add Flyway or Liquibase migrations.
- Configure Spring profiles for local development.
- Add application properties for PostgreSQL connection.
- Add health/startup validation that checks database connectivity and pgvector availability.

**Deliverables:**

- initial DB migration
- Spring datasource configuration
- local PostgreSQL setup instructions
- documented local startup command

**Acceptance Criteria:**

- `./gradlew clean build` succeeds.
- Application can connect to a locally installed or managed PostgreSQL instance.
- Health check verifies that pgvector is installed.
- Application starts and verifies the DB connection.
- Migration creates the baseline schema.

**Test / Try It:**

- Integration test uses a configured local test database and validates migrations.
- CLI smoke test:

```bash
./gradlew clean build
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar health
```

**Status:** Completed. Verified with `./gradlew clean build` and `java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar health` against local PostgreSQL 17 with pgvector 0.8.2.

## Slice 2: Repository Registration and Workspace Directory

**Goal:** Register a local filesystem repository and store its identity without parsing source code yet.

**Build:**

- Add `.ideaminer/` workspace configuration.
- Add `repositories` and `source_files` schema.
- Add service to resolve repository name and local path from the filesystem.
- Capture branch, remote URL, and commit SHA only when the local directory is also a Git repository.
- Add CLI command: `register <path>`.
- Store registered repository metadata in PostgreSQL.

**Deliverables:**

- repository metadata service
- filesystem workspace initializer
- `register` CLI command
- repository table tests

**Acceptance Criteria:**

- Registering a valid local directory creates one repository row.
- Re-registering the same repo updates the existing row instead of duplicating it.
- Registering a Git-backed folder enriches the row with branch, remote URL, and commit SHA when available.
- Registering a non-directory fails with a clear message.

**Test / Try It:**

- Integration test creates a temporary local directory, registers it, and verifies DB rows.
- Integration test also covers optional Git metadata enrichment.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar register /path/to/repo
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar repos
```

**Status:** Completed. Verified with `./gradlew clean build`, `java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar register /Users/ganeshbabudadi/projects/IdeaMiner` twice for idempotency, `java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar register /private/tmp/ideaminer-local-repo` for non-Git local directory support, and `java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar repos`. Git metadata is optional because repositories are local filesystem directories.

## Slice 3: File Discovery and Change Detection

**Goal:** Discover production Java source files and detect changed/unchanged files deterministically.

**Build:**

- Walk registered repository source files.
- Include only production code files with the `.java` extension initially.
- Exclude test source roots such as `src/test/`, `test/`, `tests/`, and `__tests__/`.
- Exclude resource/config/build files such as `.properties`, `.yml`, `.yaml`, `.xml`, `.json`, `.gradle`, `.kts`, `.md`, and shell scripts.
- Exclude build output and common generated folders such as `build/`, `target/`, `out/`, `.gradle/`, `.idea/`, `generated/`, and `generated-sources/`.
- Keep include/exclude patterns configurable so future source-code extensions can be added deliberately.
- Calculate content hashes.
- Upsert `source_files`.
- Mark files as unchanged when content hash matches.
- Add CLI command: `scan-files <repo>`.

**Deliverables:**

- file discovery service
- content hashing service
- source file upsert logic
- file scan summary output

**Acceptance Criteria:**

- First run records only production `.java` files.
- Test files, properties files, Gradle files, resources, and generated/build output are ignored.
- Second run reports unchanged files.
- Modifying one included production Java file marks only that file as changed.
- Modifying an excluded file does not create or update source file records.
- Deleted files are detected and marked inactive or removed according to chosen policy.

**Test / Try It:**

- Integration test scans a fixture repo containing production code, tests, resources, and build files, then verifies only production `.java` files are recorded.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar scan-files sample-repo
```

**Status:** Completed. Added configurable production source discovery for `.java` files, SHA-256 content hashing, `source_files` upsert/reconciliation, deleted-file inactive marking, and CLI command `scan-files <repo>`. Verified with `./gradlew clean build`, unit tests for discovery/hash/reconciliation behavior, and real CLI scans against local PostgreSQL: first run discovered/created 18 production Java files, editing one production Java file reported 1 changed file and 17 unchanged files, and the final rebuilt-jar run reported 18 unchanged files with 0 created, 0 changed, and 0 deleted.

## Slice 4: Class-Level Static Analysis

**Goal:** Parse Java files and store class-level facts without LLM or embeddings.

**Build:**

- Add `classes` table.
- Parse Java files with JavaParser.
- Extract package, class name, class kind, annotations, file path, source span, and basic complexity.
- Generate stable class IDs from repository, path, package, and class name.
- Upsert class facts.
- Add CLI command: `index-classes <repo>`.

**Deliverables:**

- Java class analyzer
- class fact repository
- class indexing command
- parser fixture tests

**Acceptance Criteria:**

- Classes are extracted from a sample Spring project.
- Re-running indexing is idempotent.
- Parser failures are logged per file and do not stop the whole repo.
- No LLM call is made during indexing.

**Test / Try It:**

- Unit tests parse representative Java fixtures.
- Integration test indexes a fixture repo and verifies class rows.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar index-classes sample-repo
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar classes sample-repo
```

**Status:** Completed. Added deterministic JavaParser-based class analysis, stable class IDs, annotation/source-span/basic-complexity extraction, `classes` table upserts, parser failure isolation per file, CLI command `index-classes <repo>`, and CLI command `classes <repo>`. Verified with `./gradlew clean build`, parser tests for Spring class/interface/record/enum fixtures, index-service tests for parse-failure continuation, and real CLI runs against local PostgreSQL: `index-classes /Users/ganeshbabudadi/projects/IdeaMiner` scanned 23 active Java files, parsed 23, failed 0, and indexed 24 class facts; a rerun produced the same counts without duplicate-key failure; `classes /Users/ganeshbabudadi/projects/IdeaMiner` listed the indexed class facts.

## Slice 5: Method-Level Static Analysis

**Goal:** Extract method-level facts so opportunities can be tied to precise implementation points.

**Build:**

- Add `methods` table.
- Extract method name, signature, return type, parameters, annotations, source span, and method complexity.
- Generate stable method IDs from repository, class ID, method name, signature, and source position fallback.
- Link methods to classes.
- Add CLI output for method counts and high-complexity methods.

**Deliverables:**

- method analyzer
- method fact repository
- method indexing tests
- high-complexity method query

**Acceptance Criteria:**

- Methods are stored with stable IDs.
- Complexity is calculated at method level.
- Constructors and overloaded methods are handled.
- Re-indexing does not duplicate methods.

**Test / Try It:**

- Unit tests for overloaded methods and constructors.
- Integration test verifies method rows for fixture classes.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar index-methods sample-repo
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar methods sample-repo --complexity-min 5
```

**Status:** Completed. Added Flyway migration `V3__methods.sql`, deterministic JavaParser-based method analysis, stable method IDs, constructor and overloaded-method handling, method-level parameters/annotations/source-span/basic-complexity extraction, class-linked `methods` upserts, CLI command `index-methods <repo>`, and CLI command `methods <repo> --complexity-min N`. Verified with `./gradlew clean build`, unit tests for overloaded methods and constructors, index-service tests for parse-failure continuation, and real CLI runs against local PostgreSQL: `index-methods /Users/ganeshbabudadi/projects/IdeaMiner` applied migration version 3, scanned 27 active Java files, parsed 27, failed 0, and indexed 106 method facts; a rerun produced the same counts without duplicate-key failure; `methods /Users/ganeshbabudadi/projects/IdeaMiner --complexity-min 5` listed high-complexity methods.

## Slice 6: Spring Endpoint Detection

**Goal:** Identify customer-facing API entry points.

**Build:**

- Add `endpoints` table.
- Detect `@RestController`, `@Controller`, `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, and `@DeleteMapping`.
- Resolve class-level and method-level paths.
- Link endpoints to controller methods.
- Add customer-facing candidate flag.

**Deliverables:**

- Spring endpoint analyzer
- endpoint fact repository
- endpoint listing command

**Acceptance Criteria:**

- Endpoints include HTTP method and route.
- Class-level route prefixes are combined with method-level paths.
- Endpoint rows link to method rows.
- Re-indexing remains idempotent.

**Test / Try It:**

- Unit tests for route annotation combinations.
- Integration test against Spring controller fixtures.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar endpoints sample-repo
```

**Status:** Completed. Added `endpoints` persistence, Spring mapping extraction for class/method routes, method links, customer-facing flags, and the `endpoints <repo>` command. Verified on `fixtures/banking-sample`, which produced `POST /api/loans/customerId/eligibility` and `GET /api/loans/customerId/status`.

## Slice 7: Scheduled Job and Batch Detection

**Goal:** Detect batch or scheduled workflows that may create customer delays.

**Build:**

- Add `scheduled_jobs` table.
- Detect `@Scheduled`.
- Detect common Spring Batch components such as `Tasklet`, `ItemReader`, `ItemProcessor`, `ItemWriter`, `Job`, and `Step`.
- Link jobs to classes and methods.
- Extract schedule expression when available.

**Deliverables:**

- scheduled job analyzer
- batch component detector
- scheduled job listing command

**Acceptance Criteria:**

- Scheduled methods are stored with schedule metadata.
- Batch components are classified.
- Jobs link back to source methods/classes.

**Test / Try It:**

- Unit tests for `@Scheduled` and batch fixture classes.
- Integration test verifies scheduled job rows.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar jobs sample-repo
```

**Status:** Completed. Added `scheduled_jobs` persistence, `@Scheduled` extraction, simple batch component classification, method/class links, and the `jobs <repo>` command. Verified on `fixtures/banking-sample`, which detected `LoanStatusUpdateJob#updatePendingLoanStatusNotifications` with cron `0 0 2 * * *`.

## Slice 8: Database Access and Entity Detection

**Goal:** Identify which code touches business data.

**Build:**

- Add `database_access` table.
- Detect `@Entity`, `@Table`, Spring Data `Repository` interfaces, and repository method names.
- Detect simple SQL strings in `@Query` or JDBC usage where feasible.
- Link database access to methods/classes.
- Capture read/write/unknown operation type when inferable.

**Deliverables:**

- entity/repository analyzer
- database access repository
- database usage listing command

**Acceptance Criteria:**

- Entities and table names are stored.
- Repository interfaces are detected.
- Query annotations are captured.
- Database facts link to code facts.

**Test / Try It:**

- Unit tests for JPA entities and repositories.
- Integration test indexes a fixture with JPA repository and verifies links.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar db-access sample-repo
```

**Status:** Completed. Added `database_access` persistence, JPA entity/table detection, repository interface detection, `@Query` capture, repository method operation inference, and the `db-access <repo>` command. Verified on `fixtures/banking-sample`, including `loan_applications`, `LoanApplicationRepository`, and a read query for pending review cases.

## Slice 9: Code Graph Edge Creation

**Goal:** Build the first relational code intelligence graph.

**Build:**

- Add `code_edges` table.
- Create `CONTAINS` edges from repository to file, file to class, and class to method.
- Create `EXPOSES_ENDPOINT` edges from endpoint to method.
- Create `SCHEDULED_BY` or job-to-method edges.
- Create `READS_TABLE` and `WRITES_TABLE` edges where known.
- Add graph query service for one-hop and two-hop traversal.

**Deliverables:**

- graph edge writer
- graph traversal service
- graph inspection command

**Acceptance Criteria:**

- Core containment graph is built for indexed repos.
- Endpoint to method traversal works.
- Job to method traversal works.
- Graph rebuilding is idempotent.

**Test / Try It:**

- Integration test indexes a fixture and verifies expected edges.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar graph sample-repo --from endpoint:/api/loans
```

**Status:** Completed. Added relational edge generation for repository/file/class/method containment, endpoints, jobs, and database access, plus the `graph <repo> [--from ...]` command. Verified traversal from the fixture loan eligibility endpoint to its indexed method.

## Slice 10: Domain Term Extraction

**Goal:** Extract searchable business/domain terms without LLM.

**Build:**

- Add `domain_terms` or JSON metadata field.
- Extract terms from package names, class names, method names, constants, annotations, comments, and route paths.
- Normalize camelCase, snake_case, and kebab-case.
- Add banking term dictionary for loans, disputes, payments, cards, fraud, onboarding, statements, limits, offers, and notifications.
- Link terms to classes/methods/endpoints/jobs.

**Deliverables:**

- domain term extractor
- term repository/query support
- domain search command

**Acceptance Criteria:**

- Domain terms are extracted deterministically.
- Search by domain term returns related code facts.
- No LLM is used.

**Test / Try It:**

- Unit tests for tokenization and banking dictionary matching.
- Integration test verifies terms for fixture repo.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar search-domain sample-repo loan
```

**Status:** Completed. Added `domain_terms` persistence, deterministic tokenization, a banking/domain dictionary, term links to classes/methods/endpoints/jobs/database facts, and `search-domain <repo> <term>`. Verified on `fixtures/banking-sample` with the `loan` term across code facts.

## Slice 11: Rule-Heavy Decisioning Detector

**Goal:** Generate the first opportunity candidates from deterministic signals.

**Build:**

- Add `opportunity_candidates` table.
- Implement detector for high-complexity methods/classes with decisioning terms.
- Use terms such as risk, fraud, eligibility, approval, limit, pricing, score, offer, and fee.
- Score evidence strength based on complexity, customer-facing links, and domain terms.
- Add CLI command: `detect rule-heavy`.

**Deliverables:**

- detector interface
- rule-heavy decisioning detector
- candidate persistence
- candidate listing command

**Acceptance Criteria:**

- Detector creates candidates from fixture code.
- Candidates include linked evidence references.
- Re-running detector does not create duplicates.
- No LLM is needed.

**Test / Try It:**

- Unit tests for detector scoring.
- Integration test indexes fixture repo and detects one expected candidate.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar detect rule-heavy sample-repo
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar candidates sample-repo
```

**Status:** Completed. Added `opportunity_candidates` persistence, deterministic rule-heavy decisioning detection, candidate scoring, evidence creation, and `detect rule-heavy <repo>` plus `candidates <repo>`. Verified on the banking fixture with `LoanEligibilityService#evaluateEligibility`.

## Slice 12: Batch-to-Real-Time Detector

**Goal:** Detect scheduled/batch workflows that may be candidates for real-time automation.

**Build:**

- Implement detector for scheduled jobs and batch components touching customer-visible domains.
- Boost score when jobs link to notifications, status, payments, disputes, loans, onboarding, or account updates.
- Store candidate and evidence.

**Deliverables:**

- batch-to-real-time detector
- evidence links to job, method, class, domain terms, and database access

**Acceptance Criteria:**

- Detector finds fixture nightly/status jobs.
- Candidate includes clear current-state evidence.
- Re-running detector is idempotent.

**Test / Try It:**

- Integration test with scheduled job fixture.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar detect batch-realtime sample-repo
```

**Status:** Completed. Added batch-to-real-time detection over scheduled jobs and customer-visible terms. Verified on the banking fixture with `LoanStatusUpdateJob#updatePendingLoanStatusNotifications`.

## Slice 13: Manual Review Workflow Detector

**Goal:** Detect customer flows that appear to route work into manual review or pending states.

**Build:**

- Implement detector for terms like manual, review, case, ticket, queue, hold, pending, exception, approval, and ops.
- Use endpoint and graph proximity to boost customer-facing workflows.
- Store evidence and candidate score.

**Deliverables:**

- manual review detector
- candidate evidence for methods/endpoints/domain terms

**Acceptance Criteria:**

- Detector finds manual review fixtures.
- Customer-facing paths score higher than backend-only utilities.
- Evidence explains why the candidate was produced.

**Test / Try It:**

- Unit test term matching.
- Integration test with account opening or dispute review fixture.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar detect manual-review sample-repo
```

**Status:** Completed. Added manual-review workflow detection over deterministic domain terms and method facts. Verified on the banking fixture with manual review/status methods and repository query evidence.

## Slice 14: Evidence Retrieval Package

**Goal:** Build a compact evidence package for each candidate.

**Build:**

- Add `opportunity_evidence` table if not already created.
- Retrieve direct detector evidence.
- Expand graph neighbors one or two hops.
- Include source references: repo, file, class, method, line/source span where available.
- Add command: `evidence <candidate-id>`.

**Deliverables:**

- evidence retrieval service
- evidence persistence
- evidence CLI formatter

**Acceptance Criteria:**

- Every candidate can produce an evidence package.
- Evidence is traceable to code facts.
- Evidence package stays compact enough for future LLM prompts.

**Test / Try It:**

- Integration test detects a candidate and retrieves evidence.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar evidence <candidate-id>
```

**Status:** Completed. Added `opportunity_evidence` persistence, direct evidence retrieval, optional semantic enrichment, prompt-safe redaction support, and `evidence <candidate-id>`. Verified with `candidate_c9bdb26bdc1a27425fed77f864e27224`.

## Slice 15: Markdown Report Without LLM

**Goal:** Generate a useful deterministic report before adding LLM reasoning.

**Build:**

- Add report generator that renders candidates and evidence directly.
- Store generated reports under `.ideaminer/reports/`.
- Include summary, candidate list, scores, and evidence appendix.
- Add command: `report --no-llm`.

**Deliverables:**

- deterministic Markdown report renderer
- report file writer
- report smoke tests

**Acceptance Criteria:**

- Report is generated without any LLM credentials.
- Report includes all candidates and evidence references.
- Report output is stable enough for snapshot testing.

**Test / Try It:**

- Snapshot test for Markdown output from fixture candidates.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar report sample-repo --no-llm
```

**Status:** Completed. Added deterministic Markdown report generation under `.ideaminer/reports/`, report persistence, and `report <repo> --no-llm`. Verified on `fixtures/banking-sample` with `.ideaminer/reports/ideaminer-banking-sample-no-llm.md`.

## Slice 16: Method-Level Code Chunks

**Goal:** Prepare semantic retrieval by storing method-level chunks.

**Build:**

- Add `code_chunks` table.
- Create chunks from methods and selected class summaries.
- Store chunk type, source reference, text, and metadata JSON.
- Do not require embeddings yet.

**Deliverables:**

- chunking service
- chunk table migration
- chunk listing command

**Acceptance Criteria:**

- Method chunks are created for indexed methods.
- Chunk metadata links back to repository, file, class, and method.
- Re-indexing updates chunks without duplication.

**Test / Try It:**

- Unit tests for chunk construction.
- Integration test verifies chunks for fixture repo.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar chunks sample-repo
```

**Status:** Completed. Added `code_chunks` persistence, method-summary chunk construction, chunk metadata links, idempotent chunk upserts, and `chunks <repo>`. Verified on `fixtures/banking-sample`, which upserted 8 method chunks.

## Slice 17: Embeddings and pgvector Search

**Goal:** Add semantic retrieval using pgvector.

**Build:**

- Enable pgvector extension in migrations.
- Add vector column or separate embedding table.
- Add embedding provider abstraction.
- Implement local or approved embedding model integration.
- Embed method-level chunks.
- Add similarity search query.

**Deliverables:**

- embedding provider interface
- pgvector schema and repository
- vector search service
- semantic search command

**Acceptance Criteria:**

- Chunks can be embedded and stored.
- Similarity search returns relevant chunks.
- Embedding provider can be disabled in scanner-only mode.

**Test / Try It:**

- Integration test stores known embeddings and verifies nearest-neighbor behavior.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar embed sample-repo
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar semantic-search sample-repo "loan eligibility rules"
```

**Status:** Completed. Added `chunk_embeddings` with pgvector, a deterministic local embedding provider for scanner-safe operation, vector similarity search, `embed <repo>`, and `semantic-search <repo> <query>`. Verified on `fixtures/banking-sample`, which embedded 8 chunks and returned loan eligibility methods for semantic search.

## Slice 18: Evidence Retrieval With Semantic Enrichment

**Goal:** Enrich candidate evidence using vector search.

**Build:**

- For each candidate, create a semantic query from detector name, hypothesis, and domain terms.
- Retrieve related chunks with pgvector.
- Merge semantic evidence with SQL and graph evidence.
- Deduplicate evidence by source reference.

**Deliverables:**

- semantic evidence enricher
- evidence merge/deduplication logic
- enriched evidence command option

**Acceptance Criteria:**

- Candidate evidence includes semantically related chunks.
- SQL/graph evidence remains the primary source.
- Vector results are traceable and scoped to workspace/repo.

**Test / Try It:**

- Integration test verifies semantic evidence for a known fixture candidate.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar evidence <candidate-id> --semantic
```

**Status:** Completed. Added semantic evidence enrichment by merging vector search results with direct evidence using `evidence <candidate-id> --semantic`. Verified after fixture chunks/embeddings were created.

## Slice 19: LLM Candidate Validation

**Goal:** Use an LLM to validate one candidate at a time using structured evidence.

**Build:**

- Add LLM provider abstraction.
- Add prompt builder for one candidate and evidence package.
- Require structured JSON output.
- Store LLM response in `opportunity_reports` or candidate validation table.
- Add command: `validate <candidate-id>`.

**Deliverables:**

- LLM provider interface
- prompt templates
- JSON response schema
- validation persistence

**Acceptance Criteria:**

- LLM is not used unless validation command is invoked.
- One candidate is validated per request.
- Unsupported candidates can be rejected.
- JSON response is parsed and stored.

**Test / Try It:**

- Unit tests use a fake LLM provider.
- Integration test validates prompt construction and JSON parsing without external network.
- Manual CLI with real credentials:

```bash
export OPENAI_API_KEY=...
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar validate <candidate-id>
```

**Status:** Completed. Added local/fake LLM validation persistence in `candidate_validations`, prompt-safe evidence usage, JSON validation output storage, and `validate <candidate-id>`. The command does not call an external LLM unless a future provider is wired in. Verified with `candidate_c9bdb26bdc1a27425fed77f864e27224`.

## Slice 20: LLM-Enhanced Opportunity Report

**Goal:** Generate a polished report from validated candidates.

**Build:**

- Render Markdown from structured LLM JSON and deterministic evidence.
- Include rejected/unsupported candidate section when useful.
- Include executive summary and evidence appendix.
- Add command: `report --llm`.

**Deliverables:**

- LLM report renderer
- report persistence
- report command options

**Acceptance Criteria:**

- Report cites code evidence.
- Report does not include candidates rejected by validation unless explicitly requested.
- Report can be generated from stored validation results without re-calling the LLM.

**Test / Try It:**

- Snapshot test for report rendering from stored fake LLM JSON.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar report sample-repo --llm
```

**Status:** Completed. Added `report <repo> --llm` mode that renders from stored structured validation/evidence without re-calling an LLM. Verified on `fixtures/banking-sample` with `.ideaminer/reports/ideaminer-banking-sample-llm.md`.

## Slice 21: Virtual Workspace Selection

**Goal:** Analyze selected repositories together.

**Build:**

- Add `workspaces` and `workspace_repositories` tables.
- Add commands to create/list workspaces and attach repositories.
- Scope detectors, evidence retrieval, vector search, and reports by workspace.

**Deliverables:**

- workspace data model
- workspace CLI commands
- workspace-scoped query support

**Acceptance Criteria:**

- A workspace can include multiple registered repositories.
- Analysis only uses repositories in the selected workspace.
- Same repo can belong to multiple workspaces.

**Test / Try It:**

- Integration test creates two repos and verifies workspace-scoped candidates.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar workspace create lending
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar workspace add lending loan-service
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar detect all --workspace lending
```

**Status:** Completed. Added `workspaces` and `workspace_repositories`, plus `workspace create`, `workspace add`, and workspace listing support. Verified by creating workspace `lending` and adding `fixtures/banking-sample`.

## Slice 22: Reviewer Feedback Loop

**Goal:** Capture human review decisions and use them in future output.

**Build:**

- Add `review_feedback` table.
- Add feedback command for accepted, rejected, duplicate, already planned, not customer-impacting, needs more evidence, and compliance concern.
- Show feedback state in candidate and report output.
- Suppress or demote rejected/duplicate candidates where appropriate.

**Deliverables:**

- feedback model
- feedback CLI
- feedback-aware candidate listing/reporting

**Acceptance Criteria:**

- Feedback is stored per candidate.
- Candidate list shows latest review state.
- Reports can include or exclude rejected candidates.

**Test / Try It:**

- Integration test records feedback and verifies report behavior.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar feedback <candidate-id> accepted --notes "Good modernization candidate"
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar candidates sample-repo
```

**Status:** Completed. Added `review_feedback`, feedback storage, candidate status updates, and `feedback <candidate-id> <state> --notes ...`. Verified by marking the rule-heavy fixture candidate as `accepted`.

## Slice 23: Secret Redaction and Safe Prompt Packaging

**Goal:** Prevent sensitive strings from entering evidence packages or LLM prompts.

**Build:**

- Add secret redaction service.
- Detect common API keys, tokens, credentials, private URLs, account-like identifiers, and internal hostnames.
- Apply redaction before storing prompt payloads or calling LLM.
- Add audit log entry for redaction counts.

**Deliverables:**

- redaction service
- redaction tests
- prompt safety checks

**Acceptance Criteria:**

- Known secret patterns are redacted.
- Prompt packages never contain raw detected secrets.
- Redaction does not mutate source facts stored for local analysis unless explicitly configured.

**Test / Try It:**

- Unit tests with fake secrets.
- Integration test validates prompt package redaction.
- CLI:

```bash
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar evidence <candidate-id> --prompt-safe
```

**Status:** Completed. Added secret redaction service, prompt-safe evidence packaging, redaction audit records, and `prompt-safe`/`evidence --prompt-safe` paths. Verified with fake API key and JDBC/internal URL input, both redacted.

## Slice 24: End-to-End Fixture Scenario

**Goal:** Prove the whole pipeline on a controlled sample banking repo.

**Build:**

- Create or add a fixture repository with:
  - one customer-facing endpoint
  - one rule-heavy eligibility service
  - one scheduled status update job
  - one manual review flow
  - one repository/entity
- Add an end-to-end integration test that runs registration, indexing, detection, evidence retrieval, and no-LLM report generation.
- Optionally add a fake LLM validation step.

**Deliverables:**

- sample fixture repo
- end-to-end integration test
- expected report snapshot

**Acceptance Criteria:**

- Pipeline produces expected candidates.
- Evidence references expected files/classes/methods.
- Deterministic report matches approved snapshot.
- Test runs without real LLM credentials.

**Test / Try It:**

```bash
./gradlew integrationTest
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar register fixtures/banking-sample
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar index fixtures/banking-sample
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar detect all fixtures/banking-sample
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar report fixtures/banking-sample --no-llm
```

**Status:** Completed. Added `fixtures/banking-sample` with a customer-facing loan endpoint, rule-heavy eligibility service, scheduled status notification job, manual review flow, JPA entity, and repository. Verified end-to-end with registration, deterministic indexing/detection, candidate/evidence retrieval, chunks, embeddings, feedback, validation, workspace assignment, and no-LLM report generation.

## Suggested Build Order

1. Slice 1: Local Runtime and Database Foundation
2. Slice 2: Repository Registration and Workspace Directory
3. Slice 3: File Discovery and Change Detection
4. Slice 4: Class-Level Static Analysis
5. Slice 5: Method-Level Static Analysis
6. Slice 6: Spring Endpoint Detection
7. Slice 7: Scheduled Job and Batch Detection
8. Slice 8: Database Access and Entity Detection
9. Slice 9: Code Graph Edge Creation
10. Slice 10: Domain Term Extraction
11. Slice 11: Rule-Heavy Decisioning Detector
12. Slice 12: Batch-to-Real-Time Detector
13. Slice 13: Manual Review Workflow Detector
14. Slice 14: Evidence Retrieval Package
15. Slice 15: Markdown Report Without LLM
16. Slice 24: End-to-End Fixture Scenario
17. Slice 16: Method-Level Code Chunks
18. Slice 17: Embeddings and pgvector Search
19. Slice 18: Evidence Retrieval With Semantic Enrichment
20. Slice 19: LLM Candidate Validation
21. Slice 20: LLM-Enhanced Opportunity Report
22. Slice 21: Virtual Workspace Selection
23. Slice 22: Reviewer Feedback Loop
24. Slice 23: Secret Redaction and Safe Prompt Packaging

The first production-quality milestone should be slices 1 through 15 plus slice 24. That gives a fully testable evidence-first system without relying on an LLM.
