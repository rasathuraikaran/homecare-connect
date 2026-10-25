package com.homecare.profile;

import com.homecare.profile.ProfileDtos.CaregiverCard;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** What care seekers browse: caregivers grouped by the kind of work they do. */
@RestController
@RequestMapping("/api/caregivers")
public class CaregiverDirectoryController {

    private static final int MAX_RESULTS = 50;

    private final CaregiverProfileRepository profiles;

    public CaregiverDirectoryController(CaregiverProfileRepository profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CaregiverCard> list(@RequestParam JobType job) {
        return profiles.findCards(job, PageRequest.of(0, MAX_RESULTS)).stream()
                .map(row -> {
                    CaregiverProfile p = (CaregiverProfile) row[0];
                    return new CaregiverCard(p.getUserId(), (String) row[1], p.getAge(), p.getExperienceYears(),
                            p.getQualification(), p.getJobType(), p.getRates(), p.photoUrl());
                })
                .toList();
    }

    @GetMapping("/counts")
    @Transactional(readOnly = true)
    public Map<JobType, Long> counts() {
        Map<JobType, Long> result = new EnumMap<>(JobType.class);
        for (JobType t : JobType.values()) result.put(t, 0L);
        for (Object[] row : profiles.countByJobType()) result.put((JobType) row[0], (Long) row[1]);
        return result;
    }
}
