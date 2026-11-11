/**
 * CV: a profile's CV, as a file uploaded as it is (PDF, Word or text), with the text read out of it so Claude can
 * match jobs against it, and shown on the CV page (a Word document as HTML); as fields (personal details, experience,
 * projects, education, skills...) the CV page builds one from; and as Claude rewrote it for one job or another. And the
 * profile's GitHub account, whose repositories it chooses for Claude to write the CV's projects from, read with a token
 * it gives, kept encrypted, when they're private. What the profile sends about a job besides the CV: a cover letter,
 * and messages to the people who hire for it, which the profile sends itself. And the story bank: what the profile did,
 * told the STAR way, for interviews. Owns the {@code cv_files}, {@code cv_details}, {@code tailored_cvs},
 * {@code github_accounts}, {@code cover_letters}, {@code outreach_drafts} and {@code cv_stories} tables.
 * <p>
 * Public API: the {@code dto}, {@code service} and {@code exception} packages, marked with {@code @NamedInterface}.
 * {@code entity}, {@code repository} and {@code service.impl} are internal to the module.
 */
package com.jobwarden.cv;
