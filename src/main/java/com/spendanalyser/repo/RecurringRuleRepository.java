package com.spendanalyser.repo;

import com.spendanalyser.model.RecurringRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringRuleRepository extends JpaRepository<RecurringRule, Long> {

    List<RecurringRule> findByUserIdOrderByIdDesc(Long userId);

    List<RecurringRule> findByUserIdAndActiveTrue(Long userId);

    List<RecurringRule> findByActiveTrue();

    Optional<RecurringRule> findByIdAndUserId(Long id, Long userId);
}
