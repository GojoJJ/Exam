package University.exam.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @org.springframework.beans.factory.annotation.Autowired
    private University.exam.repository.PaperRepository paperRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private University.exam.repository.StudentRepository studentRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private University.exam.repository.ExamRepository examRepository;

    @GetMapping("/")
    public String login() {
        return "/auth/student_login";
    }

    @GetMapping("/login")
    public String loginGetFallback() {
        return "redirect:/";
    }

    @org.springframework.web.bind.annotation.PostMapping("/login")
    public String performLogin(String enrollmentNo, String password, jakarta.servlet.http.HttpSession session) {
        // Mock authentication
        session.setAttribute("loggedInStudent", enrollmentNo);
        session.setAttribute("enrollment_no", enrollmentNo);
        
        // Ensure student exists in database (since we bypass dashboard mock logic)
        if (studentRepository.findByEnrollmentNo(enrollmentNo).isEmpty()) {
            studentRepository.save(new University.exam.Entity.Student(enrollmentNo, password));
        }
        
        // Find the latest paper to redirect directly to rules page
        java.util.List<University.exam.Entity.Paper> papers = paperRepository.findAll();
        if (papers != null && !papers.isEmpty()) {
            // Sort to get the latest (highest ID)
            papers.sort((p1, p2) -> p2.getId().compareTo(p1.getId()));
            return "redirect:/student/exam/paper-rules/" + papers.get(0).getId();
        }
        
        // Fallback: If no papers, find the latest traditional exam
        java.util.List<University.exam.Entity.Exam> exams = examRepository.findAll();
        if (exams != null && !exams.isEmpty()) {
            exams.sort((e1, e2) -> e2.getId().compareTo(e1.getId()));
            return "redirect:/student/exam/rules/" + exams.get(0).getId();
        }
        
        // If neither exists, safely go to dashboard to avoid 404
        return "redirect:/student/rules";
    }

 
    @GetMapping("/student/rules")
    public String genericRules(jakarta.servlet.http.HttpSession session, org.springframework.ui.Model model, String error) {
        if (session.getAttribute("loggedInStudent") == null) return "redirect:/";

        // Check if admin has uploaded a paper now (in case student refreshed the page)
        java.util.List<University.exam.Entity.Paper> papers = paperRepository.findAll();
        if (papers != null && !papers.isEmpty()) {
            papers.sort((p1, p2) -> p2.getId().compareTo(p1.getId()));
            return "redirect:/student/exam/paper-rules/" + papers.get(0).getId();
        }

        // Create a mock exam so the rules.html template doesn't crash
        University.exam.Entity.Exam mockExam = new University.exam.Entity.Exam();
        mockExam.setId(0L); // Use 0 to indicate it's a mock
        mockExam.setExamName("Waiting for Exam...");
        mockExam.setSubject("Please wait for the admin to upload the paper.");
        mockExam.setExamDuration(120);
        mockExam.setTotalMarks(100.0);
        
        if (error != null) {
            model.addAttribute("error", error);
        }
        
        model.addAttribute("exam", mockExam);
        model.addAttribute("isFallback", true);
        return "student/rules";
    }

    @GetMapping("/student/rules/start")
    public String startFallbackExam(jakarta.servlet.http.HttpSession session) {
        if (session.getAttribute("loggedInStudent") == null) return "redirect:/";

        // Check if a paper has been uploaded
        java.util.List<University.exam.Entity.Paper> papers = paperRepository.findAll();
        if (papers != null && !papers.isEmpty()) {
            papers.sort((p1, p2) -> p2.getId().compareTo(p1.getId()));
            return "redirect:/student/exam/confirm-paper/" + papers.get(0).getId();
        }

        // If still no paper, redirect back with error
        return "redirect:/student/rules?error=Exam is not available yet. Please wait.";
    }

    @GetMapping("/admin-login")
    public String adminLogin() {
        return "auth/admin_login";
    }

    @org.springframework.web.bind.annotation.PostMapping("/admin-login")
    public String performAdminLogin(String adminName, String password) {
        // Admin authentication logic goes here
        // Redirecting to the correct admin dashboard URL
        return "redirect:/admin/dashboard";
    } 
}