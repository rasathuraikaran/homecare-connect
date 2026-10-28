package com.homecare.profile;

/** Published when a caregiver's job changes, because what they must prove depends on it. */
public record ProfileJobChanged(Long userId) {}
