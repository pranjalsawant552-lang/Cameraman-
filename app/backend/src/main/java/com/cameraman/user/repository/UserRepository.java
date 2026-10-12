package com.cameraman.user.repository;

import com.cameraman.user.domain.User;
import com.cameraman.user.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByMobile(String mobile);

    @Query("SELECT u FROM User u WHERE u.email = :identifier OR u.mobile = :identifier")
    Optional<User> findByEmailOrMobileQuery(@Param("identifier") String identifier);

    default Optional<User> findByEmailOrMobile(String identifier, String identifier2) {
        return findByEmailOrMobileQuery(identifier);
    }

    boolean existsByEmail(String email);
    boolean existsByMobile(String mobile);

    @Query("SELECT u FROM User u WHERE " +
           "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "u.mobile LIKE CONCAT('%', :search, '%'))")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);

    // The plain searchUsers() above ignores role entirely — which meant a "search existing
    // clients by name" box anywhere in the app (billing, galleries) could just as easily
    // surface a staff member's account, since nothing filtered the search to CLIENT-role
    // users. This version filters by role too, so passing role=CLIENT alongside a search
    // term actually restricts results to clients as the caller intended.
    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r.name = :roleName AND " +
           "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
           "u.mobile LIKE CONCAT('%', :search, '%'))")
    Page<User> searchUsersByRole(@Param("search") String search, @Param("roleName") String roleName, Pageable pageable);

    Page<User> findByStatus(UserStatus status, Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName")
    Page<User> findByRoleName(@Param("roleName") String roleName, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r WHERE r.name = :roleName " +
           "AND u.status = com.cameraman.user.domain.UserStatus.ACTIVE")
    long countActiveByRoleName(@Param("roleName") String roleName);

    Optional<User> findByPasswordResetToken(String token);
    Optional<User> findByRememberMeToken(String token);

    // Fan-out target for internal notifications (e.g. "new booking" -> every ADMIN/MANAGER/
    // RECEPTIONIST). Unpaged by design: this is for an in-process broadcast loop over a
    // handful of staff accounts, not a user-facing listing endpoint.
    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r.name IN :roleNames " +
           "AND u.status = com.cameraman.user.domain.UserStatus.ACTIVE")
    java.util.List<User> findByRoleNameIn(@Param("roleNames") java.util.List<String> roleNames);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r.name = 'CLIENT' " +
           "AND u.mobile IS NOT NULL AND u.mobile <> '' " +
           "AND u.status <> com.cameraman.user.domain.UserStatus.SUSPENDED " +
           "AND u.status <> com.cameraman.user.domain.UserStatus.DELETED")
    java.util.List<User> findBroadcastEligibleClients();
}
