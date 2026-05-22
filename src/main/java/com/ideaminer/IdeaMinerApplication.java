package com.ideaminer;

import com.ideaminer.service.ClassIndexService;
import com.ideaminer.service.FeaturePipelineService;
import com.ideaminer.service.HealthService;
import com.ideaminer.service.AnalysisService;
import com.ideaminer.service.IngestionService;
import com.ideaminer.service.MethodIndexService;
import com.ideaminer.service.OnboardingService;
import com.ideaminer.service.RepositoryRegistryService;
import com.ideaminer.service.SourceFileScanService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

@SpringBootApplication
public class IdeaMinerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdeaMinerApplication.class, args);
    }

    @Bean
    public CommandLineRunner run(HealthService healthService,
                                 RepositoryRegistryService repositoryRegistryService,
                                 SourceFileScanService sourceFileScanService,
                                 ClassIndexService classIndexService,
                                 MethodIndexService methodIndexService,
                                 OnboardingService onboardingService,
                                 FeaturePipelineService featurePipelineService,
                                 ObjectProvider<IngestionService> ingestionServiceProvider,
                                 ObjectProvider<AnalysisService> analysisServiceProvider) {
        return args -> {
            System.out.println("=========================================");
            System.out.println("   IdeaMiner: AI Opportunity Discovery   ");
            System.out.println("=========================================");

            if (args.length > 0) {
                String command = args[0];
                if ("health".equalsIgnoreCase(command)) {
                    healthService.printHealthReport();
                } else if ("register".equalsIgnoreCase(command)) {
                    if (args.length < 2) {
                        System.out.println("Missing repository path.");
                        printUsage();
                        return;
                    }
                    var registration = repositoryRegistryService.register(Path.of(args[1]));
                    System.out.println("[Repository] Registered: " + registration.name());
                    System.out.println("[Repository] ID: " + registration.id());
                    System.out.println("[Repository] Path: " + registration.localPath());
                    System.out.println("[Repository] Branch: " + registration.branch());
                    System.out.println("[Repository] Commit: " + registration.commitSha());
                } else if ("repos".equalsIgnoreCase(command)) {
                    var repositories = repositoryRegistryService.listRepositories();
                    if (repositories.isEmpty()) {
                        System.out.println("No repositories registered.");
                    } else {
                        repositories.forEach(repository -> System.out.println(
                                repository.id() + " | " +
                                        repository.name() + " | " +
                                        repository.branch() + " | " +
                                        repository.commitSha() + " | " +
                                        repository.localPath()
                        ));
                    }
                } else if ("scan-files".equalsIgnoreCase(command)) {
                    if (args.length < 2) {
                        System.out.println("Missing repository id, name, or local path.");
                        printUsage();
                        return;
                    }
                    var summary = sourceFileScanService.scan(args[1]);
                    System.out.println("[Scan] Repository: " + summary.repositoryName() + " (" + summary.repositoryId() + ")");
                    System.out.println("[Scan] Discovered: " + summary.discovered());
                    System.out.println("[Scan] Created: " + summary.created());
                    System.out.println("[Scan] Changed: " + summary.changed());
                    System.out.println("[Scan] Unchanged: " + summary.unchanged());
                    System.out.println("[Scan] Reactivated: " + summary.reactivated());
                    System.out.println("[Scan] Deleted: " + summary.deleted());
                } else if ("index-classes".equalsIgnoreCase(command)) {
                    if (args.length < 2) {
                        System.out.println("Missing repository id, name, or local path.");
                        printUsage();
                        return;
                    }
                    var summary = classIndexService.index(args[1]);
                    System.out.println("[Classes] Repository: " + summary.repositoryName() + " (" + summary.repositoryId() + ")");
                    System.out.println("[Classes] Files scanned: " + summary.filesScanned());
                    System.out.println("[Classes] Files parsed: " + summary.filesParsed());
                    System.out.println("[Classes] Files failed: " + summary.filesFailed());
                    System.out.println("[Classes] Classes indexed: " + summary.classesIndexed());
                } else if ("classes".equalsIgnoreCase(command)) {
                    if (args.length < 2) {
                        System.out.println("Missing repository id, name, or local path.");
                        printUsage();
                        return;
                    }
                    var classes = classIndexService.listClasses(args[1]);
                    if (classes.isEmpty()) {
                        System.out.println("No classes indexed for repository: " + args[1]);
                    } else {
                        classes.forEach(classFact -> System.out.println(
                                classFact.classType() + " | " +
                                        classFact.packageName() + "." +
                                        classFact.className() + " | complexity=" +
                                        classFact.cyclomaticComplexity() + " | " +
                                        classFact.filePath() + ":" +
                                        classFact.beginLine()
                        ));
                    }
                } else if ("index-methods".equalsIgnoreCase(command)) {
                    if (args.length < 2) {
                        System.out.println("Missing repository id, name, or local path.");
                        printUsage();
                        return;
                    }
                    var summary = methodIndexService.index(args[1]);
                    System.out.println("[Methods] Repository: " + summary.repositoryName() + " (" + summary.repositoryId() + ")");
                    System.out.println("[Methods] Files scanned: " + summary.filesScanned());
                    System.out.println("[Methods] Files parsed: " + summary.filesParsed());
                    System.out.println("[Methods] Files failed: " + summary.filesFailed());
                    System.out.println("[Methods] Methods indexed: " + summary.methodsIndexed());
                } else if ("methods".equalsIgnoreCase(command)) {
                    if (args.length < 2) {
                        System.out.println("Missing repository id, name, or local path.");
                        printUsage();
                        return;
                    }
                    int complexityMin = parseComplexityMin(args);
                    var methods = methodIndexService.listMethods(args[1], complexityMin);
                    if (methods.isEmpty()) {
                        System.out.println("No methods indexed for repository: " + args[1]);
                    } else {
                        methods.forEach(methodFact -> System.out.println(
                                methodFact.packageName() + "." +
                                        methodFact.className() + "#" +
                                        methodFact.signature() + " -> " +
                                        methodFact.returnType() + " | complexity=" +
                                        methodFact.cyclomaticComplexity() + " | " +
                                        methodFact.filePath() + ":" +
                                        methodFact.beginLine()
                        ));
                    }
                } else if ("endpoints".equalsIgnoreCase(command)) {
                    requireRepo(args);
                    featurePipelineService.indexEndpoints(args[1]).forEach(System.out::println);
                    featurePipelineService.listEndpoints(args[1]).forEach(System.out::println);
                } else if ("jobs".equalsIgnoreCase(command)) {
                    requireRepo(args);
                    featurePipelineService.indexJobs(args[1]).forEach(System.out::println);
                    featurePipelineService.listJobs(args[1]).forEach(System.out::println);
                } else if ("db-access".equalsIgnoreCase(command)) {
                    requireRepo(args);
                    featurePipelineService.indexDatabaseAccess(args[1]).forEach(System.out::println);
                    featurePipelineService.listDatabaseAccess(args[1]).forEach(System.out::println);
                } else if ("graph".equalsIgnoreCase(command)) {
                    requireRepo(args);
                    featurePipelineService.buildGraph(args[1]).forEach(System.out::println);
                    featurePipelineService.graph(args[1], optionValue(args, "--from")).forEach(System.out::println);
                } else if ("search-domain".equalsIgnoreCase(command)) {
                    if (args.length < 3) {
                        System.out.println("Missing repository and domain term.");
                        printUsage();
                        return;
                    }
                    featurePipelineService.extractDomainTerms(args[1]).forEach(System.out::println);
                    featurePipelineService.searchDomain(args[1], args[2]).forEach(System.out::println);
                } else if ("infer-roles".equalsIgnoreCase(command)) {
                    requireRepo(args);
                    featurePipelineService.inferRoles(args[1]).forEach(System.out::println);
                } else if ("roles".equalsIgnoreCase(command)) {
                    requireRepo(args);
                    featurePipelineService.listRoles(args[1]).forEach(System.out::println);
                } else if ("detect".equalsIgnoreCase(command)) {
                    if (args.length < 3) {
                        System.out.println("Usage: detect <rule-heavy|batch-realtime|manual-review|all> <repo>");
                        return;
                    }
                    String workspace = optionValue(args, "--workspace");
                    if (workspace != null) {
                        featurePipelineService.detectWorkspace(args[1], workspace).forEach(System.out::println);
                    } else {
                        featurePipelineService.detect(args[1], args[2]).forEach(System.out::println);
                    }
                } else if ("candidates".equalsIgnoreCase(command)) {
                    String workspace = optionValue(args, "--workspace");
                    if (workspace != null) {
                        featurePipelineService.workspaceCandidates(workspace).forEach(System.out::println);
                    } else {
                        requireRepo(args);
                        featurePipelineService.candidates(args[1]).forEach(System.out::println);
                    }
                } else if ("onboard".equalsIgnoreCase(command)) {
                    requireRepo(args);
                    onboardingService.onboard(args[1], optionValue(args, "--from"), hasOption(args, "--resume"))
                            .forEach(System.out::println);
                } else if ("status".equalsIgnoreCase(command)) {
                    requireRepo(args);
                    System.out.println(onboardingService.status(args[1]));
                } else if ("evidence".equalsIgnoreCase(command)) {
                    if (args.length < 2) {
                        System.out.println("Missing candidate id.");
                        printUsage();
                        return;
                    }
                    featurePipelineService.evidence(args[1], hasOption(args, "--semantic"), hasOption(args, "--prompt-safe"))
                            .forEach(System.out::println);
                } else if ("report".equalsIgnoreCase(command)) {
                    requireRepo(args);
                    var reportPath = featurePipelineService.report(args[1], hasOption(args, "--llm"));
                    System.out.println("Report written: " + reportPath);
                } else if ("chunks".equalsIgnoreCase(command)) {
                    requireRepo(args);
                    featurePipelineService.buildChunks(args[1]).forEach(System.out::println);
                } else if ("embed".equalsIgnoreCase(command)) {
                    requireRepo(args);
                    featurePipelineService.embed(args[1]).forEach(System.out::println);
                } else if ("semantic-search".equalsIgnoreCase(command)) {
                    if (args.length < 3) {
                        System.out.println("Missing repository and query.");
                        printUsage();
                        return;
                    }
                    featurePipelineService.embed(args[1]).forEach(System.out::println);
                    featurePipelineService.semanticSearch(args[1], args[2]).forEach(System.out::println);
                } else if ("validate".equalsIgnoreCase(command)) {
                    if (args.length < 2) {
                        System.out.println("Missing candidate id.");
                        printUsage();
                        return;
                    }
                    featurePipelineService.validate(args[1]).forEach(System.out::println);
                } else if ("workspace".equalsIgnoreCase(command)) {
                    if (args.length < 2) {
                        featurePipelineService.workspace("list", null, null).forEach(System.out::println);
                    } else if ("create".equalsIgnoreCase(args[1]) && args.length >= 3) {
                        featurePipelineService.workspace("create", args[2], null).forEach(System.out::println);
                    } else if ("add".equalsIgnoreCase(args[1]) && args.length >= 4) {
                        featurePipelineService.workspace("add", args[2], args[3]).forEach(System.out::println);
                    } else {
                        printUsage();
                    }
                } else if ("feedback".equalsIgnoreCase(command)) {
                    if (args.length < 3) {
                        System.out.println("Missing candidate id and feedback state.");
                        printUsage();
                        return;
                    }
                    featurePipelineService.feedback(args[1], args[2], optionValue(args, "--notes")).forEach(System.out::println);
                } else if ("prompt-safe".equalsIgnoreCase(command)) {
                    if (args.length < 2) {
                        System.out.println("Missing text to redact.");
                        return;
                    }
                    System.out.println(featurePipelineService.promptSafe(args[1]));
                } else if ("index".equalsIgnoreCase(command)) {
                    String path = args.length > 1 ? args[1] : ".";
                    System.out.println("Indexing repository at: " + path);
                    IngestionService ingestionService = ingestionServiceProvider.getObject();
                    ingestionService.ingestRepository(path);
                } else if ("analyze".equalsIgnoreCase(command)) {
                    System.out.println("Running AI Analysis...");
                    AnalysisService analysisService = analysisServiceProvider.getObject();
                    analysisService.runAnalysis();
                } else {
                    System.out.println("Unknown command: " + command);
                    printUsage();
                }
            } else {
                printUsage();
            }
        };
    }

    private void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar ideaminer.jar health                  # Verify database and pgvector setup");
        System.out.println("  java -jar ideaminer.jar register <path/to/repo> # Register a local repository directory");
        System.out.println("  java -jar ideaminer.jar repos                   # List registered repositories");
        System.out.println("  java -jar ideaminer.jar scan-files <repo>       # Discover production Java source files");
        System.out.println("  java -jar ideaminer.jar index-classes <repo>    # Parse source files and store class facts");
        System.out.println("  java -jar ideaminer.jar classes <repo>          # List indexed class facts");
        System.out.println("  java -jar ideaminer.jar index-methods <repo>    # Parse source files and store method facts");
        System.out.println("  java -jar ideaminer.jar methods <repo> [--complexity-min N]");
        System.out.println("  java -jar ideaminer.jar endpoints <repo>");
        System.out.println("  java -jar ideaminer.jar jobs <repo>");
        System.out.println("  java -jar ideaminer.jar db-access <repo>");
        System.out.println("  java -jar ideaminer.jar graph <repo> [--from endpoint:/path]");
        System.out.println("  java -jar ideaminer.jar search-domain <repo> <term>");
        System.out.println("  java -jar ideaminer.jar infer-roles <repo>");
        System.out.println("  java -jar ideaminer.jar roles <repo>");
        System.out.println("  java -jar ideaminer.jar detect <detector|all> <repo>");
        System.out.println("  java -jar ideaminer.jar detect <detector|all> <repo> --workspace <name>");
        System.out.println("  java -jar ideaminer.jar candidates <repo>");
        System.out.println("  java -jar ideaminer.jar candidates --workspace <name>");
        System.out.println("  java -jar ideaminer.jar evidence <candidate-id> [--semantic] [--prompt-safe]");
        System.out.println("  java -jar ideaminer.jar report <repo> [--no-llm|--llm]");
        System.out.println("  java -jar ideaminer.jar chunks <repo>");
        System.out.println("  java -jar ideaminer.jar embed <repo>");
        System.out.println("  java -jar ideaminer.jar semantic-search <repo> <query>");
        System.out.println("  java -jar ideaminer.jar validate <candidate-id>");
        System.out.println("  java -jar ideaminer.jar workspace create <name>");
        System.out.println("  java -jar ideaminer.jar workspace add <name> <repo>");
        System.out.println("  java -jar ideaminer.jar onboard <repo-path-or-id> [--from <stage>] [--resume]");
        System.out.println("  java -jar ideaminer.jar status <repo-path-or-id>");
        System.out.println("  java -jar ideaminer.jar feedback <candidate-id> <state> [--notes text]");
        System.out.println("  java -jar ideaminer.jar index <path/to/repo>   # Index a repository");
        System.out.println("  java -jar ideaminer.jar analyze                # Run analysis over indexed repos");
    }

    private int parseComplexityMin(String[] args) {
        for (int index = 2; index < args.length; index++) {
            if ("--complexity-min".equals(args[index]) && index + 1 < args.length) {
                return Integer.parseInt(args[index + 1]);
            }
            if (args[index].startsWith("--complexity-min=")) {
                return Integer.parseInt(args[index].substring("--complexity-min=".length()));
            }
        }
        return 0;
    }

    private void requireRepo(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Missing repository id, name, or local path.");
        }
    }

    private boolean hasOption(String[] args, String optionName) {
        for (String arg : args) {
            if (optionName.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private String optionValue(String[] args, String optionName) {
        for (int index = 0; index < args.length; index++) {
            if (optionName.equals(args[index]) && index + 1 < args.length) {
                return args[index + 1];
            }
            if (args[index].startsWith(optionName + "=")) {
                return args[index].substring((optionName + "=").length());
            }
        }
        return null;
    }
}
