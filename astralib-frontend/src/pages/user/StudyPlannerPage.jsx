import React, { useState } from 'react';
import {
  Plus,
  Trash2,
  CalendarDays,
  Clock,
  BookOpen,
  Sparkles,
  CheckCircle2,
  Loader2,
  ClipboardList,
  ArrowLeft,
  Save,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import api from '../../services/api';

const today = new Date().toISOString().split('T')[0];

const createSubject = () => ({
  id: crypto.randomUUID(),
  name: '',
  examDate: '',
  topicsText: '',
});

export default function StudyPlannerPage() {
  const navigate = useNavigate();

  const [form, setForm] = useState({
    title: '',
    description: '',
    startDate: today,
    endDate: '',
    dailyTargetMinutes: 120,
    availableFrom: '18:00',
    availableUntil: '21:00',
  });

  const [subjects, setSubjects] = useState([createSubject()]);
  const [preview, setPreview] = useState(null);
  const [savedPlan, setSavedPlan] = useState(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const updateForm = (field, value) => {
    setForm((previous) => ({ ...previous, [field]: value }));
    setPreview(null);
    setSavedPlan(null);
    setError('');
    setSuccess('');
  };

  const updateSubject = (id, field, value) => {
    setSubjects((previous) =>
      previous.map((subject) =>
        subject.id === id ? { ...subject, [field]: value } : subject
      )
    );

    setPreview(null);
    setSavedPlan(null);
    setError('');
  };

  const addSubject = () => {
    setSubjects((previous) => [...previous, createSubject()]);
    setPreview(null);
  };

  const removeSubject = (id) => {
    setSubjects((previous) => {
      if (previous.length === 1) return previous;
      return previous.filter((subject) => subject.id !== id);
    });

    setPreview(null);
  };

  const buildPreviewPayload = () => ({
    startDate: form.startDate,
    endDate: form.endDate,
    dailyTargetMinutes: Number(form.dailyTargetMinutes),
    availableFrom: form.availableFrom,
    availableUntil: form.availableUntil,
    subjects: subjects.map((subject) => ({
      name: subject.name.trim(),
      examDate: subject.examDate,
      topics: subject.topicsText
        .split('\n')
        .map((topic) => topic.trim())
        .filter(Boolean),
    })),
  });

  const validateForm = () => {
    if (!form.title.trim()) return 'Please enter a study plan title.';
    if (!form.startDate || !form.endDate) {
      return 'Please select the study period.';
    }

    if (form.endDate < form.startDate) {
      return 'End date cannot be before the start date.';
    }

    if (
      !Number.isInteger(Number(form.dailyTargetMinutes)) ||
      Number(form.dailyTargetMinutes) < 1 ||
      Number(form.dailyTargetMinutes) > 1440
    ) {
      return 'Daily target must be between 1 and 1440 minutes.';
    }

    if (form.availableUntil <= form.availableFrom) {
      return 'Available until must be later than available from.';
    }

    if (subjects.some((subject) => !subject.name.trim())) {
      return 'Please enter a name for every subject.';
    }

    if (subjects.some((subject) => !subject.examDate)) {
      return 'Please select an exam date for every subject.';
    }

    if (
      subjects.some(
        (subject) =>
          subject.examDate < form.startDate ||
          subject.examDate > form.endDate
      )
    ) {
      return 'Each exam date must fall within your study period.';
    }

    if (
      subjects.some(
        (subject) =>
          subject.topicsText.split('\n').filter((topic) => topic.trim()).length === 0
      )
    ) {
      return 'Add at least one topic for every subject.';
    }

    return '';
  };

  const generatePreview = async () => {
    const validationError = validateForm();

    if (validationError) {
      setError(validationError);
      return;
    }

    try {
      setLoading(true);
      setError('');
      setSuccess('');
      setPreview(null);
      setSavedPlan(null);

      const response = await api.studyPlans.preview(buildPreviewPayload());

      setPreview(response?.data ?? response);
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          err?.data?.message ||
          err.message ||
          'Unable to generate the timetable.'
      );
    } finally {
      setLoading(false);
    }
  };

  const approvePlan = async () => {
    if (!preview?.tasks?.length) {
      setError('There are no generated tasks to save.');
      return;
    }

    try {
      setSaving(true);
      setError('');
      setSuccess('');

      // Step 1: Create the parent study plan.
      const planResponse = await api.studyPlans.create({
        title: form.title.trim(),
        description: form.description.trim(),
        startDate: form.startDate,
        endDate: form.endDate,
        dailyTargetMinutes: Number(form.dailyTargetMinutes),
      });

      const plan = planResponse?.data ?? planResponse;
      const planId = plan?.id ?? plan?.planId;

      if (!planId) {
        throw new Error(
          'The plan was created, but its ID was not returned. Please check the backend response before retrying.'
        );
      }

      setSavedPlan(plan);

      // Step 2: Save the reviewed timetable tasks.
      await api.studyPlans.approveTasks(planId, {
        tasks: preview.tasks.map((task) => ({
          title: task.title,
          subject: task.subject,
          topic: task.topic,
          description: task.description,
          taskType: task.taskType,
          scheduledDate: task.scheduledDate,
          startTime: task.startTime,
          endTime: task.endTime,
          estimatedMinutes: task.estimatedMinutes,
        })),
      });

      setSuccess('Your study plan and timetable have been saved!');
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          err?.data?.message ||
          err.message ||
          'Unable to save the study plan.'
      );
    } finally {
      setSaving(false);
    }
  };

  const formatMinutes = (minutes) => {
    const hours = Math.floor((minutes || 0) / 60);
    const remainingMinutes = (minutes || 0) % 60;

    if (hours && remainingMinutes) return `${hours}h ${remainingMinutes}m`;
    if (hours) return `${hours}h`;
    return `${remainingMinutes}m`;
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <div className="page-title-group">
          <button
            className="btn btn-secondary btn-sm"
            onClick={() => navigate(-1)}
            type="button"
          >
            <ArrowLeft size={16} /> Back
          </button>

          <div>
            <h1 className="page-title">AI Study Planner</h1>
            <p className="text-muted">
              Build a study timetable around your subjects, topics and exams.
            </p>
          </div>
        </div>
      </div>

      {error && (
        <div className="card" role="alert">
          <p style={{ color: 'var(--text-danger, #dc2626)', margin: 0 }}>
            {error}
          </p>
        </div>
      )}

      {success && (
        <div className="card">
          <p style={{ color: 'var(--success, #16a34a)', margin: 0 }}>
            <CheckCircle2 size={17} style={{ verticalAlign: 'middle' }} />{' '}
            {success}
          </p>
          <button
            className="btn btn-primary btn-sm"
            type="button"
            onClick={() => navigate('/study-activity')}
            style={{ marginTop: 12 }}
          >
            View Study Activity
          </button>
        </div>
      )}

      <div className="card">
        <div className="card-header">
          <h2 className="card-title">
            <ClipboardList size={19} /> Study Plan Details
          </h2>
        </div>

        <div className="form-group">
          <label className="form-label">Plan title</label>
          <input
            className="form-control"
            value={form.title}
            onChange={(e) => updateForm('title', e.target.value)}
            placeholder="Example: Semester Examination Preparation"
          />
        </div>

        <div className="form-group">
          <label className="form-label">Description (optional)</label>
          <textarea
            className="form-control"
            rows="2"
            value={form.description}
            onChange={(e) => updateForm('description', e.target.value)}
            placeholder="What do you want to achieve?"
          />
        </div>

        <div className="form-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 16 }}>
          <div className="form-group">
            <label className="form-label">Start date</label>
            <input
              type="date"
              className="form-control"
              min={today}
              value={form.startDate}
              onChange={(e) => updateForm('startDate', e.target.value)}
            />
          </div>

          <div className="form-group">
            <label className="form-label">End date</label>
            <input
              type="date"
              className="form-control"
              min={form.startDate || today}
              value={form.endDate}
              onChange={(e) => updateForm('endDate', e.target.value)}
            />
          </div>

          <div className="form-group">
            <label className="form-label">Daily study target (minutes)</label>
            <input
              type="number"
              min="1"
              max="1440"
              className="form-control"
              value={form.dailyTargetMinutes}
              onChange={(e) =>
                updateForm('dailyTargetMinutes', e.target.value)
              }
            />
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h2 className="card-title">
            <Clock size={19} /> Available Study Hours
          </h2>
        </div>

        <div className="form-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 16 }}>
          <div className="form-group">
            <label className="form-label">Available from</label>
            <input
              type="time"
              className="form-control"
              value={form.availableFrom}
              onChange={(e) => updateForm('availableFrom', e.target.value)}
            />
          </div>

          <div className="form-group">
            <label className="form-label">Available until</label>
            <input
              type="time"
              className="form-control"
              value={form.availableUntil}
              onChange={(e) => updateForm('availableUntil', e.target.value)}
            />
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h2 className="card-title">
            <BookOpen size={19} /> Subjects and Topics
          </h2>

          <button
            className="btn btn-secondary btn-sm"
            type="button"
            onClick={addSubject}
          >
            <Plus size={16} /> Add Subject
          </button>
        </div>

        {subjects.map((subject, index) => (
          <div
            key={subject.id}
            style={{
              padding: 16,
              border: '1px solid var(--border-default)',
              borderRadius: 'var(--radius-sm)',
              marginBottom: 16,
            }}
          >
            <div className="card-header">
              <h3 className="card-title">Subject {index + 1}</h3>

              <button
                className="btn btn-sm btn-secondary"
                type="button"
                onClick={() => removeSubject(subject.id)}
                disabled={subjects.length === 1}
                title="Remove subject"
              >
                <Trash2 size={16} />
              </button>
            </div>

            <div className="form-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 16 }}>
              <div className="form-group">
                <label className="form-label">Subject name</label>
                <input
                  className="form-control"
                  value={subject.name}
                  onChange={(e) =>
                    updateSubject(subject.id, 'name', e.target.value)
                  }
                  placeholder="Example: Computer Networks"
                />
              </div>

              <div className="form-group">
                <label className="form-label">Exam date</label>
                <input
                  type="date"
                  className="form-control"
                  min={form.startDate}
                  max={form.endDate || undefined}
                  value={subject.examDate}
                  onChange={(e) =>
                    updateSubject(subject.id, 'examDate', e.target.value)
                  }
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Topics (one topic per line)</label>
              <textarea
                className="form-control"
                rows="4"
                value={subject.topicsText}
                onChange={(e) =>
                  updateSubject(subject.id, 'topicsText', e.target.value)
                }
                placeholder={'Application Layer\nTransport Layer\nNetwork Layer'}
              />
            </div>
          </div>
        ))}
      </div>

      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 24 }}>
        <button
          className="btn btn-primary"
          type="button"
          onClick={generatePreview}
          disabled={loading || saving || !!savedPlan}
        >
          {loading ? (
            <><Loader2 size={17} className="spin" /> Generating...</>
          ) : (
            <><Sparkles size={17} /> Generate Timetable Preview</>
          )}
        </button>
      </div>

      {preview && (
        <div className="card">
          <div className="card-header">
            <div>
              <h2 className="card-title">
                <CalendarDays size={19} /> Timetable Preview
              </h2>
              <p className="text-muted">
                Review your timetable before approving it.
              </p>
            </div>
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))',
              gap: 12,
              marginBottom: 20,
            }}
          >
            <div className="card">
              <p className="text-muted">Total tasks</p>
              <h2>{preview.totalTasks ?? preview.tasks?.length ?? 0}</h2>
            </div>

            <div className="card">
              <p className="text-muted">Scheduled time</p>
              <h2>{formatMinutes(preview.totalScheduledMinutes)}</h2>
            </div>

            <div className="card">
              <p className="text-muted">Daily target</p>
              <h2>{formatMinutes(preview.dailyTargetMinutes)}</h2>
            </div>
          </div>

          {preview.tasks?.length ? (
            <div style={{ display: 'grid', gap: 12 }}>
              {preview.tasks.map((task, index) => (
                <div
                  key={`${task.scheduledDate}-${task.startTime}-${index}`}
                  style={{
                    padding: 16,
                    border: '1px solid var(--border-default)',
                    borderRadius: 'var(--radius-sm)',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                    <strong>{task.title}</strong>
                    <span className="text-muted">{task.taskType}</span>
                  </div>

                  <p style={{ margin: '8px 0' }}>
                    {task.subject}
                    {task.topic ? ` · ${task.topic}` : ''}
                  </p>

                  <p className="text-muted" style={{ margin: 0 }}>
                    {task.scheduledDate} · {task.startTime}–{task.endTime}
                    {' · '}
                    {formatMinutes(task.estimatedMinutes)}
                  </p>

                  {task.description && (
                    <p className="text-muted" style={{ marginBottom: 0 }}>
                      {task.description}
                    </p>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <p className="text-muted">
              The planner did not generate any tasks. Check your dates and available hours.
            </p>
          )}

          {preview.tasks?.length > 0 && !savedPlan && (
            <button
              className="btn btn-primary"
              type="button"
              onClick={approvePlan}
              disabled={saving}
              style={{ marginTop: 20 }}
            >
              {saving ? (
                <><Loader2 size={17} /> Saving...</>
              ) : (
                <><Save size={17} /> Approve and Save Study Plan</>
              )}
            </button>
          )}

          {savedPlan && (
            <p className="text-muted" style={{ marginTop: 16 }}>
              Plan created. If task approval failed, please check the error above
              before attempting another save.
            </p>
          )}
        </div>
      )}
    </div>
  );
}