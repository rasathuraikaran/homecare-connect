package com.homecare.profile;

import com.homecare.profile.ProfileDtos.*;
import com.homecare.user.User;
import com.homecare.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class ProfileService {

    private final UserRepository users;
    private final CaregiverProfileRepository profiles;
    private final PhotoStorage photos;

    public ProfileService(UserRepository users, CaregiverProfileRepository profiles, PhotoStorage photos) {
        this.users = users;
        this.profiles = profiles;
        this.photos = photos;
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
        profile.update(req.age(), req.experienceYears(), qualification, req.jobType(), req.rates());
        return toResponse(user, profiles.save(profile));
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

    private ProfileResponse toResponse(User user, CaregiverProfile p) {
        if (p == null) return new ProfileResponse(user.getFullName(), null, null, null, null, Map.of(), null);
        return new ProfileResponse(user.getFullName(), p.getAge(), p.getExperienceYears(), p.getQualification(), p.getJobType(), p.getRates(), p.photoUrl());
    }
}
