package com.xayone.tesseract.model;

public class ImageData {
    private String imageData;
    private String selectedResourceId;
    public ImageData() {
    }

    public ImageData(String imageData, String selectedResourceId) {
        this.imageData = imageData;
        this.selectedResourceId = selectedResourceId;
    }

    public String getImageData() {
        return imageData;
    }

    public String getSelectedResourceId() {
        return selectedResourceId;
    }

    public void setImageData(String imageData) {
        this.imageData = imageData;
    }

    public void setSelectedResourceId(String selectedResourceId) {
        this.selectedResourceId = selectedResourceId;
    }
}
