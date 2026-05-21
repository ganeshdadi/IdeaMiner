# Product Requirements Document (PRD)
## Product Name: IdeaMiner
**Date**: May 2026
**Domain**: Banking / Financial Services

---

## 1. Executive Summary
**IdeaMiner** is an internal AI Opportunity Discovery tool designed to analyze source code across multiple organization repositories. Because "code is the real source of truth," the tool aims to read existing codebases and automatically identify high-value business opportunities where Artificial Intelligence, Machine Learning, or modern workflow automation can be applied to improve the banking experience for customers.

## 2. Problem Statement
The organization possesses massive amounts of business logic locked inside multiple Java repositories. Product managers and architects struggle to identify systemic inefficiencies, manual rule-based processes, or workflow gaps across the entire architecture. Manually auditing millions of lines of code to find AI opportunities is impossible.

## 3. Goals & Objectives
*   **Discover AI Opportunities**: Automatically identify code patterns (e.g., heavy rule-based decisioning, nightly batch reconciliations, manual workflows) that can be upgraded using AI/ML.
*   **Customer-Centric Focus**: Prioritize opportunities that directly benefit the customer (e.g., faster approvals, real-time alerts, proactive error prevention, personalized banking).
*   **Cross-Repository Analysis**: Allow users to run analysis over *combinations* of different repositories to find cross-cutting concerns and holistic opportunities.
*   **Efficient Re-analysis**: Prevent the need to repeatedly traverse and parse raw source code for every new analysis run.

## 4. Key Requirements

### 4.1. Ingestion & Indexing
*   **Code as Truth**: The tool must parse actual source code (specifically Java/Spring Boot) as the primary input.
*   **Class-Level Granularity**: The ingestion engine must index code at the class level, extracting structural metadata (e.g., Controllers, Services, Batch Jobs, Cyclomatic Complexity).
*   **One-Time Traversal**: The tool must parse a repository exactly once and store the extracted knowledge in a persistent, queryable state.
*   *(Out of Scope for v1: Incremental updates/syncing on new git commits).*

### 4.2. Persistent Storage (The Hybrid Index)
*   The system must utilize a **Structured Database** to store metadata (class names, types, complexity) for fast, deterministic filtering.
*   The system must utilize a **Vector Database** to store semantic embeddings of the code to enable AI-driven deep dives and pattern matching.

### 4.3. Discovery & Analysis Workflow
*   **Virtual Workspaces**: Users must be able to select specific subsets of previously indexed repositories and combine them into a single analysis run.
*   **LLM Integration**: The tool must leverage a powerful Large Language Model (e.g., GPT-4o) to reason over the extracted metadata and discover patterns.
*   **Vector Validation**: Before proposing an idea, the tool must semantically search the vector store to validate the hypothesis against actual code implementations to ensure accuracy and prevent hallucinations.

### 4.4. Output & Reporting
*   **Structured Reports**: The tool must generate a persistent, structured document (e.g., Markdown or PDF) per analysis run.
*   **Opportunity Cards**: Discovered ideas must be presented as "Opportunity Cards" containing:
    *   Opportunity Title
    *   Direct Customer Benefit
    *   Current State (with references to actual classes/repos)
    *   Proposed AI/ML Solution

## 5. Non-Functional Requirements
*   **Performance**: Querying and generating a report from pre-indexed repositories should take minutes, not hours.
*   **Privacy & Security**: Code embeddings must be generated locally or via approved secure enterprise APIs to prevent sensitive IP leakage.
*   **Usability**: The tool must be easily invocable via a Command Line Interface (CLI).
