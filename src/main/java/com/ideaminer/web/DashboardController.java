package com.ideaminer.web;

import com.ideaminer.service.FeaturePipelineService;
import com.ideaminer.service.LlmEnrichmentService;
import com.ideaminer.service.OnboardingService;
import com.ideaminer.service.LlmDiscoveryService;
import com.ideaminer.service.RepositoryRegistryService;
import com.ideaminer.service.RepositoryCleanupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class DashboardController {
    private final RepositoryRegistryService repositoryRegistryService;
    private final OnboardingService onboardingService;
    private final LlmEnrichmentService llmEnrichmentService;
    private final LlmDiscoveryService llmDiscoveryService;
    private final FeaturePipelineService featurePipelineService;
    private final RepositoryCleanupService repositoryCleanupService;
    private final LocalFolderPickerService localFolderPickerService;

    public DashboardController(RepositoryRegistryService repositoryRegistryService,
                               OnboardingService onboardingService,
                               LlmEnrichmentService llmEnrichmentService,
                               LlmDiscoveryService llmDiscoveryService,
                               FeaturePipelineService featurePipelineService,
                               RepositoryCleanupService repositoryCleanupService,
                               LocalFolderPickerService localFolderPickerService) {
        this.repositoryRegistryService = repositoryRegistryService;
        this.onboardingService = onboardingService;
        this.llmEnrichmentService = llmEnrichmentService;
        this.llmDiscoveryService = llmDiscoveryService;
        this.featurePipelineService = featurePipelineService;
        this.repositoryCleanupService = repositoryCleanupService;
        this.localFolderPickerService = localFolderPickerService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("repos", repositoryRegistryService.listRepositories());
        model.addAttribute("workspaces", featurePipelineService.workspaceSummaries());
        model.addAttribute("onboardingService", onboardingService);
        return "dashboard";
    }

    @PostMapping("/onboard")
    @ResponseBody
    public Map<String, String> onboard(@RequestParam("repoPath") String repoPath) {
        String runId = onboardingService.startOnboarding(repoPath, null, false);
        return Map.of("runId", runId, "repo", repoPath);
    }

    @PostMapping("/browse-repo")
    @ResponseBody
    public Map<String, String> browseRepo() {
        return localFolderPickerService.chooseFolder();
    }

    @GetMapping("/repo")
    public String repo(@RequestParam("repo") String repo, Model model) {
        model.addAttribute("repo", repo);
        model.addAttribute("status", onboardingService.status(repo));
        model.addAttribute("candidates", featurePipelineService.candidates(repo));
        return "repo";
    }

    @GetMapping("/workspace")
    public String workspace(@RequestParam(value = "name", required = false) String name, Model model) {
        if (name == null || name.isBlank()) {
            model.addAttribute("workspaces", featurePipelineService.workspaceSummaries());
            model.addAttribute("repos", repositoryRegistryService.listRepositories());
            return "workspace-list";
        }
        model.addAttribute("name", name);
        model.addAttribute("candidates", featurePipelineService.workspaceCandidates(name));
        model.addAttribute("workspaces", featurePipelineService.workspaceSummaries());
        model.addAttribute("workspaceRepos", featurePipelineService.workspaceRepositories(name));
        model.addAttribute("repos", repositoryRegistryService.listRepositories());
        return "workspace";
    }

    @PostMapping("/workspace/create")
    public String createWorkspace(@RequestParam("name") String name) {
        featurePipelineService.workspace("create", name, null);
        return "redirect:/workspace?name=" + name;
    }

    @PostMapping("/workspace/add")
    public String addWorkspaceRepo(@RequestParam("name") String name, @RequestParam("repo") String repo) {
        featurePipelineService.workspace("add", name, repo);
        return "redirect:/workspace?name=" + name;
    }

    @PostMapping("/workspace/remove")
    public String removeWorkspaceRepo(@RequestParam("name") String name, @RequestParam("repo") String repo) {
        featurePipelineService.removeRepositoryFromWorkspace(name, repo);
        return "redirect:/workspace?name=" + name;
    }

    @PostMapping("/workspace/delete")
    public String deleteWorkspace(@RequestParam("name") String name, @RequestParam("confirm") String confirm) {
        if (!name.equals(confirm)) {
            throw new IllegalArgumentException("Confirmation does not match workspace name.");
        }
        featurePipelineService.deleteWorkspace(name);
        return "redirect:/workspace";
    }

    @GetMapping("/onboard/status")
    @ResponseBody
    public Map<String, Object> onboardStatus(@RequestParam("runId") String runId) {
        return onboardingService.runStatus(runId);
    }

    @PostMapping("/llm/validate")
    @ResponseBody
    public Map<String, String> validateCandidates(@RequestParam("repo") String repo) {
        String runId = llmEnrichmentService.startValidateCandidates(repo);
        return Map.of("runId", runId, "repo", repo, "job", "validate-candidates");
    }

    @PostMapping("/llm/report")
    @ResponseBody
    public Map<String, String> llmReport(@RequestParam("repo") String repo) {
        String runId = llmEnrichmentService.startLlmReport(repo);
        return Map.of("runId", runId, "repo", repo, "job", "llm-report");
    }

    @GetMapping("/llm/status")
    @ResponseBody
    public Map<String, Object> llmStatus(@RequestParam("runId") String runId) {
        return llmEnrichmentService.runStatus(runId);
    }

    @PostMapping("/llm-discovery/start")
    @ResponseBody
    public Map<String, String> startLlmDiscovery(@RequestParam("repo") String repo) {
        String runId = llmDiscoveryService.startRepositoryRun(repo, "v1", "internal", "gpt-4o");
        return Map.of("runId", runId, "scope", "repository", "repo", repo);
    }

    @PostMapping("/llm-discovery/start-workspace")
    @ResponseBody
    public Map<String, String> startLlmDiscoveryWorkspace(@RequestParam("workspace") String workspace) {
        String runId = llmDiscoveryService.startWorkspaceRun(workspace, "v1", "internal", "gpt-4o");
        return Map.of("runId", runId, "scope", "workspace", "workspace", workspace);
    }

    @GetMapping("/llm-discovery/status")
    @ResponseBody
    public Map<String, Object> llmDiscoveryStatus(@RequestParam("runId") String runId) {
        return llmDiscoveryService.status(runId);
    }

    @GetMapping("/llm-discovery/capabilities")
    public String llmDiscoveryCapabilities(@RequestParam("runId") String runId, Model model) {
        model.addAttribute("runId", runId);
        model.addAttribute("runStatus", llmDiscoveryService.status(runId));
        model.addAttribute("capabilities", llmDiscoveryService.capabilitySummaries(runId));
        return "llm-discovery-capabilities";
    }

    @GetMapping("/llm-discovery/method-deep-dives")
    public String llmDiscoveryMethodDeepDives(@RequestParam("runId") String runId, Model model) {
        model.addAttribute("runId", runId);
        model.addAttribute("runStatus", llmDiscoveryService.status(runId));
        model.addAttribute("deepDives", llmDiscoveryService.methodDeepDives(runId));
        return "llm-discovery-method-deep-dives";
    }

    @GetMapping("/llm-discovery/workflows")
    public String llmDiscoveryWorkflows(@RequestParam("runId") String runId, Model model) {
        model.addAttribute("runId", runId);
        model.addAttribute("runStatus", llmDiscoveryService.status(runId));
        model.addAttribute("workflows", llmDiscoveryService.workflowMaps(runId));
        return "llm-discovery-workflows";
    }

    @GetMapping("/llm-discovery/opportunities")
    public String llmDiscoveryOpportunities(@RequestParam("runId") String runId, Model model) {
        model.addAttribute("runId", runId);
        model.addAttribute("runStatus", llmDiscoveryService.status(runId));
        model.addAttribute("opportunities", llmDiscoveryService.opportunityCandidates(runId));
        return "llm-discovery-opportunities";
    }

    @GetMapping("/llm-discovery/reviews")
    public String llmDiscoveryReviews(@RequestParam("runId") String runId, Model model) {
        model.addAttribute("runId", runId);
        model.addAttribute("runStatus", llmDiscoveryService.status(runId));
        model.addAttribute("reviews", llmDiscoveryService.opportunityReviews(runId));
        return "llm-discovery-reviews";
    }

    @PostMapping("/llm-discovery/report")
    @ResponseBody
    public Map<String, String> llmDiscoveryReport(@RequestParam("runId") String runId) {
        return Map.of("reportPath", llmDiscoveryService.generateDiscoveryReport(runId).toString());
    }

    @PostMapping("/llm-discovery/feedback")
    @ResponseBody
    public Map<String, String> llmDiscoveryFeedback(@RequestParam("candidateId") String candidateId,
                                                    @RequestParam("decision") String decision,
                                                    @RequestParam(value = "notes", required = false) String notes) {
        String feedbackId = llmDiscoveryService.recordOpportunityFeedback(candidateId, decision, notes);
        return Map.of("feedbackId", feedbackId);
    }

    @PostMapping("/llm-discovery/rerank")
    @ResponseBody
    public Map<String, String> llmDiscoveryRerank(@RequestParam("runId") String runId) {
        int updated = llmDiscoveryService.rerank(runId);
        return Map.of("updated", String.valueOf(updated));
    }

    @PostMapping("/llm-discovery/retry")
    @ResponseBody
    public Map<String, String> llmDiscoveryRetry(@RequestParam("runId") String runId) {
        String newRunId = llmDiscoveryService.retryFailedRun(runId);
        return Map.of("runId", newRunId);
    }

    @PostMapping("/repo/cleanup")
    @ResponseBody
    public Map<String, String> cleanup(@RequestParam("repo") String repo, @RequestParam("confirm") String confirm) {
        if (!repo.equals(confirm)) {
            throw new IllegalArgumentException("Confirmation does not match repository identifier.");
        }
        return Map.of("message", repositoryCleanupService.cleanup(repo));
    }

    @PostMapping("/repo/hard-delete")
    @ResponseBody
    public Map<String, String> hardDelete(@RequestParam("repo") String repo, @RequestParam("confirm") String confirm) {
        if (!repo.equals(confirm)) {
            throw new IllegalArgumentException("Confirmation does not match repository identifier.");
        }
        return Map.of("message", repositoryCleanupService.hardDelete(repo));
    }
}
