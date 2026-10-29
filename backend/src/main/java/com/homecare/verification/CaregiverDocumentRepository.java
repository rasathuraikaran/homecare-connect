package com.homecare.verification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CaregiverDocumentRepository extends JpaRepository<CaregiverDocument, Long> {

    List<CaregiverDocument> findByUserId(Long userId);

    long countByUserId(Long userId);

    Optional<CaregiverDocument> findByUserIdAndDocType(Long userId, DocumentType docType);

    List<CaregiverDocument> findByUserIdIn(Collection<Long> userIds);

    List<CaregiverDocument> findByUserIdInAndStatus(Collection<Long> userIds, DocumentStatus status);

    @Query("select distinct d.userId from CaregiverDocument d where d.status = :status")
    List<Long> userIdsWithStatus(@Param("status") DocumentStatus status, Pageable pageable);

    @Query("select distinct d.userId from CaregiverDocument d")
    List<Long> userIdsWithDocuments(Pageable pageable);
}
