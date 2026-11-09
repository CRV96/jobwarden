package com.jobwarden.inbox.service.impl;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.jobwarden.jobs.enums.ApplicationStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * What an email says about the job hunt, and which of the profile's jobs it's about. What it says comes from its words,
 * in the app's languages: a rejection, an offer, an invitation to an interview, a confirmation that the application
 * arrived. The job comes from the company's name (in the email, or as the sender's domain) and the job's title. It
 * leans to saying nothing: job alerts, and emails it isn't sure of, are left alone rather than shown as news.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class JobEmails {

	/** A phrase that settles it, and one that only hints, which counts with a word about the application. */
	private static final int CLEAR = 3;

	private static final int HINT = 1;

	/** What makes a hint count: the email is about an application. */
	private static final Pattern ABOUT_AN_APPLICATION = Pattern.compile("\\b(?:application|applying|candidacy|candidature"
			+ "|bewerbung|candidatura|solicitud|postulaci[oó]n|aplicar(?:e|ea)|aplica[tț]i[ae]|aplikacj|zg[lł]oszeni"
			+ "|kandydatur)");

	/** The words an interview is called, which count with a word about when. */
	private static final Pattern INTERVIEW = Pattern.compile("\\b(?:interview|gespr[aä]ch|entretien|entrevista"
			+ "|interviu|rozmow)");

	private static final Pattern WHEN = Pattern.compile("\\b(?:availability|available|schedule|calendar|slot|termin"
			+ "|verf[uü]gbar|disponibilit|disponibilidad|disponibilidade|dost[eę]pno)");

	/** Ordered: when two say as much, the earlier wins, e.g. a rejection that thanks for the application. */
	private static final List<Rule> RULES = List.of(
			new Rule(ApplicationStatus.REJECTED, CLEAR, "regret to inform", "(?:decided|chosen) to (?:move|go) forward with "
					+ "(?:another|other)", "move forward with other candidates", "not (?:to )?(?:move|moving) forward with "
					+ "your", "will not be (?:moving|proceeding|progressing)", "won't be (?:moving|proceeding|progressing)",
					"pursue other candidates", "position has (?:now )?been filled", "decided not to proceed",
					"(?:were|are|have) not (?:been )?selected", "unsuccessful on this occasion", "application (?:was|has been) "
							+ "unsuccessful",
					"leider (?:m[uü]ssen wir )?(?:ihnen |dir )?(?:mitteilen|absagen)", "\\babsage\\b",
					"nicht (?:weiter )?ber[uü]cksichtig", "f[uü]r (?:eine[n]? )?andere[n]? (?:kandidat|bewerb)",
					"ne pas donner (?:une suite favorable|suite)", "n'avons pas retenu", "pas (?:[eé]t[eé] )?retenue?",
					"poursuivre avec d'autres candidat",
					"no (?:hemos podido|vamos a|podemos) (?:continuar|seguir|avanzar) con (?:tu|su) (?:candidatura|solicitud)",
					"no (?:has|ha) sido seleccionad", "hemos decidido (?:continuar|seguir|avanzar) con otr",
					"n[aã]o (?:foi|fo[ií]) selecionad", "decidimos (?:avan[cç]ar|seguir|prosseguir) com outr",
					"n[aã]o (?:vamos|iremos) (?:avan[cç]ar|prosseguir) com a sua candidatura",
					"nu (?:vom|putem) (?:continua|merge mai departe) cu (?:candidatura|aplicarea|aplica[tț]ia)",
					"nu (?:ai|a[tț]i) fost selecta[tț]", "am decis s[aă] continu[aă]m cu (?:un )?al", "am ales (?:un )?alt candidat",
					"nie (?:zosta[lł](?:e[sś]|a[sś])?|zostali[sś]my) wybran", "zdecydowali[sś]my si[eę] na (?:innego|inn[aą]) kandydat",
					"nie b[eę]dziemy kontynuowa[cć]"),
			new Rule(ApplicationStatus.REJECTED, HINT, "unfortunately", "\\bleider\\b", "malheureusement", "lamentablemente",
					"desafortunadamente", "infelizmente", "din p[aă]cate", "niestety"),
			new Rule(ApplicationStatus.OFFER, CLEAR, "pleased to offer you", "offer letter", "offer of employment",
					"extend (?:you )?an offer", "would like to offer you", "formal offer", "(?:arbeits)?vertragsangebot",
					"(?:ihnen|dir) (?:gerne )?(?:die stelle|ein angebot|einen vertrag) an(?:zu)?bieten", "promesse d'embauche",
					"proposition d'embauche", "le plaisir de vous proposer le poste", "carta (?:de )?oferta",
					"nos complace ofrecerte", "propuesta de contrataci[oó]n", "temos o prazer de (?:lhe )?oferecer",
					"proposta de contrata[cç][aã]o", "ofert[aă] de angajare", "pl[aă]cerea (?:de )?a v[aă] oferi", "s[aă] (?:[iî][tț]i|v[aă]) oferim",
					"propozycj[aęi] zatrudnienia", "z przyjemno[sś]ci[aą] (?:przedstawiamy|sk[lł]adamy) ofert"),
			new Rule(ApplicationStatus.INTERVIEWING, CLEAR,
					"invite you (?:to|for) (?:an?|the|a short) (?:\\w+ )?(?:interview|call|chat|conversation|meeting)",
					"(?:schedule|book|arrange|set up) (?:an?|the|your) (?:\\w+ )?(?:interview|call|chat)",
					"interview invitation", "invitation to (?:an? )?interview", "calendly\\.com/", "(?:share|send us|let us know)"
							+ " your availability",
					"technical (?:interview|assessment|challenge)", "take-home (?:task|assignment|exercise|test)",
					"coding (?:challenge|exercise|assignment|test)",
					"einladung zu(?:m)? (?:einem )?(?:vorstellungs|kennenlern|telefon)?(?:gespr[aä]ch|interview)",
					"(?:vorstellungs|kennenlern)gespr[aä]ch", "terminvorschl[aä]ge", "vous inviter [aà] un entretien",
					"entretien (?:t[eé]l[eé]phonique|visio|technique|en visio)", "vos disponibilit[eé]s",
					"invitarte a una entrevista", "entrevista (?:telef[oó]nica|t[eé]cnica|por video)", "tu disponibilidad",
					"convidar(?:-lo|-la)? para uma entrevista", "entrevista (?:t[eé]cnica|por telefone|online)",
					"sua disponibilidade", "invit[aă]m la (?:un )?interviu", "interviu (?:tehnic|telefonic|online)",
					"disponibilitatea (?:ta|dumneavoastr[aă])", "zaprosi[cć] (?:ci[eę] |pana |pani[aą] )?na rozmow",
					"rozmow[aęy] (?:rekrutacyjn|techniczn|telefoniczn)", "twoj[aą] dost[eę]pno"),
			new Rule(ApplicationStatus.APPLIED, CLEAR, "thank you for (?:your )?appl(?:ying|ication)", "thanks for applying",
					"received your application", "your application (?:has been|was) (?:received|submitted|sent)",
					"application (?:received|submitted)", "vielen dank f[uü]r (?:ihre|deine) bewerbung",
					"(?:ihre|deine) bewerbung (?:ist )?(?:bei uns )?eingegangen", "merci pour votre candidature",
					"(?:avons bien )?re[cç]u votre candidature", "gracias por (?:tu|su) (?:candidatura|solicitud|postulaci[oó]n)",
					"hemos recibido (?:tu|su) (?:candidatura|solicitud)", "obrigad[oa] pela (?:sua )?candidatura",
					"recebemos a sua candidatura", "(?:[iî][tț]i|v[aă]) mul[tț]umim pentru (?:aplicare|aplica[tț]ia|candidatura)",
					"am primit (?:aplica[tț]ia|candidatura)",
					"dzi[eę]kujemy za (?:przes[lł]anie |z[lł]o[zż]enie )?(?:aplikacj|zg[lł]oszeni|cv)",
					"otrzymali[sś]my (?:twoj[aą] |pana |pani )?(?:aplikacj|zg[lł]oszeni)"));

	/** Job alerts and digests: postings, not news about an application. */
	private static final Pattern ALERT = Pattern.compile("job ?alert|jobs? (?:for you|you may|you might|similar)"
			+ "|new jobs|recommended jobs|empfohlene jobs|neue jobs|offres? d'emploi pour vous|nouvelles offres"
			+ "|nuevas ofertas|ofertas de empleo para|vagas para voc[eê]|joburi noi|oferte noi|nowe oferty|oferty pracy dla");

	private static final Pattern ALERT_SENDER = Pattern.compile("jobalert|job-alert|alerts?@|digest|newsletter");

	/**
	 * A word a company's legal name ends with, which an email leaves out: one at a time, so a name ending in several,
	 * e.g. Acme Group Ltd, loses them one after another, never by repeating a group (withoutLegalForms).
	 */
	private static final Pattern LEGAL_FORM = Pattern.compile("(?<![\\s,])[\\s,]+(?:inc|llc|ltd|limited|gmbh|ag|sa|srl|s\\.r\\.l"
			+ "|sp\\.? z o\\.?o|bv|nv|plc|corp|corporation|co|company|group|holding|se|ab|oy|kft|spa|sarl|sas)\\.?$");

	/** The senders' domains that say nothing of the company: job boards, applicant tracking, mail providers. */
	private static final Set<String> NOT_A_COMPANY = Set.of("greenhouse", "lever", "workday", "myworkday",
			"myworkdayjobs", "smartrecruiters", "ashbyhq", "workable", "teamtailor", "recruitee", "personio", "bamboohr",
			"jobvite", "icims", "taleo", "successfactors", "breezy", "linkedin", "indeed", "glassdoor", "xing", "gmail",
			"googlemail", "outlook", "hotmail", "live", "yahoo", "icloud", "proton", "protonmail", "gmx", "mail", "web");

	/** Title words too common to tell one job from another. */
	private static final Set<String> COMMON_TITLE_WORDS = Set.of("senior", "junior", "lead", "principal", "staff",
			"remote", "hybrid", "full", "time", "part", "with", "and", "for", "the", "der", "die", "das", "und");

	private static final int EXCERPT_MAX_LENGTH = 280;

	/** The most words a company's name has, as found in an email. */
	private static final int NAME_WORDS = 5;

	private static final Pattern WORD_BREAK = Pattern.compile("[^\\p{L}\\p{N}]+");

	/** How much of a long email is read: news comes first. */
	private static final int TEXT_MAX_LENGTH = 8000;

	/**
	 * One of the profile's jobs an email may be about.
	 */
	record Job(long id, String title, @Nullable String company, ApplicationStatus status) {
	}

	/**
	 * @param applicationId the job it's about; {@code null} when none matched, for the profile to pick
	 * @param excerpt the words that said it
	 */
	record Found(ApplicationStatus status, @Nullable Long applicationId, String excerpt) {
	}

	/**
	 * The profile's jobs, made ready to look for in many emails.
	 */
	static List<Prepared> prepare(List<Job> jobs) {
		return jobs.stream().map(Prepared::new).toList();
	}

	/**
	 * @return what the email says about one of the jobs (given those changed last first), or nothing
	 */
	static Optional<Found> read(MailMessage message, List<Prepared> jobs) {
		String subject = fold(message.subject());
		String sender = fold((message.fromAddress() != null) ? message.fromAddress() : "");
		if (ALERT.matcher(subject).find() || ALERT_SENDER.matcher(sender).find()) {
			return Optional.empty();
		}
		String text = message.text().length() > TEXT_MAX_LENGTH ? message.text().substring(0, TEXT_MAX_LENGTH)
				: message.text();
		// Composed, as folding keeps it the same length: the excerpt is taken from it where the folded text matched
		String original = Normalizer.normalize(message.subject() + "\n" + text, Normalizer.Form.NFC);
		Optional<Said> said = say(fold(original), original);
		if (said.isEmpty()) {
			return Optional.empty();
		}
		ApplicationStatus status = said.get().status();
		Optional<Prepared> job = match(message, jobs);
		if (job.isPresent()) {
			return makesSense(status, job.get().job().status())
					? Optional.of(new Found(status, job.get().job().id(), said.get().excerpt())) : Optional.empty();
		}
		// With no job, only clear news, for the profile to pick the job: a confirmation of an application to a job it
		// doesn't track isn't news to it
		return (said.get().clear() && status != ApplicationStatus.APPLIED)
				? Optional.of(new Found(status, null, said.get().excerpt())) : Optional.empty();
	}

	/**
	 * Whether the email moves the job on: a confirmation only for a job still saved, an interview only before there
	 * is one, and nothing the job already is.
	 */
	private static boolean makesSense(ApplicationStatus said, ApplicationStatus now) {
		return switch (said) {
			case APPLIED -> now == ApplicationStatus.SAVED;
			case INTERVIEWING -> now != ApplicationStatus.INTERVIEWING && now != ApplicationStatus.OFFER;
			default -> said != now;
		};
	}

	/**
	 * What the email says: the rule that says the most, the earlier one when two say as much. A hint counts only with
	 * the application named.
	 */
	private static Optional<Said> say(String folded, String original) {
		Said best = null;
		boolean application = ABOUT_AN_APPLICATION.matcher(folded).find();
		for (Rule rule : RULES) {
			Matcher found = rule.pattern().matcher(folded);
			if (!found.find()) {
				continue;
			}
			int weight = rule.weight();
			if (weight == HINT) {
				if (!application) {
					continue;
				}
				weight = 2;
			}
			if (best == null || weight > best.weight()) {
				best = new Said(rule.status(), weight, excerpt(original, found.start()));
			}
		}
		// An interview named with a word about when, though no phrase settled it
		if (best == null) {
			Matcher interview = INTERVIEW.matcher(folded);
			if (interview.find() && WHEN.matcher(folded).find() && application) {
				best = new Said(ApplicationStatus.INTERVIEWING, 2, excerpt(original, interview.start()));
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * The job the email is about: its company named in it, or as the sender's domain; among several at that company,
	 * the one whose title it names. Its title alone isn't enough: many companies post the same title.
	 */
	private static Optional<Prepared> match(MailMessage message, List<Prepared> jobs) {
		String folded = fold(message.subject() + " " + ((message.fromName() != null) ? message.fromName() : "") + " "
				+ ((message.text().length() > TEXT_MAX_LENGTH) ? message.text().substring(0, TEXT_MAX_LENGTH)
						: message.text()));
		Set<String> phrases = phrases(folded);
		String domain = senderDomain(message.fromAddress());
		Prepared best = null;
		int bestScore = 0;
		for (Prepared job : jobs) {
			if (!job.companyIn(phrases, domain)) {
				continue;
			}
			int score = job.titleIn(folded) ? 5 : 3;
			if (score > bestScore) {
				best = job;
				bestScore = score;
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * Every run of up to {@value #NAME_WORDS} words in the text: a company's name is found among them at once, for each
	 * of thousands of jobs.
	 */
	private static Set<String> phrases(String folded) {
		String[] words = WORD_BREAK.split(folded);
		Set<String> phrases = new HashSet<>();
		for (int start = 0; start < words.length; start++) {
			StringBuilder phrase = new StringBuilder();
			for (int end = start; end < Math.min(words.length, start + NAME_WORDS); end++) {
				if (words[end].isEmpty()) {
					break;
				}
				phrase.append((end > start) ? " " : "").append(words[end]);
				phrases.add(phrase.toString());
			}
		}
		return phrases;
	}

	/**
	 * The sentence around a place in the text, short enough to show.
	 */
	static String excerpt(String text, int place) {
		int at = Math.min(place, text.length());
		int start = at;
		while (start > 0 && at - start < EXCERPT_MAX_LENGTH / 2 && ".!?\n".indexOf(text.charAt(start - 1)) < 0) {
			start--;
		}
		int end = at;
		while (end < text.length() && end - start < EXCERPT_MAX_LENGTH && ".!?\n".indexOf(text.charAt(end)) < 0) {
			end++;
		}
		if (end < text.length() && text.charAt(end) != '\n') {
			end++;
		}
		return text.substring(start, Math.min(end, text.length())).replaceAll("\\s+", " ").strip();
	}

	/** The name without the legal forms it ends with, the last first. */
	static String withoutLegalForms(String name) {
		String left = name;
		Matcher legalForm = LEGAL_FORM.matcher(left);
		while (legalForm.find()) {
			left = left.substring(0, legalForm.start());
			legalForm = LEGAL_FORM.matcher(left);
		}
		return left;
	}

	/**
	 * The name of the sender's domain, without what comes after: talent@mail.acme.co.uk is acme. None for a domain
	 * that says nothing of the company.
	 */
	static @Nullable String senderDomain(@Nullable String address) {
		if (address == null || address.indexOf('@') < 0) {
			return null;
		}
		String[] labels = address.substring(address.indexOf('@') + 1).toLowerCase(Locale.ROOT).split("\\.");
		if (labels.length < 2) {
			return null;
		}
		boolean twoPartSuffix = labels.length >= 3 && labels[labels.length - 1].length() == 2
				&& Set.of("co", "com", "org", "net", "ac", "gov", "edu").contains(labels[labels.length - 2]);
		String name = labels[labels.length - (twoPartSuffix ? 3 : 2)];
		return NOT_A_COMPANY.contains(name) ? null : name;
	}

	/** Lower case, without accents, with the apostrophes alike, so the rules read every language's spelling. */
	static String fold(String text) {
		String plain = Normalizer.normalize(text.replace('’', '\'').replace('ș', 's').replace('ş', 's')
			.replace('ț', 't').replace('ţ', 't'), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return plain.toLowerCase(Locale.ROOT).replace('ł', 'l');
	}

	/**
	 * A rule's phrases, folded like the text they're looked for in.
	 */
	private record Rule(ApplicationStatus status, int weight, Pattern pattern) {

		Rule(ApplicationStatus status, int weight, String... phrases) {
			this(status, weight, Pattern.compile(Arrays.stream(phrases)
				.map(phrase -> "(?:" + fold(phrase) + ")")
				.collect(Collectors.joining("|"))));
		}

	}

	private record Said(ApplicationStatus status, int weight, String excerpt) {

		boolean clear() {
			return this.weight >= CLEAR;
		}

	}

	/**
	 * A job, with its company's name ready to look for: folded, in words, without its legal form.
	 */
	static final class Prepared {

		private final Job job;

		/** The name's words, a space between each: what {@link #phrases} has when the email names it. */
		private final @Nullable String company;

		/** The name without spaces, as a domain writes it: Acme Labs is acmelabs. */
		private final @Nullable String compactCompany;

		/** Its first word, when it's long enough to stand for it as a domain: Contoso Ltd's contoso.com. */
		private final @Nullable String firstWord;

		private final String title;

		private final List<String> titleWords;

		Prepared(Job job) {
			this.job = job;
			String name = (job.company() != null)
					? String.join(" ", WORD_BREAK.split(withoutLegalForms(fold(job.company()).strip()))).strip()
					: "";
			boolean usable = name.replace(" ", "").length() >= 3;
			this.company = usable ? name : null;
			this.compactCompany = usable ? name.replace(" ", "") : null;
			String first = usable ? name.split(" ")[0] : "";
			this.firstWord = (first.length() >= 5) ? first : null;
			this.title = fold(job.title()).strip();
			this.titleWords = Arrays.stream(WORD_BREAK.split(this.title))
				.filter(word -> word.length() >= 3 && !COMMON_TITLE_WORDS.contains(word))
				.toList();
		}

		Job job() {
			return this.job;
		}

		/** Whether the email names the company, or comes from its domain. */
		boolean companyIn(Set<String> phrases, @Nullable String domain) {
			if (this.company == null) {
				return false;
			}
			return phrases.contains(this.company)
					|| (domain != null && (domain.equals(this.compactCompany) || domain.equals(this.firstWord)));
		}

		/** Whether the email names the title, or every word of it that tells it apart. */
		boolean titleIn(String folded) {
			if (folded.contains(this.title)) {
				return true;
			}
			return !this.titleWords.isEmpty() && this.titleWords.stream().allMatch(folded::contains);
		}

	}

}
