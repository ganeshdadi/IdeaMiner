# IdeaMiner Architecture Walkthrough

IdeaMiner should be understood as a code intelligence system for business opportunity discovery.

It is not primarily a chatbot over code. It first converts source repositories into structured evidence, then uses deterministic opportunity detectors and LLM reasoning to produce explainable recommendations.

## 1. Repository Ingestion

The ingestion layer reads one or more source repositories and records:

- repository name
- local path
- branch, remote URL, and commit SHA when the directory is Git-backed
- file path
- language
- content hash
- indexing timestamp

This identity layer matters because every opportunity must trace back to exact source evidence.

## 2. Static Analysis

The static analysis engine parses Java/Spring Boot code and extracts facts such as:

- classes and methods
- Spring annotations
- HTTP endpoints
- services and repositories
- entities and database access
- scheduled jobs and batch jobs
- message listeners
- external integrations
- method calls
- complexity and branching signals
- domain terms from names, comments, constants, and annotations

This produces structured facts that can be queried and scored. It is more reliable than asking an LLM to infer everything from raw source text.

## 3. Code Intelligence Graph

The graph connects facts into workflows.

Example:

```text
Customer endpoint
-> controller method
-> eligibility service
-> rule-heavy decision method
-> database table
-> notification service
```

This is where many real opportunities become visible. An AI or automation opportunity usually does not live in one class; it lives across a workflow.

## 4. Search and Vector Retrieval

IdeaMiner should use multiple retrieval styles:

- SQL for deterministic facts
- keyword search for names, constants, comments, and domain terms
- vector search for semantic similarity over method-level code chunks

Vector search is useful, but it should not be the whole product. It supports evidence retrieval after the system has found a plausible candidate.

## 5. Opportunity Signal Engine

The signal engine finds candidates before LLM reasoning.

Examples:

- A service with many branching rules around eligibility, fraud, pricing, or limits may indicate ML-assisted decisioning.
- A nightly job that updates customer-visible status may indicate a real-time automation opportunity.
- A flow that creates manual review cases may indicate document AI or workflow automation.
- A retry-heavy integration in payments or account servicing may indicate anomaly detection or resilience opportunities.
- Static offer or message selection may indicate personalization opportunities.

Each signal produces a candidate with evidence references and preliminary scores.

## 6. Evidence Retrieval

For each candidate, the system retrieves:

- direct code facts
- related graph neighbors
- source snippets or method summaries
- affected endpoints
- tables and external systems
- scheduled jobs or message flows

The output is a compact evidence package for one candidate.

## 7. LLM Reasoning

The LLM receives one candidate at a time.

Its job is to:

- validate whether the candidate is plausible
- reject weak or unsupported ideas
- explain the customer benefit
- identify missing evidence
- suggest whether AI, ML, workflow automation, or real-time processing is appropriate
- produce structured JSON and final Markdown

The LLM should not be asked to invent opportunities from a vague list of classes.

## 8. Opportunity Report

The final report contains ranked opportunity cards.

Each card should include:

- opportunity title
- customer benefit
- current state with code references
- proposed solution
- evidence strength
- confidence score
- AI suitability
- automation suitability
- data dependencies
- risks and compliance considerations
- recommended next step

## 9. Human Feedback

Reviewers should classify opportunities as:

- accepted
- rejected
- duplicate
- already planned
- not customer-impacting
- needs more evidence
- compliance concern

This feedback improves future detection and ranking.

## 10. How the Current Prototype Evolves

The current CLI prototype demonstrates the first pieces: local repository registration, PostgreSQL metadata storage, production Java file discovery, Java parsing, local embeddings, vector retrieval, and LLM report generation.

The next architecture step is to move from an LLM-first analysis flow to an evidence-first flow:

1. Add stable source-derived IDs.
2. Store repository and file metadata.
3. Index methods and logical chunks, not just classes.
4. Add graph edges for calls, endpoints, tables, jobs, and integrations.
5. Create deterministic opportunity signal detectors.
6. Retrieve evidence per candidate.
7. Ask the LLM to validate and explain each candidate.
8. Store reviewer feedback.
