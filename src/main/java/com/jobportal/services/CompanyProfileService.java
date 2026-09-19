package com.jobportal.services;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobportal.model.CompanyProfile;
import com.jobportal.repository.CompanyProfileRepository;

@Service
public class CompanyProfileService {

    private final CompanyProfileRepository companyProfileRepository;

    public CompanyProfileService(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    public Optional<CompanyProfile> getProfileForUser(Long userId) {
        return companyProfileRepository.findByUserId(userId);
    }

    public boolean hasProfile(Long userId) {
        return companyProfileRepository.existsByUserId(userId);
    }

    @Transactional
    public CompanyProfile saveProfile(CompanyProfile companyProfile, Long userId) {
        Optional<CompanyProfile> existingProfile =
                companyProfileRepository.findByUserId(userId);

        if (existingProfile.isPresent()) {
            CompanyProfile existing = existingProfile.get();

            existing.setCompanyName(companyProfile.getCompanyName());
            existing.setDescription(companyProfile.getDescription());
            existing.setLocation(companyProfile.getLocation());
            existing.setIndustry(companyProfile.getIndustry());
            existing.setWebsite(companyProfile.getWebsite());
            existing.setCompanySize(companyProfile.getCompanySize());
            existing.setFoundedYear(companyProfile.getFoundedYear());
            existing.setContactEmail(companyProfile.getContactEmail());
            existing.setAddress(companyProfile.getAddress());
            existing.setLogoPath(companyProfile.getLogoPath());

            return companyProfileRepository.save(existing);
        }

        companyProfile.setUserId(userId);

        return companyProfileRepository.save(companyProfile);
    }

    @Transactional
    public boolean deleteProfile(Long userId) {
        Optional<CompanyProfile> existingProfile =
                companyProfileRepository.findByUserId(userId);

        if (existingProfile.isEmpty()) {
            return false;
        }

        companyProfileRepository.delete(existingProfile.get());
        return true;
    }
}