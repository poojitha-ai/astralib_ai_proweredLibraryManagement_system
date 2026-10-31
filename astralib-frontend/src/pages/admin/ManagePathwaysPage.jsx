import React, { useEffect, useMemo, useState } from 'react';
import { api } from '../../services/api';
import {
  Plus,
  Save,
  Trash2,
  Edit2,
  AlertCircle,
  CheckCircle2,
  Layers,
  GraduationCap,
  BookOpen,
  Search,
  ArrowUp,
  ArrowDown,
} from 'lucide-react';
import { Badge, LoadingSpinner, EmptyState, Modal } from '../../components/common/Components';
import { LEVEL_LABEL } from '../../components/library/LevelChip';

const emptyCreate = {
  name: '',
  type: 'LEARNING',
  subject: '',
  audience: '',
  description: '',
};

/**
 * Pathway curation.
 *
 * Pathways are an administrative structure: they record sequence,
 * difficulty and prerequisites, and the catalogue uses them to label
 * books. Readers never see this page or a pathway card.
 */
export function ManagePathwaysPage() {
  const [pathways, setPathways] = useState([]);
  const [gaps, setGaps] = useState([]);
  const [pool, setPool] = useState([]);

  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const [selectedId, setSelectedId] = useState(null);
  const [detail, setDetail] = useState({
    name: '',
    type: 'LEARNING',
    subject: '',
    audience: '',
    description: '',
  });

  const [showCreate, setShowCreate] = useState(false);
  const [createForm, setCreateForm] = useState(emptyCreate);

  const [search, setSearch] = useState('');
  const [draft, setDraft] = useState({
    bookId: '',
    level: '',
    position: '',
    prerequisiteBookId: '',
    rationale: '',
    learningOutcome: '',
  });

  const [editing, setEditing] = useState(null);
  const [editForm, setEditForm] = useState({});

  const selected = useMemo(
    () => pathways.find((pathway) => pathway.id === selectedId) || null,
    [pathways, selectedId]
  );

  // =========================================================
  // LOAD
  // =========================================================

  const load = async (keepSelection = true) => {
    setLoading(true);
    setError('');

    try {
      const [pathwayData, gapData, catalogue] = await Promise.all([
        api.pathways.admin.list(),
        api.pathways.admin.metadataGaps(),
        // Include catalogue entries without a readable file: they are
        // levelled through pathways even though no edition is held yet.
        api.books.getAll({ catalogueOnly: true }),
      ]);

      const list = Array.isArray(pathwayData) ? pathwayData : [];

      setPathways(list);
      setGaps(Array.isArray(gapData) ? gapData : []);
      setPool(Array.isArray(catalogue) ? catalogue : []);

      setSelectedId((current) => {
        if (keepSelection && current && list.some((pathway) => pathway.id === current)) {
          return current;
        }
        return list[0]?.id ?? null;
      });
    } catch (err) {
      setError(err.message || 'Could not load pathways.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(false);
  }, []);

  useEffect(() => {
    if (!selected) return;

    setDetail({
      name: selected.name || '',
      type: selected.type || 'LEARNING',
      subject: selected.subject || '',
      audience: selected.audience || '',
      description: selected.description || '',
    });

    setDraft({
      bookId: '',
      level: selected.allowedLevels?.[0] || '',
      position: String((selected.books?.length || 0) + 1),
      prerequisiteBookId: '',
      rationale: '',
      learningOutcome: '',
    });

    setSearch('');
  }, [selectedId, selected?.bookCount]); // eslint-disable-line react-hooks/exhaustive-deps

  // =========================================================
  // ACTIONS
  // =========================================================

  const replacePathway = (updated) => {
    setPathways((previous) =>
      previous.map((pathway) => (pathway.id === updated.id ? updated : pathway))
    );
  };

  const handleCreate = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    setNotice('');

    try {
      const created = await api.pathways.admin.create(createForm);

      setPathways((previous) => [...previous, created]);
      setSelectedId(created.id);
      setShowCreate(false);
      setCreateForm(emptyCreate);
      setNotice(`Created "${created.name}".`);
    } catch (err) {
      setError(err.message || 'Could not create the pathway.');
    } finally {
      setBusy(false);
    }
  };

  const handleSaveDetail = async (event) => {
    event.preventDefault();
    if (!selected) return;

    setBusy(true);
    setError('');
    setNotice('');

    try {
      const updated = await api.pathways.admin.update(selected.id, detail);

      replacePathway(updated);
      setNotice('Pathway details saved.');
    } catch (err) {
      setError(err.message || 'Could not save the pathway.');
    } finally {
      setBusy(false);
    }
  };

  const handleAddBook = async (event) => {
    event.preventDefault();
    if (!selected) return;

    setBusy(true);
    setError('');
    setNotice('');

    try {
      const updated = await api.pathways.admin.addBook(selected.id, {
        bookId: Number(draft.bookId),
        level: draft.level,
        position: draft.position ? Number(draft.position) : null,
        prerequisiteBookId: draft.prerequisiteBookId ? Number(draft.prerequisiteBookId) : null,
        rationale: draft.rationale,
        learningOutcome: draft.learningOutcome,
      });

      replacePathway(updated);
      setDraft({
        bookId: '',
        level: selected.allowedLevels?.[0] || '',
        position: String((updated.books?.length || 0) + 1),
        prerequisiteBookId: '',
        rationale: '',
        learningOutcome: '',
      });
      setNotice('Book added to the pathway.');
    } catch (err) {
      setError(err.message || 'Could not add the book.');
    } finally {
      setBusy(false);
    }
  };

  const handleRemoveBook = async (entry) => {
    if (!selected) return;

    if (
      !window.confirm(
        `Remove "${entry.title}" from this pathway? The book, its file and its embeddings are kept.`
      )
    ) {
      return;
    }

    setBusy(true);
    setError('');
    setNotice('');

    try {
      const updated = await api.pathways.admin.removeBook(selected.id, entry.entryId);

      replacePathway(updated);
      setNotice('Book removed from the pathway.');
    } catch (err) {
      setError(err.message || 'Could not remove the book.');
    } finally {
      setBusy(false);
    }
  };

  const openEdit = (entry) => {
    setEditing(entry);
    setEditForm({
      level: entry.level || '',
      position: String(entry.position ?? ''),
      prerequisiteBookId: entry.prerequisiteBookId ? String(entry.prerequisiteBookId) : '',
      rationale: entry.rationale || '',
      learningOutcome: entry.learningOutcome || '',
    });
  };

  const handleSaveEntry = async (event) => {
    event.preventDefault();
    if (!selected || !editing) return;

    setBusy(true);
    setError('');
    setNotice('');

    try {
      const updated = await api.pathways.admin.updateBook(selected.id, editing.entryId, {
        level: editForm.level,
        position: Number(editForm.position),
        prerequisiteBookId: editForm.prerequisiteBookId
          ? Number(editForm.prerequisiteBookId)
          : null,
        rationale: editForm.rationale,
        learningOutcome: editForm.learningOutcome,
      });

      replacePathway(updated);
      setEditing(null);
      setNotice('Membership updated.');
    } catch (err) {
      setError(err.message || 'Could not update the membership.');
    } finally {
      setBusy(false);
    }
  };

  /**
   * Moves an entry one place up or down by swapping positions, so the
   * sequence can be adjusted without retyping numbers.
   */
  const move = async (entry, direction) => {
    if (!selected) return;

    const ordered = selected.books || [];
    const index = ordered.findIndex((item) => item.entryId === entry.entryId);
    const neighbour = ordered[index + direction];

    if (!neighbour) return;

    setBusy(true);
    setError('');
    setNotice('');

    try {
      const first = await api.pathways.admin.updateBook(selected.id, entry.entryId, {
        level: entry.level,
        position: neighbour.position,
      });

      const second = await api.pathways.admin.updateBook(selected.id, neighbour.entryId, {
        level: neighbour.level,
        position: entry.position,
      });

      replacePathway(second || first);
    } catch (err) {
      setError(
        err.message ||
          'Could not reorder. A prerequisite must always come earlier in the sequence.'
      );
      await load();
    } finally {
      setBusy(false);
    }
  };

  const optionPool = useMemo(() => {
    const taken = new Set((selected?.books || []).map((entry) => entry.bookId));
    const query = search.trim().toLowerCase();

    return pool
      .filter((book) => !taken.has(book.id))
      .filter((book) => {
        if (!query) return true;

        return [book.title, book.author?.name, book.category?.name]
          .filter(Boolean)
          .join(' ')
          .toLowerCase()
          .includes(query);
      })
      .slice(0, 10);
  }, [pool, selected, search]);

  const draftBook = pool.find((book) => String(book.id) === String(draft.bookId));

  // =========================================================
  // RENDER
  // =========================================================

  if (loading) {
    return <LoadingSpinner message="Loading pathway curation…" />;
  }

  return (
    <div className="page-container">
      <div className="page-header">
        <div className="page-title-group">
          <h1>Pathway Curation</h1>
          <p>
            Sequences, levels and prerequisites used to label books in the catalogue. Readers
            see a difficulty label only; this structure stays inside the administration area.
          </p>
        </div>
        <div className="page-actions">
          <button
            type="button"
            className="btn btn-primary btn-sm"
            onClick={() => setShowCreate(true)}
          >
            <Plus size={16} /> New pathway
          </button>
        </div>
      </div>

      {error && (
        <div className="banner banner-error">
          <AlertCircle size={16} />
          <span>{error}</span>
        </div>
      )}

      {notice && (
        <div
          className="banner banner-info"
          style={{
            backgroundColor: 'var(--status-success-bg)',
            color: 'var(--status-success-text)',
            borderColor: 'var(--status-success-border)',
          }}
        >
          <CheckCircle2 size={16} />
          <span>{notice}</span>
        </div>
      )}

      <div className="admin-pathway-layout">
        {/* ---------- Pathway list ---------- */}
        <aside>
          <div className="admin-pathway-list">
            {pathways.length === 0 && (
              <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                No pathways yet. Create one to start grouping books.
              </p>
            )}

            {pathways.map((pathway) => (
              <button
                key={pathway.id}
                type="button"
                className={`admin-pathway-item ${pathway.id === selectedId ? 'active' : ''}`}
                onClick={() => setSelectedId(pathway.id)}
              >
                <div className="admin-pathway-item-name">
                  {pathway.type === 'LEARNING' ? (
                    <GraduationCap size={14} style={{ marginRight: 6, verticalAlign: '-2px' }} />
                  ) : (
                    <BookOpen size={14} style={{ marginRight: 6, verticalAlign: '-2px' }} />
                  )}
                  {pathway.name}
                </div>
                <div className="admin-pathway-item-meta">
                  {pathway.type === 'LEARNING' ? 'Learning' : 'Leisure'} · {pathway.bookCount}{' '}
                  {pathway.bookCount === 1 ? 'book' : 'books'}
                </div>
              </button>
            ))}
          </div>
        </aside>

        {/* ---------- Selected pathway ---------- */}
        <div style={{ display: 'grid', gap: '1.5rem' }}>
          {!selected ? (
            <EmptyState
              icon={Layers}
              title="No pathway selected"
              description="Create a pathway, then add books to it with a level, position and rationale."
              action={
                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  onClick={() => setShowCreate(true)}
                >
                  Create the first pathway
                </button>
              }
            />
          ) : (
            <>
              <section className="card" style={{ padding: '1.5rem' }}>
                <div className="card-header">
                  <h2 className="card-title">{selected.name}</h2>
                  <Badge variant={selected.type === 'LEARNING' ? 'primary' : 'default'}>
                    {selected.type === 'LEARNING' ? 'Learning pathway' : 'Leisure pathway'}
                  </Badge>
                </div>

                <form onSubmit={handleSaveDetail}>
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '2fr 1fr',
                      gap: '1rem',
                    }}
                  >
                    <div className="form-group">
                      <label className="form-label" htmlFor="pw-name">Name</label>
                      <input
                        id="pw-name"
                        className="form-input"
                        value={detail.name}
                        onChange={(event) => setDetail({ ...detail, name: event.target.value })}
                        required
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label" htmlFor="pw-type">Type</label>
                      <select
                        id="pw-type"
                        className="form-select"
                        value={detail.type}
                        onChange={(event) => setDetail({ ...detail, type: event.target.value })}
                        disabled={selected.bookCount > 0}
                        title={
                          selected.bookCount > 0
                            ? 'Remove the books before changing the pathway type.'
                            : undefined
                        }
                      >
                        <option value="LEARNING">Learning</option>
                        <option value="LEISURE">Leisure</option>
                      </select>
                      {selected.bookCount > 0 && (
                        <div className="form-hint">
                          Type is fixed while the pathway holds books, because the level labels
                          depend on it.
                        </div>
                      )}
                    </div>
                  </div>

                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '1fr 1fr',
                      gap: '1rem',
                    }}
                  >
                    <div className="form-group">
                      <label className="form-label" htmlFor="pw-subject">Subject</label>
                      <input
                        id="pw-subject"
                        className="form-input"
                        value={detail.subject}
                        onChange={(event) => setDetail({ ...detail, subject: event.target.value })}
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label" htmlFor="pw-audience">Audience</label>
                      <input
                        id="pw-audience"
                        className="form-input"
                        value={detail.audience}
                        onChange={(event) => setDetail({ ...detail, audience: event.target.value })}
                      />
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="pw-description">Description</label>
                    <textarea
                      id="pw-description"
                      className="form-textarea"
                      rows={2}
                      value={detail.description}
                      onChange={(event) => setDetail({ ...detail, description: event.target.value })}
                    />
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <button type="submit" className="btn btn-primary btn-sm" disabled={busy}>
                      <Save size={15} /> Save details
                    </button>
                  </div>
                </form>
              </section>

              {/* ---------- Memberships ---------- */}
              <section className="card" style={{ padding: '1.5rem' }}>
                <div className="card-header">
                  <h2 className="card-title">Sequence</h2>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                    {selected.bookCount} {selected.bookCount === 1 ? 'book' : 'books'}
                  </span>
                </div>

                {selected.books?.length ? (
                  <div style={{ display: 'grid', gap: '0.75rem' }}>
                    {[...selected.books]
                      .sort((left, right) => left.position - right.position)
                      .map((entry) => (
                        <div key={entry.entryId} className="admin-membership">
                          <div className="admin-membership-head">
                            <span className="book-tile-meta">#{entry.position}</span>
                            <span className={`level-chip level-${(entry.level || '').toLowerCase()}`}>
                              {LEVEL_LABEL[entry.level] || entry.level}
                            </span>
                            <span className="admin-membership-title">{entry.title}</span>
                            <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                              {entry.author || 'Unknown author'}
                            </span>
                            <span style={{ marginLeft: 'auto', display: 'inline-flex', gap: '0.3rem' }}>
                              <button
                                type="button"
                                className="btn btn-secondary btn-sm"
                                onClick={() => move(entry, -1)}
                                disabled={busy}
                                title="Move earlier"
                              >
                                <ArrowUp size={13} />
                              </button>
                              <button
                                type="button"
                                className="btn btn-secondary btn-sm"
                                onClick={() => move(entry, 1)}
                                disabled={busy}
                                title="Move later"
                              >
                                <ArrowDown size={13} />
                              </button>
                              <button
                                type="button"
                                className="btn btn-secondary btn-sm"
                                onClick={() => openEdit(entry)}
                                title="Edit membership"
                              >
                                <Edit2 size={13} />
                              </button>
                              <button
                                type="button"
                                className="btn btn-danger btn-sm"
                                onClick={() => handleRemoveBook(entry)}
                                title="Remove from pathway"
                              >
                                <Trash2 size={13} />
                              </button>
                            </span>
                          </div>

                          <div className="admin-membership-body">
                            <div>
                              {entry.prerequisiteTitle
                                ? `Prerequisite: ${entry.prerequisiteTitle}`
                                : 'No prerequisite'}
                            </div>
                            <div>
                              {entry.indexed
                                ? `${entry.chunkCount} indexed passages`
                                : 'Not indexed yet'}
                              {entry.resourceAvailable ? '' : ' · file missing'}
                            </div>
                            {entry.rationale && (
                              <div style={{ gridColumn: '1 / -1' }}>{entry.rationale}</div>
                            )}
                            {entry.learningOutcome && (
                              <div style={{ gridColumn: '1 / -1' }}>
                                <strong>Outcome:</strong> {entry.learningOutcome}
                              </div>
                            )}
                          </div>
                        </div>
                      ))}
                  </div>
                ) : (
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                    This pathway has no books yet. Add one below.
                  </p>
                )}
              </section>

              {/* ---------- Add a book ---------- */}
              <section className="card" style={{ padding: '1.5rem' }}>
                <div className="card-header">
                  <h2 className="card-title">Add a book</h2>
                </div>

                <div style={{ position: 'relative', marginBottom: '1rem', maxWidth: '420px' }}>
                  <Search
                    size={15}
                    style={{
                      position: 'absolute',
                      left: '0.75rem',
                      top: '50%',
                      transform: 'translateY(-50%)',
                      color: 'var(--text-muted)',
                    }}
                  />
                  <input
                    className="form-input"
                    style={{ paddingLeft: '2.2rem' }}
                    placeholder="Search library titles by name, author or category"
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                  />
                </div>

                <form onSubmit={handleAddBook}>
                  <div className="form-group">
                    <label className="form-label" htmlFor="pw-book">Book</label>
                    <select
                      id="pw-book"
                      className="form-select"
                      value={draft.bookId}
                      onChange={(event) => setDraft({ ...draft, bookId: event.target.value })}
                      required
                    >
                      <option value="">Select a book…</option>
                      {optionPool.map((book) => (
                        <option key={book.id} value={book.id}>
                          {book.title}
                          {book.author?.name ? ` — ${book.author.name}` : ''}
                        </option>
                      ))}
                    </select>
                    {optionPool.length === 0 && (
                      <div className="form-hint">
                        No unassigned titles match. Every matching book may already be in this
                        pathway.
                      </div>
                    )}
                  </div>

                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '1fr 1fr 1fr',
                      gap: '1rem',
                    }}
                  >
                    <div className="form-group">
                      <label className="form-label" htmlFor="pw-level">Level</label>
                      <select
                        id="pw-level"
                        className="form-select"
                        value={draft.level}
                        onChange={(event) => setDraft({ ...draft, level: event.target.value })}
                      >
                        {(selected.allowedLevels || []).map((value) => (
                          <option key={value} value={value}>
                            {LEVEL_LABEL[value] || value}
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="form-group">
                      <label className="form-label" htmlFor="pw-position">Position</label>
                      <input
                        id="pw-position"
                        type="number"
                        min="1"
                        className="form-input"
                        value={draft.position}
                        onChange={(event) => setDraft({ ...draft, position: event.target.value })}
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label" htmlFor="pw-prereq">Prerequisite</label>
                      <select
                        id="pw-prereq"
                        className="form-select"
                        value={draft.prerequisiteBookId}
                        onChange={(event) =>
                          setDraft({ ...draft, prerequisiteBookId: event.target.value })
                        }
                      >
                        <option value="">None</option>
                        {(selected.books || [])
                          .filter((entry) => String(entry.bookId) !== String(draft.bookId))
                          .map((entry) => (
                            <option key={entry.bookId} value={entry.bookId}>
                              {entry.position}. {entry.title}
                            </option>
                          ))}
                      </select>
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="pw-rationale">
                      Why this book sits at this level
                    </label>
                    <textarea
                      id="pw-rationale"
                      className="form-textarea"
                      rows={2}
                      value={draft.rationale}
                      onChange={(event) => setDraft({ ...draft, rationale: event.target.value })}
                      placeholder="A documented reason, so the level is an assessment rather than a label."
                    />
                  </div>

                  {selected.type === 'LEARNING' && (
                    <div className="form-group">
                      <label className="form-label" htmlFor="pw-outcome">
                        Learning outcome (optional, verifiable)
                      </label>
                      <input
                        id="pw-outcome"
                        className="form-input"
                        value={draft.learningOutcome}
                        onChange={(event) =>
                          setDraft({ ...draft, learningOutcome: event.target.value })
                        }
                      />
                    </div>
                  )}

                  {draftBook && (
                    <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                      Adding <strong>{draftBook.title}</strong>
                      {draftBook.category?.name ? ` · ${draftBook.category.name}` : ''}
                    </p>
                  )}

                  <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <button
                      type="submit"
                      className="btn btn-primary btn-sm"
                      disabled={busy || !draft.bookId}
                    >
                      <Plus size={15} /> Add to pathway
                    </button>
                  </div>
                </form>
              </section>
            </>
          )}

          {/* ---------- Metadata gaps ---------- */}
          <section className="card" style={{ padding: '1.5rem' }}>
            <div className="card-header">
              <h2 className="card-title">Books with incomplete metadata</h2>
              <Badge variant={gaps.length ? 'warning' : 'success'}>{gaps.length}</Badge>
            </div>

            {gaps.length === 0 ? (
              <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                Every held title has an author, category, description, source, licence, file and
                indexed text.
              </p>
            ) : (
              <div>
                {gaps.map((gap) => (
                  <div key={gap.bookId} className="admin-gap-row">
                    <strong>{gap.title}</strong>
                    <span style={{ color: 'var(--text-muted)' }}>
                      {gap.author || 'Unknown author'}
                    </span>
                    {gap.missing.map((field) => (
                      <Badge key={field} variant="warning">
                        {field}
                      </Badge>
                    ))}
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>

      {/* ---------- Create pathway ---------- */}
      <Modal
        isOpen={showCreate}
        onClose={() => setShowCreate(false)}
        title="New pathway"
        footer={
          <div style={{ display: 'flex', gap: '0.6rem' }}>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setShowCreate(false)}
            >
              Cancel
            </button>
            <button
              type="submit"
              form="create-pathway-form"
              className="btn btn-primary"
              disabled={busy}
            >
              Create pathway
            </button>
          </div>
        }
      >
        <form id="create-pathway-form" onSubmit={handleCreate}>
          <div className="form-group">
            <label className="form-label" htmlFor="new-name">Name</label>
            <input
              id="new-name"
              className="form-input"
              value={createForm.name}
              onChange={(event) => setCreateForm({ ...createForm, name: event.target.value })}
              placeholder="e.g. Python Foundations"
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="new-type">Type</label>
            <select
              id="new-type"
              className="form-select"
              value={createForm.type}
              onChange={(event) => setCreateForm({ ...createForm, type: event.target.value })}
            >
              <option value="LEARNING">Learning (Beginner / Intermediate / Advanced)</option>
              <option value="LEISURE">Leisure (Accessible / Standard / Complex)</option>
            </select>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="new-subject">Subject</label>
            <input
              id="new-subject"
              className="form-input"
              value={createForm.subject}
              onChange={(event) => setCreateForm({ ...createForm, subject: event.target.value })}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="new-audience">Audience</label>
            <input
              id="new-audience"
              className="form-input"
              value={createForm.audience}
              onChange={(event) => setCreateForm({ ...createForm, audience: event.target.value })}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="new-description">Description</label>
            <textarea
              id="new-description"
              className="form-textarea"
              rows={2}
              value={createForm.description}
              onChange={(event) =>
                setCreateForm({ ...createForm, description: event.target.value })
              }
            />
          </div>
        </form>
      </Modal>

      {/* ---------- Edit membership ---------- */}
      <Modal
        isOpen={Boolean(editing)}
        onClose={() => setEditing(null)}
        title={editing ? `Edit “${editing.title}”` : 'Edit membership'}
      >
        {editing && (
          <form onSubmit={handleSaveEntry}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '1rem' }}>
              <div className="form-group">
                <label className="form-label" htmlFor="ed-level">Level</label>
                <select
                  id="ed-level"
                  className="form-select"
                  value={editForm.level}
                  onChange={(event) => setEditForm({ ...editForm, level: event.target.value })}
                >
                  {(selected?.allowedLevels || []).map((value) => (
                    <option key={value} value={value}>
                      {LEVEL_LABEL[value] || value}
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="ed-position">Position</label>
                <input
                  id="ed-position"
                  type="number"
                  min="1"
                  className="form-input"
                  value={editForm.position}
                  onChange={(event) => setEditForm({ ...editForm, position: event.target.value })}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="ed-prereq">Prerequisite</label>
                <select
                  id="ed-prereq"
                  className="form-select"
                  value={editForm.prerequisiteBookId}
                  onChange={(event) =>
                    setEditForm({ ...editForm, prerequisiteBookId: event.target.value })
                  }
                >
                  <option value="">None</option>
                  {(selected?.books || [])
                    .filter((entry) => entry.entryId !== editing.entryId)
                    .map((entry) => (
                      <option key={entry.bookId} value={entry.bookId}>
                        {entry.position}. {entry.title}
                      </option>
                    ))}
                </select>
              </div>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="ed-rationale">Rationale</label>
              <textarea
                id="ed-rationale"
                className="form-textarea"
                rows={2}
                value={editForm.rationale}
                onChange={(event) => setEditForm({ ...editForm, rationale: event.target.value })}
              />
            </div>

            {selected?.type === 'LEARNING' && (
              <div className="form-group">
                <label className="form-label" htmlFor="ed-outcome">Learning outcome</label>
                <input
                  id="ed-outcome"
                  className="form-input"
                  value={editForm.learningOutcome}
                  onChange={(event) =>
                    setEditForm({ ...editForm, learningOutcome: event.target.value })
                  }
                />
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.6rem' }}>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setEditing(null)}
              >
                Cancel
              </button>
              <button type="submit" className="btn btn-primary" disabled={busy}>
                Save membership
              </button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  );
}
