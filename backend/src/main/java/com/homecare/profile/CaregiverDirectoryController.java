package com.homecare.profile;

import com.homecare.profile.ProfileDtos.CaregiverCard;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.homecare.review.ReviewRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What care seekers browse: caregivers grouped by the kind of work they do. */
@RestController
@RequestMapping("/api/caregivers")
public class CaregiverDirectoryController {

    private static final int MAX_RESULTS = 50;

    private final CaregiverProfileRepository profiles;
    private final ReviewRepository reviews;

    public CaregiverDirectoryController(CaregiverProfileRepository profiles, ReviewRepository reviews) {
        this.profiles = profiles;
        this.reviews = reviews;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CaregiverCard> list(@RequestParam JobType job) {
        List<Object[]> rows = profiles.findCards(job, PageRequest.of(0, MAX_RESULTS));
        List<Long> ids = rows.stream().map(r -> ((CaregiverProfile) r[0]).getUserId()).toList();

        // one query for all the ratings instead of one per card
        Map<Long, Object[]> ratings = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] r : reviews.summaries(ids)) ratings.put((Long) r[0], r);
        }

        return rows.stream().map(row -> {
            CaregiverProfile p = (CaregiverProfile) row[0];
            Object[] rating = ratings.get(p.getUserId());
            Double average = rating == null ? null : BigDecimal.valueOf((Double) rating[1]).setScale(1, RoundingMode.HALF_UP).doubleValue();
            long count = rating == null ? 0 : (Long) rating[2];
            return new CaregiverCard(p.getUserId(), (String) row[1], p.getAge(), p.getExperienceYears(),
                    p.getQualification(), p.getJobType(), p.getRates(), p.photoUrl(), average, count);
        }).toList();
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
