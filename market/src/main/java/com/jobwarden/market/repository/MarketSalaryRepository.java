package com.jobwarden.market.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jobwarden.market.entity.MarketSalaryEntity;
import com.jobwarden.market.enums.MarketSource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MarketSalaryRepository extends JpaRepository<MarketSalaryEntity, Long> {

	List<MarketSalaryEntity> findBySourceAndRoleAndSkillAndCountryIn(MarketSource source, String role, String skill,
			Collection<String> countries);

	/**
	 * The figures of every country together for one dimension, the most answered first: the roles (with
	 * {@code skill} empty) or the skills (with {@code role} empty).
	 */
	List<MarketSalaryEntity> findBySourceAndCountryAndRoleAndSkillOrderBySampleSizeDesc(MarketSource source,
			String country, String role, String skill);

	@Query("""
			select m from MarketSalaryEntity m
			where m.source = :source and m.country = '' and m.skill = '' and m.role <> ''
			order by m.sampleSize desc""")
	List<MarketSalaryEntity> findRoles(MarketSource source);

	@Query("""
			select m from MarketSalaryEntity m
			where m.source = :source and m.country = '' and m.role = '' and m.skill <> ''
			order by m.sampleSize desc""")
	List<MarketSalaryEntity> findSkills(MarketSource source);

	@Query("""
			select m from MarketSalaryEntity m
			where m.source = :source and m.role = '' and m.skill = '' and m.country <> ''
			order by m.sampleSize desc""")
	List<MarketSalaryEntity> findCountries(MarketSource source);

	long countBySource(MarketSource source);

	Optional<MarketSalaryEntity> findFirstBySourceOrderByImportedAtDesc(MarketSource source);

	@Modifying
	@Query("delete from MarketSalaryEntity m where m.source = :source")
	int deleteBySource(MarketSource source);

	default Optional<Instant> lastImported(MarketSource source) {
		return findFirstBySourceOrderByImportedAtDesc(source).map(MarketSalaryEntity::getImportedAt);
	}

}
