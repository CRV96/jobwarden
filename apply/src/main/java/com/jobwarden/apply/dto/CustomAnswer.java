package com.jobwarden.apply.dto;

/**
 * A question application forms ask, with the profile's answer, e.g. "Do you have a driving licence?" "Yes, B".
 */
public record CustomAnswer(String question, String answer) {
}
