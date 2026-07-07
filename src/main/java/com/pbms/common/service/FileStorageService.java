package com.pbms.common.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Chức năng của file:
 * FileStorageService là service dùng để lưu file upload vào thư mục uploads của backend PBMS.
 * File này hỗ trợ lưu file dạng MultipartFile và file dạng Base64, sau đó trả về URL để frontend có thể truy cập.
 *
 * Liên quan backend:
 * - WebMvcConfig hoặc WebConfig map đường dẫn /uploads/** tới thư mục uploads trong project.
 * - SecurityConfig cho phép frontend truy cập các file public trong /uploads/**.
 * - MultipartFile thường được dùng khi frontend upload file qua form-data.
 * - Base64 thường được dùng khi frontend gửi ảnh dưới dạng chuỗi trong request body.
 *
 * Pseudo code:
 * 1. Khi service được tạo, xác định thư mục lưu file là uploads.
 * 2. Tạo thư mục uploads nếu thư mục chưa tồn tại.
 * 3. Khi nhận MultipartFile, lấy đuôi file gốc.
 * 4. Tạo tên file mới bằng UUID để tránh trùng tên.
 * 5. Lưu file vào thư mục uploads.
 * 6. Trả về đường dẫn /uploads/{fileName}.
 * 7. Khi nhận chuỗi Base64, tách nhiều file bằng ký tự | nếu có.
 * 8. Nếu dữ liệu đã là URL thì giữ nguyên.
 * 9. Nếu dữ liệu là Base64 thì decode và lưu thành file.
 * 10. Trả về danh sách URL file đã lưu.
 */
@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    /**
     * Khởi tạo vị trí lưu file cho hệ thống.
     * Constructor này tạo thư mục uploads nếu thư mục chưa tồn tại.
     *
     * Pseudo code:
     * 1. Lấy đường dẫn thư mục uploads.
     * 2. Chuyển đường dẫn thành absolute path và normalize.
     * 3. Tạo thư mục uploads nếu chưa tồn tại.
     * 4. Nếu tạo thư mục thất bại thì ném RuntimeException.
     */
    public FileStorageService() {
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    /**
     * Lưu file được upload bằng MultipartFile.
     * Method này tạo tên file mới bằng UUID để tránh bị trùng tên với file cũ.
     *
     * Pseudo code:
     * 1. Lấy tên file gốc từ MultipartFile.
     * 2. Tách phần mở rộng của file nếu có.
     * 3. Tạo tên file mới bằng UUID và phần mở rộng.
     * 4. Tạo đường dẫn đích trong thư mục uploads.
     * 5. Lưu file vào đường dẫn đích.
     * 6. Trả về URL public của file.
     * 7. Nếu lưu file lỗi thì ném RuntimeException.
     */
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

    /**
     * Lưu file từ chuỗi Base64.
     * Method này hỗ trợ một hoặc nhiều ảnh Base64 được nối với nhau bằng ký tự |.
     *
     * Pseudo code:
     * 1. Kiểm tra chuỗi Base64 có rỗng hay không.
     * 2. Tách chuỗi thành nhiều phần bằng ký tự |.
     * 3. Duyệt từng phần dữ liệu.
     * 4. Nếu phần dữ liệu đã là URL thì giữ nguyên.
     * 5. Nếu phần dữ liệu là Base64 thì xác định đuôi file.
     * 6. Decode Base64 thành byte array.
     * 7. Tạo tên file mới bằng UUID.
     * 8. Ghi byte array thành file trong thư mục uploads.
     * 9. Ghép các URL file bằng ký tự |.
     * 10. Trả về chuỗi URL sau khi lưu xong.
     */
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
                System.err.println("Could not store base64 part: " + ex.getMessage());
                resultUrls.append(part).append("|");
            }
        }

        if (resultUrls.length() > 0) {
            resultUrls.setLength(resultUrls.length() - 1);
            return resultUrls.toString();
        }

        return null;
    }
}