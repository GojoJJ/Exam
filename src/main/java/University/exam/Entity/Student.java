package University.exam.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "students")
public class Student {
    
    @Id
    private String enrollmentNo;
    private String password;
    
    @Column(nullable = true)
    private String name;
    
    @Column(nullable = true)
    private String semester;
    
    @Column(nullable = true)
    private String division;

    public Student() {}

    public Student(String enrollmentNo, String password) {
        this.enrollmentNo = enrollmentNo;
        this.password = password;
        this.name = "Unknown Student";
        this.semester = "Unknown Semester";
        this.division = "Unknown Division";
    }

    public String getEnrollmentNo() { return enrollmentNo; }
    public void setEnrollmentNo(String enrollmentNo) { this.enrollmentNo = enrollmentNo; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }
}
