package University.exam.controller;

import University.exam.Entity.*;
import University.exam.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/student/exam")
public class StudentExamController {

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private ExamAttemptRepository examAttemptRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private University.exam.service.SectionService sectionService;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        // Since we are mocking login during dev, fallback to a mock student if null
        String enrollmentNo = (String) session.getAttribute("loggedInStudent");
        if (enrollmentNo == null) {
            enrollmentNo = "STU123";
            session.setAttribute("loggedInStudent", enrollmentNo);
            
            // Create mock student if doesn't exist
            if (studentRepository.findById(enrollmentNo).isEmpty()) {
                studentRepository.save(new Student(enrollmentNo, "password"));
            }
        }

        // Clear any previous exam session vars
        session.removeAttribute("currentExamId");
        session.removeAttribute("currentExamType");
        session.removeAttribute("currentAttemptId");

        // Instead of returning a missing template, redirect to the smart rules routing
        return "redirect:/student/rules";
    }

    @GetMapping("/rules/{id}")
    public String rules(@PathVariable Long id, HttpSession session, Model model) {
        if (session.getAttribute("loggedInStudent") == null) return "redirect:/login";

        Exam exam = examRepository.findById(id).orElse(null);
        if (exam == null) return "redirect:/student/rules";

        model.addAttribute("exam", exam);
        return "student/rules";
    }

    @GetMapping("/start/{id}")
    public String startExam(@PathVariable Long id, HttpSession session, Model model) {
        String enrollmentNo = (String) session.getAttribute("loggedInStudent");
        if (enrollmentNo == null) return "redirect:/login";

        Exam exam = examRepository.findById(id).orElse(null);
        if (exam == null) return "redirect:/student/rules";

        Student student = studentRepository.findById(enrollmentNo).orElse(null);

        ExamAttempt attempt = examAttemptRepository.findByStudentEnrollmentNoAndExamId(enrollmentNo, id)
                .orElseGet(() -> {
                    ExamAttempt newAttempt = new ExamAttempt();
                    newAttempt.setStudent(student);
                    newAttempt.setExam(exam);
                    newAttempt.setStartTime(LocalDateTime.now());
                    newAttempt.setStatus("Ongoing");
                    return examAttemptRepository.save(newAttempt);
                });

        if ("Submitted".equals(attempt.getStatus())) {
            return "redirect:/student/rules?error=already_submitted";
        }

        model.addAttribute("exam", exam);
        model.addAttribute("student", student);
        model.addAttribute("attemptId", attempt.getId());
        model.addAttribute("type", "exam");
        
        long elapsedSeconds = java.time.Duration.between(attempt.getStartTime(), LocalDateTime.now()).getSeconds();
        long totalSeconds = exam.getDurationMinutes() != null ? exam.getDurationMinutes() * 60 : 3600;
        long remainingSeconds = Math.max(0, totalSeconds - elapsedSeconds);
        
        session.setAttribute("currentExamId", id);
        session.setAttribute("currentExamType", "exam");
        session.setAttribute("currentAttemptId", attempt.getId());

        return "redirect:/student/exam/section/0";
    }

    // View: Rules for PDF-based paper
    @GetMapping("/paper-rules/{id}")
    public String paperRules(@PathVariable Long id, HttpSession session, Model model) {
        if (session.getAttribute("loggedInStudent") == null) return "redirect:/login";

        Paper paper = paperRepository.findById(id).orElse(null);
        if (paper == null) return "redirect:/student/rules";
        
        List<Question> questions = questionRepository.findByPaperId(id);
        if (questions == null || questions.isEmpty()) {
            return "redirect:/student/rules?error=Exam is not available yet";
        }

        model.addAttribute("paper", paper);
        return "student/paper_rules";
    }

    // View: Start PDF-based exam (writing answers while viewing PDF)
    @GetMapping("/confirm-paper/{id}")
    public String confirmPaperExam(@PathVariable Long id, HttpSession session, Model model) {
        String enrollmentNo = (String) session.getAttribute("loggedInStudent");
        if (enrollmentNo == null) return "redirect:/login";

        Paper paper = paperRepository.findById(id).orElse(null);
        if (paper == null) return "redirect:/student/rules";

        Student student = studentRepository.findById(enrollmentNo).orElse(null);

        model.addAttribute("paper", paper);
        model.addAttribute("student", student);
        
        return "student/confirm_paper";
    }

    @GetMapping("/start-paper/{id}")
    public String startPaperExam(@PathVariable Long id, HttpSession session, Model model) {
        String enrollmentNo = (String) session.getAttribute("loggedInStudent");
        if (enrollmentNo == null) return "redirect:/login";

        Paper paper = paperRepository.findById(id).orElse(null);
        if (paper == null) return "redirect:/student/rules";

        Student student = studentRepository.findById(enrollmentNo).orElse(null);

        List<Question> questions = questionRepository.findByPaperId(id);
        if (questions == null || questions.isEmpty()) {
            return "redirect:/student/rules?error=Exam is not available yet";
        }

        // Use specific repository method for better performance
        Submission submission = submissionRepository.findByStudentEnrollmentNoAndPaperId(enrollmentNo, id)
                .orElseGet(() -> {
                    Submission s = new Submission();
                    s.setStudent(student);
                    s.setPaper(paper);
                    s.setSubmittedAt(LocalDateTime.now()); // Acts as startTime here
                    s.setStatus("Ongoing");
                    return submissionRepository.save(s);
                });

        System.out.println("Starting Paper Exam - Student: " + enrollmentNo + ", Paper ID: " + id + ", Submission ID: " + submission.getId());

        if ("Submitted".equals(submission.getStatus())) {
            return "redirect:/student/rules?error=already_submitted";
        }

        model.addAttribute("questions", questions);
        model.addAttribute("paper", paper);
        model.addAttribute("exam", paper); // Use 'exam' key to avoid changing template logic
        model.addAttribute("student", student); // Expose student info to Thymeleaf
        model.addAttribute("attemptId", submission.getId()); // Use 'attemptId' key for template
        model.addAttribute("type", "paper");
        
        long elapsedSeconds = java.time.Duration.between(submission.getSubmittedAt(), LocalDateTime.now()).getSeconds();
        long totalSeconds = (paper.getDurationMinutes() != null ? paper.getDurationMinutes() : 120) * 60;
        long remainingSeconds = Math.max(0, totalSeconds - elapsedSeconds);
        
        session.setAttribute("currentExamId", id);
        session.setAttribute("currentExamType", "paper");
        session.setAttribute("currentAttemptId", submission.getId());

        return "redirect:/student/exam/section/0";
    }

    @GetMapping("/section/{index}")
    public String loadSection(@PathVariable int index, HttpSession session, Model model) {
        String enrollmentNo = (String) session.getAttribute("loggedInStudent");
        if (enrollmentNo == null) return "redirect:/login";

        Long examId = (Long) session.getAttribute("currentExamId");
        String type = (String) session.getAttribute("currentExamType");
        Long attemptId = (Long) session.getAttribute("currentAttemptId");

        if (examId == null || type == null || attemptId == null) {
            return "redirect:/student/rules";
        }

        Student student = studentRepository.findById(enrollmentNo).orElse(null);
        model.addAttribute("student", student);
        model.addAttribute("type", type);
        model.addAttribute("attemptId", attemptId);

        List<University.exam.dto.Section> sections;
        long remainingSeconds = 0;
        
        if ("paper".equals(type)) {
            Paper paper = paperRepository.findById(examId).orElse(null);
            if (paper == null) return "redirect:/student/rules";
            sections = sectionService.getSectionsByPaperId(examId);
            model.addAttribute("exam", paper);
            
            Submission submission = submissionRepository.findById(attemptId).orElse(null);
            if (submission != null) {
                long elapsedSeconds = java.time.Duration.between(submission.getSubmittedAt(), LocalDateTime.now()).getSeconds();
                long totalSeconds = (paper.getDurationMinutes() != null ? paper.getDurationMinutes() : 120) * 60;
                remainingSeconds = Math.max(0, totalSeconds - elapsedSeconds);
            }
        } else {
            Exam exam = examRepository.findById(examId).orElse(null);
            if (exam == null) return "redirect:/student/rules";
            sections = sectionService.getSectionsByExamId(examId);
            model.addAttribute("exam", exam);
            
            ExamAttempt attempt = examAttemptRepository.findById(attemptId).orElse(null);
            if (attempt != null) {
                long elapsedSeconds = java.time.Duration.between(attempt.getStartTime(), LocalDateTime.now()).getSeconds();
                long totalSeconds = exam.getDurationMinutes() != null ? exam.getDurationMinutes() * 60 : 3600;
                remainingSeconds = Math.max(0, totalSeconds - elapsedSeconds);
            }
        }

        if (index < 0 || index >= sections.size()) {
            return "redirect:/student/rules";
        }

        University.exam.dto.Section currentSection = sections.get(index);
        
        java.util.Map<Long, String> savedAnswers = new java.util.HashMap<>();
        for (Question q : currentSection.getQuestions()) {
            if ("paper".equals(type)) {
                answerRepository.findFirstBySubmissionIdAndQuestionIdOrderByUpdatedAtDesc(attemptId, q.getId())
                    .ifPresent(ans -> savedAnswers.put(q.getId(), ans.getStudentAnswer()));
            } else {
                answerRepository.findFirstByExamAttemptIdAndQuestionIdOrderByUpdatedAtDesc(attemptId, q.getId())
                    .ifPresent(ans -> savedAnswers.put(q.getId(), ans.getStudentAnswer()));
            }
        }

        model.addAttribute("remainingSeconds", remainingSeconds);
        model.addAttribute("section", currentSection);
        model.addAttribute("currentIndex", index);
        model.addAttribute("totalPages", sections.size());
        model.addAttribute("savedAnswers", savedAnswers);

        return "student/exam_section";
    }

    @GetMapping("/feedback")
    public String feedbackForm(HttpSession session, Model model) {
        String enrollmentNo = (String) session.getAttribute("loggedInStudent");
        if (enrollmentNo == null) return "redirect:/login";

        Long examId = (Long) session.getAttribute("currentExamId");
        String type = (String) session.getAttribute("currentExamType");

        if (examId == null || type == null) {
            return "redirect:/student/rules";
        }

        // Check if feedback already submitted
        boolean alreadySubmitted = feedbackRepository.findByStudentEnrollmentNoAndExamIdAndExamType(enrollmentNo, examId, type).isPresent();
        if (alreadySubmitted) {
            return "redirect:/student/exam/result-summary";
        }

        Student student = studentRepository.findById(enrollmentNo).orElse(null);
        model.addAttribute("student", student);
        
        String examName = "Theory Examination";
        if ("paper".equals(type)) {
            Paper paper = paperRepository.findById(examId).orElse(null);
            if (paper != null) examName = paper.getSubject();
        } else {
            Exam exam = examRepository.findById(examId).orElse(null);
            if (exam != null) examName = exam.getExamName() != null ? exam.getExamName() : exam.getSubject();
        }
        
        model.addAttribute("examName", examName);
        model.addAttribute("examId", examId);
        model.addAttribute("examType", type);

        return "student/feedback";
    }

    @PostMapping("/feedback")
    public String submitFeedback(
            @RequestParam Integer rating,
            @RequestParam(required = false) String comments,
            @RequestParam(required = false) String systemEasyToUse,
            @RequestParam(required = false) String paperClear,
            @RequestParam(required = false) String technicalIssues,
            HttpSession session) {
            
        String enrollmentNo = (String) session.getAttribute("loggedInStudent");
        if (enrollmentNo == null) return "redirect:/login";

        Long examId = (Long) session.getAttribute("currentExamId");
        String type = (String) session.getAttribute("currentExamType");

        if (examId == null || type == null) {
            return "redirect:/student/rules";
        }

        boolean alreadySubmitted = feedbackRepository.findByStudentEnrollmentNoAndExamIdAndExamType(enrollmentNo, examId, type).isPresent();
        if (alreadySubmitted) {
            return "redirect:/student/exam/result-summary";
        }

        Student student = studentRepository.findById(enrollmentNo).orElse(null);
        if (student != null) {
            Feedback feedback = new Feedback();
            feedback.setStudent(student);
            feedback.setExamId(examId);
            feedback.setExamType(type);
            feedback.setRating(rating);
            feedback.setComments(comments);
            feedback.setSystemEasyToUse(systemEasyToUse);
            feedback.setPaperClear(paperClear);
            feedback.setTechnicalIssues(technicalIssues);
            feedback.setSubmittedAt(java.time.LocalDateTime.now());
            
            feedbackRepository.save(feedback);
        }

        return "redirect:/student/exam/result-summary";
    }

    @GetMapping("/result-summary")
    public String resultSummary(HttpSession session, Model model) {
        String enrollmentNo = (String) session.getAttribute("loggedInStudent");
        if (enrollmentNo == null) return "redirect:/login";

        Long examId = (Long) session.getAttribute("currentExamId");
        String type = (String) session.getAttribute("currentExamType");
        Long attemptId = (Long) session.getAttribute("currentAttemptId");

        if (examId == null || type == null || attemptId == null) {
            return "redirect:/student/rules";
        }

        // Verify feedback is submitted to prevent skipping feedback
        boolean feedbackSubmitted = feedbackRepository.findByStudentEnrollmentNoAndExamIdAndExamType(enrollmentNo, examId, type).isPresent();
        if (!feedbackSubmitted) {
            return "redirect:/student/exam/feedback";
        }

        int totalQuestions = 0;
        int attemptedQuestions = 0;
        
        List<Question> questions = "paper".equals(type) ? questionRepository.findByPaperId(examId) : questionRepository.findByExamId(examId);
        
        if (questions != null) {
            totalQuestions = questions.size();
            for (Question q : questions) {
                Answer ans = null;
                if ("paper".equals(type)) {
                    ans = answerRepository.findFirstBySubmissionIdAndQuestionIdOrderByUpdatedAtDesc(attemptId, q.getId()).orElse(null);
                } else {
                    ans = answerRepository.findFirstByExamAttemptIdAndQuestionIdOrderByUpdatedAtDesc(attemptId, q.getId()).orElse(null);
                }
                
                if (ans != null && ans.getStudentAnswer() != null && !ans.getStudentAnswer().trim().isEmpty()) {
                    attemptedQuestions++;
                }
            }
        }

        model.addAttribute("totalQuestions", totalQuestions);
        model.addAttribute("attemptedQuestions", attemptedQuestions);
        model.addAttribute("unattemptedQuestions", totalQuestions - attemptedQuestions);
        
        return "student/result_summary";
    }
}
