package com.cameraman.broadcast.repository;

import com.cameraman.broadcast.domain.BroadcastRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BroadcastRecipientRepository extends JpaRepository<BroadcastRecipient, String> {
    List<BroadcastRecipient> findByCampaignIdOrderByCreatedAtAsc(String campaignId);
    Page<BroadcastRecipient> findByCampaignIdOrderByCreatedAtAsc(String campaignId, Pageable pageable);
    long countByCampaignIdAndStatus(String campaignId, String status);
}
