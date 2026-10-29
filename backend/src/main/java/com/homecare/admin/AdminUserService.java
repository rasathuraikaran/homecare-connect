package com.homecare.admin;

import com.homecare.admin.AdminUserDtos.AdminUserRow;
import com.homecare.admin.AdminUserDtos.DeletionPreview;
import com.homecare.booking.BookingRepository;
import com.homecare.booking.BookingStatus;
import com.homecare.profile.CaregiverProfile;
import com.homecare.profile.CaregiverProfileRepository;
import com.homecare.profile.PhotoStorage;
import com.homecare.review.ReviewRepository;
import com.homecare.user.Role;
import com.homecare.user.User;
import com.homecare.user.UserRepository;
import com.homecare.verification.CaregiverDocument;
import com.homecare.verification.CaregiverDocumentRepository;
import com.homecare.verification.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/** The admin's account tools: find a caregiver or care seeker and delete their account for good. */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final int LIST_LIMIT = 200;
    private static final List<BookingStatus> OPEN = List.of(BookingStatus.REQUESTED, BookingStatus.CONFIRMED);

    private final UserRepository users;
    private final CaregiverProfileRepository profiles;
    private final CaregiverDocumentRepository documents;
    private final BookingRepository bookings;
    private final ReviewRepository reviews;
    private final PhotoStorage photos;
    private final DocumentStorage documentFiles;

    public AdminUserService(UserRepository users, CaregiverProfileRepository profiles, CaregiverDocumentRepository documents,
                            BookingRepository bookings, ReviewRepository reviews, PhotoStorage photos, DocumentStorage documentFiles) {
        this.users = users;
        this.profiles = profiles;
        this.documents = documents;
        this.bookings = bookings;
        this.reviews = reviews;
        this.photos = photos;
        this.documentFiles = documentFiles;
    }

    @Transactional(readOnly = true)
    public List<AdminUserRow> list(Role role, String query) {
        String pattern = "%" + escapeLike(query == null ? "" : query.trim().toLowerCase(Locale.ROOT)) + "%";
        PageRequest page = PageRequest.of(0, LIST_LIMIT);
        List<Object[]> rows = role == null ? users.findForAdmin(pattern, page) : users.findForAdminByRole(role, pattern, page);
        return rows.stream().map(row -> {
            User u = (User) row[0];
            CaregiverProfile p = (CaregiverProfile) row[1];
            return new AdminUserRow(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isAdmin(), u.getCreatedAt(),
                    p == null ? null : p.getJobType(), p == null ? null : p.isListed());
        }).toList();
    }

    @Transactional(readOnly = true)
    public DeletionPreview preview(Long id) {
        User u = find(id);
        CaregiverProfile profile = profiles.findById(id).orElse(null);
        return new DeletionPreview(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isAdmin(),
                bookings.countBySeekerId(id) + bookings.countByCaregiverId(id),
                bookings.countBySeekerIdAndStatusIn(id, OPEN) + bookings.countByCaregiverIdAndStatusIn(id, OPEN),
                reviews.countByReviewerId(id), reviews.countByCaregiverId(id),
                documents.countByUserId(id), profile != null && profile.getPhotoFile() != null);
    }

    /**
     * Removes the account for good. The database deletes everything that hangs off it (profile, charges, documents,
     * reviews written and received, requests with their alerts); this method also removes the photo and document files.
     */
    @Transactional
    public void delete(User admin, Long id) {
        User target = find(id);
        if (target.getId().equals(admin.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot delete your own account.");
        }
        if (target.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Admins cannot be deleted here. Remove the admin rights first (./start-service.sh make-admin EMAIL --remove), then delete the account.");
        }

        String photo = profiles.findById(id).map(CaregiverProfile::getPhotoFile).orElse(null);
        List<String> files = documents.findByUserId(id).stream().map(CaregiverDocument::getFileName).toList();

        users.delete(target);
        users.flush();   // surface any database problem now, before anything is removed from disk

        deleteFilesAfterCommit(photo, files);
        log.info("Admin {} deleted {} account {}", admin.getId(), target.getRole(), id);   // who and what, never the person's details
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void deleteFilesAfterCommit(String photo, List<String> documentNames) {
        Runnable remove = () -> {
            photos.delete(photo);
            documentNames.forEach(documentFiles::delete);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    remove.run();
                }
            });
        } else {
            remove.run();
        }
    }

    /** So that a typed % or _ is searched for as itself instead of matching everything. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
