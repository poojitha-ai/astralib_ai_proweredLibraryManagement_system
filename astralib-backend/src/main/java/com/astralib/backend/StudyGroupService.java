package com.astralib.backend;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudyGroupService {

    private final StudyGroupRepository studyGroupRepository;

    private final StudyGroupMemberRepository
            studyGroupMemberRepository;

    private final UserRepository userRepository;

    public StudyGroupService(
            StudyGroupRepository studyGroupRepository,
            StudyGroupMemberRepository studyGroupMemberRepository,
            UserRepository userRepository
    ) {
        this.studyGroupRepository =
                studyGroupRepository;

        this.studyGroupMemberRepository =
                studyGroupMemberRepository;

        this.userRepository =
                userRepository;
    }

    // =========================================================
    // LIST GROUPS
    // =========================================================

    public List<Map<String, Object>>
    getAllGroups(Long currentUserId) {

        List<StudyGroup> groups =
                studyGroupRepository
                        .findAllByOrderByCreatedAtDesc();

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (StudyGroup group : groups) {

            boolean privateGroup =
                    "PRIVATE".equalsIgnoreCase(
                            group.getVisibility()
                    );

            boolean member =
                    currentUserId != null
                            &&
                    studyGroupMemberRepository
                            .existsByStudyGroupIdAndUserId(
                                    group.getId(),
                                    currentUserId
                            );

            /*
             * Public groups are discoverable.
             *
             * Private groups are visible only
             * to their members.
             */
            if (
                    !privateGroup
                            ||
                    member
            ) {

                result.add(
                        toGroupResponse(
                                group,
                                currentUserId
                        )
                );
            }
        }

        return result;
    }

    // =========================================================
    // GET ONE GROUP
    // =========================================================

    public Map<String, Object>
    getGroupById(
            Long id,
            Long currentUserId
    ) {

        StudyGroup group =
                findGroup(id);

        /*
         * Protect private classroom details.
         */
        if (
                "PRIVATE".equalsIgnoreCase(
                        group.getVisibility()
                )
        ) {

            boolean member =
                    currentUserId != null
                            &&
                    studyGroupMemberRepository
                            .existsByStudyGroupIdAndUserId(
                                    id,
                                    currentUserId
                            );

            if (!member) {

                throw new IllegalArgumentException(
                        "You do not have access to this private study group"
                );
            }
        }

        return toGroupResponse(
                group,
                currentUserId
        );
    }

    // =========================================================
    // CREATE GROUP
    // =========================================================

    @Transactional
    public Map<String, Object>
    createGroup(
            Long userId,
            Map<String, Object> request
    ) {

        validateUser(userId);

        String name =
                getString(
                        request,
                        "name"
                );

        String academicDomain =
                getString(
                        request,
                        "academicDomain"
                );

        if (name.isBlank()) {

            throw new IllegalArgumentException(
                    "Group name is required"
            );
        }

        if (academicDomain.isBlank()) {

            throw new IllegalArgumentException(
                    "Academic domain is required"
            );
        }

        StudyGroup group =
                new StudyGroup();

        group.setName(name);

        group.setAcademicDomain(
                academicDomain
        );

        group.setTargetMaterials(
                getString(
                        request,
                        "targetMaterials"
                )
        );

        group.setDescription(
                getString(
                        request,
                        "description"
                )
        );

        String visibility =
                getString(
                        request,
                        "visibility"
                );

        if (!visibility.isBlank()) {

            visibility =
                    visibility
                            .trim()
                            .toUpperCase();

            if (
                    !visibility.equals("PUBLIC")
                            &&
                    !visibility.equals("PRIVATE")
            ) {

                throw new IllegalArgumentException(
                        "Visibility must be PUBLIC or PRIVATE"
                );
            }

            group.setVisibility(
                    visibility
            );
        }

        group.setCreatorId(
                userId
        );

        StudyGroup savedGroup =
                studyGroupRepository
                        .save(group);

        /*
         * Creator automatically becomes
         * member #1.
         */
        StudyGroupMember creatorMember =
                new StudyGroupMember();

        creatorMember.setStudyGroupId(
                savedGroup.getId()
        );

        creatorMember.setUserId(
                userId
        );

        studyGroupMemberRepository
                .save(creatorMember);

        return toGroupResponse(
                savedGroup,
                userId
        );
    }

    // =========================================================
    // JOIN PUBLIC GROUP
    // =========================================================

    @Transactional
    public Map<String, Object>
    joinGroup(
            Long groupId,
            Long userId
    ) {

        validateUser(userId);

        StudyGroup group =
                findGroup(groupId);

        if (
                "PRIVATE".equalsIgnoreCase(
                        group.getVisibility()
                )
        ) {

            throw new IllegalArgumentException(
                    "Private groups can only be joined through an invitation"
            );
        }

        boolean alreadyMember =
                studyGroupMemberRepository
                        .existsByStudyGroupIdAndUserId(
                                groupId,
                                userId
                        );

        if (alreadyMember) {

            throw new IllegalArgumentException(
                    "You are already a member of this study group"
            );
        }

        StudyGroupMember member =
                new StudyGroupMember();

        member.setStudyGroupId(
                groupId
        );

        member.setUserId(
                userId
        );

        studyGroupMemberRepository
                .save(member);

        return toGroupResponse(
                group,
                userId
        );
    }

    // =========================================================
    // LEAVE GROUP
    // =========================================================

    @Transactional
    public Map<String, Object>
    leaveGroup(
            Long groupId,
            Long userId
    ) {

        validateUser(userId);

        StudyGroup group =
                findGroup(groupId);

        if (
                group.getCreatorId()
                        .equals(userId)
        ) {

            throw new IllegalArgumentException(
                    "The creator cannot leave their own study group"
            );
        }

        StudyGroupMember member =
                studyGroupMemberRepository
                        .findByStudyGroupIdAndUserId(
                                groupId,
                                userId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "You are not a member of this study group"
                                        )
                        );

        studyGroupMemberRepository
                .delete(member);

        return toGroupResponse(
                group,
                userId
        );
    }

    // =========================================================
    // OWNER INVITES MEMBER
    // =========================================================

    @Transactional
    public Map<String, Object>
    addMember(
            Long groupId,
            Long ownerUserId,
            Map<String, Object> request
    ) {

        validateUser(
                ownerUserId
        );

        StudyGroup group =
                findGroup(
                        groupId
                );

        validateOwner(
                group,
                ownerUserId
        );

        String email =
                getString(
                        request,
                        "email"
                );

        if (email.isBlank()) {

            throw new IllegalArgumentException(
                    "Member email is required"
            );
        }

        User invitedUser =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "No AstraLib user exists with this email"
                                        )
                        );

        Long invitedUserId =
                invitedUser.getId();

        boolean alreadyMember =
                studyGroupMemberRepository
                        .existsByStudyGroupIdAndUserId(
                                groupId,
                                invitedUserId
                        );

        if (alreadyMember) {

            throw new IllegalArgumentException(
                    "This user is already a member of the study group"
            );
        }

        StudyGroupMember membership =
                new StudyGroupMember();

        membership.setStudyGroupId(
                groupId
        );

        membership.setUserId(
                invitedUserId
        );

        StudyGroupMember savedMembership =
                studyGroupMemberRepository
                        .save(
                                membership
                        );

        Map<String, Object> member =
                buildMemberResponse(
                        invitedUser,
                        savedMembership,
                        group
                );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "message",
                "Member added successfully"
        );

        response.put(
                "member",
                member
        );

        response.put(
                "memberCount",
                studyGroupMemberRepository
                        .countByStudyGroupId(
                                groupId
                        )
        );

        return response;
    }

    // =========================================================
    // OWNER REMOVES MEMBER
    // =========================================================

    @Transactional
    public Map<String, Object>
    removeMember(
            Long groupId,
            Long memberUserId,
            Long ownerUserId
    ) {

        validateUser(
                ownerUserId
        );

        StudyGroup group =
                findGroup(
                        groupId
                );

        validateOwner(
                group,
                ownerUserId
        );

        if (
                group.getCreatorId()
                        .equals(
                                memberUserId
                        )
        ) {

            throw new IllegalArgumentException(
                    "The study group owner cannot be removed"
            );
        }

        StudyGroupMember membership =
                studyGroupMemberRepository
                        .findByStudyGroupIdAndUserId(
                                groupId,
                                memberUserId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User is not a member of this study group"
                                        )
                        );

        studyGroupMemberRepository
                .delete(
                        membership
                );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "message",
                "Member removed successfully"
        );

        response.put(
                "removedUserId",
                memberUserId
        );

        response.put(
                "memberCount",
                studyGroupMemberRepository
                        .countByStudyGroupId(
                                groupId
                        )
        );

        return response;
    }

    // =========================================================
    // GROUP MEMBERS
    // =========================================================

    public List<Map<String, Object>>
    getGroupMembers(
            Long groupId,
            Long currentUserId
    ) {

        StudyGroup group =
                findGroup(
                        groupId
                );

        /*
         * Private classroom member list is
         * visible only to its members.
         */
        if (
                "PRIVATE".equalsIgnoreCase(
                        group.getVisibility()
                )
        ) {

            boolean member =
                    studyGroupMemberRepository
                            .existsByStudyGroupIdAndUserId(
                                    groupId,
                                    currentUserId
                            );

            if (!member) {

                throw new IllegalArgumentException(
                        "You do not have access to this private study group"
                );
            }
        }

        List<StudyGroupMember> memberships =
                studyGroupMemberRepository
                        .findByStudyGroupIdOrderByJoinedAtAsc(
                                groupId
                        );

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (
                StudyGroupMember membership :
                        memberships
        ) {

            userRepository
                    .findById(
                            membership.getUserId()
                    )
                    .ifPresent(
                            user ->
                                    result.add(
                                            buildMemberResponse(
                                                    user,
                                                    membership,
                                                    group
                                            )
                                    )
                    );
        }

        return result;
    }

    // =========================================================
    // MY GROUPS
    // =========================================================

    public List<Map<String, Object>>
    getMyGroups(
            Long userId
    ) {

        validateUser(
                userId
        );

        List<StudyGroupMember> memberships =
                studyGroupMemberRepository
                        .findByUserIdOrderByJoinedAtDesc(
                                userId
                        );

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (
                StudyGroupMember membership :
                        memberships
        ) {

            studyGroupRepository
                    .findById(
                            membership
                                    .getStudyGroupId()
                    )
                    .ifPresent(
                            group ->
                                    result.add(
                                            toGroupResponse(
                                                    group,
                                                    userId
                                            )
                                    )
                    );
        }

        return result;
    }

    // =========================================================
    // MEMBER RESPONSE
    // =========================================================

    private Map<String, Object>
    buildMemberResponse(
            User user,
            StudyGroupMember membership,
            StudyGroup group
    ) {

        Map<String, Object> member =
                new LinkedHashMap<>();

        member.put(
                "userId",
                user.getId()
        );

        member.put(
                "name",
                user.getName()
        );

        member.put(
                "email",
                user.getEmail()
        );

        member.put(
                "role",
                user.getRole()
        );

        member.put(
                "joinedAt",
                membership.getJoinedAt()
        );

        member.put(
                "owner",
                group.getCreatorId()
                        .equals(
                                user.getId()
                        )
        );

        return member;
    }

    // =========================================================
    // GROUP RESPONSE
    // =========================================================

    private Map<String, Object>
    toGroupResponse(
            StudyGroup group,
            Long currentUserId
    ) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "id",
                group.getId()
        );

        response.put(
                "name",
                group.getName()
        );

        response.put(
                "academicDomain",
                group.getAcademicDomain()
        );

        response.put(
                "targetMaterials",
                group.getTargetMaterials()
        );

        response.put(
                "description",
                group.getDescription()
        );

        response.put(
                "visibility",
                group.getVisibility()
        );

        response.put(
                "creatorId",
                group.getCreatorId()
        );

        response.put(
                "createdAt",
                group.getCreatedAt()
        );

        long memberCount =
                studyGroupMemberRepository
                        .countByStudyGroupId(
                                group.getId()
                        );

        response.put(
                "memberCount",
                memberCount
        );

        boolean joined =
                currentUserId != null
                        &&
                studyGroupMemberRepository
                        .existsByStudyGroupIdAndUserId(
                                group.getId(),
                                currentUserId
                        );

        response.put(
                "joinedByCurrentUser",
                joined
        );

        boolean creator =
                currentUserId != null
                        &&
                group.getCreatorId()
                        .equals(
                                currentUserId
                        );

        response.put(
                "createdByCurrentUser",
                creator
        );

        return response;
    }

    // =========================================================
    // OWNER VALIDATION
    // =========================================================

    private void validateOwner(
            StudyGroup group,
            Long currentUserId
    ) {

        if (
                !group.getCreatorId()
                        .equals(
                                currentUserId
                        )
        ) {

            throw new IllegalArgumentException(
                    "Only the study group owner can manage members"
            );
        }
    }

    // =========================================================
    // FIND GROUP
    // =========================================================

    private StudyGroup findGroup(
            Long groupId
    ) {

        return studyGroupRepository
                .findById(
                        groupId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Study group not found"
                                )
                );
    }

    // =========================================================
    // USER VALIDATION
    // =========================================================

    private void validateUser(
            Long userId
    ) {

        if (
                userId == null
                        ||
                !userRepository
                        .existsById(
                                userId
                        )
        ) {

            throw new IllegalArgumentException(
                    "Authenticated user not found"
            );
        }
    }

    // =========================================================
    // REQUEST STRING
    // =========================================================

    private String getString(
            Map<String, Object> request,
            String key
    ) {

        Object value =
                request.get(
                        key
                );

        if (
                value == null
        ) {
            return "";
        }

        return value
                .toString()
                .trim();
    }
}