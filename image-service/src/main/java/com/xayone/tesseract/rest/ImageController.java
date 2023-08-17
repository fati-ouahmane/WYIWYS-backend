package com.xayone.tesseract.rest;

import com.xayone.tesseract.model.ImageData;
import com.xayone.tesseract.service.ImageService;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Base64Utils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@SuppressWarnings("rawtypes")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
public class ImageController {

    private final ImageService imageService;

    @Autowired
    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }


    @PostMapping("/verifier/{id}")
    public ResponseEntity<String> uploadImage( @PathVariable String id,@RequestBody ImageData imageData) {
        String base64Image = imageData.getImageData();
        String selectedResourceId = imageData.getSelectedResourceId();

        String draftId = id;
        byte[] imageBytes = Base64Utils.decodeFromString(base64Image);
        try {

            String text = imageService.performOCR(imageBytes, draftId,selectedResourceId);
            return ResponseEntity.status(HttpStatus.OK).body(text);
        } catch (TesseractException e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error occurred during OCR of the image", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
