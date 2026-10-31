package com.homecare.profile;

import com.homecare.profile.ProfileDtos.*;
import com.homecare.user.User;
import com.homecare.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class ProfileService {

    private final UserRepository users;
    private final CaregiverProfileRepository profiles;
    private final CaretakerOnboardingRepository caretakerOnboardings;
    private final PhotoStorage photos;
    private final ApplicationEventPublisher events;

    public ProfileService(UserRepository users, CaregiverProfileRepository profiles,
                          CaretakerOnboardingRepository caretakerOnboardings,
                          PhotoStorage photos, ApplicationEventPublisher events) {
        this.users = users;
        this.profiles = profiles;
        this.caretakerOnboardings = caretakerOnboardings;
        this.photos = photos;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(User principal) {
        User user = users.findById(principal.getId()).orElseThrow();
        return toResponse(user, profiles.findById(user.getId()).orElse(null));
    }

    @Transactional
    public ProfileResponse save(User principal, ProfileRequest req) {
        User user = users.findById(principal.getId()).orElseThrow();
        user.setFullName(req.fullName().trim());
        CaregiverProfile profile = profiles.findById(user.getId()).orElseGet(() -> new CaregiverProfile(user.getId()));
        String qualification = req.qualification() == null || req.qualification().isBlank() ? null : req.qualification().trim();
        JobType before = profile.getJobType();
        profile.update(req.age(), req.experienceYears(), qualification, req.jobType(), req.rates(), req.address().trim());
        CaregiverProfile saved = profiles.save(profile);

        // Only persist caretaker onboarding details when the caregiver is a CARETAKER
        if (req.jobType() == JobType.CARETAKER && req.caretakerOnboarding() != null) {
            CaretakerOnboarding onboarding = caretakerOnboardings.findById(user.getId())
                    .orElseGet(() -> new CaretakerOnboarding(user.getId()));
            onboarding.apply(req.caretakerOnboarding());
            caretakerOnboardings.save(onboarding);
        }

        if (before != req.jobType()) events.publishEvent(new ProfileJobChanged(user.getId()));   // what they must prove depends on the job
        return toResponse(user, saved);
    }

    @Transactional(readOnly = true)
    public CaretakerOnboardingDto getCaretakerOnboarding(User principal) {
        return caretakerOnboardings.findById(principal.getId())
                .map(CaretakerOnboarding::toDto)
                .orElse(null);
    }

    @Transactional
    public CaretakerOnboardingDto saveCaretakerOnboarding(User principal, CaretakerOnboardingDto dto) {
        CaregiverProfile profile = profiles.findById(principal.getId()).orElse(null);
        if (profile != null && profile.getJobType() != null && profile.getJobType() != JobType.CARETAKER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caretaker onboarding is only applicable to Caretaker role");
        }
        CaretakerOnboarding onboarding = caretakerOnboardings.findById(principal.getId())
                .orElseGet(() -> new CaretakerOnboarding(principal.getId()));
        onboarding.apply(dto);
        return caretakerOnboardings.save(onboarding).toDto();
    }

    @Transactional
    public ProfileResponse setPhoto(User principal, MultipartFile file) {
        User user = users.findById(principal.getId()).orElseThrow();
        CaregiverProfile profile = profiles.findById(user.getId()).orElseGet(() -> new CaregiverProfile(user.getId()));
        String old = profile.getPhotoFile();
        profile.setPhotoFile(photos.save(file));
        profiles.save(profile);
        photos.delete(old);
        return toResponse(user, profile);
    }

    @Transactional
    public ProfileResponse removePhoto(User principal) {
        User user = users.findById(principal.getId()).orElseThrow();
        CaregiverProfile profile = profiles.findById(user.getId()).orElse(null);
        if (profile != null && profile.getPhotoFile() != null) {
            photos.delete(profile.getPhotoFile());
            profile.setPhotoFile(null);
            profiles.save(profile);
        }
        return toResponse(user, profile);
    }

    @Transactional
    public ProfileResponse setAvailable(User principal, boolean available) {
        User user = users.findById(principal.getId()).orElseThrow();
        CaregiverProfile profile = profiles.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Save your profile first"));
        profile.setAvailable(available);
        return toResponse(user, profiles.save(profile));
    }

    private ProfileResponse toResponse(User user, CaregiverProfile p) {
        if (p == null) {
            CaretakerOnboardingDto onboarding = caretakerOnboardings.findById(user.getId())
                    .map(CaretakerOnboarding::toDto)
                    .orElse(null);
            return new ProfileResponse(user.getFullName(), null, null, null, null, Map.of(), true, null, false, null, onboarding);
        }
        CaretakerOnboardingDto onboarding = p.getJobType() == JobType.CARETAKER
                ? caretakerOnboardings.findById(user.getId()).map(CaretakerOnboarding::toDto).orElse(null)
                : null;
        return new ProfileResponse(user.getFullName(), p.getAge(), p.getExperienceYears(), p.getQualification(), p.getJobType(),
                p.getRates(), p.isAvailable(), p.photoUrl(), p.isListed(), p.getAddress(), onboarding);
    }
}
