package com.jobwarden.inbox.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.jobwarden.inbox.service.impl.JobEmails.Found;
import com.jobwarden.inbox.service.impl.JobEmails.Job;
import com.jobwarden.jobs.enums.ApplicationStatus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobEmailsTests {

	private static final List<JobEmails.Prepared> JOBS = JobEmails.prepare(List.of(
			new Job(1, "Senior Java Developer", "Acme Inc.", ApplicationStatus.APPLIED),
			new Job(2, "Platform Engineer", "Acme Inc.", ApplicationStatus.APPLIED),
			new Job(3, "Backend Engineer", "Globex GmbH", ApplicationStatus.APPLIED),
			new Job(4, "Kotlin Developer", "Initech", ApplicationStatus.SAVED),
			new Job(5, "Dezvoltator Java", "Umbrella SRL", ApplicationStatus.APPLIED),
			new Job(6, "DevOps Engineer", "Hooli", ApplicationStatus.INTERVIEWING)));

	@Test
	void tellsARejectionAndTheJobByItsCompanyAndTitle() {
		Optional<Found> found = read("no-reply@greenhouse.io", "Your application to Acme", """
				Hi Robert,

				Thank you for your interest in the Platform Engineer role at Acme. After careful \
				consideration, we have decided to move forward with other candidates.

				Best, the Acme team""");

		assertThat(found).hasValueSatisfying(update -> {
			assertThat(update.status()).isEqualTo(ApplicationStatus.REJECTED);
			assertThat(update.applicationId()).isEqualTo(2);
			assertThat(update.excerpt()).isEqualTo(
					"After careful consideration, we have decided to move forward with other candidates.");
		});
	}

	@Test
	void readsTheAppsLanguages() {
		assertThat(read("talent@globex.de", "Einladung zum Vorstellungsgespräch",
				"Hallo Robert, gerne möchten wir Sie zu einem Kennenlerngespräch für die Stelle Backend Engineer einladen."))
			.map(Found::status)
			.contains(ApplicationStatus.INTERVIEWING);
		assertThat(read("hr@umbrella.ro", "Aplicația ta la Umbrella",
				"Bună Robert, îți mulțumim pentru interes. Din păcate, am decis să continuăm cu alți candidați."))
			.map(Found::applicationId)
			.contains(5L);
		assertThat(read("hr@hooli.com", "Hooli", "Nous avons le plaisir de vous proposer le poste. Vous trouverez la "
				+ "promesse d’embauche en pièce jointe."))
			.map(Found::status)
			.contains(ApplicationStatus.OFFER);
	}

	@Test
	void knowsTheCompanyByTheSendersDomain() {
		assertThat(read("jobs@initech.com", "Thanks for applying!", "We received your application and will be in touch."))
			.hasValueSatisfying(update -> {
				assertThat(update.status()).isEqualTo(ApplicationStatus.APPLIED);
				assertThat(update.applicationId()).isEqualTo(4);
			});
	}

	@Test
	void saysNothingTheJobAlreadyIs() {
		// Job 1 is applied to already, job 6 interviewing
		assertThat(read("careers@acme.com", "Application received", "Thank you for applying for Senior Java Developer."))
			.isEmpty();
		assertThat(read("hr@hooli.com", "Next round", "We would like to schedule a technical interview with you."))
			.isEmpty();
	}

	@Test
	void offersClearNewsOfAJobItDoesntKnowForTheProfileToPick() {
		assertThat(read("talent@contoso.com", "Your application",
				"Unfortunately, we regret to inform you that the position has been filled."))
			.hasValueSatisfying(update -> {
				assertThat(update.status()).isEqualTo(ApplicationStatus.REJECTED);
				assertThat(update.applicationId()).isNull();
			});
		// A confirmation of an application it doesn't track isn't news to it
		assertThat(read("talent@contoso.com", "Thanks for applying", "We received your application.")).isEmpty();
	}

	@Test
	void leavesAloneWhatIsntNewsOfAnApplication() {
		assertThat(read("jobalerts-noreply@linkedin.com", "Job alert: 25 new Java jobs at Acme",
				"Unfortunately we missed you: new jobs for you")).isEmpty();
		// A hint of a rejection, with no application in it
		assertThat(read("news@acme.com", "Acme weekly", "Unfortunately the event at Acme is postponed.")).isEmpty();
		assertThat(read("friend@gmail.com", "Lunch?", "Are you free on Friday?")).isEmpty();
	}

	@Test
	void leavesAloneABanksAdThatOffersSomething() {
		// A Romanian bank's newsletter: "we'd like to offer you" a loan, "the app" in Romanian is aplicația
		assertThat(read("noreply@raiffeisen.ro", "Oferta lunii pentru tine", """
				Bună Robert,
				Vrem să îți oferim o dobândă avantajoasă la creditul de nevoi personale. Descarcă aplicația Smart Mobile.
				Din păcate, oferta e valabilă doar până vineri.
				Dacă nu mai vrei să primești aceste emailuri, apasă aici pentru dezabonare.""")).isEmpty();
		assertThat(read("offers@shop.com", "A special offer", "We would like to offer you 20% off your next order. "
				+ "Unfortunately, it ends on Sunday. Check your application settings.")).isEmpty();
		assertThat(read("news@bank.com", "Changes to your account", "We regret to inform you that the branch closes.")).isEmpty();
	}

	@Test
	void readsAMailingToManyOnlyForAJobItNamesInSoManyWords() {
		// Sent to many (List-Unsubscribe), from the company of a job: a clear rejection is still news
		assertThat(bulk("careers@globex.de", "Your application at Globex",
				"Thank you for applying. We regret to inform you that we decided not to proceed."))
			.map(Found::applicationId)
			.contains(3L);
		// A hint is not, nor clear news of no job of the profile's
		assertThat(bulk("careers@globex.de", "Globex news", "Unfortunately our event is full. Thanks for your application."))
			.isEmpty();
		assertThat(bulk("talent@contoso.com", "Your application", "We regret to inform you that the position has been filled."))
			.isEmpty();
	}

	@Test
	void tellsAnOfferOfAJobFromAnAdsWords() {
		assertThat(read("hr@initech.com", "Initech", "We are delighted to offer you the Kotlin Developer position."))
			.map(Found::status)
			.contains(ApplicationStatus.OFFER);
		// Only a hint without the job: news of the job it names, nothing for one it doesn't
		assertThat(read("hr@initech.com", "Your application", "We would like to offer you a contract."))
			.map(Found::status)
			.contains(ApplicationStatus.OFFER);
		assertThat(read("hr@contoso.com", "Your application", "We would like to offer you a contract.")).isEmpty();
	}

	@Test
	void excerptsTheSentenceThatSaidIt() {
		assertThat(JobEmails.excerpt("Hello there. We regret to inform you, sadly. Bye", 14))
			.isEqualTo("We regret to inform you, sadly.");
	}

	@Test
	void namesTheSendersDomainWithoutWhatSaysNothing() {
		assertThat(JobEmails.senderDomain("talent@mail.acme.co.uk")).isEqualTo("acme");
		assertThat(JobEmails.senderDomain("hr@globex.de")).isEqualTo("globex");
		assertThat(JobEmails.senderDomain("no-reply@us.greenhouse-mail.io")).isEqualTo("greenhouse-mail");
		assertThat(JobEmails.senderDomain("someone@gmail.com")).isNull();
		assertThat(JobEmails.senderDomain(null)).isNull();
	}

	private static Optional<Found> read(@Nullable String from, String subject, String text) {
		return JobEmails.read(new MailMessage("<" + subject.hashCode() + "@example.com>", Instant.now(), null, from, subject,
				text), JOBS);
	}

	/** One whose headers say it went to many, as a newsletter's do. */
	private static Optional<Found> bulk(@Nullable String from, String subject, String text) {
		return JobEmails.read(new MailMessage("<" + subject.hashCode() + "@example.com>", Instant.now(), null, from, subject,
				text, true), JOBS);
	}

}
