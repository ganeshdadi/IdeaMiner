package com.example.banking.loan;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanApplicationRepository extends CrudRepository<LoanApplication, String> {
    @Query("select l from LoanApplication l where l.status = 'PENDING_REVIEW'")
    java.util.List<LoanApplication> findPendingReviewCases();

    LoanApplication findByCustomerId(String customerId);
}
