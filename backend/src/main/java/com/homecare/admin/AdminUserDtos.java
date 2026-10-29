package com.homecare.admin;

import com.homecare.profile.JobType;
import com.homecare.user.Role;

import java.time.Instant;

public final class AdminUserDtos {
    private AdminUserDtos() {}

    /** One account in the admin's list. job and listed are only set for caregivers; listed says whether families can see them. */
    public record AdminUserRow(Long id, String name, String email, Role role, boolean admin, Instant createdAt,
                               JobType job, Boolean listed) {}

    /** What deleting the account would remove, so the admin can see it before confirming. */
    public record DeletionPreview(Long id, String name, String email, Role role, boolean admin,
                                  long requests, long openRequests, long reviewsWritten, long reviewsReceived,
                                  long documents, boolean hasPhoto) {}
}
