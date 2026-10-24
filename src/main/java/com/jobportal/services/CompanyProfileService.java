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
        CompanyProfile profile = companyProfileRepository.findByUserId(userId).orElseGet(CompanyProfile::new);
        profile.setUserId(userId);
        profile.setCompanyName(companyProfile.getCompanyName());
        profile.setDescription(companyProfile.getDescription());
        profile.setLocation(companyProfile.getLocation());
        profile.setIndustry(companyProfile.getIndustry());
        profile.setWebsite(companyProfile.getWebsite());
        profile.setCompanySize(companyProfile.getCompanySize());
        profile.setFoundedYear(companyProfile.getFoundedYear());
        profile.setContactEmail(companyProfile.getContactEmail());
        profile.setAddress(companyProfile.getAddress());
        profile.setLogoPath(companyProfile.getLogoPath());

        return companyProfileRepository.save(profile);
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
