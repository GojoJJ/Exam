package University.exam.controller;

import University.exam.Entity.*;
import University.exam.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
public class StudentExamRestController {

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ExamAttemptRepository examAttemptRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @GetMapping("/exams")
    public ResponseEntity<?> getExams() {
        return ResponseEntity.ok(examRepository.findAll());
    }

    @GetMapping("/exam/{id}")
    public ResponseEntity<?> getExamDetails(@PathVariable Long id) {
        return examRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/exam/{id}/questions")
    public ResponseEntity<?> getShuffledQuestions(@PathVariable Long id, @RequestParam(required = false) String type, HttpSession session) {
        String enrollmentNo = (String) session.getAttribute("loggedInStudent");
        if (enrollmentNo == null) return ResponseEntity.status(401).body("Unauthorized");

        List<Question> questions = "paper".equals(type) ? 
            questionRepository.findByPaperId(id) : questionRepository.findByExamId(id);
        
        System.out.println("API Request - Type: " + type + ", ID: " + id + ", Questions Found: " + (questions != null ? questions.size() : 0));

        if (questions == null || questions.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        long seed = 0;
        if ("paper".equals(type)) {
            Optional<Submission> sub = submissionRepository.findByStudentEnrollmentNoAndPaperId(enrollmentNo, id);
            if (sub.isPresent()) seed = sub.get().getId();
        } else {
            Optional<ExamAttempt> attempt = examAttemptRepository.findByStudentEnrollmentNoAndExamId(enrollmentNo, id);
            if (attempt.isPresent()) seed = attempt.get().getId();
        }

        if (seed != 0) {
            // Group by dynamic questionGroup and preserve order of appearance initially
            Map<String, List<Question>> groupedQuestions = new LinkedHashMap<>();
            
            for (Question q : questions) {
                String group = q.getQuestionGroup() != null ? q.getQuestionGroup() : "Q1";
                groupedQuestions.computeIfAbsent(group, k -> new ArrayList<>()).add(q);
            }
            
            Random rand = new Random(seed);
            List<List<Question>> sections = new ArrayList<>();
            for (List<Question> groupList : groupedQuestions.values()) {
                if (!groupList.isEmpty()) {
                    sections.add(groupList);
                }
            }
            
            // Shuffle sections (blocks) as a whole
            Collections.shuffle(sections, rand);
            
            List<Question> processedQuestions = new ArrayList<>();
            for (List<Question> sectionList : sections) {
                // Keep questions inside section in original order
                processedQuestions.addAll(sectionList);
            }
            questions = processedQuestions;
        } else {
            Collections.shuffle(questions);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Question q : questions) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", q.getId());
            map.put("text", q.getText());
            map.put("marks", q.getMarks());
            map.put("questionGroup", q.getQuestionGroup());
            map.put("isOptional", q.isOptional());
            
            if ("paper".equals(type)) {
                submissionRepository.findByStudentEnrollmentNoAndPaperId(enrollmentNo, id)
                    .flatMap(sub -> answerRepository.findFirstBySubmissionIdAndQuestionIdOrderByUpdatedAtDesc(sub.getId(), q.getId()))
                    .ifPresent(ans -> map.put("savedAnswer", ans.getStudentAnswer()));
            } else {
                examAttemptRepository.findByStudentEnrollmentNoAndExamId(enrollmentNo, id)
                    .flatMap(attempt -> answerRepository.findFirstByExamAttemptIdAndQuestionIdOrderByUpdatedAtDesc(attempt.getId(), q.getId()))
                    .ifPresent(ans -> map.put("savedAnswer", ans.getStudentAnswer()));
            }
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/answer/save")
    public ResponseEntity<?> saveAnswer(@RequestBody Map<String, Object> payload, HttpSession session) {
        String enrollmentNo = (String) session.getAttribute("loggedInStudent");
        if (enrollmentNo == null) return ResponseEntity.status(401).body("Unauthorized");

        Long attemptId = payload.get("attemptId") != null ? Long.valueOf(payload.get("attemptId").toString()) : null;
        Long submissionId = payload.get("submissionId") != null ? Long.valueOf(payload.get("submissionId").toString()) : null;
        Long questionId = payload.get("questionId") != null ? Long.valueOf(payload.get("questionId").toString()) : null;
        String answerText = payload.get("answer") != null ? payload.get("answer").toString() : "";

        Answer answer;
        if (attemptId != null) {
            ExamAttempt attempt = examAttemptRepository.findById(attemptId).orElse(null);
            if (attempt == null || "Submitted".equals(attempt.getStatus())) return ResponseEntity.badRequest().body("Invalid attempt");
            
            Question question = questionRepository.findById(questionId).orElse(null);
            answer = answerRepository.findFirstByExamAttemptIdAndQuestionIdOrderByUpdatedAtDesc(attemptId, questionId)
                    .orElseGet(() -> {
                        Answer a = new Answer();
                        a.setExamAttempt(attempt);
                        a.setQuestion(question);
                        a.setQuestionText(question != null ? question.getText() : "Theory Answer");
                        a.setMaxMarks(question != null ? question.getMarks() : 0.0);
                        return a;
                    });
        } else if (submissionId != null) {
            Submission submission = submissionRepository.findById(submissionId).orElse(null);
            if (submission == null || "Submitted".equals(submission.getStatus())) return ResponseEntity.badRequest().body("Invalid submission");
            
            Question question = questionRepository.findById(questionId).orElse(null);
            answer = answerRepository.findFirstBySubmissionIdAndQuestionIdOrderByUpdatedAtDesc(submissionId, questionId)
                    .orElseGet(() -> {
                        Answer a = new Answer();
                        a.setSubmission(submission);
                        a.setQuestion(question);
                        a.setQuestionText(question != null ? question.getText() : "Paper Answer");
                        a.setMaxMarks(question != null ? question.getMarks() : 0.0);
                        return a;
                    });
        } else {
            return ResponseEntity.badRequest().body("Missing ID");
        }

        answer.setStudentAnswer(answerText);
        answer.setUpdatedAt(LocalDateTime.now());
        answerRepository.save(answer);

        return ResponseEntity.ok(Collections.singletonMap("status", "saved"));
    }

    @PostMapping("/exam/submit")
    public ResponseEntity<?> submitExam(@RequestBody Map<String, Object> payload, HttpSession session) {
        String enrollmentNo = (String) session.getAttribute("loggedInStudent");
        if (enrollmentNo == null) return ResponseEntity.status(401).body("Unauthorized");

        Long attemptId = payload.get("attemptId") != null ? Long.valueOf(payload.get("attemptId").toString()) : null;
        Long submissionId = payload.get("submissionId") != null ? Long.valueOf(payload.get("submissionId").toString()) : null;

        if (attemptId != null) {
            ExamAttempt attempt = examAttemptRepository.findById(attemptId).orElse(null);
            if (attempt != null && !"Submitted".equals(attempt.getStatus())) {
                attempt.setStatus("Submitted");
                attempt.setEndTime(LocalDateTime.now());
                examAttemptRepository.save(attempt);
                return ResponseEntity.ok(Collections.singletonMap("status", "submitted"));
            }
        } else if (submissionId != null) {
            Submission submission = submissionRepository.findById(submissionId).orElse(null);
            if (submission != null && !"Submitted".equals(submission.getStatus())) {
                submission.setStatus("Submitted");
                submission.setSubmittedAt(LocalDateTime.now());
                submissionRepository.save(submission);
                return ResponseEntity.ok(Collections.singletonMap("status", "submitted"));
            }
        }
        
        return ResponseEntity.badRequest().body("Invalid or already submitted");
    }
}
