package University.exam.service;

import University.exam.Entity.Paper;
import University.exam.Entity.Question;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;

@Service
public class PaperParsingService {

    public List<Question> parsePaper(File file, Paper paper) throws IOException {
        String text = "";
        System.out.println("Attempting to parse file: " + file.getName());
        
        if (file.getName().toLowerCase().endsWith(".pdf")) {
            text = extractTextFromPdf(file);
            System.out.println("Extracted PDF Text Length: " + text.length());
        } else if (file.getName().toLowerCase().endsWith(".docx")) {
            System.out.println("Word (.docx) file detected. Extracting text...");
            text = extractTextFromWord(file);
            System.out.println("Extracted Word Text Length: " + text.length());
        } else if (file.getName().toLowerCase().endsWith(".doc")) {
            System.out.println("Legacy Word (.doc) file detected. Converting to fallback.");
            text = ""; 
        }

        if (text == null || text.trim().isEmpty()) {
            System.out.println("WARNING: No text extracted from file.");
            return new ArrayList<>();
        }

        List<Question> questions = structureQuestions(text, paper);
        System.out.println("Parsed Questions Count: " + questions.size());
        return questions;
    }

    private String extractTextFromPdf(File file) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractTextFromWord(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (Exception e) {
            System.err.println("Error extracting from Word document: " + e.getMessage());
            return "";
        }
    }

    private List<Question> structureQuestions(String text, Paper paper) {
        List<Question> questions = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        
        String currentGroup = "Q1";
        int groupIndex = 1;
        
        // 1. Group Header: Q -1, Q.1, Question 1
        Pattern groupPattern = Pattern.compile("(?i)^(Q|Question)\\s*[\\.\\-]?\\s*(\\d+)");
        // 2. Optional: OR at start
        Pattern orPattern = Pattern.compile("(?i)^(OR)\\b");
        // 3. Marks: (4), [4], 4 Marks, 4M, or just a number at the end
        Pattern marksPattern = Pattern.compile("(.*?)\\s*[\\(\\[]?\\s*(\\d+)\\s*(Marks|Mark|M)?\\s*[\\)\\]]?$");
        // 4. Starts with a number (e.g. 1. Question text...)
        Pattern startNumberPattern = Pattern.compile("^\\d+\\.\\s+.*");

        String[] ignoreKeywords = {
            "instructions", "attempt all questions", "figures on the right", "draw the figures",
            "seatno", "enrolmentno", "university", "subject name", "duration", "total marks"
        };
        
        boolean extractionStarted = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.contains("******")) continue;

            // Check for group header
            Matcher groupMatcher = groupPattern.matcher(trimmed);
            if (groupMatcher.find()) {
                groupIndex = Integer.parseInt(groupMatcher.group(2));
                currentGroup = "Q" + groupIndex;
                extractionStarted = true;
                continue;
            }

            // Rule 1: Ignore everything before the first valid group or valid question
            if (!extractionStarted) {
                if (startNumberPattern.matcher(trimmed).matches() || marksPattern.matcher(trimmed).matches()) {
                    extractionStarted = true;
                } else {
                    continue;
                }
            }

            // Rule 2: Ignore Instruction Lines
            boolean skipLine = false;
            String lowerLine = trimmed.toLowerCase();
            for (String keyword : ignoreKeywords) {
                if (lowerLine.contains(keyword)) {
                    skipLine = true;
                    break;
                }
            }
            if (skipLine) continue;

            // Rule 3: Only extract valid question lines (starts with number, ends with marks, or is OR)
            if (!startNumberPattern.matcher(trimmed).matches() && 
                !marksPattern.matcher(trimmed).matches() && 
                !orPattern.matcher(trimmed).find()) {
                continue;
            }

            String questionText = trimmed;
            boolean isOptional = false;

            // Check for OR
            if (orPattern.matcher(trimmed).find()) {
                isOptional = true;
                questionText = trimmed.replaceAll("(?i)^OR\\s*", "");
            }

            // Extract marks
            double marks = 1.0; 
            Matcher marksMatcher = marksPattern.matcher(questionText);
            if (marksMatcher.matches()) {
                questionText = marksMatcher.group(1).trim();
                marks = Double.parseDouble(marksMatcher.group(2));
            }

            // Basic filter: must have some length and not be just a number or short label
            if (questionText.length() > 3 && !questionText.matches("^\\d+$")) {
                Question q = new Question();
                q.setPaper(paper);
                q.setText(questionText);
                q.setMarks(marks);
                q.setQuestionGroup(currentGroup);
                q.setOptional(isOptional);
                questions.add(q);
            }
        }

        if (!extractionStarted) {
            throw new RuntimeException("Invalid paper format");
        }

        if (questions.isEmpty()) {
            throw new RuntimeException("No questions found");
        }

        return questions;
    }

}
