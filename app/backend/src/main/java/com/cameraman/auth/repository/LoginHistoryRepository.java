package com.cameraman.auth.repository;

import com.cameraman.auth.domain.LoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, String> {
    Page<LoginHistory> findByUserId(String userId, Pageable pageable);
    Page<LoginHistory> findByUserIdOrderByLoggedInAtDesc(String userId, Pageable pageable);
}
