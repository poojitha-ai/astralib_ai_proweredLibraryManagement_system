package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Applies the curated collection manifest. Admin-only by the existing
 * {@code /api/admin/**} security rule.
 */
@RestController
@RequestMapping("/api/admin/catalogue")
public class CatalogueSeedController {

    private final CatalogueSeedService catalogueSeedService;

    public CatalogueSeedController(CatalogueSeedService catalogueSeedService) {
        this.catalogueSeedService = catalogueSeedService;
    }

    @PostMapping("/seed")
    public ResponseEntity<?> seed() {

        try {

            return ResponseEntity.ok(
                    catalogueSeedService.applyManifest()
            );

        } catch (IllegalStateException e) {

            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "error", e.getMessage())
            );

        } catch (Exception e) {

            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "error", e.getMessage())
            );
        }
    }
}
