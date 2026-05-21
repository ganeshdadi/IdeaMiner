# IdeaMiner: AI Opportunity Discovery Tool

I have successfully built the foundation for the `IdeaMiner` project! The architecture we designed is now fully implemented as a Java/Spring Boot CLI application. 

Here is a walkthrough of what was built and how the two main engines work together.

---

## 1. The Ingestion Engine (The `index` command)

The ingestion engine is responsible for parsing your code without relying on generic text search. It uses native Java tools to deeply understand the repositories.

*   **JavaParser AST Extraction**: When you run the `index` command, the `JavaParserUtil` walks through the AST (Abstract Syntax Tree) of every `.java` file. It extracts the class name, the package, its structural type (e.g., `@RestController`, `@Service`), and calculates a cyclomatic complexity score by counting branching logic (`if`, `switch`).
*   **Structured Storage**: This extracted metadata is saved into a local SQLite database (`ideaminer_metadata.db`). This allows us to quickly query for complex classes across millions of lines of code.
*   **Vector Embeddings**: Simultaneously, the local `all-MiniLM-L6-v2` model converts the class signatures and code into semantic vector embeddings and stores them in a local JSON-backed Vector Store (`ideaminer_vectors.json`). This all happens locally on your CPU, without sending any code to OpenAI.

## 2. The Analysis Engine (The `analyze` command)

The Analysis engine is where the magic happens. It uses the "Map-Reduce" pattern we discussed to discover unknown opportunities.

*   **Step 1: The Map Phase (SQL Discovery)**: The `AnalysisService` queries the SQLite database to find the top 50 most complex classes, or classes specifically tagged as `BatchJob` or `Service`. It sends these high-level summaries to GPT-4o and asks it to brainstorm 3-5 potential "AI/ML Opportunities".
*   **Step 2: The Deep Dive Phase (Vector Validation)**: Once GPT-4o has a few ideas, we take those ideas and embed them. We then query the local Vector Store to pull the actual source code related to those ideas. 
*   **Step 3: The Reduce Phase**: GPT-4o looks at the *actual* code to validate if its idea makes sense, and filters out the bad ones.

## 3. The Output

Finally, the tool generates a markdown report (`AI_Opportunity_Report.md`) in your working directory. It produces "Opportunity Cards" formatted like this:

> [!TIP]
> **Example Opportunity Card**
> *   **Opportunity Title**: Real-time Fraud Detection
> *   **Customer Benefit**: Prevents unauthorized transactions instantly rather than catching them in an overnight batch.
> *   **Current State**: `com.bank.batch.NightlyReconciliationJob`
> *   **Proposed AI Solution**: Replace the nightly batch rule engine with an inline ML scoring model.

## Next Steps for You

1.  **Import to IDE**: You can now open `/Users/ganeshbabudadi/projects/IdeaMiner` in IntelliJ IDEA or Eclipse.
2.  **API Key**: Export your OpenAI API key in your terminal before running it: `export OPENAI_API_KEY="your-key-here"`.
3.  **Run it**: You can compile and run it via Maven or your IDE to test it on one of your organization's repositories!
