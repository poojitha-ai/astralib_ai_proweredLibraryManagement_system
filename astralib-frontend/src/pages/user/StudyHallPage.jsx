import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import {
  Users2,
  BookOpen,
  Plus,
  X,
  Loader2,
  AlertCircle,
  Globe2,
  Lock,
  UserPlus,
  LogOut,
  Eye,
  Video,
  CalendarDays,
  Play,
  Square,
  Radio,
} from 'lucide-react';

import {
  Badge,
} from '../../components/common/Components';

import { api } from '../../services/api';

export function StudyHallPage() {
  const navigate = useNavigate();

  const [groups, setGroups] = useState([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [viewMode, setViewMode] = useState('all');

  const [showCreateForm, setShowCreateForm] =
    useState(false);

  const [creating, setCreating] =
    useState(false);

  const [createError, setCreateError] =
    useState('');

  const [actionGroupId, setActionGroupId] =
    useState(null);

  const [showMembersModal, setShowMembersModal] =
    useState(false);

  const [membersGroup, setMembersGroup] =
    useState(null);

  const [members, setMembers] =
    useState([]);

  const [loadingMembers, setLoadingMembers] =
    useState(false);

  const [membersError, setMembersError] =
    useState('');

  const [showAddMemberForm, setShowAddMemberForm] =
    useState(false);

  const [memberEmail, setMemberEmail] =
    useState('');

  const [addingMember, setAddingMember] =
    useState(false);

  const [addMemberError, setAddMemberError] =
    useState('');

  const [addMemberSuccess, setAddMemberSuccess] =
    useState('');

  // =========================================================
  // STUDY SESSION STATE
  // =========================================================

  const [sessionStates, setSessionStates] =
    useState({});

  const [loadingSessionGroupIds, setLoadingSessionGroupIds] =
    useState({});

  const [sessionActionGroupId, setSessionActionGroupId] =
    useState(null);

  const [showSessionModal, setShowSessionModal] =
    useState(false);

  const [sessionModalMode, setSessionModalMode] =
    useState('schedule');

  const [sessionGroup, setSessionGroup] =
    useState(null);

  const [sessionForm, setSessionForm] =
    useState({
      title: '',
      agenda: '',
      scheduledAt: '',
    });

  const [sessionError, setSessionError] =
    useState('');

  const [savingSession, setSavingSession] =
    useState(false);

  const [formData, setFormData] = useState({
    name: '',
    academicDomain: '',
    targetMaterials: '',
    description: '',
    visibility: 'PUBLIC',
  });

  useEffect(() => {
    loadGroups();
  }, [viewMode]);

  // =========================================================
  // LOAD GROUPS
  // =========================================================

  const loadGroups = async () => {
    try {
      setLoading(true);
      setError('');

      let data;

      if (viewMode === 'mine') {
        data =
          await api.studyGroups.getMyGroups();
      } else {
        data =
          await api.studyGroups.getAll();
      }

      const normalizedGroups =
        Array.isArray(data) ? data : [];

      setGroups(normalizedGroups);

      await loadSessionStates(
        normalizedGroups
      );
    } catch (err) {
      console.error(
        'Failed to load study groups:',
        err
      );

      setError(
        err.message ||
          'Unable to load study groups.'
      );
    } finally {
      setLoading(false);
    }
  };

  // =========================================================
  // LOAD SESSION STATUS FOR VISIBLE GROUPS
  // =========================================================

  const loadSessionStates = async (
    groupList
  ) => {
    const eligibleGroups =
      groupList.filter(
        (group) =>
          group.joinedByCurrentUser ||
          group.createdByCurrentUser
      );

    if (eligibleGroups.length === 0) {
      setSessionStates({});
      return;
    }

    const loadingMap = {};

    eligibleGroups.forEach(
      (group) => {
        loadingMap[group.id] = true;
      }
    );

    setLoadingSessionGroupIds(
      loadingMap
    );

    const results =
      await Promise.allSettled(
        eligibleGroups.map(
          async (group) => ({
            groupId: group.id,
            data:
              await api.studySessions.getCurrent(
                group.id
              ),
          })
        )
      );

    const nextStates = {};

    results.forEach(
      (result, index) => {
        const group =
          eligibleGroups[index];

        if (
          result.status ===
          'fulfilled'
        ) {
          nextStates[group.id] =
            result.value.data;
        } else {
          console.error(
            `Failed to load session state for group ${group.id}:`,
            result.reason
          );

          nextStates[group.id] = {
            live: false,
            liveSession: null,
            nextSession: null,
            loadError:
              result.reason?.message ||
              'Unable to load session state.',
          };
        }
      }
    );

    setSessionStates(
      nextStates
    );

    setLoadingSessionGroupIds(
      {}
    );
  };

  const refreshSessionState = async (
    groupId
  ) => {
    try {
      setLoadingSessionGroupIds(
        (current) => ({
          ...current,
          [groupId]: true,
        })
      );

      const data =
        await api.studySessions.getCurrent(
          groupId
        );

      setSessionStates(
        (current) => ({
          ...current,
          [groupId]: data,
        })
      );

      return data;

    } catch (err) {
      console.error(
        'Failed to refresh study session:',
        err
      );

      setSessionStates(
        (current) => ({
          ...current,
          [groupId]: {
            live: false,
            liveSession: null,
            nextSession: null,
            loadError:
              err.message ||
              'Unable to load session state.',
          },
        })
      );

      throw err;

    } finally {
      setLoadingSessionGroupIds(
        (current) => ({
          ...current,
          [groupId]: false,
        })
      );
    }
  };

  // =========================================================
  // CREATE GROUP FORM
  // =========================================================

  const handleInputChange = (e) => {
    const { name, value } = e.target;

    setFormData((current) => ({
      ...current,
      [name]: value,
    }));
  };

  const resetForm = () => {
    setFormData({
      name: '',
      academicDomain: '',
      targetMaterials: '',
      description: '',
      visibility: 'PUBLIC',
    });

    setCreateError('');
  };

  const closeCreateForm = () => {
    setShowCreateForm(false);
    resetForm();
  };

  const handleCreateGroup = async (e) => {
    e.preventDefault();

    if (!formData.name.trim()) {
      setCreateError(
        'Group name is required.'
      );
      return;
    }

    if (!formData.academicDomain.trim()) {
      setCreateError(
        'Academic domain is required.'
      );
      return;
    }

    try {
      setCreating(true);
      setCreateError('');

      await api.studyGroups.create({
        name: formData.name.trim(),

        academicDomain:
          formData.academicDomain.trim(),

        targetMaterials:
          formData.targetMaterials.trim(),

        description:
          formData.description.trim(),

        visibility:
          formData.visibility,
      });

      closeCreateForm();

      await loadGroups();
    } catch (err) {
      console.error(
        'Failed to create study group:',
        err
      );

      setCreateError(
        err.message ||
          'Unable to create study group.'
      );
    } finally {
      setCreating(false);
    }
  };

  // =========================================================
  // JOIN GROUP
  // =========================================================

  const handleJoinGroup = async (group) => {
    try {
      setActionGroupId(group.id);
      setError('');

      await api.studyGroups.join(
        group.id
      );

      await loadGroups();
    } catch (err) {
      console.error(
        'Failed to join group:',
        err
      );

      setError(
        err.message ||
          'Unable to join this study group.'
      );
    } finally {
      setActionGroupId(null);
    }
  };

  // =========================================================
  // LEAVE GROUP
  // =========================================================

  const handleLeaveGroup = async (group) => {
    const confirmed =
      window.confirm(
        `Leave "${group.name}"?`
      );

    if (!confirmed) {
      return;
    }

    try {
      setActionGroupId(group.id);
      setError('');

      await api.studyGroups.leave(
        group.id
      );

      await loadGroups();
    } catch (err) {
      console.error(
        'Failed to leave group:',
        err
      );

      setError(
        err.message ||
          'Unable to leave this study group.'
      );
    } finally {
      setActionGroupId(null);
    }
  };

  // =========================================================
  // MEMBERS
  // =========================================================

  const handleViewMembers = async (group) => {
    try {
      setMembersGroup(group);
      setMembers([]);
      setMembersError('');
      setLoadingMembers(true);
      setShowMembersModal(true);

      const data =
        await api.studyGroups.getMembers(
          group.id
        );

      setMembers(
        Array.isArray(data) ? data : []
      );
    } catch (err) {
      console.error(
        'Failed to load members:',
        err
      );

      setMembersError(
        err.message ||
          'Unable to load group members.'
      );
    } finally {
      setLoadingMembers(false);
    }
  };

  const refreshMembers = async (groupId) => {
    const data =
      await api.studyGroups.getMembers(groupId);

    setMembers(
      Array.isArray(data) ? data : []
    );
  };

  const openAddMemberForm = () => {
    setMemberEmail('');
    setAddMemberError('');
    setAddMemberSuccess('');
    setShowAddMemberForm(true);
  };

  const closeAddMemberForm = () => {
    setShowAddMemberForm(false);
    setMemberEmail('');
    setAddMemberError('');
    setAddMemberSuccess('');
  };

  const handleAddMember = async (e) => {
    e.preventDefault();

    if (!membersGroup?.id) {
      return;
    }

    const email = memberEmail.trim();

    if (!email) {
      setAddMemberError('Email address is required.');
      return;
    }

    try {
      setAddingMember(true);
      setAddMemberError('');
      setAddMemberSuccess('');

      const response = await fetch(
        `/api/study-groups/${membersGroup.id}/members`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization:
              'Bearer ' +
              localStorage.getItem('astralib_token'),
          },
          body: JSON.stringify({ email }),
        }
      );

      const contentType =
        response.headers.get('content-type') || '';

      const data = contentType.includes(
        'application/json'
      )
        ? await response.json()
        : null;

      if (!response.ok) {
        throw new Error(
          data?.error ||
            data?.message ||
            'Unable to add this member.'
        );
      }

      setAddMemberSuccess(
        data?.message || 'Member added successfully.'
      );

      setMemberEmail('');

      await refreshMembers(membersGroup.id);
      await loadGroups();

      setMembersGroup((current) =>
        current
          ? {
              ...current,
              memberCount:
                data?.memberCount ??
                current.memberCount,
            }
          : current
      );
    } catch (err) {
      console.error(
        'Failed to add study group member:',
        err
      );

      setAddMemberError(
        err.message ||
          'Unable to add this member.'
      );
    } finally {
      setAddingMember(false);
    }
  };

  const closeMembersModal = () => {
    setShowMembersModal(false);
    setMembersGroup(null);
    setMembers([]);
    setMembersError('');
    closeAddMemberForm();
  };

  // =========================================================
  // STUDY SESSION MANAGEMENT
  // =========================================================

  const getDefaultSessionDateTime = () => {
    const date =
      new Date(
        Date.now() +
          30 * 60 * 1000
      );

    return new Date(
      date.getTime() -
        date.getTimezoneOffset() *
          60000
    )
      .toISOString()
      .slice(0, 16);
  };

  const openSessionModal = (
    group,
    mode
  ) => {
    setSessionGroup(group);
    setSessionModalMode(mode);
    setSessionError('');

    setSessionForm({
      title:
        mode === 'start-now'
          ? 'Live Study Session'
          : '',
      agenda: '',
      scheduledAt:
        mode === 'schedule'
          ? getDefaultSessionDateTime()
          : '',
    });

    setShowSessionModal(true);
  };

  const closeSessionModal = () => {
    if (savingSession) {
      return;
    }

    setShowSessionModal(false);
    setSessionGroup(null);
    setSessionModalMode(
      'schedule'
    );
    setSessionForm({
      title: '',
      agenda: '',
      scheduledAt: '',
    });
    setSessionError('');
  };

  const handleSessionFormChange = (
    e
  ) => {
    const {
      name,
      value,
    } = e.target;

    setSessionForm(
      (current) => ({
        ...current,
        [name]: value,
      })
    );

    setSessionError('');
  };

  const handleSaveSession = async (
    e
  ) => {
    e.preventDefault();

    if (!sessionGroup?.id) {
      return;
    }

    const title =
      sessionForm.title.trim();

    const agenda =
      sessionForm.agenda.trim();

    if (!title) {
      setSessionError(
        'Session title is required.'
      );
      return;
    }

    if (
      sessionModalMode ===
        'schedule' &&
      !sessionForm.scheduledAt
    ) {
      setSessionError(
        'Session date and time are required.'
      );
      return;
    }

    try {
      setSavingSession(true);
      setSessionError('');

      if (
        sessionModalMode ===
        'schedule'
      ) {
        await api.studySessions.schedule(
          sessionGroup.id,
          {
            title,
            agenda,
            scheduledAt:
              sessionForm.scheduledAt,
          }
        );
      } else {
        await api.studySessions.startNow(
          sessionGroup.id,
          {
            title,
            agenda,
          }
        );
      }

      const groupId =
        sessionGroup.id;

      setShowSessionModal(false);
      setSessionGroup(null);

      await refreshSessionState(
        groupId
      );

    } catch (err) {
      console.error(
        'Failed to save study session:',
        err
      );

      setSessionError(
        err.message ||
          'Unable to save this study session.'
      );

    } finally {
      setSavingSession(false);
    }
  };

  const handleStartScheduledSession =
    async (
      group,
      session
    ) => {
      if (
        !group?.id ||
        !session?.id
      ) {
        return;
      }

      const confirmed =
        window.confirm(
          `Start "${session.title}" now?`
        );

      if (!confirmed) {
        return;
      }

      try {
        setSessionActionGroupId(
          group.id
        );
        setError('');

        await api.studySessions.start(
          session.id
        );

        await refreshSessionState(
          group.id
        );

      } catch (err) {
        console.error(
          'Failed to start scheduled session:',
          err
        );

        setError(
          err.message ||
            'Unable to start this study session.'
        );

      } finally {
        setSessionActionGroupId(
          null
        );
      }
    };

  const handleEndLiveSession =
    async (
      group,
      session
    ) => {
      if (
        !group?.id ||
        !session?.id
      ) {
        return;
      }

      const confirmed =
        window.confirm(
          `End the live session "${session.title}"?`
        );

      if (!confirmed) {
        return;
      }

      try {
        setSessionActionGroupId(
          group.id
        );
        setError('');

        await api.studySessions.end(
          session.id
        );

        await refreshSessionState(
          group.id
        );

      } catch (err) {
        console.error(
          'Failed to end study session:',
          err
        );

        setError(
          err.message ||
            'Unable to end this study session.'
        );

      } finally {
        setSessionActionGroupId(
          null
        );
      }
    };

  const renderSessionStatus = (
    group
  ) => {
    const isMember =
      group.joinedByCurrentUser ||
      group.createdByCurrentUser;

    if (!isMember) {
      return (
        <span
          style={{
            color:
              'var(--text-muted)',
            fontSize: '0.8rem',
          }}
        >
          Join to view sessions
        </span>
      );
    }

    if (
      loadingSessionGroupIds[
        group.id
      ] &&
      !sessionStates[group.id]
    ) {
      return (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.4rem',
            color:
              'var(--text-muted)',
          }}
        >
          <Loader2 size={14} />
          Loading...
        </div>
      );
    }

    const state =
      sessionStates[group.id];

    if (state?.loadError) {
      return (
        <span
          style={{
            color:
              'var(--text-muted)',
            fontSize: '0.8rem',
          }}
        >
          Session status unavailable
        </span>
      );
    }

    if (
      state?.live &&
      state?.liveSession
    ) {
      return (
        <div
          style={{
            minWidth: '220px',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.45rem',
              flexWrap: 'wrap',
              marginBottom: '0.4rem',
            }}
          >
            <Badge variant="success">
              LIVE NOW
            </Badge>

            <Radio size={14} />
          </div>

          <strong
            style={{
              display: 'block',
              fontSize: '0.88rem',
              marginBottom: '0.25rem',
            }}
          >
            {
              state.liveSession
                .title
            }
          </strong>

          <span
            style={{
              display: 'block',
              fontSize: '0.76rem',
              color:
                'var(--text-muted)',
            }}
          >
            Started{' '}
            {formatDate(
              state.liveSession
                .startedAt
            )}
          </span>

          {state.liveSession
            .agenda && (
            <span
              style={{
                display: 'block',
                fontSize: '0.76rem',
                color:
                  'var(--text-muted)',
                marginTop: '0.3rem',
                maxWidth: '260px',
              }}
            >
              {
                state.liveSession
                  .agenda
              }
            </span>
          )}
        </div>
      );
    }

    if (state?.nextSession) {
      return (
        <div
          style={{
            minWidth: '220px',
          }}
        >
          <Badge variant="warning">
            SCHEDULED
          </Badge>

          <strong
            style={{
              display: 'block',
              fontSize: '0.88rem',
              marginTop: '0.4rem',
              marginBottom: '0.25rem',
            }}
          >
            {
              state.nextSession
                .title
            }
          </strong>

          <span
            style={{
              display: 'block',
              fontSize: '0.76rem',
              color:
                'var(--text-muted)',
            }}
          >
            <CalendarDays
              size={13}
              style={{
                verticalAlign:
                  'middle',
                marginRight:
                  '0.3rem',
              }}
            />

            {formatDate(
              state.nextSession
                .scheduledAt
            )}
          </span>

          {state.nextSession
            .agenda && (
            <span
              style={{
                display: 'block',
                fontSize: '0.76rem',
                color:
                  'var(--text-muted)',
                marginTop: '0.3rem',
                maxWidth: '260px',
              }}
            >
              {
                state.nextSession
                  .agenda
              }
            </span>
          )}
        </div>
      );
    }

    return (
      <span
        style={{
          color:
            'var(--text-muted)',
          fontSize: '0.8rem',
        }}
      >
        No session scheduled
      </span>
    );
  };

  // =========================================================
  // DATE FORMAT
  // =========================================================

  const formatDate = (dateValue) => {
    if (!dateValue) {
      return '';
    }

    const date =
      new Date(dateValue);

    if (
      Number.isNaN(
        date.getTime()
      )
    ) {
      return dateValue;
    }

    return date.toLocaleString();
  };

  return (
    <div className="page-container">

      {/* =====================================================
          HEADER
      ====================================================== */}

      <div className="page-header">
        <div className="page-title-group">

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.75rem',
              marginBottom: '0.25rem',
            }}
          >
            <h1>
              Study Hall & Academic Collaboration
            </h1>

            <Badge variant="success">
              Active
            </Badge>
          </div>

          <p>
            Create academic study groups,
            discover collaborative cohorts,
            and organize shared learning
            around AstraLib resources.
          </p>

        </div>
      </div>

      {/* =====================================================
          INTRO BANNER
      ====================================================== */}

      <div
        className="banner"
        style={{
          display: 'flex',
          gap: '0.75rem',
          alignItems: 'flex-start',
          marginBottom: '1.5rem',
        }}
      >
        <Users2 size={18} />

        <div>
          <strong>
            Study Hall is connected.
          </strong>{' '}

          Create public or private research
          groups, join public cohorts and
          collaborate around academic topics.
        </div>
      </div>

      {/* =====================================================
          FEATURE CARDS
      ====================================================== */}

      <div
        style={{
          display: 'grid',

          gridTemplateColumns:
            'repeat(auto-fit, minmax(300px, 1fr))',

          gap: '1.5rem',

          marginBottom: '2rem',
        }}
      >

        {/* CREATE GROUP */}

        <div className="card">

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              marginBottom: '0.75rem',
            }}
          >
            <Users2
              size={20}
              color="var(--primary-ink)"
            />

            <h3 className="card-title">
              Research Cohorts & Reading Groups
            </h3>
          </div>

          <p
            style={{
              fontSize: '0.88rem',
              color: 'var(--text-secondary)',
              lineHeight: '1.6',
            }}
          >
            Create study circles around
            academic disciplines, courses,
            research areas or shared
            reading interests.
          </p>

          <div
            style={{
              marginTop: '1rem',
            }}
          >
            <button
              type="button"
              className="btn btn-primary btn-sm"
              onClick={() =>
                setShowCreateForm(true)
              }
            >
              <Plus size={16} />

              Create Research Group
            </button>
          </div>

        </div>

        {/* SHARED READING */}

        <div className="card">

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              marginBottom: '0.75rem',
            }}
          >
            <BookOpen
              size={20}
              color="var(--primary-ink)"
            />

            <h3 className="card-title">
              Shared Reading Lists
            </h3>
          </div>

          <p
            style={{
              fontSize: '0.88rem',
              color: 'var(--text-secondary)',
              lineHeight: '1.6',
            }}
          >
            Study groups will later be able
            to organize books, papers and
            learning resources into shared
            reading lists.
          </p>

          <div
            style={{
              marginTop: '1rem',
            }}
          >
            <Badge variant="default">
              Next Phase
            </Badge>
          </div>

        </div>

        {/* DISCUSSIONS */}

        <div className="card">

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              marginBottom: '0.75rem',
            }}
          >
            <Users2
              size={20}
              color="var(--primary-ink)"
            />

            <h3 className="card-title">
              Peer Discussions
            </h3>
          </div>

          <p
            style={{
              fontSize: '0.88rem',
              color: 'var(--text-secondary)',
              lineHeight: '1.6',
            }}
          >
            Members will later be able to
            post academic discussions,
            questions and collaborative
            notes inside their groups.
          </p>

          <div
            style={{
              marginTop: '1rem',
            }}
          >
            <Badge variant="default">
              Next Phase
            </Badge>
          </div>

        </div>

      </div>

      {/* =====================================================
          ERROR
      ====================================================== */}

      {error && (
        <div
          className="banner banner-warning"
          style={{
            marginBottom: '1rem',
          }}
        >
          <AlertCircle size={18} />

          <span>
            {error}
          </span>
        </div>
      )}

      {/* =====================================================
          GROUPS
      ====================================================== */}

      <div className="card">

        <div className="card-header">

          <div>
            <h2 className="card-title">
              {viewMode === 'mine'
                ? 'My Study Groups'
                : 'Available Study Groups'}
            </h2>

            <div
              style={{
                marginTop: '0.25rem',
                fontSize: '0.8rem',
                color: 'var(--text-muted)',
              }}
            >
              {viewMode === 'mine'
                ? 'Groups that you currently belong to.'
                : 'Academic cohorts currently registered in AstraLib.'}
            </div>
          </div>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.65rem',
              flexWrap: 'wrap',
            }}
          >

            <button
              type="button"
              className={
                viewMode === 'all'
                  ? 'btn btn-primary btn-sm'
                  : 'btn btn-secondary btn-sm'
              }
              onClick={() =>
                setViewMode('all')
              }
            >
              All Groups
            </button>

            <button
              type="button"
              className={
                viewMode === 'mine'
                  ? 'btn btn-primary btn-sm'
                  : 'btn btn-secondary btn-sm'
              }
              onClick={() =>
                setViewMode('mine')
              }
            >
              My Groups
            </button>

            <Badge variant="default">
              {groups.length}{' '}
              {groups.length === 1
                ? 'Group'
                : 'Groups'}
            </Badge>

          </div>
        </div>

        {/* LOADING */}

        {loading && (
          <div
            style={{
              padding: '2rem',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '0.75rem',
            }}
          >
            <Loader2 size={18} />

            Loading study groups...
          </div>
        )}

        {/* EMPTY */}

        {!loading &&
          groups.length === 0 && (

            <div
              style={{
                padding: '3rem 2rem',
                textAlign: 'center',
              }}
            >
              <Users2
                size={40}
                style={{
                  marginBottom: '1rem',
                }}
              />

              <h3>
                {viewMode === 'mine'
                  ? 'You have not joined any groups'
                  : 'No study groups yet'}
              </h3>

              <p
                style={{
                  color:
                    'var(--text-muted)',
                }}
              >
                {viewMode === 'mine'
                  ? 'Join a public study group or create your own cohort.'
                  : 'Create the first academic collaboration group.'}
              </p>

            </div>
          )}

        {/* TABLE */}

        {!loading &&
          groups.length > 0 && (

            <div className="table-container">

              <table className="formal-table">

                <thead>
                  <tr>
                    <th>
                      Group Name
                    </th>

                    <th>
                      Academic Domain
                    </th>

                    <th>
                      Target Materials
                    </th>

                    <th>
                      Visibility
                    </th>

                    <th>
                      Members
                    </th>

                    <th>
                      Study Session
                    </th>

                    <th>
                      Created
                    </th>

                    <th>
                      Actions
                    </th>
                  </tr>
                </thead>

                <tbody>

                  {groups.map(
                    (group) => (

                      <tr key={group.id}>

                        {/* GROUP */}

                        <td>
                          <strong>
                            {group.name}
                          </strong>

                          {group.description && (

                            <div
                              style={{
                                marginTop:
                                  '0.3rem',

                                fontSize:
                                  '0.78rem',

                                color:
                                  'var(--text-muted)',

                                maxWidth:
                                  '320px',
                              }}
                            >
                              {
                                group.description
                              }
                            </div>
                          )}

                          {group.createdByCurrentUser && (

                            <div
                              style={{
                                marginTop:
                                  '0.45rem',
                              }}
                            >
                              <Badge variant="primary">
                                Created by You
                              </Badge>
                            </div>
                          )}

                        </td>

                        {/* DOMAIN */}

                        <td>
                          {
                            group.academicDomain
                          }
                        </td>

                        {/* MATERIALS */}

                        <td>
                          {group.targetMaterials ||
                            '—'}
                        </td>

                        {/* VISIBILITY */}

                        <td>

                          <div
                            style={{
                              display: 'flex',
                              alignItems:
                                'center',
                              gap: '0.4rem',
                            }}
                          >

                            {group.visibility ===
                            'PRIVATE' ? (

                              <Lock size={14} />

                            ) : (

                              <Globe2
                                size={14}
                              />

                            )}

                            <Badge
                              variant={
                                group.visibility ===
                                'PRIVATE'
                                  ? 'warning'
                                  : 'success'
                              }
                            >
                              {
                                group.visibility
                              }
                            </Badge>

                          </div>

                        </td>

                        {/* MEMBERS */}

                        <td>

                          <div
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: '0.4rem',
                            }}
                          >
                            <Users2 size={15} />

                            <strong>
                              {group.memberCount ?? 0}
                            </strong>
                          </div>

                        </td>

                        {/* STUDY SESSION */}

                        <td>
                          {renderSessionStatus(
                            group
                          )}
                        </td>

                        {/* DATE */}

                        <td>
                          {formatDate(
                            group.createdAt
                          )}
                        </td>

                        {/* ACTIONS */}

                        <td>

                          <div
                            style={{
                              display: 'flex',
                              alignItems:
                                'center',

                              gap: '0.45rem',

                              flexWrap: 'wrap',
                            }}
                          >

                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              onClick={() =>
                                handleViewMembers(
                                  group
                                )
                              }
                            >
                              <Eye size={14} />

                              Members
                            </button>

                            {group.createdByCurrentUser &&
                              group.visibility ===
                                'PRIVATE' && (

                                <button
                                  type="button"
                                  className="btn btn-secondary btn-sm"
                                  onClick={() => {
                                    handleViewMembers(group);
                                    setTimeout(
                                      openAddMemberForm,
                                      0
                                    );
                                  }}
                                >
                                  <UserPlus size={14} />
                                  Add People
                                </button>
                              )}

                            {/* CREATOR */}

                            {group.createdByCurrentUser && (

                              <Badge variant="primary">
                                Owner
                              </Badge>

                            )}

                            {/* OWNER SESSION CONTROLS */}

                            {group.createdByCurrentUser &&
                              !sessionStates[group.id]?.live && (
                                <>
                                  {sessionStates[group.id]
                                    ?.nextSession && (
                                    <button
                                      type="button"
                                      className="btn btn-primary btn-sm"
                                      disabled={
                                        sessionActionGroupId ===
                                        group.id
                                      }
                                      onClick={() =>
                                        handleStartScheduledSession(
                                          group,
                                          sessionStates[group.id]
                                            .nextSession
                                        )
                                      }
                                    >
                                      {sessionActionGroupId ===
                                      group.id ? (
                                        <Loader2 size={14} />
                                      ) : (
                                        <Play size={14} />
                                      )}
                                      Start Session
                                    </button>
                                  )}

                                  <button
                                    type="button"
                                    className="btn btn-secondary btn-sm"
                                    onClick={() =>
                                      openSessionModal(
                                        group,
                                        'schedule'
                                      )
                                    }
                                  >
                                    <CalendarDays size={14} />
                                    Schedule
                                  </button>

                                  <button
                                    type="button"
                                    className="btn btn-secondary btn-sm"
                                    onClick={() =>
                                      openSessionModal(
                                        group,
                                        'start-now'
                                      )
                                    }
                                  >
                                    <Play size={14} />
                                    Start Now
                                  </button>
                                </>
                              )}

                            {group.createdByCurrentUser &&
                              sessionStates[group.id]?.live &&
                              sessionStates[group.id]
                                ?.liveSession && (
                                <button
                                  type="button"
                                  className="btn btn-secondary btn-sm"
                                  disabled={
                                    sessionActionGroupId ===
                                    group.id
                                  }
                                  onClick={() =>
                                    handleEndLiveSession(
                                      group,
                                      sessionStates[group.id]
                                        .liveSession
                                    )
                                  }
                                >
                                  {sessionActionGroupId ===
                                  group.id ? (
                                    <Loader2 size={14} />
                                  ) : (
                                    <Square size={14} />
                                  )}
                                  End Session
                                </button>
                              )}

                            {/* ENTER / JOIN STUDY ROOM */}

                            {(group.joinedByCurrentUser ||
                              group.createdByCurrentUser) && (

                              <button
                                type="button"
                                className={
                                  sessionStates[group.id]
                                    ?.live
                                    ? 'btn btn-primary btn-sm'
                                    : 'btn btn-secondary btn-sm'
                                }
                                onClick={() =>
                                  navigate(
                                    `/study-hall/${group.id}/room`
                                  )
                                }
                              >
                                <Video size={14} />

                                {sessionStates[group.id]
                                  ?.live
                                  ? 'Join Live Room'
                                  : 'Open Study Room'}
                              </button>

                            )}

                            {/* JOIN */}

                            {!group.createdByCurrentUser &&
                              !group.joinedByCurrentUser &&
                              group.visibility ===
                                'PUBLIC' && (

                                <button
                                  type="button"
                                  className="btn btn-primary btn-sm"

                                  disabled={
                                    actionGroupId ===
                                    group.id
                                  }

                                  onClick={() =>
                                    handleJoinGroup(
                                      group
                                    )
                                  }
                                >

                                  {actionGroupId ===
                                  group.id ? (

                                    <Loader2
                                      size={14}
                                    />

                                  ) : (

                                    <UserPlus
                                      size={14}
                                    />

                                  )}

                                  Join
                                </button>
                              )}

                            {/* PRIVATE */}

                            {!group.createdByCurrentUser &&
                              !group.joinedByCurrentUser &&
                              group.visibility ===
                                'PRIVATE' && (

                                <Badge variant="warning">
                                  Invitation Required
                                </Badge>

                              )}

                            {/* LEAVE */}

                            {!group.createdByCurrentUser &&
                              group.joinedByCurrentUser && (

                                <button
                                  type="button"
                                  className="btn btn-secondary btn-sm"

                                  disabled={
                                    actionGroupId ===
                                    group.id
                                  }

                                  onClick={() =>
                                    handleLeaveGroup(
                                      group
                                    )
                                  }
                                >

                                  {actionGroupId ===
                                  group.id ? (

                                    <Loader2
                                      size={14}
                                    />

                                  ) : (

                                    <LogOut
                                      size={14}
                                    />

                                  )}

                                  Leave
                                </button>
                              )}

                          </div>

                        </td>

                      </tr>
                    )
                  )}

                </tbody>

              </table>

            </div>
          )}

      </div>

      {/* =====================================================
          CREATE GROUP MODAL
      ====================================================== */}

      {showCreateForm && (

        <div
          style={{
            position: 'fixed',
            inset: 0,

            background:
              'rgba(0, 0, 0, 0.35)',

            display: 'flex',

            alignItems: 'center',

            justifyContent: 'center',

            padding: '2rem',

            zIndex: 1000,
          }}

          onClick={closeCreateForm}
        >

          <div
            className="card"

            style={{
              width:
                'min(650px, 95vw)',

              maxHeight: '90vh',

              overflowY: 'auto',

              padding: '1.75rem',
            }}

            onClick={(e) =>
              e.stopPropagation()
            }
          >

            <div
              style={{
                display: 'flex',

                justifyContent:
                  'space-between',

                alignItems:
                  'flex-start',

                gap: '1rem',

                marginBottom:
                  '1.5rem',
              }}
            >

              <div>

                <h2
                  style={{
                    margin:
                      '0 0 0.35rem 0',
                  }}
                >
                  Create Research Group
                </h2>

                <p
                  style={{
                    margin: 0,

                    color:
                      'var(--text-muted)',

                    fontSize:
                      '0.9rem',
                  }}
                >
                  Create an academic cohort
                  for collaborative study.
                </p>

              </div>

              <button
                type="button"

                className="btn btn-secondary btn-sm"

                onClick={
                  closeCreateForm
                }
              >
                <X size={16} />
              </button>

            </div>

            {createError && (

              <div
                className="banner banner-warning"

                style={{
                  marginBottom:
                    '1rem',
                }}
              >
                <AlertCircle size={18} />

                <span>
                  {createError}
                </span>
              </div>
            )}

            <form
              onSubmit={
                handleCreateGroup
              }
            >

              <div
                style={{
                  display: 'flex',

                  flexDirection:
                    'column',

                  gap: '1rem',
                }}
              >

                <div>

                  <label>
                    Group Name *
                  </label>

                  <input
                    type="text"

                    name="name"

                    className="form-control"

                    value={
                      formData.name
                    }

                    onChange={
                      handleInputChange
                    }

                    placeholder="Example: AI Research Circle"
                  />

                </div>

                <div>

                  <label>
                    Academic Domain *
                  </label>

                  <input
                    type="text"

                    name="academicDomain"

                    className="form-control"

                    value={
                      formData.academicDomain
                    }

                    onChange={
                      handleInputChange
                    }

                    placeholder="Example: Computer Science"
                  />

                </div>

                <div>

                  <label>
                    Target Materials
                  </label>

                  <input
                    type="text"

                    name="targetMaterials"

                    className="form-control"

                    value={
                      formData.targetMaterials
                    }

                    onChange={
                      handleInputChange
                    }

                    placeholder="Example: RAG, Transformers, LLMs"
                  />

                </div>

                <div>

                  <label>
                    Description
                  </label>

                  <textarea
                    name="description"

                    className="form-control"

                    rows={4}

                    value={
                      formData.description
                    }

                    onChange={
                      handleInputChange
                    }

                    placeholder="Describe the purpose of this study group..."
                  />

                </div>

                <div>

                  <label>
                    Visibility
                  </label>

                  <select
                    name="visibility"

                    className="form-control"

                    value={
                      formData.visibility
                    }

                    onChange={
                      handleInputChange
                    }
                  >

                    <option value="PUBLIC">
                      Public
                    </option>

                    <option value="PRIVATE">
                      Private
                    </option>

                  </select>

                </div>

              </div>

              <div
                style={{
                  marginTop:
                    '1.5rem',

                  display: 'flex',

                  justifyContent:
                    'flex-end',

                  gap: '0.75rem',
                }}
              >

                <button
                  type="button"

                  className="btn btn-secondary"

                  onClick={
                    closeCreateForm
                  }

                  disabled={creating}
                >
                  Cancel
                </button>

                <button
                  type="submit"

                  className="btn btn-primary"

                  disabled={creating}
                >

                  {creating ? (

                    <>
                      <Loader2
                        size={16}
                      />

                      Creating...
                    </>

                  ) : (

                    <>
                      <Plus
                        size={16}
                      />

                      Create Group
                    </>

                  )}

                </button>

              </div>

            </form>

          </div>

        </div>
      )}

      {/* =====================================================
          STUDY SESSION MODAL
      ====================================================== */}

      {showSessionModal && (

        <div
          style={{
            position: 'fixed',
            inset: 0,

            background:
              'rgba(0, 0, 0, 0.35)',

            display: 'flex',

            alignItems: 'center',

            justifyContent: 'center',

            padding: '2rem',

            zIndex: 1100,
          }}

          onClick={
            closeSessionModal
          }
        >

          <div
            className="card"

            style={{
              width:
                'min(650px, 95vw)',

              maxHeight: '90vh',

              overflowY: 'auto',

              padding: '1.75rem',
            }}

            onClick={(e) =>
              e.stopPropagation()
            }
          >

            <div
              style={{
                display: 'flex',

                justifyContent:
                  'space-between',

                alignItems:
                  'flex-start',

                gap: '1rem',

                marginBottom:
                  '1.5rem',
              }}
            >

              <div>

                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.55rem',
                    marginBottom: '0.35rem',
                  }}
                >
                  {sessionModalMode ===
                  'schedule' ? (
                    <CalendarDays
                      size={20}
                    />
                  ) : (
                    <Play
                      size={20}
                    />
                  )}

                  <h2
                    style={{
                      margin: 0,
                    }}
                  >
                    {sessionModalMode ===
                    'schedule'
                      ? 'Schedule Study Session'
                      : 'Start Study Session Now'}
                  </h2>
                </div>

                <p
                  style={{
                    margin: 0,

                    color:
                      'var(--text-muted)',

                    fontSize:
                      '0.9rem',
                  }}
                >
                  {sessionGroup?.name}
                </p>

              </div>

              <button
                type="button"

                className="btn btn-secondary btn-sm"

                onClick={
                  closeSessionModal
                }

                disabled={
                  savingSession
                }
              >
                <X size={16} />
              </button>

            </div>

            {sessionError && (

              <div
                className="banner banner-warning"

                style={{
                  marginBottom:
                    '1rem',
                }}
              >
                <AlertCircle
                  size={18}
                />

                <span>
                  {sessionError}
                </span>
              </div>
            )}

            <form
              onSubmit={
                handleSaveSession
              }
            >

              <div
                style={{
                  display: 'flex',

                  flexDirection:
                    'column',

                  gap: '1rem',
                }}
              >

                <div>

                  <label>
                    Session Title *
                  </label>

                  <input
                    type="text"

                    name="title"

                    className="form-control"

                    value={
                      sessionForm.title
                    }

                    onChange={
                      handleSessionFormChange
                    }

                    placeholder="Example: DAA - Dynamic Programming"

                    disabled={
                      savingSession
                    }
                  />

                </div>

                <div>

                  <label>
                    Agenda
                  </label>

                  <textarea
                    name="agenda"

                    className="form-control"

                    rows={4}

                    value={
                      sessionForm.agenda
                    }

                    onChange={
                      handleSessionFormChange
                    }

                    placeholder="What will the group discuss in this session?"

                    disabled={
                      savingSession
                    }
                  />

                </div>

                {sessionModalMode ===
                  'schedule' && (

                  <div>

                    <label>
                      Date & Time *
                    </label>

                    <input
                      type="datetime-local"

                      name="scheduledAt"

                      className="form-control"

                      value={
                        sessionForm
                          .scheduledAt
                      }

                      onChange={
                        handleSessionFormChange
                      }

                      disabled={
                        savingSession
                      }
                    />

                    <div
                      style={{
                        marginTop:
                          '0.35rem',

                        color:
                          'var(--text-muted)',

                        fontSize:
                          '0.78rem',
                      }}
                    >
                      Members will receive a
                      notification after the
                      session is scheduled.
                    </div>

                  </div>
                )}

              </div>

              <div
                style={{
                  marginTop:
                    '1.5rem',

                  display: 'flex',

                  justifyContent:
                    'flex-end',

                  gap: '0.75rem',

                  flexWrap: 'wrap',
                }}
              >

                <button
                  type="button"

                  className="btn btn-secondary"

                  onClick={
                    closeSessionModal
                  }

                  disabled={
                    savingSession
                  }
                >
                  Cancel
                </button>

                <button
                  type="submit"

                  className="btn btn-primary"

                  disabled={
                    savingSession
                  }
                >

                  {savingSession ? (

                    <>
                      <Loader2
                        size={16}
                      />

                      Saving...
                    </>

                  ) : sessionModalMode ===
                    'schedule' ? (

                    <>
                      <CalendarDays
                        size={16}
                      />

                      Schedule Session
                    </>

                  ) : (

                    <>
                      <Play
                        size={16}
                      />

                      Start Session
                    </>
                  )}

                </button>

              </div>

            </form>

          </div>

        </div>
      )}

      {/* =====================================================
          MEMBERS MODAL
      ====================================================== */}

      {showMembersModal && (

        <div
          style={{
            position: 'fixed',
            inset: 0,

            background:
              'rgba(0, 0, 0, 0.35)',

            display: 'flex',

            alignItems: 'center',

            justifyContent: 'center',

            padding: '2rem',

            zIndex: 1000,
          }}

          onClick={
            closeMembersModal
          }
        >

          <div
            className="card"

            style={{
              width:
                'min(600px, 95vw)',

              maxHeight: '80vh',

              overflowY: 'auto',

              padding: '1.75rem',
            }}

            onClick={(e) =>
              e.stopPropagation()
            }
          >

            <div
              style={{
                display: 'flex',

                justifyContent:
                  'space-between',

                alignItems:
                  'flex-start',

                gap: '1rem',

                marginBottom:
                  '1.25rem',
              }}
            >

              <div>

                <h2
                  style={{
                    margin:
                      '0 0 0.35rem 0',
                  }}
                >
                  Group Members
                </h2>

                <p
                  style={{
                    margin: 0,

                    color:
                      'var(--text-muted)',

                    fontSize:
                      '0.9rem',
                  }}
                >
                  {membersGroup?.name}
                </p>

              </div>

              <button
                type="button"

                className="btn btn-secondary btn-sm"

                onClick={
                  closeMembersModal
                }
              >
                <X size={16} />
              </button>

            </div>

            {membersGroup?.createdByCurrentUser &&
              membersGroup?.visibility ===
                'PRIVATE' && (

                <div
                  style={{
                    marginBottom: '1rem',
                  }}
                >
                  {!showAddMemberForm ? (

                    <button
                      type="button"
                      className="btn btn-primary btn-sm"
                      onClick={openAddMemberForm}
                    >
                      <UserPlus size={15} />
                      Add People
                    </button>

                  ) : (

                    <form
                      onSubmit={handleAddMember}
                      style={{
                        border:
                          '1px solid var(--border-color)',
                        borderRadius: '0.5rem',
                        padding: '1rem',
                      }}
                    >
                      <div
                        style={{
                          display: 'flex',
                          justifyContent:
                            'space-between',
                          alignItems: 'center',
                          gap: '1rem',
                          marginBottom: '0.8rem',
                        }}
                      >
                        <strong>
                          Add a registered AstraLib user
                        </strong>

                        <button
                          type="button"
                          className="btn btn-secondary btn-sm"
                          onClick={closeAddMemberForm}
                          disabled={addingMember}
                        >
                          <X size={14} />
                        </button>
                      </div>

                      <label>
                        User Email
                      </label>

                      <input
                        type="email"
                        className="form-control"
                        value={memberEmail}
                        onChange={(e) => {
                          setMemberEmail(
                            e.target.value
                          );
                          setAddMemberError('');
                          setAddMemberSuccess('');
                        }}
                        placeholder="Example: anu@gmail.com"
                        disabled={addingMember}
                        autoFocus
                      />

                      {addMemberError && (
                        <div
                          className="banner banner-warning"
                          style={{
                            marginTop: '0.8rem',
                          }}
                        >
                          <AlertCircle size={16} />
                          <span>
                            {addMemberError}
                          </span>
                        </div>
                      )}

                      {addMemberSuccess && (
                        <div
                          className="banner"
                          style={{
                            marginTop: '0.8rem',
                          }}
                        >
                          <Users2 size={16} />
                          <span>
                            {addMemberSuccess}
                          </span>
                        </div>
                      )}

                      <div
                        style={{
                          display: 'flex',
                          justifyContent:
                            'flex-end',
                          gap: '0.65rem',
                          marginTop: '1rem',
                        }}
                      >
                        <button
                          type="button"
                          className="btn btn-secondary btn-sm"
                          onClick={closeAddMemberForm}
                          disabled={addingMember}
                        >
                          Cancel
                        </button>

                        <button
                          type="submit"
                          className="btn btn-primary btn-sm"
                          disabled={addingMember}
                        >
                          {addingMember ? (
                            <Loader2 size={14} />
                          ) : (
                            <UserPlus size={14} />
                          )}

                          {addingMember
                            ? 'Adding...'
                            : 'Add Member'}
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              )}

            {loadingMembers && (

              <div
                style={{
                  padding: '2rem',

                  display: 'flex',

                  justifyContent:
                    'center',

                  alignItems: 'center',

                  gap: '0.75rem',
                }}
              >
                <Loader2 size={18} />

                Loading members...
              </div>
            )}

            {membersError && (

              <div
                className="banner banner-warning"
              >
                <AlertCircle size={18} />

                <span>
                  {membersError}
                </span>
              </div>
            )}

            {!loadingMembers &&
              !membersError &&
              members.length === 0 && (

                <div
                  style={{
                    padding:
                      '2rem 1rem',

                    textAlign:
                      'center',

                    color:
                      'var(--text-muted)',
                  }}
                >
                  No members found.
                </div>
              )}

            {!loadingMembers &&
              members.length > 0 && (

                <div className="table-container">

                  <table className="formal-table">

                    <thead>
                      <tr>
                        <th>
                          Researcher
                        </th>

                        <th>
                          Role
                        </th>

                        <th>
                          Joined
                        </th>
                      </tr>
                    </thead>

                    <tbody>

                      {members.map(
                        (member) => (

                          <tr
                            key={
                              member.userId
                            }
                          >

                            <td>
                              <strong>
                                {
                                  member.name ||
                                  `User #${member.userId}`
                                }
                              </strong>
                            </td>

                            <td>
                              <Badge variant="default">
                                {
                                  member.role ||
                                  'USER'
                                }
                              </Badge>
                            </td>

                            <td>
                              {formatDate(
                                member.joinedAt
                              )}
                            </td>

                          </tr>
                        )
                      )}

                    </tbody>

                  </table>

                </div>
              )}

          </div>

        </div>
      )}

    </div>
  );
}

export default StudyHallPage;