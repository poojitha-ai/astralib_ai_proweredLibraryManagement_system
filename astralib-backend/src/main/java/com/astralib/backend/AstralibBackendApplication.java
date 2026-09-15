package com.astralib.backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AstralibBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AstralibBackendApplication.class, args);
    }

    @Bean
    public CommandLineRunner initData(UserRepository userRepository, UserService userService, DatasetLoaderService datasetLoaderService) {
        return args -> {
            // Ensure demo user exists
            if (!userRepository.existsByEmail("testuser@astralib.com")) {
                User testUser = new User("Test User", "testuser@astralib.com", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password123"));
                testUser.setRole(Role.USER);
                userRepository.save(testUser);
                System.out.println("Seeded testuser@astralib.com demo account");
            }

            // Ensure demo admin exists
            if (!userRepository.existsByEmail("admin@astralib.com")) {
                User adminUser = new User("Admin User", "admin@astralib.com", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("adminpassword123"));
                adminUser.setRole(Role.ADMIN);
                userRepository.save(adminUser);
                System.out.println("Seeded admin@astralib.com demo account");
            }

            // Update librarian user role
            User librarian = userRepository.findByEmail("librarian@example.com").orElse(null);
            if (librarian != null && librarian.getRole() == Role.USER) {
                userService.updateUserRole(librarian.getId(), Role.LIBRARIAN);
                System.out.println("Updated librarian@example.com to LIBRARIAN role");
            }

            // Update admin user role
            User admin = userRepository.findByEmail("admin@example.com").orElse(null);
            if (admin != null && admin.getRole() == Role.USER) {
                userService.updateUserRole(admin.getId(), Role.ADMIN);
                System.out.println("Updated admin@example.com to ADMIN role");
            }

            // Load datasets
            try {
                datasetLoaderService.loadAllDatasets();
            } catch (Exception e) {
                System.err.println("Dataset loading failed: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }

}
