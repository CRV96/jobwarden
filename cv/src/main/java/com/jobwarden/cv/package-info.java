/**
 * CV: a profile's CV, as a file uploaded as it is (PDF, Word or text), with the text read out of it so Claude can
 * match jobs against it; and as fields (personal details, experience, education, skills...) the CV page builds one
 * from; and as Claude rewrote it for one job or another. Owns the {@code cv_files}, {@code cv_details} and
 * {@code tailored_cvs} tables.
 * <p>
 * Public API: the {@code dto}, {@code service} and {@code exception} packages, marked with {@code @NamedInterface}.
 * {@code entity}, {@code repository} and {@code service.impl} are internal to the module.
 */
package com.jobwarden.cv;
