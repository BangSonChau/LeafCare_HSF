package com.son.leafcare.scan;

import com.son.leafcare.user.AppUser;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScanHistoryRepository extends JpaRepository<ScanHistory, Long> {

    List<ScanHistory> findTop5ByOrderByCreatedAtDesc();

    /** ADMIN's list shows who scanned each row; load owners in the same query. */
    @Override
    @EntityGraph(attributePaths = "owner")
    Page<ScanHistory> findAll(Pageable pageable);

    List<ScanHistory> findTop5ByOwnerOrderByCreatedAtDesc(AppUser owner);

    Page<ScanHistory> findByOwner(AppUser owner, Pageable pageable);

    long countByOwner(AppUser owner);

    long countByOwnerAndCreatedAtGreaterThanEqual(AppUser owner, LocalDateTime since);

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    /** Only the timestamps, for the per-day chart. */
    @Query("select s.createdAt from ScanHistory s where s.createdAt >= :since")
    List<LocalDateTime> findCreatedAtSince(LocalDateTime since);

    /** [ownerId, count] rows, most scans first; scans without owner are left out. */
    @Query("select s.owner.id, count(s) from ScanHistory s where s.owner is not null "
            + "group by s.owner.id order by count(s) desc, s.owner.id")
    List<Object[]> topOwnerIds(Pageable pageable);

    /** [ownerId, count] rows for the given users. */
    @Query("select s.owner.id, count(s) from ScanHistory s where s.owner in :owners group by s.owner.id")
    List<Object[]> countByOwners(Collection<AppUser> owners);
}
