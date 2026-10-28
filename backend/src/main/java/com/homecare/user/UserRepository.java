package com.homecare.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByGoogleSub(String googleSub);
    boolean existsByEmailIgnoreCase(String email);

    /** Rows of [User, CaregiverProfile or null], newest first. pattern is lower case, like %sam%, with % and _ in the typed text escaped by a backslash. */
    @Query("select u, p from User u left join CaregiverProfile p on p.userId = u.id "
            + "where (lower(u.fullName) like :pattern escape '\\' or lower(u.email) like :pattern escape '\\') "
            + "order by u.createdAt desc, u.id desc")
    List<Object[]> findForAdmin(@Param("pattern") String pattern, Pageable pageable);

    @Query("select u, p from User u left join CaregiverProfile p on p.userId = u.id "
            + "where u.role = :role and (lower(u.fullName) like :pattern escape '\\' or lower(u.email) like :pattern escape '\\') "
            + "order by u.createdAt desc, u.id desc")
    List<Object[]> findForAdminByRole(@Param("role") Role role, @Param("pattern") String pattern, Pageable pageable);
}
