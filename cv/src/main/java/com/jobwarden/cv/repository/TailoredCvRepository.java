package com.jobwarden.cv.repository;

import java.util.List;
import java.util.Optional;

import com.jobwarden.cv.entity.TailoredCvEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TailoredCvRepository extends JpaRepository<TailoredCvEntity, Long> {

	Optional<TailoredCvEntity> findByUserIdAndApplicationId(long userId, long applicationId);

	List<TailoredCvEntity> findByUserIdOrderByUpdatedAtDescIdDesc(long userId);

	@Modifying
	@Query("delete from TailoredCvEntity t where t.userId = :userId and t.applicationId = :applicationId")
	int deleteOwned(long userId, long applicationId);

}
