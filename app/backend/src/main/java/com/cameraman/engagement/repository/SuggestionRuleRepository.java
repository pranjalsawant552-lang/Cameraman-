package com.cameraman.engagement.repository;

import com.cameraman.engagement.domain.SuggestionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuggestionRuleRepository extends JpaRepository<SuggestionRule, String> {
    List<SuggestionRule> findByActiveTrue();
}
