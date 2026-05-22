# Product Requirements Document

## Product Name: IdeaMiner

**Date:** May 2026

**Domain:** Banking / Financial Services

---

## 1. Executive Summary

IdeaMiner is an internal code intelligence product that mines Java/Spring Boot repositories for evidence-backed AI, ML, and workflow automation opportunities.

The product is based on a simple principle: source code is the most accurate record of how business processes actually work. IdeaMiner turns that code into structured evidence, detects opportunity signals, retrieves supporting implementation details, and uses an LLM to synthesize explainable recommendations.

The LLM is not the primary source of truth. It is an analyst and narrator over evidence produced by static analysis, graph relationships, search, and scoring.

## 2. Problem Statement

Large banking organizations have years of business logic spread across services, batch jobs, controllers, queues, rules, database integrations, and external system calls. Product managers, architects, and engineering leaders struggle to answer questions like:

- Which customer-facing workflows are slowed down by overnight batch processing?
- Where are critical decisions still implemented as brittle rule trees?
- Which manual review flows could be automated?
- Which processes would benefit from ML, anomaly detection, personalization, or better workflow orchestration?
- Which ideas are actually supported by code evidence instead of generic AI speculation?

Manual review does not scale across hundreds of repositories and millions of lines of code.

## 3. Product Goal

IdeaMiner should convert codebases into a business-process evidence graph and use that graph to produce ranked, explainable opportunity cards.

The product should help users identify:

- AI/ML opportunities
- workflow automation opportunities
- real-time conversion opportunities
- customer friction points
- operational risk and anomaly detection opportunities
- personalization and proactive notification opportunities

## 4. Guiding Principles

- **Evidence first:** Every recommendation must reference concrete code evidence.
- **LLM as analyst, not oracle:** LLMs should explain, synthesize, rank, and challenge candidates, not invent unsupported ideas.
- **Code plus context:** Static code facts, call relationships, annotations, database usage, endpoints, and domain terms matter more than raw text alone.
- **Customer impact:** Opportunities should be ranked by customer benefit, not just technical novelty.
- **AI is not always the answer:** Some opportunities should recommend workflow automation, real-time processing, better rules, or observability instead of ML.
- **Security aware:** Source code and sensitive metadata must be protected, redacted, and routed only through approved models.

## 5. Target Users

- Enterprise architects
- Product managers
- Engineering leads
- AI transformation teams
- Platform and modernization teams
- Banking operations leaders

## 6. Core User Journeys

### 6.1 Index Repositories

A user points IdeaMiner at one or more repositories. The system parses code, extracts structured facts, builds relationship edges, embeds searchable chunks, and stores indexed evidence with repository and commit metadata.

### 6.2 Create a Virtual Workspace

A user selects a group of indexed repositories, such as loan origination, notifications, and document processing. IdeaMiner analyzes that workspace as a connected business area.

### 6.3 Discover Opportunity Candidates

The system applies deterministic signal detectors to find candidate opportunities, such as rule-heavy decisioning, batch-driven customer updates, manual review queues, repeated validations, and customer-impacting retry/reconciliation flows.

### 6.4 Validate with Evidence

For each candidate, the system retrieves code, graph paths, endpoints, jobs, methods, database tables, and external dependencies that support or weaken the hypothesis.

### 6.5 Generate Opportunity Cards

The LLM produces structured recommendations with evidence references, impact scoring, feasibility notes, and implementation approach.

### 6.6 Review and Improve

Users can mark opportunities as accepted, rejected, duplicate, already planned, not customer-impacting, or needing more evidence. This feedback improves future ranking.

## 7. Functional Requirements

### 7.1 Repository Ingestion

The system must:

- ingest local filesystem repositories
- record repository name, local path, file path, and indexing timestamp
- record branch, remote URL, and commit SHA only when the local directory is Git-backed
- support repeated indexing without creating duplicate records
- detect changed files using commit or content hashes
- preserve traceability from every fact back to source code

### 7.2 Static Analysis

For Java/Spring Boot repositories, the system must extract:

- packages, classes, interfaces, enums, and records
- methods, parameters, return types, and thrown exceptions
- Spring controllers and request mappings
- services, repositories, entities, components, configurations
- scheduled jobs and batch jobs
- Kafka/listener/message consumers where applicable
- database table usage and repository methods
- external API clients and integrations
- annotations and constants
- method call relationships
- complexity metrics
- domain terms from names, annotations, constants, and comments

### 7.3 Code Intelligence Graph

The system must build a graph of code and business process relationships.

Required node types:

- Repository
- File
- Class
- Method
- Endpoint
- DatabaseTable
- ExternalSystem
- ScheduledJob
- MessageConsumer
- DomainConcept
- OpportunityCandidate

Required edge types:

- CONTAINS
- CALLS
- EXPOSES_ENDPOINT
- READS_TABLE
- WRITES_TABLE
- DEPENDS_ON
- SCHEDULED_BY
- CONSUMES_MESSAGE
- PRODUCES_MESSAGE
- USES_EXTERNAL_SYSTEM
- IMPLEMENTS_WORKFLOW
- SUPPORTS_EVIDENCE

### 7.4 Search and Retrieval

The system must support:

- keyword search over code and metadata
- structured filtering by repo, class type, endpoint, domain, and complexity
- vector search over method-level and logical-block-level chunks
- retrieval of evidence for each candidate opportunity
- workspace-scoped retrieval

### 7.5 Opportunity Signal Detection

The system must include deterministic detectors for:

- rule-heavy decisioning
- batch-to-real-time conversion
- manual review and case management workflows
- customer-facing latency or status delay
- repeated validation logic
- exception-heavy or retry-heavy flows
- reconciliation and operational anomaly patterns
- static offer/product/message selection
- document processing and verification workflows

Each detector must produce a candidate with evidence references and a preliminary score.

### 7.6 LLM Reasoning

The LLM layer must:

- analyze one candidate at a time
- receive structured evidence, not raw repository dumps
- validate whether an opportunity is plausible
- identify missing evidence
- reject unsupported candidates
- produce structured JSON before rendering Markdown
- cite code references for every recommendation

### 7.7 Scoring and Ranking

Each opportunity must be scored on:

- customer impact
- evidence strength
- technical feasibility
- AI/ML suitability
- automation suitability
- data availability
- compliance and operational risk
- estimated implementation complexity

### 7.8 Reporting

The system must generate persistent reports containing:

- executive summary
- ranked opportunity cards
- evidence references
- impacted repositories and workflows
- customer benefit
- proposed solution
- confidence score
- implementation complexity
- risks and assumptions
- recommended next step

### 7.9 Feedback Loop

The system should allow reviewers to classify generated opportunities as:

- accepted
- rejected
- duplicate
- already planned
- not customer-impacting
- needs more evidence
- compliance concern

Feedback should be stored and used to tune future ranking.

## 8. Non-Functional Requirements

### 8.1 Performance

- Ingestion should scale to large multi-repository workspaces.
- Re-analysis should avoid re-parsing unchanged source files.
- Reports should be generated in minutes for indexed repositories.

### 8.2 Security and Privacy

- Embeddings should be generated locally or through approved enterprise services.
- Source code sent to external LLMs must be configurable and auditable.
- Secret and sensitive token redaction must run before prompt construction.
- The system should support a metadata-only mode for stricter environments.

### 8.3 Explainability

- Every recommendation must include evidence.
- The system must distinguish strong evidence from weak inference.
- Unsupported ideas should be rejected rather than reported.

### 8.4 Extensibility

- Java/Spring Boot is the first supported ecosystem.
- The architecture should allow adding Kotlin, Python, JavaScript, and COBOL later.
- Signal detectors should be pluggable.
- LLM providers should be replaceable.

## 9. Out of Scope for Initial Version

- Automatic code modification
- Production deployment of AI models
- Full runtime tracing integration
- Multi-language support beyond Java/Spring Boot
- Autonomous creation of business cases without human review

## 10. Success Metrics

- Percentage of opportunities with concrete code evidence
- Reviewer acceptance rate
- Reduction in manual architecture review time
- Number of cross-repository workflows discovered
- Number of duplicate or unsupported ideas suppressed
- Time from indexed workspace to ranked report
