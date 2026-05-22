package com.example.banking.loan;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LoanStatusUpdateJob {
    @Scheduled(cron = "0 0 2 * * *")
    public void updatePendingLoanStatusNotifications() {
        String status = "pending";
        if (status.equals("pending")) {
            System.out.println("send loan status notification");
        }
    }
}
