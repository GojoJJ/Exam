package University.exam.repository;

import University.exam.Entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    long countByStatus(String status);
    Optional<Submission> findByStudentEnrollmentNoAndPaperId(String enrollmentNo, Long paperId);
}
