# AI Opportunity Discovery Tool

This tool analyzes multiple Java/Spring Boot codebases to identify AI/ML and automation opportunities that can improve the customer banking experience. It builds a persistent hybrid index of your code, allowing you to run multiple analyses on combinations of repositories without having to re-parse the source code.

## The "How do we traverse it?" Question (The Core Strategy)

We use a **Hybrid Index (SQL + Vector)** and a **Bottom-Up Synthesis** approach to solve the discovery problem:

1. **SQL is for Traversal & Discovery:** During ingestion, we use a native Java AST parser (`JavaParser`) to deeply understand the code and extract structural metadata, saving it into a standard SQLite database. We extract things like:
   - What is the class name and its purpose?
   - Is it a REST Controller? (Customer-facing API)
   - Is it a Batch Job? (Potential for real-time AI)
   - Does it have high cyclomatic complexity (lots of if/else rules)?
2. **The "Discovery" Workflow:**
   - **Step 1:** The tool queries the SQLite DB to find "clusters" of interesting business logic. For example: `SELECT class_name, summary FROM classes WHERE type = 'Service' AND name LIKE '%Loan%'`.
   - **Step 2:** We feed these high-level summaries into the LLM (GPT-4) and ask it: *"Look at these 50 services related to Loans. What manual processes or rule-based logic here could be replaced by ML to help the customer?"*
   - **Step 3:** If the LLM spots an opportunity but needs more details, *then* it uses the Vector Store to retrieve the actual code implementation for that specific class to verify its hypothesis.

---

## Finalized Implementation Plan

### 1. Technology Stack
*   **Language:** Java (Spring Boot Console Application)
*   **Parsing:** JavaParser (for native AST extraction)
*   **AI Orchestration:** LangChain4j
*   **LLM Provider (Reasoning):** OpenAI (GPT-4o) via API key
*   **Embedding Model:** `all-MiniLM-L6-v2` (Local, in-process via ONNX)
*   **Metadata DB:** SQLite (JDBC)
*   **Vector DB:** ChromaDB or in-memory LangChain4j store (backed by disk)

### 2. Ingestion Phase (Run Once per Repo)
*   **Target:** GitHub Repositories.
*   **Granularity:** Class-level indexing.
*   **Action:** 
    *   Point the tool at a local directory containing the cloned repo.
    *   Use **`JavaParser`** to natively parse all `.java` files, resolving Spring annotations (`@RestController`, `@Entity`) and method signatures.
    *   Generate a brief summary of the class using a quick LLM call (or heuristics).
    *   **Embeddings:** Generate vector embeddings for the class code using the local `all-MiniLM-L6-v2` model.
    *   **Storage:** 
        *   Save metadata into a local **SQLite Database**.
        *   Store the vector embeddings into the vector store.

### 3. Composition Phase (Run per Analysis)
*   **Action:** You specify which repositories (e.g., `repo-loans` and `repo-notifications`) you want to analyze together.
*   The tool simply attaches to the pre-built SQLite and Vector DBs for those specific repos, creating a "Virtual Workspace."

### 4. Analysis Phase (The AI Brain)
*   **Action:** We run an orchestrated LLM pipeline using OpenAI GPT-4o.
*   **The "Map-Reduce" Strategy:**
    1.  **Map:** Query the SQLite database to group classes by business domain. Feed these summaries to the LLM to brainstorm initial ideas.
    2.  **Deep Dive:** The LLM selects the top 5 most promising ideas. It queries the Vector DB to pull the actual source code for the classes involved to validate if the opportunity is real.
    3.  **Reduce:** The LLM refines the ideas, focusing strictly on **Customer-Centric Opportunities**.

### 5. Reporting Phase
*   **Action:** The tool generates a structured Markdown report containing a ranked list of "Opportunity Cards".
