package com.pocketops.backend.session;

import com.pocketops.backend.user.UserEntity;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {
    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public void recordDevice(UserEntity user, String platform) {
        DeviceEntity device = new DeviceEntity();
        device.setUser(user);
        device.setPlatform(safePlatform(platform));
        deviceRepository.save(device);
    }

    private String safePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return "unknown";
        }
        return platform.trim();
    }
}
