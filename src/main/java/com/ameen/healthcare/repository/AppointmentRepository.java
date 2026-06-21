package com.ameen.healthcare.repository;

import com.ameen.healthcare.entity.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Data-access for {@link Appointment} booking records. */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Page<Appointment> findByPatientIdOrderByAppointmentDateDescStartTimeDesc(Long patientId, Pageable pageable);

    Page<Appointment> findByDoctorIdOrderByAppointmentDateDescStartTimeDesc(Long doctorId, Pageable pageable);
}
