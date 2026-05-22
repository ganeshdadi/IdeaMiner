package com.example.banking.loan;

import org.springframework.stereotype.Service;

@Service
public class LoanEligibilityService {
    public String evaluateEligibility(String customerId, int riskScore, int requestedLimit, boolean fraudFlag) {
        if (fraudFlag) {
            return "manual-review";
        }
        if (riskScore < 600) {
            return "declined-risk";
        }
        if (requestedLimit > 100000) {
            if (riskScore > 760) {
                return "approval-high-limit";
            }
            return "manual-approval";
        }
        if (customerId.startsWith("VIP")) {
            return "offer-premium-pricing";
        }
        return "approved";
    }

    public String manualReviewStatus(String customerId) {
        if (customerId == null) {
            return "pending-review";
        }
        return "case-queue";
    }
}
