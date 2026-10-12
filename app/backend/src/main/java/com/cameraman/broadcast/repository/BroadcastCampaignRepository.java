package com.cameraman.broadcast.repository;

import com.cameraman.broadcast.domain.BroadcastCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastCampaignRepository extends JpaRepository<BroadcastCampaign, String> {
    Page<BroadcastCampaign> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
