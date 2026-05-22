package com.ideaminer.web;

import com.ideaminer.service.FeaturePipelineService;
import com.ideaminer.service.OnboardingService;
import com.ideaminer.service.RepositoryRegistryService;
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
    private final FeaturePipelineService featurePipelineService;

    public DashboardController(RepositoryRegistryService repositoryRegistryService,
                               OnboardingService onboardingService,
                               FeaturePipelineService featurePipelineService) {
        this.repositoryRegistryService = repositoryRegistryService;
        this.onboardingService = onboardingService;
        this.featurePipelineService = featurePipelineService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("repos", repositoryRegistryService.listRepositories());
        return "dashboard";
    }

    @PostMapping("/onboard")
    @ResponseBody
    public Map<String, String> onboard(@RequestParam("repoPath") String repoPath) {
        String runId = onboardingService.startOnboarding(repoPath, null, false);
        return Map.of("runId", runId, "repo", repoPath);
    }

    @GetMapping("/repo")
    public String repo(@RequestParam("repo") String repo, Model model) {
        model.addAttribute("repo", repo);
        model.addAttribute("status", onboardingService.status(repo));
        model.addAttribute("candidates", featurePipelineService.candidates(repo));
        return "repo";
    }

    @GetMapping("/workspace")
    public String workspace(@RequestParam("name") String name, Model model) {
        model.addAttribute("name", name);
        model.addAttribute("candidates", featurePipelineService.workspaceCandidates(name));
        model.addAttribute("workspaces", featurePipelineService.workspace("list", null, null));
        return "workspace";
    }

    @GetMapping("/onboard/status")
    @ResponseBody
    public Map<String, Object> onboardStatus(@RequestParam("runId") String runId) {
        return onboardingService.runStatus(runId);
    }
}
