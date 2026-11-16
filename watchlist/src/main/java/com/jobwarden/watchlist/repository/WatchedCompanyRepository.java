package com.jobwarden.watchlist.repository;

import java.util.List;
import java.util.Optional;

import com.jobwarden.watchlist.entity.WatchedCompanyEntity;
import com.jobwarden.watchlist.enums.Ats;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchedCompanyRepository extends JpaRepository<WatchedCompanyEntity, Long> {

	List<WatchedCompanyEntity> findByUserIdOrderByNameAscIdAsc(long userId);

	Optional<WatchedCompanyEntity> findByIdAndUserId(long id, long userId);

	Optional<WatchedCompanyEntity> findByUserIdAndAtsAndBoard(long userId, Ats ats, String board);

	/** Every profile's, a board's together. */
	List<WatchedCompanyEntity> findAllByOrderByAtsAscBoardAscIdAsc();

}
