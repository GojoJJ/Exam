package University.exam.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "results")
public class Result {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "submission_id")
    private Submission submission;
    
    private Double totalMarks;
    private Double maxTotalMarks;
    private LocalDateTime evaluatedAt;

    private String enrollmentNo;
    private String studentName;
    private String subject;
    private String semester;
    private String division;
    private Double obtainedMarks;
    private String status;

    public Result() {}

    public Result(Long id, Submission submission, Double totalMarks, Double maxTotalMarks, LocalDateTime evaluatedAt) {
        this.id = id;
        this.submission = submission;
        this.totalMarks = totalMarks;
        this.maxTotalMarks = maxTotalMarks;
        this.evaluatedAt = evaluatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Submission getSubmission() { return submission; }
    public void setSubmission(Submission submission) { this.submission = submission; }
    public Double getTotalMarks() { return totalMarks; }
    public void setTotalMarks(Double totalMarks) { this.totalMarks = totalMarks; }
    public Double getMaxTotalMarks() { return maxTotalMarks; }
    public void setMaxTotalMarks(Double maxTotalMarks) { this.maxTotalMarks = maxTotalMarks; }
    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    public String getEnrollmentNo() { return enrollmentNo; }
    public void setEnrollmentNo(String enrollmentNo) { this.enrollmentNo = enrollmentNo; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }
    public Double getObtainedMarks() { return obtainedMarks; }
    public void setObtainedMarks(Double obtainedMarks) { this.obtainedMarks = obtainedMarks; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
