package com.jobwarden.cv.repository;

import java.util.Optional;

import com.jobwarden.cv.entity.CvFileEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CvFileRepository extends JpaRepository<CvFileEntity, Long> {

	@Query("""
			select new com.jobwarden.cv.repository.CvFileSummary(f.fileName, f.contentType, f.sizeBytes, length(f.text),
			    f.uploadedAt)
			from CvFileEntity f
			where f.userId = :userId""")
	Optional<CvFileSummary> findSummary(long userId);

	@Query("select f.text from CvFileEntity f where f.userId = :userId")
	Optional<String> findText(long userId);

}
