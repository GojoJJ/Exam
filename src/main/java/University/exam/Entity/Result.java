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
}
