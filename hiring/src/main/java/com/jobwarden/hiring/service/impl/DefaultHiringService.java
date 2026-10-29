package com.jobwarden.hiring.service.impl;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.hiring.dto.AddedHiringPosts;
import com.jobwarden.hiring.dto.AddedRecruiters;
import com.jobwarden.hiring.dto.ContactJob;
import com.jobwarden.hiring.dto.HiringPost;
import com.jobwarden.hiring.dto.HiringPostQuery;
import com.jobwarden.hiring.dto.NewHiringPost;
import com.jobwarden.hiring.dto.NewRecruiter;
import com.jobwarden.hiring.dto.Recruiter;
import com.jobwarden.hiring.entity.HiringPostEntity;
import com.jobwarden.hiring.entity.RecruiterEntity;
import com.jobwarden.hiring.enums.RecruiterOrigin;
import com.jobwarden.hiring.exception.HiringErrorCode;
import com.jobwarden.hiring.exception.HiringPostNotFoundException;
import com.jobwarden.hiring.exception.RecruiterNotFoundException;
import com.jobwarden.hiring.repository.HiringPostRepository;
import com.jobwarden.hiring.repository.RecruiterRepository;
import com.jobwarden.hiring.service.HiringService;
import com.jobwarden.jobs.dto.JobContact;
import com.jobwarden.jobs.service.ApplicationService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A profile has a few hundred posts at most: the page's filters run over them in memory.
 */
@Service
@CustomLog
@RequiredArgsConstructor
class DefaultHiringService implements HiringService {

	private static final Pattern HASHTAG = Pattern.compile("#([\\p{L}\\p{N}_]*\\p{L}[\\p{L}\\p{N}_]*)");

	private static final int MAX_TAGS = 12;

	/** The latest first: by the day posted, or found when that isn't known */
	private static final Comparator<HiringPost> LATEST_FIRST = Comparator
		.comparing((HiringPost post) -> post.day(ZoneId.systemDefault()))
		.thenComparing(HiringPost::foundAt)
		.thenComparing(HiringPost::id)
		.reversed();

	private final RecruiterRepository recruiters;

	private final HiringPostRepository posts;

	private final ApplicationService applications;

	@Override
	@Transactional
	public AddedHiringPosts addPosts(long userId, List<NewHiringPost> found) {
		Set<String> known = new HashSet<>(this.posts.findLinks(userId));
		List<HiringPostEntity> added = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		for (NewHiringPost post : found) {
			String link = webAddress(post.link());
			String profile = webAddress(post.authorProfileUrl());
			String text = text(post.text());
			if (link == null || profile == null || text == null || !known.add(link)) {
				skipped.add(post.link());
				continue;
			}
			RecruiterEntity author = recruiter(userId, post.authorName().strip(), text(post.authorHeadline()), profile,
					RecruiterOrigin.POST);
			added.add(this.posts.save(new HiringPostEntity(userId, author, link, text, text(post.role()),
					text(post.company()), country(post.country()), tags(text), post.postedOn())));
		}
		log.info("Added {} hiring posts for profile {}, skipped {}", added.size(), userId, skipped.size());
		Map<Long, Long> counts = counts(userId);
		return new AddedHiringPosts(added.stream().map(post -> toHiringPost(post, counts)).toList(), skipped);
	}

	@Override
	@Transactional(readOnly = true)
	public List<HiringPost> findPosts(long userId, HiringPostQuery query) {
		List<HiringPostEntity> visible = this.posts.findByUserIdAndHiddenFalse(userId);
		Map<Long, Long> counts = new HashMap<>();
		visible.forEach(post -> counts.merge(post.getRecruiter().getId(), 1L, Long::sum));
		return visible.stream()
			.map(post -> toHiringPost(post, counts))
			.filter(matching(query))
			.sorted(LATEST_FIRST)
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Set<String> findPostLinks(long userId) {
		return Set.copyOf(this.posts.findLinks(userId));
	}

	@Override
	@Transactional(readOnly = true)
	public HiringPost findPost(long userId, long postId) {
		return toHiringPost(getOwnedPost(userId, postId), counts(userId));
	}

	@Override
	@Transactional
	public void hidePost(long userId, long postId) {
		getOwnedPost(userId, postId).hide();
		log.debug("Hid hiring post {} of profile {}", postId, userId);
	}

	@Override
	@Transactional
	public HiringPost markSaved(long userId, long postId, long applicationId) {
		HiringPostEntity post = getOwnedPost(userId, postId);
		post.savedAs(applicationId);
		log.info("Hiring post {} of profile {} saved as application {}", postId, userId, applicationId);
		return toHiringPost(post, counts(userId));
	}

	@Override
	@Transactional
	public List<Recruiter> findRecruiters(long userId) {
		Map<String, List<ContactJob>> contacts = addContacts(userId);
		Map<Long, Long> counts = counts(userId);
		return this.recruiters.findByUserIdAndHiddenFalseOrderByNameAsc(userId)
			.stream()
			.map(recruiter -> toRecruiter(recruiter, counts, contacts.getOrDefault(recruiter.getProfileUrl(), List.of())))
			.sorted(Comparator.comparing(Recruiter::followed).thenComparing(Recruiter::posts).reversed())
			.toList();
	}

	@Override
	@Transactional
	public AddedRecruiters addFoundRecruiters(long userId, List<NewRecruiter> found) {
		List<RecruiterEntity> added = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		for (NewRecruiter recruiter : found) {
			String profile = webAddress(recruiter.profileUrl());
			String name = text(recruiter.name());
			if (profile == null || name == null || this.recruiters.findByUserIdAndProfileUrl(userId, profile).isPresent()) {
				skipped.add(recruiter.profileUrl());
				continue;
			}
			RecruiterEntity entity = recruiter(userId, name, text(recruiter.headline()), profile, RecruiterOrigin.FOUND);
			entity.describe(text(recruiter.company()), country(recruiter.country()), text(recruiter.note()));
			added.add(entity);
		}
		log.info("Added {} recruiters found for profile {}, skipped {}", added.size(), userId, skipped.size());
		Map<Long, Long> counts = counts(userId);
		return new AddedRecruiters(added.stream().map(recruiter -> toRecruiter(recruiter, counts)).toList(), skipped);
	}

	/**
	 * @throws BusinessRuleException if the profile isn't a web address
	 */
	@Override
	@Transactional
	public Recruiter addRecruiter(long userId, String name, @Nullable String headline, String profileUrl) {
		String profile = webAddress(profileUrl);
		if (profile == null) {
			throw new BusinessRuleException(HiringErrorCode.PROFILE_LINK_NOT_A_WEB_ADDRESS,
					"A recruiter's profile must be a web address, not " + profileUrl);
		}
		RecruiterEntity recruiter = recruiter(userId, name.strip(), text(headline), profile, RecruiterOrigin.HAND);
		recruiter.show();
		log.info("Added recruiter {} for profile {}", recruiter.getId(), userId);
		return toRecruiter(recruiter, counts(userId));
	}

	/**
	 * @throws RecruiterNotFoundException if the profile has no such recruiter
	 */
	@Override
	@Transactional
	public Recruiter follow(long userId, long recruiterId, boolean followed) {
		RecruiterEntity recruiter = this.recruiters.findByIdAndUserId(recruiterId, userId)
			.orElseThrow(() -> new RecruiterNotFoundException(recruiterId));
		recruiter.follow(followed);
		log.debug("Profile {} {} recruiter {}", userId, followed ? "follows" : "no longer follows", recruiterId);
		return toRecruiter(recruiter, counts(userId));
	}

	/**
	 * @throws RecruiterNotFoundException if the profile has no such recruiter
	 */
	@Override
	@Transactional
	public void removeRecruiter(long userId, long recruiterId) {
		this.recruiters.findByIdAndUserId(recruiterId, userId)
			.orElseThrow(() -> new RecruiterNotFoundException(recruiterId))
			.hide();
		log.info("Profile {} removed recruiter {}", userId, recruiterId);
	}

	/**
	 * The recruiter with that profile, brought up to date, or a new one.
	 *
	 * @param origin how a new one came: an existing one keeps its own
	 */
	private RecruiterEntity recruiter(long userId, String name, @Nullable String headline, String profile,
			RecruiterOrigin origin) {
		RecruiterEntity recruiter = this.recruiters.findByUserIdAndProfileUrl(userId, profile)
			.orElseGet(() -> this.recruiters.save(new RecruiterEntity(userId, name, headline, profile, origin)));
		recruiter.rename(name, headline);
		return recruiter;
	}

	/**
	 * Adds the contacts saved on the profile's jobs, with a name and a profile link, as recruiters it follows, the first
	 * time they're seen. One the profile already has keeps its name, gets the role and company it didn't have, and
	 * stays removed if it was.
	 *
	 * @return the jobs each contact's profile is the contact for
	 */
	private Map<String, List<ContactJob>> addContacts(long userId) {
		Map<String, List<ContactJob>> jobs = new HashMap<>();
		for (JobContact contact : this.applications.findContacts(userId)) {
			String profile = webAddress(contact.link());
			String name = text(contact.name());
			if (profile == null || name == null) {
				continue;
			}
			Optional<RecruiterEntity> known = this.recruiters.findByUserIdAndProfileUrl(userId, profile);
			if (known.isPresent()) {
				known.get().complete(text(contact.role()), text(contact.company()));
			} else {
				RecruiterEntity recruiter = this.recruiters.save(
						new RecruiterEntity(userId, name, text(contact.role()), profile, RecruiterOrigin.CONTACT));
				recruiter.describe(text(contact.company()), null, null);
				recruiter.follow(true);
				log.info("Contact of application {} is now recruiter {} of profile {}", contact.applicationId(),
						recruiter.getId(), userId);
			}
			jobs.computeIfAbsent(profile, key -> new ArrayList<>())
				.add(new ContactJob(contact.applicationId(), contact.title(), contact.company()));
		}
		return jobs;
	}

	private HiringPostEntity getOwnedPost(long userId, long postId) {
		return this.posts.findByIdAndUserId(postId, userId).orElseThrow(() -> new HiringPostNotFoundException(postId));
	}

	private Map<Long, Long> counts(long userId) {
		Map<Long, Long> counts = new HashMap<>();
		for (Object[] count : this.posts.countByRecruiter(userId)) {
			counts.put((Long) count[0], (Long) count[1]);
		}
		return counts;
	}

	private static Predicate<HiringPost> matching(HiringPostQuery query) {
		LocalDate since = query.postedWithin().since(LocalDate.now());
		String search = (query.search() != null) ? query.search().strip().toLowerCase(Locale.ROOT) : "";
		return post -> (since == null || !post.day(ZoneId.systemDefault()).isBefore(since))
				&& (query.country() == null || query.country().equalsIgnoreCase(post.country()))
				&& (query.tag() == null || post.tags().contains(query.tag().toLowerCase(Locale.ROOT)))
				&& (!query.followedOnly() || post.recruiter().followed())
				&& (query.recruiterId() == null || query.recruiterId() == post.recruiter().id())
				&& (search.isEmpty() || contains(search, post.text(), post.role(), post.company(), post.recruiter().name(),
						post.recruiter().headline()));
	}

	private static boolean contains(String search, @Nullable String... texts) {
		for (String text : texts) {
			if (text != null && text.toLowerCase(Locale.ROOT).contains(search)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The hashtags, without the #, in lower case, each once: #Hiring #Java becomes hiring, java.
	 */
	static List<String> tags(String text) {
		Set<String> tags = new LinkedHashSet<>();
		Matcher matcher = HASHTAG.matcher(text);
		while (matcher.find() && tags.size() < MAX_TAGS) {
			tags.add(matcher.group(1).toLowerCase(Locale.ROOT));
		}
		return List.copyOf(tags);
	}

	/**
	 * The address the same page always has: without its fragment, and on LinkedIn without the tracking in its query
	 * and on www.linkedin.com, whichever country's subdomain it came from. A profile without the slash at its end.
	 *
	 * @return {@code null} if it isn't an http(s) address
	 */
	static @Nullable String webAddress(@Nullable String address) {
		if (address == null) {
			return null;
		}
		URI uri;
		try {
			uri = URI.create(address.strip());
		} catch (IllegalArgumentException notAnAddress) {
			return null;
		}
		String scheme = (uri.getScheme() != null) ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
		String host = (uri.getHost() != null) ? uri.getHost().toLowerCase(Locale.ROOT) : null;
		if (!(scheme.equals("https") || scheme.equals("http")) || host == null) {
			return null;
		}
		boolean linkedIn = host.equals("linkedin.com") || host.endsWith(".linkedin.com");
		String path = (uri.getRawPath() != null) ? uri.getRawPath().replaceAll("/+$", "") : "";
		String query = (!linkedIn && uri.getRawQuery() != null) ? "?" + uri.getRawQuery() : "";
		return scheme + "://" + (linkedIn ? "www.linkedin.com" : host) + ((uri.getPort() != -1) ? ":" + uri.getPort() : "")
				+ path + query;
	}

	private static @Nullable String country(@Nullable String code) {
		String country = text(code);
		return (country != null && country.matches("[A-Za-z]{2}")) ? country.toUpperCase(Locale.ROOT) : null;
	}

	private static @Nullable String text(@Nullable String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

	private static Recruiter toRecruiter(RecruiterEntity recruiter, Map<Long, Long> counts) {
		return toRecruiter(recruiter, counts, List.of());
	}

	private static Recruiter toRecruiter(RecruiterEntity recruiter, Map<Long, Long> counts, List<ContactJob> contactFor) {
		return Recruiter.builder()
			.id(recruiter.getId())
			.name(recruiter.getName())
			.headline(recruiter.getHeadline())
			.profileUrl(recruiter.getProfileUrl())
			.company(recruiter.getCompany())
			.country(recruiter.getCountry())
			.note(recruiter.getNote())
			.origin(recruiter.getOrigin())
			.followed(recruiter.isFollowed())
			.posts(counts.getOrDefault(recruiter.getId(), 0L))
			.contactFor(contactFor)
			.build();
	}

	private static HiringPost toHiringPost(HiringPostEntity post, Map<Long, Long> counts) {
		return HiringPost.builder()
			.id(post.getId())
			.link(post.getLink())
			.text(post.getText())
			.role(post.getRole())
			.company(post.getCompany())
			.country(post.getCountry())
			.tags(post.getTags())
			.postedOn(post.getPostedOn())
			.foundAt(post.getFoundAt())
			.applicationId(post.getApplicationId())
			.recruiter(toRecruiter(post.getRecruiter(), counts))
			.build();
	}

}
