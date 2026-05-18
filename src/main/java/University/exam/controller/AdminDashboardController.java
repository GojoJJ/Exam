package University.exam.controller;

import University.exam.Entity.Paper;
import University.exam.Entity.Question;
import University.exam.Entity.Submission;
import University.exam.Entity.Answer;
import University.exam.Entity.Result;
import University.exam.Entity.Student;
import University.exam.repository.PaperRepository;
import University.exam.repository.SubmissionRepository;
import University.exam.repository.AnswerRepository;
import University.exam.repository.ResultRepository;
import University.exam.repository.QuestionRepository;
import University.exam.repository.StudentRepository;
import University.exam.service.PaperParsingService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private PaperParsingService paperParsingService;

    @Autowired
    private StudentRepository studentRepository;

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
    public String viewSubmissions(
            @RequestParam(value = "course", required = false) String course,
            @RequestParam(value = "semester", required = false) String semester,
            @RequestParam(value = "division", required = false) String division,
            Model model) {
        addAdminAttributes(model);
        
        List<Submission> allSubmissions = submissionRepository.findAll();
        List<Submission> filteredSubmissions = new ArrayList<>();
        
        for (Submission sub : allSubmissions) {
            boolean matches = true;
            
            if (course != null && !course.trim().isEmpty()) {
                if (sub.getPaper() == null || !course.equalsIgnoreCase(sub.getPaper().getCourse())) {
                    matches = false;
                }
            }
            if (semester != null && !semester.trim().isEmpty()) {
                if (sub.getPaper() == null || !semester.equalsIgnoreCase(sub.getPaper().getSemester())) {
                    matches = false;
                }
            }
            if (division != null && !division.trim().isEmpty()) {
                if (sub.getStudent() == null || !division.equalsIgnoreCase(sub.getStudent().getDivision())) {
                    matches = false;
                }
            }
            
            if (matches) {
                filteredSubmissions.add(sub);
            }
        }
        
        model.addAttribute("submissions", filteredSubmissions);
        model.addAttribute("selectedCourse", course);
        model.addAttribute("selectedSemester", semester);
        model.addAttribute("selectedDivision", division);
        
        return "admin/view_submissions";
    }

    @GetMapping("/evaluate")
    public String evaluatePaper(@RequestParam(value = "id", required = false) Long submissionId, Model model) {
        addAdminAttributes(model);
        if (submissionId != null) {
            Submission submission = submissionRepository.findById(submissionId).orElse(null);
            model.addAttribute("submission", submission);
            if (submission != null) {
                List<Answer> answers = answerRepository.findBySubmissionId(submissionId);
                
                // Sort answers by Question ID in ascending order to prevent shuffling
                if (answers != null) {
                    answers.sort(new Comparator<Answer>() {
                        @Override
                        public int compare(Answer a1, Answer a2) {
                            Long id1 = (a1.getQuestion() != null) ? a1.getQuestion().getId() : 0L;
                            Long id2 = (a2.getQuestion() != null) ? a2.getQuestion().getId() : 0L;
                            return id1.compareTo(id2);
                        }
                    });
                }
                
                // Group answers by question group/section
                Map<String, List<Answer>> groupedAnswers = new LinkedHashMap<>();
                for (Answer ans : answers) {
                    String group = "Q1"; // Default fallback section name
                    if (ans.getQuestion() != null && ans.getQuestion().getQuestionGroup() != null && !ans.getQuestion().getQuestionGroup().isEmpty()) {
                        group = ans.getQuestion().getQuestionGroup();
                    }
                    groupedAnswers.computeIfAbsent(group, k -> new ArrayList<>()).add(ans);
                }
                
                // Sort sections numerically (Q1, Q2, Q3...)
                Map<String, List<Answer>> sortedGroupedAnswers = new TreeMap<>(new Comparator<String>() {
                    @Override
                    public int compare(String o1, String o2) {
                        try {
                            int n1 = Integer.parseInt(o1.replaceAll("\\D+", ""));
                            int n2 = Integer.parseInt(o2.replaceAll("\\D+", ""));
                            return Integer.compare(n1, n2);
                        } catch (Exception e) {
                            return o1.compareTo(o2);
                        }
                    }
                });
                sortedGroupedAnswers.putAll(groupedAnswers);
                
                model.addAttribute("groupedAnswers", sortedGroupedAnswers);
                model.addAttribute("answers", answers);
            }
        }
        return "admin/evaluate_paper";
    }

    @PostMapping("/upload-paper")
    public String handleUploadPaper(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "manualContent", required = false) String manualContent,
            @RequestParam("subject") String subject,
            @RequestParam("course") String course,
            @RequestParam("semester") String semester,
            @RequestParam("duration") Integer duration,
            @RequestParam("totalMarks") Double totalMarks,
            HttpSession session) {

        try {
            Paper paper = new Paper();
            paper.setSubject(subject);  
            paper.setCourse(course);
            paper.setSemester(semester);
            paper.setUploadedAt(LocalDateTime.now());
            paper.setExamDuration(duration);
            paper.setTotalMarks(totalMarks);

            boolean isManual = (manualContent != null && !manualContent.trim().isEmpty());

            if (!isManual && (file == null || file.isEmpty())) {
                return "redirect:/admin/upload-paper?error=" + URLEncoder.encode("Please upload a paper file or enter the paper manually", StandardCharsets.UTF_8);
            }

            if (!isManual) {
                // Save the file locally to an external directory
                String uploadDir = "C:/uploads/";
                File dir = new File(uploadDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                String originalFilename = file.getOriginalFilename();
                String originalExtension = ".pdf"; // Default fallback
                if (originalFilename != null && originalFilename.lastIndexOf(".") > -1) {
                    originalExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }
                
                String safeFilename = UUID.randomUUID().toString() + originalExtension;
                String filePath = uploadDir + safeFilename;
                
                File destFile = new File(filePath);
                file.transferTo(destFile);

                paper.setFilePath("/uploads/" + safeFilename);
                paper = paperRepository.save(paper);

                // EXTRACT QUESTIONS
                try {
                    List<Question> questions = paperParsingService.parsePaper(destFile, paper);
                    if (!questions.isEmpty()) {
                        session.setAttribute("previewQuestions_" + paper.getId(), questions);
                        return "redirect:/admin/paper/" + paper.getId() + "/preview";
                    }
                } catch (Exception e) {
                    String errorMsg = (e.getMessage() != null) ? e.getMessage() : "Extraction failed";
                    return "redirect:/admin/dashboard?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8);
                }
            } else {
                // Manual Entry
                paper.setManualContent(manualContent);
                paper = paperRepository.save(paper);

                // EXTRACT QUESTIONS FROM MANUAL CONTENT
                try {
                    List<Question> questions = paperParsingService.structureQuestions(manualContent, paper);
                    if (!questions.isEmpty()) {
                        session.setAttribute("previewQuestions_" + paper.getId(), questions);
                        return "redirect:/admin/paper/" + paper.getId() + "/preview";
                    }
                } catch (Exception e) {
                    String errorMsg = (e.getMessage() != null) ? e.getMessage() : "Parsing failed";
                    return "redirect:/admin/dashboard?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            return "redirect:/admin/dashboard?error=" + URLEncoder.encode("File upload failed", StandardCharsets.UTF_8);
        }

        return "redirect:/admin/dashboard";
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/paper/{id}/preview")
    public String previewQuestions(@PathVariable Long id, Model model, HttpSession session) {
        addAdminAttributes(model);
        Paper paper = paperRepository.findById(id).orElse(null);
        if (paper == null) return "redirect:/admin/dashboard";

        List<Question> questions = (List<Question>) session.getAttribute("previewQuestions_" + id);
        
        if (questions == null) {
            questions = questionRepository.findByPaperId(id);
            if(questions == null || questions.isEmpty()) {
                return "redirect:/admin/dashboard";
            }
        }

        model.addAttribute("paper", paper);
        model.addAttribute("questions", questions);
        return "admin/preview_questions";
    }

    @GetMapping("/paper/{id}/confirm-questions")
    public String confirmQuestionsGetFallback(@PathVariable Long id) {
        return "redirect:/admin/paper/" + id + "/preview";
    }

    @PostMapping("/paper/{id}/confirm-questions")
    public String confirmQuestions(@PathVariable Long id, 
                                 @RequestParam Map<String, String> formData,
                                 HttpSession session) {
        
        Paper paper = paperRepository.findById(id).orElse(null);
        if (paper != null) {
            List<Question> finalQuestions = new ArrayList<>();
            int index = 0;
            while (formData.containsKey("q_" + index + "_text")) {
                Question q = new Question();
                q.setPaper(paper);
                q.setText(formData.get("q_" + index + "_text"));
                q.setMarks(Double.parseDouble(formData.getOrDefault("q_" + index + "_marks", "1.0")));
                q.setQuestionGroup(formData.getOrDefault("q_" + index + "_group", "Q1"));
                q.setOptional(formData.containsKey("q_" + index + "_optional"));
                q.setPairId(formData.get("q_" + index + "_pair_id"));
                finalQuestions.add(q);
                index++;
            }
            
            if (!finalQuestions.isEmpty()) {
                questionRepository.saveAll(finalQuestions);
            }
            session.removeAttribute("previewQuestions_" + id);
        }
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/submit-evaluation")
    public String submitEvaluationGetFallback() {
        return "redirect:/admin/submissions";
    }

    @PostMapping("/submit-evaluation")
    public String handleSubmitEvaluation(@RequestParam("submissionId") Long submissionId, 
                                       @RequestParam Map<String, String> formData) {
        
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission != null) {
            List<Answer> answers = answerRepository.findBySubmissionId(submissionId);
            double totalObtained = 0;
            double totalMax = 0;
            Set<String> seenPairs = new HashSet<>();

            for (Answer ans : answers) {
                String marksStr = formData.get("marks_" + ans.getId());
                String feedback = formData.get("feedback_" + ans.getId());
                
                if (marksStr != null && !marksStr.trim().isEmpty()) {
                    double marks = Double.parseDouble(marksStr);
                    ans.setMarksObtained(marks);
                    ans.setFeedback(feedback);
                    answerRepository.save(ans);
                    totalObtained += marks;
                }

                Question q = ans.getQuestion();
                if (q != null && q.getPairId() != null && !q.getPairId().isEmpty()) {
                    if (!seenPairs.contains(q.getPairId())) {
                        totalMax += (ans.getMaxMarks() != null ? ans.getMaxMarks() : (q.getMarks() != null ? q.getMarks() : 0));
                        seenPairs.add(q.getPairId());
                    }
                } else {
                    totalMax += (ans.getMaxMarks() != null ? ans.getMaxMarks() : (q != null && q.getMarks() != null ? q.getMarks() : 0));
                }
            }

            double paperMaxMarks = (submission.getPaper() != null && submission.getPaper().getTotalMarks() != null)
                    ? submission.getPaper().getTotalMarks()
                    : totalMax;

            Result result = resultRepository.findBySubmissionId(submissionId)
                    .orElse(new Result());
            
            result.setSubmission(submission);
            result.setTotalMarks(totalObtained);
            result.setMaxTotalMarks(paperMaxMarks);
            result.setEvaluatedAt(LocalDateTime.now());
            
            // Set the new required fields
            if (submission.getStudent() != null) {
                result.setEnrollmentNo(submission.getStudent().getEnrollmentNo());
                result.setStudentName(submission.getStudent().getName());
                result.setSemester(submission.getStudent().getSemester());
                result.setDivision(submission.getStudent().getDivision());
            }
            if (submission.getPaper() != null) {
                result.setSubject(submission.getPaper().getSubject());
                // Fallback for semester if not set in student
                if (result.getSemester() == null || result.getSemester().isEmpty()) {
                    result.setSemester(submission.getPaper().getSemester());
                }
            }
            result.setObtainedMarks(totalObtained);
            
            // Pass/Fail status: Pass if obtained marks >= 40% of paper max marks
            double passThreshold = paperMaxMarks * 0.40;
            if (totalObtained >= passThreshold) { 
                result.setStatus("Pass");
            } else {
                result.setStatus("Fail");
            }
            
            resultRepository.save(result);

            submission.setStatus("Evaluated");
            submissionRepository.save(submission);
        }

        return "redirect:/admin/submissions";
    }

    @GetMapping("/results")
    public String viewResults(
            @RequestParam(value = "division", required = false) String division,
            @RequestParam(value = "semester", required = false) String semester,
            @RequestParam(value = "subject", required = false) String subject,
            Model model) {
        addAdminAttributes(model);
        if (division == null) division = "";
        if (semester == null) semester = "";
        if (subject == null) subject = "";
        
        List<Result> allResults = resultRepository.findAll();
        List<Result> filteredResults = new ArrayList<>();
        
        Result topper = null;
        double maxObtained = -1;
        
        for (Result r : allResults) {
            boolean matches = true;
            
            if (division != null && !division.trim().isEmpty() && !division.equalsIgnoreCase("All")) {
                if (r.getDivision() == null || !division.equalsIgnoreCase(r.getDivision())) {
                    matches = false;
                }
            }
            if (semester != null && !semester.trim().isEmpty() && !semester.equalsIgnoreCase("All")) {
                if (r.getSemester() == null || !semester.equalsIgnoreCase(r.getSemester())) {
                    matches = false;
                }
            }
            if (subject != null && !subject.trim().isEmpty() && !subject.equalsIgnoreCase("All")) {
                if (r.getSubject() == null || !subject.equalsIgnoreCase(r.getSubject())) {
                    matches = false;
                }
            }
            
            if (matches) {
                filteredResults.add(r);
                if (r.getObtainedMarks() != null && r.getObtainedMarks() > maxObtained) {
                    maxObtained = r.getObtainedMarks();
                    topper = r;
                }
            }
        }
        
        // Find distinct subjects, semesters dynamically from all entered papers!
        Set<String> distinctSubjects = new TreeSet<>();
        Set<String> distinctSemesters = new TreeSet<>();
        Set<String> distinctDivisions = new TreeSet<>();
        
        List<Paper> allPapers = paperRepository.findAll();
        for (Paper p : allPapers) {
            if (p.getSubject() != null && !p.getSubject().trim().isEmpty()) {
                distinctSubjects.add(p.getSubject().trim());
            }
            if (p.getSemester() != null && !p.getSemester().trim().isEmpty()) {
                distinctSemesters.add(p.getSemester().trim());
            }
        }
        
        // Find distinct divisions dynamically from all students!
        List<Student> allStudents = studentRepository.findAll();
        for (Student s : allStudents) {
            if (s.getDivision() != null && !s.getDivision().trim().isEmpty()) {
                distinctDivisions.add(s.getDivision().trim());
            }
        }
        if (distinctDivisions.isEmpty()) {
            distinctDivisions.add("A");
            distinctDivisions.add("B");
        }
        
        model.addAttribute("results", filteredResults);
        model.addAttribute("topper", topper);
        model.addAttribute("selectedDivision", division);
        model.addAttribute("selectedSemester", semester);
        model.addAttribute("selectedSubject", subject);
        
        model.addAttribute("distinctSubjects", distinctSubjects);
        model.addAttribute("distinctSemesters", distinctSemesters);
        model.addAttribute("distinctDivisions", distinctDivisions);
        
        System.out.println("Results size: " + filteredResults.size());
        
        return "admin/view_results";
    }

    @GetMapping("/results/pdf")
    public void downloadResultsPdf(
            @RequestParam(value = "division", required = false) String division,
            @RequestParam(value = "semester", required = false) String semester,
            @RequestParam(value = "subject", required = false) String subject,
            HttpServletResponse response) throws IOException {
        
        List<Result> allResults = resultRepository.findAll();
        List<Result> filteredResults = new ArrayList<>();
        Result topper = null;
        double maxObtained = -1;
        
        for (Result r : allResults) {
            boolean matches = true;
            if (division != null && !division.trim().isEmpty()) {
                if (r.getDivision() == null || !division.equalsIgnoreCase(r.getDivision())) matches = false;
            }
            if (semester != null && !semester.trim().isEmpty()) {
                if (r.getSemester() == null || !semester.equalsIgnoreCase(r.getSemester())) matches = false;
            }
            if (subject != null && !subject.trim().isEmpty()) {
                if (r.getSubject() == null || !subject.equalsIgnoreCase(r.getSubject())) matches = false;
            }
            
            if (matches) {
                filteredResults.add(r);
                if (r.getObtainedMarks() != null && r.getObtainedMarks() > maxObtained) {
                    maxObtained = r.getObtainedMarks();
                    topper = r;
                }
            }
        }
        
        response.setContentType("application/pdf");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=results_" + 
                             (division != null ? division : "all") + ".pdf";
        response.setHeader(headerKey, headerValue);
        
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, response.getOutputStream());
            document.open();
            
            // Fonts
            Font fontUniv = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font fontBody = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            
            // University Name
            Paragraph univPara = new Paragraph("UNIVERSITY THEORY EXAMINATION RESULTS", fontUniv);
            univPara.setAlignment(Element.ALIGN_CENTER);
            univPara.setSpacingAfter(20);
            document.add(univPara);
            
            // Meta Info
            Paragraph metaPara = new Paragraph();
            metaPara.setFont(fontBold);
            metaPara.add("Subject: " + (subject != null && !subject.isEmpty() ? subject : "All Subjects") + "\n");
            metaPara.add("Semester: " + (semester != null && !semester.isEmpty() ? semester : "All Semesters") + "\n");
            metaPara.add("Division: " + (division != null && !division.isEmpty() ? division : "All Divisions") + "\n");
            metaPara.setSpacingAfter(15);
            document.add(metaPara);
            
            // Topper Highlight
            if (topper != null) {
                Paragraph topperPara = new Paragraph("🏆 Topper: " + topper.getStudentName() + 
                                                     " (" + topper.getObtainedMarks() + " Marks)", fontBold);
                topperPara.setAlignment(Element.ALIGN_LEFT);
                topperPara.setSpacingAfter(15);
                document.add(topperPara);
            }
            
            // Table
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100f);
            table.setWidths(new float[] {2.5f, 4.0f, 2.0f, 1.5f});
            
            // Table Headers
            PdfPCell cell = new PdfPCell(new Phrase("Enrollment No", fontHeader));
            cell.setPadding(6);
            table.addCell(cell);
            
            cell = new PdfPCell(new Phrase("Student Name", fontHeader));
            cell.setPadding(6);
            table.addCell(cell);
            
            cell = new PdfPCell(new Phrase("Marks", fontHeader));
            cell.setPadding(6);
            table.addCell(cell);
            
            cell = new PdfPCell(new Phrase("Result", fontHeader));
            cell.setPadding(6);
            table.addCell(cell);
            
            // Table Body
            for (Result r : filteredResults) {
                table.addCell(new Phrase(r.getEnrollmentNo() != null ? r.getEnrollmentNo() : "N/A", fontBody));
                table.addCell(new Phrase(r.getStudentName() != null ? r.getStudentName() : "N/A", fontBody));
                table.addCell(new Phrase(r.getObtainedMarks() + " / " + r.getMaxTotalMarks(), fontBody));
                table.addCell(new Phrase(r.getStatus() != null ? r.getStatus() : "N/A", fontBody));
            }
            
            document.add(table);
            
        } catch (DocumentException e) {
            e.printStackTrace();
        } finally {
            document.close();
        }
    }

    @PostMapping("/results/pdf/bulk")
    public void downloadBulkResultsPdf(
            @RequestParam("resultIds") List<Long> resultIds,
            HttpServletResponse response) throws IOException {
        
        List<Result> selectedResults = new ArrayList<>();
        Result topper = null;
        double maxObtained = -1;
        
        for (Long id : resultIds) {
            Optional<Result> oRes = resultRepository.findById(id);
            if (oRes.isPresent()) {
                Result r = oRes.get();
                selectedResults.add(r);
                if (r.getObtainedMarks() != null && r.getObtainedMarks() > maxObtained) {
                    maxObtained = r.getObtainedMarks();
                    topper = r;
                }
            }
        }
        
        response.setContentType("application/pdf");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=bulk_results.pdf";
        response.setHeader(headerKey, headerValue);
        
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, response.getOutputStream());
            document.open();
            
            // Fonts
            Font fontUniv = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font fontBody = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            
            // University Name
            Paragraph univPara = new Paragraph("UNIVERSITY THEORY EXAMINATION RESULTS (BULK EXPORT)", fontUniv);
            univPara.setAlignment(Element.ALIGN_CENTER);
            univPara.setSpacingAfter(20);
            document.add(univPara);
            
            // Meta Info
            Paragraph metaPara = new Paragraph();
            metaPara.setFont(fontBold);
            metaPara.add("Total Selected Students: " + selectedResults.size() + "\n");
            metaPara.setSpacingAfter(15);
            document.add(metaPara);
            
            // Topper Highlight
            if (topper != null) {
                Paragraph topperPara = new Paragraph("🏆 Best Performer (Selected): " + topper.getStudentName() + 
                                                     " (" + topper.getObtainedMarks() + " Marks in " + topper.getSubject() + ")", fontBold);
                topperPara.setAlignment(Element.ALIGN_LEFT);
                topperPara.setSpacingAfter(15);
                document.add(topperPara);
            }
            
            // Table
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100f);
            table.setWidths(new float[] {2.0f, 3.5f, 2.5f, 2.0f, 1.5f});
            
            // Table Headers
            PdfPCell cell = new PdfPCell(new Phrase("Enrollment No", fontHeader));
            cell.setPadding(6);
            table.addCell(cell);
            
            cell = new PdfPCell(new Phrase("Student Name", fontHeader));
            cell.setPadding(6);
            table.addCell(cell);
            
            cell = new PdfPCell(new Phrase("Subject", fontHeader));
            cell.setPadding(6);
            table.addCell(cell);
            
            cell = new PdfPCell(new Phrase("Marks", fontHeader));
            cell.setPadding(6);
            table.addCell(cell);
            
            cell = new PdfPCell(new Phrase("Result", fontHeader));
            cell.setPadding(6);
            table.addCell(cell);
            
            // Table Body
            for (Result r : selectedResults) {
                table.addCell(new Phrase(r.getEnrollmentNo() != null ? r.getEnrollmentNo() : "N/A", fontBody));
                table.addCell(new Phrase(r.getStudentName() != null ? r.getStudentName() : "N/A", fontBody));
                table.addCell(new Phrase(r.getSubject() != null ? r.getSubject() : "N/A", fontBody));
                table.addCell(new Phrase(r.getObtainedMarks() + " / " + r.getMaxTotalMarks(), fontBody));
                table.addCell(new Phrase(r.getStatus() != null ? r.getStatus() : "N/A", fontBody));
            }
            
            document.add(table);
            
        } catch (DocumentException e) {
            e.printStackTrace();
        } finally {
            document.close();
        }
    }
}
