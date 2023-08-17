package com.xayone.tesseract.service;

import com.xayone.tesseract.exception.NotFoundException;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Component
@SuppressWarnings({"unchecked", "unused", "rawtypes"})
public class ImageService {
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private GridFsTemplate template;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private GridFsOperations operations;


    private String collectionName = "draft";

    private Tesseract tesseract;

    public ImageService() {
        tesseract = new Tesseract();
        tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
        tesseract.setPageSegMode(12);
        tesseract.setLanguage("eng+fra");
        tesseract.setOcrEngineMode(1);
    }

    public String performOCR(byte[] imageBytes, String id, String selectedResourceId) throws TesseractException, IOException {
        File tempImageFile = File.createTempFile("temp-image", ".jpg");
        Files.write(tempImageFile.toPath(), imageBytes, StandardOpenOption.CREATE);
        String extractedText = tesseract.doOCR(tempImageFile);
        tempImageFile.delete();

        String draftServiceUrl = "http://localhost:5111";
        ResponseEntity<Map> draftResponse = restTemplate.getForEntity(draftServiceUrl + "/draft/" + id, Map.class);
        Map<String, Object> result = draftResponse.getBody();

        if (result != null) {
            List<Map<String, Object>> documentResources = (List<Map<String, Object>>) result.get("documentResource");

            if (documentResources != null && !documentResources.isEmpty()) {

                String selectedHandler = getSelectedHandler(documentResources, selectedResourceId);
                if (selectedHandler == null) {
                    throw new NotFoundException("FILE_NOT_FOUND");
                }

                String originalextractedText = extractTextFromBase64Pdf(selectedHandler);

                extractedText = cleanText(extractedText);
                originalextractedText = cleanText(originalextractedText);

                System.out.println("Extracted Text:");
                System.out.println(extractedText);


                System.out.println("Original Extracted Text:");
                System.out.println(originalextractedText);


                String comparisonResult = compareAndPrintDifferences(originalextractedText, extractedText);
                return comparisonResult;
            } else {
                throw new NotFoundException("documentResource not found in the result.");
            }
        } else {
            throw new NotFoundException("Result is null.");
        }
    }

    private String getSelectedHandler(List<Map<String, Object>> documentResources, String selectedResourceId) {
        for (Map<String, Object> docResource : documentResources) {
            String resourceId = (String) docResource.get("id");
            if (resourceId != null && resourceId.equals(selectedResourceId)) {
                return (String) docResource.get("handler");
            }
        }
        return null;
    }

    private String compareAndPrintDifferences(String originalText, String extractedText) {
        String[] originalWords = originalText.split("\\s+");
        String[] extractedWords = extractedText.split("\\s+");
        StringBuilder diffDetails = new StringBuilder();
        boolean textsMatch = true;

        int maxLength = Math.min(originalWords.length, extractedWords.length);

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());


        for (int i = 0; i < originalWords.length; i++) {
            final int index = i;
            Runnable task = () -> {
                for (int j = 0; j < extractedWords.length; j++) {
                    if (!originalWords[index].equals(extractedWords[j]) && isMinorTypo(originalWords[index], extractedWords[j])) {
                        synchronized (diffDetails) {
                            diffDetails.append("Minor typo: ").append(originalWords[index]).append(" -> ").append(extractedWords[j]).append("\n");
                        }
                    }
                }
            };
            executor.submit(task);
        }

        executor.shutdown();

        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }


        if (originalWords.length > extractedWords.length) {
            for (int i = maxLength; i < originalWords.length; i++) {
               if (!containsMinorTypo(extractedWords, originalWords[i])) {
                    diffDetails.append("Missing word: ").append(originalWords[i]).append("\n");
                    textsMatch = false;
              }
            }
        } else if (originalWords.length < extractedWords.length) {
            for (int i = maxLength; i < extractedWords.length; i++) {
            if (!containsMinorTypo(originalWords, extractedWords[i])) {
                    diffDetails.append("Extra word: ").append(extractedWords[i]).append("\n");
                   textsMatch = false;
            }
            }
        }

        if (textsMatch) {
            return "Texts match!";
        } else {
            return "Texts don't match!" + diffDetails.toString();
        }
    }

    private boolean containsMinorTypo(String[] words, String word) {
        for (String w : words) {
            if (isMinorTypo(w, word)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMinorTypo(String originalWord, String extractedWord ) {
        int levenshteinDistance = StringUtils.getLevenshteinDistance(originalWord, extractedWord);
        int maxLength = Math.max(originalWord.length(), extractedWord.length());

        int maxAllowedTypos = (int) ( maxLength * 0.1);
        return levenshteinDistance <= maxAllowedTypos;
    }

    private String cleanText(String text) {
        text = text.replaceAll("\\s+", " ");
        text = text.replaceAll("[^a-zA-Z0-9\\s]", "");
        text = text.toLowerCase();
        text = removeIsolatedLetter(text, "a");
        text = text.trim();
        return text;
    }

    public static String removeIsolatedLetter(String text, String letter) {
        String regex = "(?<![\\p{L}])" + Pattern.quote(letter) + "(?![\\p{L}])";
        return text.replaceAll(regex, "");
    }

    public static String extractTextFromBase64Pdf(String base64Pdf) throws IOException {
        byte[] pdfBytes = Base64.getDecoder().decode(base64Pdf);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes);

        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            return textStripper.getText(document);
        }
    }
}
