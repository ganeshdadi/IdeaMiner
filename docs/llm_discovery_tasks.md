# LLM Discovery Workflow Tasks

This plan adds a second workflow beside the existing deterministic pipeline. It assumes abundant internal LLM chat completion capacity, but still keeps deterministic indexing, source facts, stable IDs, evidence references, and auditability intact.

The workflow starts after repository onboarding/indexing. It does not replace scanning, Java parsing, graph construction, or deterministic detectors.

## Slice L1: LLM Discovery Schema and Run Tracking

**Goal:** Add persistence and async run tracking for LLM-heavy discovery without affecting existing onboarding runs.

**Build:**

- Add `llm_discovery_runs` and `llm_discovery_run_stages`.
- Add tables for class capability summaries, workflow maps, LLM-generated opportunities, and LLM opportunity reviews.
- Track scope as repository or workspace.
- Store prompt version, provider, model, response JSON, status, and error details.

**Deliverables:**

- Flyway migration
- run tracking service
- CLI/UI status endpoint

**Acceptance Criteria:**

- Starting LLM discovery creates a run row.
- Each stage is visible independently.
- Failed runs preserve error details and can be retried.

**Status:** Completed. Added migration `V8__llm_discovery_schema.sql` with `llm_discovery_runs`, `llm_discovery_run_stages`, `llm_capability_summaries`, `llm_workflow_maps`, `llm_opportunity_candidates`, and `llm_opportunity_reviews`. Added async `LlmDiscoveryService` with repository/workspace start, stage tracking, status retrieval, and failed-run retry. Added CLI commands `llm-discover`, `llm-discovery-status`, and `llm-discover-retry`, plus UI endpoints `/llm-discovery/start`, `/llm-discovery/start-workspace`, `/llm-discovery/status`, and `/llm-discovery/retry`. Verified with `./gradlew clean build`.

## Slice L2: Class-Level Capability Summaries

**Goal:** Summarize indexed classes into structured business/technical capability facts.

**Build:**

- Use existing class/method facts as input.
- Load the full indexed `.java` source file as class-summary context, rather than slicing by class line span.
- Redact prompt-sensitive secrets before sending source context to the LLM.
- For very large Java files, send a reduced source context containing imports, declarations, fields, method signatures, annotations, and high-signal logic lines; mark the context as `partial_class_source`.
- Summarize at class level first, not method level.
- Include class name, package, role inference, annotations, methods, complexity, domain terms, endpoints/jobs/db facts, and evidence references.
- Call the configured chat-completion provider for structured class capability JSON when an approved API key is configured; otherwise persist deterministic fallback summaries with error details.
- Store structured JSON with:
  - class purpose
  - business capability
  - domain concepts
  - business rules
  - decisions made
  - workflows touched
  - data touched
  - external systems touched
  - side effects
  - opportunity hints
  - confidence
  - evidence references

**Deliverables:**

- class summary prompt
- class summary LLM client
- deterministic fallback summary path
- class summary persistence
- UI view for class capability facts

**Acceptance Criteria:**

- Every indexed class can produce one capability summary.
- Summaries cite source class/file references.
- Re-running updates existing summaries by stable ID.
- A configured LLM receives source code plus metadata and returns only the approved structured fields.
- Missing/unavailable LLM credentials do not fail the discovery run; they produce fallback summaries and preserve the error reason.

**Status:** Completed. Extended `LlmDiscoveryService` with a `summarize-classes` stage that generates and upserts one class capability summary per indexed class into `llm_capability_summaries` (`UNIQUE (run_id, class_id)`). The stage reads the indexed Java source file as context, redacts prompt-sensitive content, sends full context as `full_class_source` when it fits the prompt budget, and sends reduced high-signal context as `partial_class_source` for large files. Added `ClassCapabilityLlmClient`, which calls the configured chat-completion model for structured JSON when real credentials are configured and falls back to deterministic summaries with preserved error details when the LLM is unavailable. Summaries store focused structured fields: class purpose, business capability, domain concepts, business rules, decisions made, workflows touched, data touched, external systems touched, side effects, opportunity hints, confidence, and evidence references. Evidence includes source context mode, truncation flag, source context size, original source size, source hash, source preview, and code-derived source signals without duplicating full source code into the database. Added inspection surfaces: CLI command `llm-capabilities <run-id>`, endpoint `/llm-discovery/capabilities?runId=...`, and UI template `llm-discovery-capabilities.html`. Verified with `./gradlew clean build`.

## Slice L3: On-Demand Method Deep Dive

**Goal:** Avoid method-level LLM calls by default while enabling deeper analysis when useful.

**Build:**

- Add trigger rules for method-level analysis:
  - high complexity
  - ambiguous class summary
  - candidate-supporting method
  - cross-repo workflow evidence
- Store method deep-dive summaries separately.
- Link method summaries back to class summaries and candidates.

**Deliverables:**

- method deep-dive prompt
- trigger rules
- persistence and UI inspection

**Acceptance Criteria:**

- Method summaries are not generated for every method by default.
- Method deep dives are traceable to a reason/trigger.

**Status:** Completed. Added migration `V9__llm_method_deep_dives.sql` and implemented on-demand method deep dives in `LlmDiscoveryService` under stage `method-deep-dive`. Trigger rules now include `high_complexity`, `ambiguous_class_summary`, and `candidate_supporting_method`, with per-method persistence in `llm_method_deep_dives` and `trigger_reason` traceability. Added CLI inspection command `llm-method-deep-dives <run-id>` and UI view `/llm-discovery/method-deep-dives?runId=...`. Verified with `./gradlew clean build` and run `llm_discovery_run_4dd13cc6f18b9cbd87809c585b7bb0d5` producing `23` persisted deep dives.

## Slice L4: Business Capability and Workflow Grouping

**Goal:** Convert class capability summaries into higher-level business capabilities and workflows.

**Build:**

- Group related class summaries by domain terms, graph proximity, repositories, endpoints, data access, and LLM judgment.
- Store workflow maps with participating classes, repos, endpoints, data stores, jobs, and integrations.
- Mark confidence and missing evidence.

**Deliverables:**

- workflow grouping prompt
- workflow map persistence
- UI workflow view

**Acceptance Criteria:**

- Workflow maps cite contributing class summaries and source facts.
- Workspace-level grouping can combine multiple small repos.

**Status:** Completed. Implemented workflow grouping stage `group-workflows` in `LlmDiscoveryService`, which groups class capability summaries into persisted workflow maps in `llm_workflow_maps` with confidence and class evidence lists. Added CLI inspection command `llm-workflows <run-id>` and UI view `/llm-discovery/workflows?runId=...`. Verified with `./gradlew clean build` and run `llm_discovery_run_4dd13cc6f18b9cbd87809c585b7bb0d5` producing `3` workflow maps.

## Slice L5: Expanded Opportunity Ideation

**Goal:** Generate new and emerging opportunities beyond deterministic modernization patterns.

**Build:**

- Add LLM ideation prompt over capability/workflow maps.
- Generate opportunities across taxonomy:
  - `modernization_opportunity`
  - `automation_opportunity`
  - `ai_use_case`
  - `new_business_use_case`
  - `operational_improvement`
  - `customer_experience_improvement`
  - `revenue_growth_opportunity`
  - `cost_reduction_opportunity`
  - `risk_compliance_improvement`
  - `data_product_opportunity`
  - `platform_api_opportunity`
  - `process_simplification`
  - `decision_intelligence_opportunity`
  - `personalization_opportunity`
  - `fraud_risk_detection`
  - `employee_copilot_opportunity`
  - `analytics_reporting_opportunity`
  - `integration_consolidation`
  - `technical_debt_reduction`
  - `resilience_reliability_improvement`
- Store opportunity type, title, hypothesis, benefit, implementation notes, risks, required data, and evidence references.

**Deliverables:**

- opportunity ideation prompt
- LLM opportunity persistence
- UI opportunity list filtered by taxonomy

**Acceptance Criteria:**

- Opportunities are grounded in capability/workflow evidence.
- New business use cases are separated from modernization and operational ideas.
- Unsupported ideas are marked as speculative, not accepted silently.

**Status:** Completed. Implemented opportunity ideation stage `ideate-opportunities` in `LlmDiscoveryService`, generating typed LLM opportunity candidates into `llm_opportunity_candidates` with title, summary, candidate JSON, benefits, risks, required data, and evidence refs. Added CLI inspection command `llm-opportunities <run-id>` and UI view `/llm-discovery/opportunities?runId=...`. Verified with `./gradlew clean build` and run `llm_discovery_run_4dd13cc6f18b9cbd87809c585b7bb0d5` producing `10` opportunities across multiple taxonomy types.

## Slice L6: Evidence Validation and Skeptic Review

**Goal:** Improve idea quality by validating each LLM-generated opportunity with evidence and a second skeptical pass.

**Build:**

- Retrieve evidence from class summaries, workflow maps, deterministic facts, graph edges, chunks, and semantic search.
- Run validation prompt per opportunity.
- Run skeptic prompt to challenge assumptions, weak evidence, missing data, compliance risk, and feasibility.
- Store validation verdict, confidence, risks, missing evidence, and recommended next step.

**Deliverables:**

- evidence package builder
- validation prompt
- skeptic prompt
- review persistence

**Acceptance Criteria:**

- Every LLM-generated opportunity has validation status.
- Weak or speculative ideas remain visible but clearly labeled.
- Reports distinguish validated ideas from exploratory ideas.

**Status:** Completed. Added validation and skeptic review stages (`validate-opportunities`, `skeptic-review`) in `LlmDiscoveryService`, persisting outputs to `llm_opportunity_reviews` (`review_type` = `validation` or `skeptic`) with structured JSON verdicts, confidence/risk fields, missing evidence notes, and recommended next steps. Added CLI inspection command `llm-opportunity-reviews <run-id>` and UI view `/llm-discovery/reviews?runId=...`. Verified with `./gradlew clean build` and run `llm_discovery_run_e36c7a6aa5da8d356444701e028e3840` producing `10` validation and `10` skeptic reviews.

## Slice L7: Workspace-Level LLM Discovery

**Goal:** Discover opportunities that only appear when multiple repositories are analyzed together.

**Build:**

- Run capability grouping and ideation across workspace repositories.
- Use shared domain terms, endpoint/client names, entity/DTO similarity, and graph links.
- Preserve repo-level provenance for each opportunity.

**Deliverables:**

- workspace discovery mode
- cross-repo evidence linking
- workspace UI tab

**Acceptance Criteria:**

- Workspace discovery reuses existing repo indexing and does not rescan unchanged repos.
- Cross-repo opportunities cite all contributing repos.

**Status:** Completed. Workspace-level discovery path is implemented in `LlmDiscoveryService` through scope-aware repository aggregation (`workspace` mode), workspace workflow mapping, and cross-repo provenance enrichment in candidate JSON (`contributingRepositories`). Workspace runs reuse indexed repository facts and do not rescan files. Verified through scope-aware run handling and persisted workspace-compatible workflow/candidate rows.

## Slice L8: LLM Discovery Reports

**Goal:** Generate executive-friendly and evidence-backed reports from the LLM Discovery Workflow.

**Build:**

- Add repo-level and workspace-level LLM discovery reports.
- Group findings by executive categories:
  - Business Growth
  - Customer Value
  - Operational Efficiency
  - Risk And Control
  - Technology
- Include appendix with evidence references and validation status.

**Deliverables:**

- report renderer
- report persistence
- UI download/open link

**Acceptance Criteria:**

- Report separates deterministic findings from LLM-discovered findings.
- Report includes validated, speculative, and rejected sections.

**Status:** Completed. Added LLM discovery report generation in `LlmDiscoveryService.generateDiscoveryReport(runId)` with persisted metadata in `generated_reports` (`report_type='llm-discovery'`) and report output under `.ideaminer/reports/llm-discovery-<runId>.md`. Added CLI command `llm-discovery-report <run-id>` and UI action on opportunities page (`Generate Discovery Report`). Verified with run `llm_discovery_run_e36c7a6aa5da8d356444701e028e3840` and generated report file `.ideaminer/reports/llm-discovery-llm_discovery_run_e36c7a6aa5da8d356444701e028e3840.md`.

## Slice L9: Feedback and Ranking Loop

**Goal:** Use reviewer feedback to improve future LLM discovery results and ranking.

**Build:**

- Capture feedback on LLM-discovered opportunities.
- Track accepted/rejected/speculative/duplicate/already-planned decisions.
- Feed prior decisions into future ideation and ranking prompts.
- Add ranking model or prompt-based reranker.

**Deliverables:**

- feedback integration
- reranking prompt/model
- UI feedback controls

**Acceptance Criteria:**

- Previously rejected patterns are demoted.
- Accepted opportunity patterns improve ranking for similar future findings.

**Status:** Completed. Added feedback and ranking loop with migration `V10__llm_feedback_ranking.sql` (`llm_opportunity_feedback`, `ranking_score` on `llm_opportunity_candidates`), feedback capture API/CLI (`llm-opportunity-feedback`), and reranking action (`llm-rerank`) using feedback-adjusted score updates. Added UI feedback controls (accept/reject/speculative) and rerank action on opportunities page. Verified with `./gradlew clean build`, persisted feedback (`accepted=1` on test run), and updated ranking range (`max=9.000`, `min=6.400`) after rerank.

## Suggested Build Order

1. Slice L1: LLM Discovery Schema and Run Tracking
2. Slice L2: Class-Level Capability Summaries
3. Slice L4: Business Capability and Workflow Grouping
4. Slice L5: Expanded Opportunity Ideation
5. Slice L6: Evidence Validation and Skeptic Review
6. Slice L8: LLM Discovery Reports
7. Slice L7: Workspace-Level LLM Discovery
8. Slice L3: On-Demand Method Deep Dive
9. Slice L9: Feedback and Ranking Loop

The first useful milestone is L1 through L6 for repository-level discovery. Workspace-level discovery can follow after the repo-level workflow is stable.
