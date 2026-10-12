package com.cameraman.engagement.service;

import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.engagement.domain.SuggestionRule;
import com.cameraman.engagement.domain.SuggestionTriggerType;
import com.cameraman.engagement.dto.EngagementDto;
import com.cameraman.engagement.repository.SuggestionRuleRepository;
import com.cameraman.package_mgmt.domain.PackageCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SuggestionRuleService {

    private final SuggestionRuleRepository ruleRepository;

    public EngagementDto.RuleResponse create(EngagementDto.RuleRequest request) {
        SuggestionRule rule = SuggestionRule.builder()
                .name(request.getName())
                .category(request.getCategory() != null && !request.getCategory().isBlank() ? PackageCategory.valueOf(request.getCategory()) : null)
                .triggerType(SuggestionTriggerType.valueOf(request.getTriggerType()))
                .triggerQuestionKey(request.getTriggerQuestionKey())
                .offsetDays(request.getOffsetDays())
                .messageTemplate(request.getMessageTemplate())
                .active(request.getActive() == null || request.getActive())
                .autoSend(Boolean.TRUE.equals(request.getAutoSend()))
                .channel(request.getChannel() != null && !request.getChannel().isBlank()
                        ? com.cameraman.engagement.domain.OutreachChannel.valueOf(request.getChannel())
                        : com.cameraman.engagement.domain.OutreachChannel.WHATSAPP)
                .recurring(Boolean.TRUE.equals(request.getRecurring()))
                .build();
        return toResponse(ruleRepository.save(rule));
    }

    public EngagementDto.RuleResponse update(String id, EngagementDto.RuleRequest request) {
        SuggestionRule rule = findById(id);
        rule.setName(request.getName());
        rule.setCategory(request.getCategory() != null && !request.getCategory().isBlank() ? PackageCategory.valueOf(request.getCategory()) : null);
        rule.setTriggerType(SuggestionTriggerType.valueOf(request.getTriggerType()));
        rule.setTriggerQuestionKey(request.getTriggerQuestionKey());
        rule.setOffsetDays(request.getOffsetDays());
        rule.setMessageTemplate(request.getMessageTemplate());
        if (request.getActive() != null) rule.setActive(request.getActive());
        if (request.getAutoSend() != null) rule.setAutoSend(request.getAutoSend());
        if (request.getChannel() != null && !request.getChannel().isBlank()) {
            rule.setChannel(com.cameraman.engagement.domain.OutreachChannel.valueOf(request.getChannel()));
        }
        if (request.getRecurring() != null) rule.setRecurring(request.getRecurring());
        return toResponse(ruleRepository.save(rule));
    }

    public void setActive(String id, boolean active) {
        SuggestionRule rule = findById(id);
        rule.setActive(active);
        ruleRepository.save(rule);
    }

    @Transactional(readOnly = true)
    public List<EngagementDto.RuleResponse> listAll() {
        return ruleRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    private SuggestionRule findById(String id) {
        return ruleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Suggestion rule", "id", id));
    }

    private EngagementDto.RuleResponse toResponse(SuggestionRule r) {
        return EngagementDto.RuleResponse.builder()
                .id(r.getId()).name(r.getName())
                .category(r.getCategory() != null ? r.getCategory().name() : null)
                .triggerType(r.getTriggerType().name()).triggerQuestionKey(r.getTriggerQuestionKey())
                .offsetDays(r.getOffsetDays()).messageTemplate(r.getMessageTemplate()).active(r.isActive())
                .autoSend(r.isAutoSend()).channel(r.getChannel().name())
                .recurring(r.isRecurring())
                .build();
    }
}
