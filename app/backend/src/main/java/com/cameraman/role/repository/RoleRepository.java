package com.cameraman.role.repository;

import com.cameraman.role.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {
    Optional<Role> findByName(String name);
    boolean existsByName(String name);
    List<Role> findByActiveTrue();
}
