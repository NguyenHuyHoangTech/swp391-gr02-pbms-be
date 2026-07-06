package com.pbms.common.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageService() {
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public String storeFile(MultipartFile file) {
        try {
            String originalFileName = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFileName != null && originalFileName.contains(".")) {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }
            String newFileName = UUID.randomUUID().toString() + fileExtension;
            
            Path targetLocation = this.fileStorageLocation.resolve(newFileName);
            file.transferTo(targetLocation.toFile());

            return "/uploads/" + newFileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + file.getOriginalFilename() + ". Please try again!", ex);
        }
    }

    public String storeBase64File(String base64String) {
        if (base64String == null || base64String.trim().isEmpty()) {
            return null;
        }
        
        String[] parts = base64String.split("\\|");
        StringBuilder resultUrls = new StringBuilder();
        
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            
            if (part.startsWith("http") || part.startsWith("/uploads")) {
                resultUrls.append(part).append("|");
                continue;
            }
            
            try {
                String base64Image = part;
                String fileExtension = ".jpg";
                
                if (part.contains(",")) {
                    String[] dataParts = part.split(",");
                    if (dataParts.length > 1) {
                        String prefix = dataParts[0];
                        base64Image = dataParts[1];
                        if (prefix.contains("png")) {
                            fileExtension = ".png";
                        } else if (prefix.contains("jpeg") || prefix.contains("jpg")) {
                            fileExtension = ".jpg";
                        }
                    }
                }
                
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Image);
                String newFileName = UUID.randomUUID().toString() + fileExtension;
                Path targetLocation = this.fileStorageLocation.resolve(newFileName);
                Files.write(targetLocation, decodedBytes);
                
                resultUrls.append("/uploads/").append(newFileName).append("|");
            } catch (Exception ex) {
                // If one fails, log and append the original string (or skip)
                System.err.println("Could not store base64 part: " + ex.getMessage());
                resultUrls.append(part).append("|");
            }
        }
        
        if (resultUrls.length() > 0) {
            resultUrls.setLength(resultUrls.length() - 1); // remove last |
            return resultUrls.toString();
        }
        
        return null;
    }
}

