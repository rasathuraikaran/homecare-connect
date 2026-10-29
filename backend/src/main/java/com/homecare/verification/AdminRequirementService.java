package com.homecare.verification;

import com.homecare.profile.JobType;
import com.homecare.user.User;
import com.homecare.verification.VerificationDtos.Impact;
import com.homecare.verification.VerificationDtos.RequirementChange;
import com.homecare.verification.VerificationDtos.RequirementView;
import com.homecare.verification.VerificationDtos.SetRequirementRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** The admin decides which documents are mandatory for which job. Everything is optional until they say otherwise. */
@Service
public class AdminRequirementService {

    private static final Logger log = LoggerFactory.getLogger(AdminRequirementService.class);

    private final DocumentRules rules;
    private final VerificationService verification;

    public AdminRequirementService(DocumentRules rules, VerificationService verification) {
        this.rules = rules;
        this.verification = verification;
    }

    @Transactional(readOnly = true)
    public List<RequirementView> list() {
        return rules.all().stream()
                .map(r -> new RequirementView(r.job(), r.type(), r.type().label(), r.type().hint(), r.required()))
                .toList();
    }

    /** Shows how many caregivers a change would hide or show, so the admin can think before confirming. */
    @Transactional(readOnly = true)
    public Impact impact(JobType job, DocumentType type, boolean required) {
        checkApplies(job, type);
        return verification.impact(job, type, required);
    }

    /** Saves the setting and, in the same step, hides or shows the caregivers of that job accordingly. */
    @Transactional
    public RequirementChange set(User admin, SetRequirementRequest req) {
        checkApplies(req.job(), req.type());
        rules.set(req.job(), req.type(), req.required(), admin.getId());
        VerificationService.Change change = verification.refreshJob(req.job());
        log.info("Admin {} made {} {} for {}: {} caregivers hidden, {} shown", admin.getId(), req.type(),
                req.required() ? "mandatory" : "optional", req.job(), change.hidden(), change.shown());
        return new RequirementChange(list(), change.hidden(), change.shown());
    }

    private static void checkApplies(JobType job, DocumentType type) {
        if (!DocumentType.applicableTo(job).contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That document does not apply to this job");
        }
    }
}
