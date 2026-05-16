package University.exam.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    @org.springframework.beans.factory.annotation.Autowired
    private University.exam.repository.PaperRepository paperRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private University.exam.repository.SubmissionRepository submissionRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private University.exam.repository.AnswerRepository answerRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private University.exam.repository.ResultRepository resultRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private University.exam.repository.QuestionRepository questionRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private University.exam.service.PaperParsingService paperParsingService;

    // Helper method to simulate a logged-in admin
    private void addAdminAttributes(Model model) {
        model.addAttribute("adminName", "Super Admin");
        model.addAttribute("logoUrl", "/images/logo.png");
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        addAdminAttributes(model);
        
        long totalPapers = paperRepository.count();
        long totalSubmissions = submissionRepository.count();
        long pendingEvaluations = submissionRepository.countByStatus("Pending");
        
        model.addAttribute("totalPapers", totalPapers);
        model.addAttribute("totalSubmissions", totalSubmissions);
        model.addAttribute("pendingEvaluations", pendingEvaluations);
        
        return "admin/dashboard";
    }

    @GetMapping("/upload-paper")
    public String uploadPaper(Model model) {
        addAdminAttributes(model);
        return "admin/upload_paper";
    }

    @GetMapping("/submissions")
    public String viewSubmissions(Model model) {
        addAdminAttributes(model);
        java.util.List<University.exam.Entity.Submission> submissions = submissionRepository.findAll();
        model.addAttribute("submissions", submissions);
        return "admin/view_submissions";
    }

    @GetMapping("/evaluate")
    public String evaluatePaper(@org.springframework.web.bind.annotation.RequestParam(value = "id", required = false) Long submissionId, Model model) {
        addAdminAttributes(model);
        if (submissionId != null) {
            University.exam.Entity.Submission submission = submissionRepository.findById(submissionId).orElse(null);
            model.addAttribute("submission", submission);
            if (submission != null) {
                java.util.List<University.exam.Entity.Answer> answers = answerRepository.findBySubmissionId(submissionId);
                model.addAttribute("answers", answers);
            }
        }
        return "admin/evaluate_paper";
    }

    // Handles the form submission for uploading a paper
    @org.springframework.web.bind.annotation.PostMapping("/upload-paper")
    public String handleUploadPaper(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam("subject") String subject,
            @org.springframework.web.bind.annotation.RequestParam("course") String course,
            @org.springframework.web.bind.annotation.RequestParam("semester") String semester) {

        try {
            // Save the file locally to an external directory (fixes Windows path length issues)
            String uploadDir = "C:/uploads/";
            java.io.File dir = new java.io.File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String originalExtension = ".pdf"; // Default fallback
            if (originalFilename != null && originalFilename.lastIndexOf(".") > -1) {
                originalExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            
            // Clean file name to prevent "filename too long" errors
            String safeFilename = java.util.UUID.randomUUID().toString() + originalExtension;
            String filePath = uploadDir + safeFilename;
            
            java.io.File destFile = new java.io.File(filePath);
            file.transferTo(destFile);

            // Save the details to the database
            University.exam.Entity.Paper paper = new University.exam.Entity.Paper();
            paper.setSubject(subject);
            paper.setCourse(course);
            paper.setSemester(semester);
            paper.setFilePath("/uploads/" + safeFilename);
            paper.setUploadedAt(java.time.LocalDateTime.now());
            paper.setDurationMinutes(120); // Default duration
            paper.setTotalMarks(100.0); // Default marks

            paper = paperRepository.save(paper);

            // EXTRACT QUESTIONS FROM PDF/Word
            try {
                java.util.List<University.exam.Entity.Question> questions = paperParsingService.parsePaper(destFile, paper);
                if (!questions.isEmpty()) {
                    // Store in session for preview instead of saving directly
                    jakarta.servlet.http.HttpSession session = ((org.springframework.web.context.request.ServletRequestAttributes) 
                        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest().getSession();
                    session.setAttribute("previewQuestions_" + paper.getId(), questions);
                    System.out.println("Extracted " + questions.size() + " questions. Redirecting to preview...");
                    return "redirect:/admin/paper/" + paper.getId() + "/preview";
                }
            } catch (Exception e) {
                System.err.println("Failed to extract questions: " + e.getMessage());
                return "redirect:/admin/dashboard?error=" + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
            }

        } catch (java.io.IOException e) {
            e.printStackTrace();
            return "redirect:/admin/dashboard?error=" + java.net.URLEncoder.encode("File upload failed", java.nio.charset.StandardCharsets.UTF_8);
        }

        return "redirect:/admin/dashboard";
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/paper/{id}/preview")
    public String previewQuestions(@org.springframework.web.bind.annotation.PathVariable Long id, Model model, jakarta.servlet.http.HttpSession session) {
        addAdminAttributes(model);
        University.exam.Entity.Paper paper = paperRepository.findById(id).orElse(null);
        if (paper == null) return "redirect:/admin/dashboard";

        java.util.List<University.exam.Entity.Question> questions = 
            (java.util.List<University.exam.Entity.Question>) session.getAttribute("previewQuestions_" + id);
        
        if (questions == null) {
            // Already saved or session expired, fetch from DB
            questions = questionRepository.findByPaperId(id); // assuming we can fetch, or just redirect
            if(questions == null || questions.isEmpty()) {
                return "redirect:/admin/dashboard";
            }
        }

        model.addAttribute("paper", paper);
        model.addAttribute("questions", questions);
        return "admin/preview_questions";
    }

    @org.springframework.web.bind.annotation.GetMapping("/paper/{id}/confirm-questions")
    public String confirmQuestionsGetFallback(@org.springframework.web.bind.annotation.PathVariable Long id) {
        return "redirect:/admin/paper/" + id + "/preview";
    }

    @org.springframework.web.bind.annotation.PostMapping("/paper/{id}/confirm-questions")
    public String confirmQuestions(@org.springframework.web.bind.annotation.PathVariable Long id, 
                                 @org.springframework.web.bind.annotation.RequestParam java.util.Map<String, String> formData,
                                 jakarta.servlet.http.HttpSession session) {
        
        University.exam.Entity.Paper paper = paperRepository.findById(id).orElse(null);
        if (paper != null) {
            java.util.List<University.exam.Entity.Question> finalQuestions = new java.util.ArrayList<>();
            
            // Reconstruct questions from form data
            // Form fields will be like: q_0_text, q_0_marks, q_0_group, q_0_optional
            int index = 0;
            while (formData.containsKey("q_" + index + "_text")) {
                University.exam.Entity.Question q = new University.exam.Entity.Question();
                q.setPaper(paper);
                q.setText(formData.get("q_" + index + "_text"));
                q.setMarks(Double.parseDouble(formData.getOrDefault("q_" + index + "_marks", "1.0")));
                q.setQuestionGroup(formData.getOrDefault("q_" + index + "_group", "Q1"));
                q.setOptional(formData.containsKey("q_" + index + "_optional"));
                finalQuestions.add(q);
                index++;
            }
            
            if (!finalQuestions.isEmpty()) {
                questionRepository.saveAll(finalQuestions);
                System.out.println("Confirmed and saved " + finalQuestions.size() + " questions for paper " + id);
            }
            session.removeAttribute("previewQuestions_" + id);
        }
        return "redirect:/admin/dashboard";
    }

    @org.springframework.web.bind.annotation.GetMapping("/submit-evaluation")
    public String submitEvaluationGetFallback() {
        return "redirect:/admin/submissions";
    }

    // Handles the form submission for saving an evaluation
    @org.springframework.web.bind.annotation.PostMapping("/submit-evaluation")
    public String handleSubmitEvaluation() {
        // Logic to save marks and feedback goes here
        // Redirect back to submissions list after successful evaluation
        return "redirect:/admin/submissions";
    }
}
