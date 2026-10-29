package com.jobwarden.hiring.service;

import java.util.List;
import java.util.Set;

import com.jobwarden.hiring.dto.AddedHiringPosts;
import com.jobwarden.hiring.dto.AddedRecruiters;
import com.jobwarden.hiring.dto.HiringPost;
import com.jobwarden.hiring.dto.HiringPostQuery;
import com.jobwarden.hiring.dto.NewHiringPost;
import com.jobwarden.hiring.dto.NewRecruiter;
import com.jobwarden.hiring.dto.Recruiter;
import org.jspecify.annotations.Nullable;

/**
 * Every method takes the owning user's id, so one profile can never read or change another's recruiters or posts.
 */
public interface HiringService {

	/**
	 * Adds the posts the profile doesn't have yet, with their authors: an author is one profile link, whose name and
	 * headline the latest post brings up to date. Each post's hashtags become its tags. A post whose link, or whose
	 * author's profile, isn't a web address is skipped, like one the profile has.
	 */
	AddedHiringPosts addPosts(long userId, List<NewHiringPost> posts);

	/**
	 * @return the posts the query asks for, hidden ones left out, the latest first
	 */
	List<HiringPost> findPosts(long userId, HiringPostQuery query);

	/**
	 * @return every post link the profile has, hidden ones included: what not to add again
	 */
	Set<String> findPostLinks(long userId);

	/**
	 * @throws com.jobwarden.hiring.exception.HiringPostNotFoundException if the profile has no such post
	 */
	HiringPost findPost(long userId, long postId);

	/**
	 * Hides the post for good: it's never shown, nor added again.
	 */
	void hidePost(long userId, long postId);

	/**
	 * @param applicationId the job the post was saved as
	 */
	HiringPost markSaved(long userId, long postId, long applicationId);

	/**
	 * The profile's recruiters, removed ones left out: those it follows first, then those with the most posts. The
	 * contacts saved on its jobs with a profile link are recruiters too: the first time they're seen, they're added,
	 * and followed.
	 */
	List<Recruiter> findRecruiters(long userId);

	/**
	 * Adds the recruiters Claude found that the profile doesn't have yet, removed ones included, each with what's known
	 * of them and why it's worth following. One whose profile isn't a web address is skipped too.
	 */
	AddedRecruiters addFoundRecruiters(long userId, List<NewRecruiter> recruiters);

	/**
	 * Adds a recruiter by hand, or brings the one with that profile up to date, and back if it was removed.
	 *
	 * @throws com.jobwarden.common.exception.BusinessRuleException if the profile isn't a web address
	 */
	Recruiter addRecruiter(long userId, String name, @Nullable String headline, String profileUrl);

	/**
	 * Removes the recruiter from the page for good: no longer followed, nor added again by Claude. Their posts stay.
	 *
	 * @throws com.jobwarden.hiring.exception.RecruiterNotFoundException if the profile has no such recruiter
	 */
	void removeRecruiter(long userId, long recruiterId);

	/**
	 * @throws com.jobwarden.hiring.exception.RecruiterNotFoundException if the profile has no such recruiter
	 */
	Recruiter follow(long userId, long recruiterId, boolean followed);

}
