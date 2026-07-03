package com.pbms.modules.system.service;

import com.pbms.modules.system.domain.BuildingProfile;
import com.pbms.modules.system.repository.BuildingProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuildingProfileService {

    private final BuildingProfileRepository repository;

    public BuildingProfileService(BuildingProfileRepository repository) {
        this.repository = repository;
    }

    public BuildingProfile getProfile() {
        return repository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Building profile not found in database"));
    }

    @Transactional
    public BuildingProfile updateProfile(BuildingProfile updateData) {
        BuildingProfile existingProfile = getProfile();
        
        // Singleton constraint: Always strictly use ID 1, ignore any ID from updateData
        existingProfile.setName(updateData.getName());
        existingProfile.setAddress(updateData.getAddress());
        existingProfile.setHotline(updateData.getHotline());
        existingProfile.setOperatingHours(updateData.getOperatingHours());
        existingProfile.setRules(updateData.getRules());
        
        return repository.save(existingProfile);
    }
}

