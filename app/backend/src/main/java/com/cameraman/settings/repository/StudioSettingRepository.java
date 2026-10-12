package com.cameraman.settings.repository;

import com.cameraman.settings.domain.StudioSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudioSettingRepository extends JpaRepository<StudioSetting, String> {
    Optional<StudioSetting> findByKey(String key);
    List<StudioSetting> findByCategory(String category);
    List<StudioSetting> findByIsPublicTrue();
}
