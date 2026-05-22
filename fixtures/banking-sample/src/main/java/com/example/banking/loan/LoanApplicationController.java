package com.example.banking.loan;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
public class LoanApplicationController {
    private final LoanEligibilityService eligibilityService;

    public LoanApplicationController(LoanEligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
    }

    @PostMapping("/{customerId}/eligibility")
    public String checkEligibility(@PathVariable String customerId) {
        return eligibilityService.evaluateEligibility(customerId, 720, 50000, false);
    }

    @GetMapping("/{customerId}/status")
    public String status(@PathVariable String customerId) {
        return eligibilityService.manualReviewStatus(customerId);
    }
}
