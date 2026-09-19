package com.jobportal.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.jobportal.model.Profile;
import com.jobportal.repository.ProfileRepository;

@Service
public class ProfileService {

    private static final long MAX_RESUME_SIZE = 5 * 1024 * 1024;

    private final ProfileRepository profileRepository;
    private final Path uploadDirectory;

    public ProfileService(ProfileRepository profileRepository,
            @Value("${app.upload.dir:uploads/resumes}") String uploadDirectory) {
        this.profileRepository = profileRepository;
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    public Profile getOrCreateProfile(Long userId) {
        return profileRepository.findByUserId(userId).orElseGet(() -> {
            Profile profile = new Profile();
            profile.setUserId(userId);
            return profile;
        });
    }

    public Profile save(Profile profile) {
        return profileRepository.save(profile);
    }

    public Path getResumePath(Profile profile) {
        if (profile == null || !StringUtils.hasText(profile.getResumePath())) {
            return null;
        }
        Path path = uploadDirectory.resolve(profile.getResumePath()).normalize();
        return path.getParent().equals(uploadDirectory) ? path : null;
    }

    public String storeResume(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select a PDF resume.");
        }
        if (file.getSize() > MAX_RESUME_SIZE) {
            throw new IllegalArgumentException("Resume must be 5 MB or smaller.");
        }
        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName) || !originalName.toLowerCase().endsWith(".pdf")
                || !"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new IllegalArgumentException("Resume must be a PDF file.");
        }

        Files.createDirectories(uploadDirectory);
        String storedName = UUID.randomUUID() + ".pdf";
        Path destination = uploadDirectory.resolve(storedName).normalize();
        if (!destination.getParent().equals(uploadDirectory)) {
            throw new IllegalArgumentException("Invalid resume filename.");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(5);
            if (header.length < 5 || header[0] != '%' || header[1] != 'P' || header[2] != 'D'
                    || header[3] != 'F' || header[4] != '-') {
                throw new IllegalArgumentException("Resume must be a valid PDF file.");
            }
        }
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return storedName;
    }

    public void deleteResume(String resumePath) throws IOException {
        Profile profile = new Profile();
        profile.setResumePath(resumePath);
        Path path = getResumePath(profile);
        if (path != null) {
            Files.deleteIfExists(path);
        }
    }
}
