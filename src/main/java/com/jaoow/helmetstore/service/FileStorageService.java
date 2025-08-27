package com.jaoow.helmetstore.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Serviço para gerenciar upload e armazenamento de arquivos NF-e (PDF e XML)
 */
@Service
@Slf4j
public class FileStorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * Salva um arquivo PDF ou XML relacionado a uma NF-e
     */
    public String storeFile(MultipartFile file, String fileType, Long purchaseOrderId) throws IOException {
        // Validar tipo de arquivo
        if (!isValidFileType(file, fileType)) {
            throw new IllegalArgumentException("Invalid file type. Expected: " + fileType);
        }

        // Criar diretório se não existir
        Path uploadPath = Paths.get(uploadDir, "purchase-orders", purchaseOrderId.toString());
        Files.createDirectories(uploadPath);

        // Gerar nome único do arquivo
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? 
            originalFilename.substring(originalFilename.lastIndexOf('.')) : "";
        String filename = fileType + "_" + UUID.randomUUID() + extension;

        // Salvar arquivo
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        log.info("File saved: {} for purchase order: {}", filePath, purchaseOrderId);
        
        return filePath.toString();
    }

    /**
     * Valida se o arquivo é do tipo esperado (PDF ou XML)
     */
    private boolean isValidFileType(MultipartFile file, String expectedType) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        
        if (filename == null) {
            return false;
        }

        return switch (expectedType.toLowerCase()) {
            case "pdf" -> contentType != null && contentType.equals("application/pdf") && 
                         filename.toLowerCase().endsWith(".pdf");
            case "xml" -> (contentType != null && (contentType.equals("application/xml") || 
                          contentType.equals("text/xml"))) && 
                         filename.toLowerCase().endsWith(".xml");
            default -> false;
        };
    }

    /**
     * Remove um arquivo do sistema
     */
    public void deleteFile(String filePath) {
        try {
            if (filePath != null) {
                Path path = Paths.get(filePath);
                Files.deleteIfExists(path);
                log.info("File deleted: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Error deleting file: {}", filePath, e);
        }
    }
}