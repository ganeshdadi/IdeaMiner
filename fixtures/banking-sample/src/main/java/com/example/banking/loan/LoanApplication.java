package com.example.banking.loan;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "loan_applications")
public class LoanApplication {
    private String customerId;
    private String status;
}
