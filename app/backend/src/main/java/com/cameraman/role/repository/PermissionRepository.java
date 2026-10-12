package com.cameraman.role.repository;

import com.cameraman.role.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {
    Optional<Permission> findByModuleAndAction(String module, String action);
    List<Permission> findByModule(String module);
    List<Permission> findByActiveTrue();
}
