package com.ameen.healthcare.repository;

import com.ameen.healthcare.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Data-access for {@link Doctor} professional profiles. */
@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByUserId(Long userId);

    Optional<Doctor> findByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumber(String licenseNumber);

    /**
     * List providers, optionally filtered by (case-insensitive) specialization.
     * A {@code null} filter returns all doctors.
     */
    @Query("""
            SELECT d FROM Doctor d
            WHERE :specialization IS NULL
               OR LOWER(d.specialization) = LOWER(:specialization)
            """)
    List<Doctor> findBySpecializationOptional(@Param("specialization") String specialization);
}
