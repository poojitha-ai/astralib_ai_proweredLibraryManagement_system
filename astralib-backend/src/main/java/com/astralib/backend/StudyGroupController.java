package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/study-groups")
public class StudyGroupController {

    private final StudyGroupService
            studyGroupService;

    public StudyGroupController(
            StudyGroupService studyGroupService
    ) {

        this.studyGroupService =
                studyGroupService;
    }

    // =========================================================
    // GET ALL GROUPS
    // =========================================================

    @GetMapping
    public ResponseEntity<?> getAllGroups(
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(
                            authentication
                    );

            return ResponseEntity.ok(
                    studyGroupService
                            .getAllGroups(
                                    userId
                            )
            );

        } catch (
                IllegalArgumentException e
        ) {

            return badRequest(e);
        }
    }

    // =========================================================
    // MY GROUPS
    // =========================================================

    @GetMapping("/my-groups")
    public ResponseEntity<?> getMyGroups(
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(
                            authentication
                    );

            return ResponseEntity.ok(
                    studyGroupService
                            .getMyGroups(
                                    userId
                            )
            );

        } catch (
                IllegalArgumentException e
        ) {

            return badRequest(e);
        }
    }

    // =========================================================
    // GET ONE GROUP
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<?> getGroup(
            @PathVariable Long id,
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(
                            authentication
                    );

            return ResponseEntity.ok(
                    studyGroupService
                            .getGroupById(
                                    id,
                                    userId
                            )
            );

        } catch (
                IllegalArgumentException e
        ) {

            return badRequest(e);
        }
    }

    // =========================================================
    // CREATE GROUP
    // =========================================================

    @PostMapping
    public ResponseEntity<?> createGroup(
            Authentication authentication,
            @RequestBody
            Map<String, Object> request
    ) {

        try {

            Long userId =
                    getUserId(
                            authentication
                    );

            return ResponseEntity.ok(
                    studyGroupService
                            .createGroup(
                                    userId,
                                    request
                            )
            );

        } catch (
                IllegalArgumentException e
        ) {

            return badRequest(e);
        }
    }

    // =========================================================
    // JOIN PUBLIC GROUP
    // =========================================================

    @PostMapping("/{id}/join")
    public ResponseEntity<?> joinGroup(
            @PathVariable Long id,
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(
                            authentication
                    );

            Map<String, Object> group =
                    studyGroupService
                            .joinGroup(
                                    id,
                                    userId
                            );

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "message",
                    "Joined study group successfully"
            );

            response.put(
                    "group",
                    group
            );

            return ResponseEntity.ok(
                    response
            );

        } catch (
                IllegalArgumentException e
        ) {

            return badRequest(e);
        }
    }

    // =========================================================
    // LEAVE GROUP
    // =========================================================

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<?> leaveGroup(
            @PathVariable Long id,
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(
                            authentication
                    );

            Map<String, Object> group =
                    studyGroupService
                            .leaveGroup(
                                    id,
                                    userId
                            );

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "message",
                    "Left study group successfully"
            );

            response.put(
                    "group",
                    group
            );

            return ResponseEntity.ok(
                    response
            );

        } catch (
                IllegalArgumentException e
        ) {

            return badRequest(e);
        }
    }

    // =========================================================
    // VIEW MEMBERS
    // =========================================================

    @GetMapping("/{id}/members")
    public ResponseEntity<?> getMembers(
            @PathVariable Long id,
            Authentication authentication
    ) {

        try {

            Long userId =
                    getUserId(
                            authentication
                    );

            return ResponseEntity.ok(
                    studyGroupService
                            .getGroupMembers(
                                    id,
                                    userId
                            )
            );

        } catch (
                IllegalArgumentException e
        ) {

            return badRequest(e);
        }
    }

    // =========================================================
    // OWNER ADDS MEMBER
    // =========================================================

    @PostMapping("/{id}/members")
    public ResponseEntity<?> addMember(
            @PathVariable Long id,
            Authentication authentication,
            @RequestBody
            Map<String, Object> request
    ) {

        try {

            Long ownerUserId =
                    getUserId(
                            authentication
                    );

            return ResponseEntity.ok(
                    studyGroupService
                            .addMember(
                                    id,
                                    ownerUserId,
                                    request
                            )
            );

        } catch (
                IllegalArgumentException e
        ) {

            return badRequest(e);
        }
    }

    // =========================================================
    // OWNER REMOVES MEMBER
    // =========================================================

    @DeleteMapping(
            "/{id}/members/{memberUserId}"
    )
    public ResponseEntity<?> removeMember(
            @PathVariable Long id,
            @PathVariable Long memberUserId,
            Authentication authentication
    ) {

        try {

            Long ownerUserId =
                    getUserId(
                            authentication
                    );

            return ResponseEntity.ok(
                    studyGroupService
                            .removeMember(
                                    id,
                                    memberUserId,
                                    ownerUserId
                            )
            );

        } catch (
                IllegalArgumentException e
        ) {

            return badRequest(e);
        }
    }

    // =========================================================
    // CURRENT USER ID
    // =========================================================

    private Long getUserId(
            Authentication authentication
    ) {

        if (
                authentication == null
                        ||
                authentication
                        .getPrincipal()
                        == null
        ) {

            throw new IllegalArgumentException(
                    "Authenticated user not found"
            );
        }

        Object principal =
                authentication
                        .getPrincipal();

        if (
                principal instanceof Long
        ) {

            return (Long) principal;
        }

        try {

            return Long.valueOf(
                    principal.toString()
            );

        } catch (
                NumberFormatException e
        ) {

            throw new IllegalArgumentException(
                    "Invalid authenticated user"
            );
        }
    }

    // =========================================================
    // ERROR RESPONSE
    // =========================================================

    private ResponseEntity<?>
    badRequest(
            IllegalArgumentException e
    ) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "error",
                e.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(
                        response
                );
    }
}