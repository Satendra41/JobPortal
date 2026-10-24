package com.jobportal.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

import com.jobportal.model.Profile;
import com.jobportal.repository.ProfileRepository;

@Service
public class ProfileService {

    private static final long MAX_RESUME_SIZE = 5 * 1024 * 1024;
    private static final int MAX_DOCX_CONTENT_TYPES_SIZE = 64 * 1024;
    private static final int MAX_DOCX_RELATIONSHIPS_SIZE = 64 * 1024;
    private static final int MAX_DOCX_DOCUMENT_SIZE = 16 * 1024 * 1024;
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOCX_MAIN_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";

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
        return profile == null ? null : getResumePath(profile.getResumePath());
    }

    /** Resolves only a single stored filename inside the private upload directory. */
    public Path getResumePath(String storedFilename) {
        if (!StringUtils.hasText(storedFilename)) {
            return null;
        }
        try {
            Path relative = Path.of(storedFilename);
            if (relative.isAbsolute() || relative.getNameCount() != 1) {
                return null;
            }
            Path path = uploadDirectory.resolve(relative).normalize();
            return uploadDirectory.equals(path.getParent()) ? path : null;
        } catch (InvalidPathException ex) {
            return null;
        }
    }

    /** Stores a profile resume, preserving the existing PDF-only behavior. */
    public String storeResume(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select a PDF resume.");
        }
        if (file.getSize() > MAX_RESUME_SIZE) {
            throw new IllegalArgumentException("Resume must be 5 MB or smaller.");
        }
        if (!"pdf".equals(extensionOf(file.getOriginalFilename()))
                || !PDF_CONTENT_TYPE.equalsIgnoreCase(normalizedContentType(file.getContentType()))) {
            throw new IllegalArgumentException("Resume must be a PDF file.");
        }
        return storeValidatedResume(file, "pdf");
    }

    /** Stores an application-specific PDF or DOCX without changing the profile resume. */
    public String storeApplicationResume(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please upload a resume file.");
        }
        if (file.getSize() > MAX_RESUME_SIZE) {
            throw new IllegalArgumentException("Resume must be 5 MB or smaller.");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!"pdf".equals(extension) && !"docx".equals(extension)) {
            throw new IllegalArgumentException("Resume must have a .pdf or .docx extension.");
        }
        String expectedContentType = "pdf".equals(extension) ? PDF_CONTENT_TYPE : DOCX_CONTENT_TYPE;
        if (!expectedContentType.equalsIgnoreCase(normalizedContentType(file.getContentType()))) {
            throw new IllegalArgumentException("Resume content type must match the selected file format.");
        }

        return storeValidatedResume(file, extension);
    }

    private String storeValidatedResume(MultipartFile file, String extension) throws IOException {
        Files.createDirectories(uploadDirectory);
        String storedName = UUID.randomUUID() + "." + extension;
        Path destination = getResumePath(storedName);
        if (destination == null) {
            throw new IllegalArgumentException("Invalid resume filename.");
        }

        Path temporaryFile = Files.createTempFile(uploadDirectory, ".resume-upload-", ".tmp");
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(temporaryFile) == 0) {
                throw new IllegalArgumentException("Please upload a non-empty resume file.");
            }
            if (Files.size(temporaryFile) > MAX_RESUME_SIZE) {
                throw new IllegalArgumentException("Resume must be 5 MB or smaller.");
            }

            if ("pdf".equals(extension)) {
                validatePdf(temporaryFile);
            } else {
                validateDocx(temporaryFile);
            }

            Files.move(temporaryFile, destination);
            return storedName;
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private void validatePdf(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] header = input.readNBytes(5);
            if (header.length < 5 || header[0] != '%' || header[1] != 'P' || header[2] != 'D'
                    || header[3] != 'F' || header[4] != '-') {
                throw new IllegalArgumentException("Resume must be a valid PDF file.");
            }
        }

        long size = Files.size(path);
        int tailLength = (int) Math.min(size, 1024);
        byte[] tail = new byte[tailLength];
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(size - tailLength);
            file.readFully(tail);
        }
        if (!new String(tail, StandardCharsets.ISO_8859_1).contains("%%EOF")) {
            throw new IllegalArgumentException("Resume must be a valid PDF file.");
        }
    }

    private void validateDocx(Path path) {
        try {
            byte[] signature = new byte[4];
            try (InputStream input = Files.newInputStream(path)) {
                if (input.read(signature) != signature.length
                        || signature[0] != 'P' || signature[1] != 'K'
                        || signature[2] != 3 || signature[3] != 4) {
                    throw new IllegalArgumentException("Resume must be a valid DOCX document.");
                }
            }

            try (ZipFile zip = new ZipFile(path.toFile())) {
                ZipEntry contentTypesEntry = requiredEntry(zip, "[Content_Types].xml");
                ZipEntry relationshipsEntry = requiredEntry(zip, "_rels/.rels");
                ZipEntry documentEntry = requiredEntry(zip, "word/document.xml");

                Document contentTypes = parseXml(readZipEntry(zip, contentTypesEntry, MAX_DOCX_CONTENT_TYPES_SIZE));
                Document relationships = parseXml(readZipEntry(zip, relationshipsEntry, MAX_DOCX_RELATIONSHIPS_SIZE));
                Document document = parseXml(readZipEntry(zip, documentEntry, MAX_DOCX_DOCUMENT_SIZE));

                if (!hasWordDocumentContentType(contentTypes)
                        || !hasMainDocumentRelationship(relationships)
                        || !isWordDocument(document)) {
                    throw new IllegalArgumentException("Resume must be a valid DOCX document.");
                }
            }
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            throw new IllegalArgumentException("Resume must be a valid DOCX document.", ex);
        }
    }

    private ZipEntry requiredEntry(ZipFile zip, String name) {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory()) {
            throw new IllegalArgumentException("Resume must be a valid DOCX document.");
        }
        return entry;
    }

    private byte[] readZipEntry(ZipFile zip, ZipEntry entry, int maximumSize)
            throws IOException {
        if (entry.getSize() < 0 || entry.getSize() > maximumSize) {
            throw new IOException("DOCX package part has an invalid size.");
        }
        try (InputStream input = zip.getInputStream(entry);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maximumSize) {
                    throw new IOException("DOCX package part is too large.");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private Document parseXml(byte[] xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private boolean hasWordDocumentContentType(Document contentTypes) {
        NodeList overrides = contentTypes.getElementsByTagNameNS("*", "Override");
        for (int i = 0; i < overrides.getLength(); i++) {
            Element override = (Element) overrides.item(i);
            if ("/word/document.xml".equals(override.getAttribute("PartName"))
                    && DOCX_MAIN_CONTENT_TYPE.equals(override.getAttribute("ContentType"))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMainDocumentRelationship(Document relationships) {
        NodeList items = relationships.getElementsByTagNameNS("*", "Relationship");
        for (int i = 0; i < items.getLength(); i++) {
            Element relationship = (Element) items.item(i);
            String type = relationship.getAttribute("Type");
            if ("word/document.xml".equals(relationship.getAttribute("Target"))
                    && (type.endsWith("/officeDocument")
                            || type.endsWith("/relationships/officeDocument"))) {
                return true;
            }
        }
        return false;
    }

    private boolean isWordDocument(Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"document".equals(root.getLocalName())) {
            return false;
        }
        String namespace = root.getNamespaceURI();
        return "http://schemas.openxmlformats.org/wordprocessingml/2006/main".equals(namespace)
                || "http://purl.oclc.org/ooxml/wordprocessingml/main".equals(namespace);
    }

    private String extensionOf(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        String leafName = originalFilename.replace('\\', '/');
        leafName = leafName.substring(leafName.lastIndexOf('/') + 1);
        int extensionStart = leafName.lastIndexOf('.');
        return extensionStart < 0 ? "" : leafName.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizedContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        int parameterIndex = contentType.indexOf(';');
        return (parameterIndex < 0 ? contentType : contentType.substring(0, parameterIndex)).trim();
    }

    public void deleteResume(String resumePath) throws IOException {
        Path path = getResumePath(resumePath);
        if (path != null) {
            Files.deleteIfExists(path);
        }
    }
}
