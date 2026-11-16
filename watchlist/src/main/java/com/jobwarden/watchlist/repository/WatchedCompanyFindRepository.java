package com.jobwarden.watchlist.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.jobwarden.watchlist.entity.WatchedCompanyFindEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WatchedCompanyFindRepository extends JpaRepository<WatchedCompanyFindEntity, Long> {

	/**
	 * @return each company's id, and how many found jobs its board gave since then
	 */
	@Query("""
			SELECT f.watchedCompanyId, COUNT(f) FROM WatchedCompanyFindEntity f
			WHERE f.watchedCompanyId IN :companyIds AND f.foundAt >= :since
			GROUP BY f.watchedCompanyId""")
	List<Object[]> countSince(Collection<Long> companyIds, Instant since);

}
