package com.distributed.job_finder.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Maps the foreign key back to the companies table using UUIDs
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String title;

    @Column(name = "experience_level")
    private String experienceLevel;

    @Column(name = "employment_type")
    private String employmentType;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @Column(name = "salary_currency")
    private String salaryCurrency;

    private String location;
    
    private String department;

    @Column(name = "description_text", columnDefinition = "TEXT")
    private String descriptionText;

    @Column(name = "apply_url", nullable = false, columnDefinition = "TEXT")
    private String applyUrl;

    @Column(name = "ats_job_id")
    private String atsJobId;

    @Column(name = "fingerprint_hash", nullable = false, unique = true)
    private String fingerprintHash;

    @Enumerated(EnumType.STRING)
    @Column(insertable = false)
    private JobStatus status;

    @Column(name = "posted_at")
    private OffsetDateTime postedAt;

    @Column(name = "last_seen_at", insertable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public enum JobStatus {
        ACTIVE, STALE, EXPIRED, ARCHIVED
    }
}