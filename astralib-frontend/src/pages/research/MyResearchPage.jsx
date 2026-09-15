import React, { useEffect, useState } from 'react';
import {
  BookmarkCheck,
  Loader2,
  AlertCircle,
  FileText,
  Eye,
  Trash2,
  X,
} from 'lucide-react';

import { api } from '../../services/api';

export function MyResearchPage() {
  const [savedResearch, setSavedResearch] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [selectedItem, setSelectedItem] = useState(null);
  const [openingId, setOpeningId] = useState(null);

  const [deletingId, setDeletingId] = useState(null);
  const [deleteError, setDeleteError] = useState('');

  useEffect(() => {
    loadSavedResearch();
  }, []);

  const loadSavedResearch = async () => {
    try {
      setLoading(true);
      setError('');

      const data = await api.savedResearch.getAll();

      setSavedResearch(
        Array.isArray(data) ? data : []
      );
    } catch (err) {
      console.error(
        'Failed to load saved research:',
        err
      );

      setError(
        err.message ||
          'Unable to load your saved research.'
      );
    } finally {
      setLoading(false);
    }
  };

  const handleOpen = async (id) => {
    try {
      setOpeningId(id);
      setError('');

      const item =
        await api.savedResearch.getById(id);

      setSelectedItem(item);
    } catch (err) {
      console.error(
        'Failed to open saved research:',
        err
      );

      setError(
        err.message ||
          'Unable to open saved research.'
      );
    } finally {
      setOpeningId(null);
    }
  };

  const handleDelete = async (id) => {
    const confirmed = window.confirm(
      'Are you sure you want to delete this saved research?'
    );

    if (!confirmed) {
      return;
    }

    try {
      setDeletingId(id);
      setDeleteError('');

      await api.savedResearch.delete(id);

      setSavedResearch((current) =>
        current.filter(
          (item) => item.id !== id
        )
      );

      if (selectedItem?.id === id) {
        setSelectedItem(null);
      }
    } catch (err) {
      console.error(
        'Failed to delete saved research:',
        err
      );

      setDeleteError(
        err.message ||
          'Unable to delete saved research.'
      );
    } finally {
      setDeletingId(null);
    }
  };

  const formatDate = (dateValue) => {
    if (!dateValue) {
      return '';
    }

    const date = new Date(dateValue);

    if (Number.isNaN(date.getTime())) {
      return dateValue;
    }

    return date.toLocaleString();
  };

  return (
    <div
      style={{
        padding: '2rem',
        width: '100%',
      }}
    >
      {/* Page Header */}
      <div
        style={{
          marginBottom: '2rem',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
            marginBottom: '0.5rem',
          }}
        >
          <BookmarkCheck size={28} />

          <h1
            style={{
              margin: 0,
              fontSize: '1.8rem',
            }}
          >
            My Research
          </h1>
        </div>

        <p
          style={{
            margin: 0,
            color: 'var(--text-muted)',
            maxWidth: '750px',
          }}
        >
          Review the answers and research notes
          you saved from the AstraLib Research
          Assistant.
        </p>
      </div>

      {/* Delete Error */}
      {deleteError && (
        <div
          className="card"
          style={{
            padding: '1rem 1.25rem',
            marginBottom: '1rem',
            display: 'flex',
            gap: '0.75rem',
            alignItems: 'center',
          }}
        >
          <AlertCircle size={18} />
          <span>{deleteError}</span>
        </div>
      )}

      {/* Loading */}
      {loading && (
        <div
          className="card"
          style={{
            padding: '2rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '0.75rem',
          }}
        >
          <Loader2
            size={20}
            style={{
              animation:
                'spin 1s linear infinite',
            }}
          />

          <span>Loading saved research...</span>
        </div>
      )}

      {/* Error */}
      {!loading && error && (
        <div
          className="card"
          style={{
            padding: '1.25rem',
            display: 'flex',
            gap: '0.75rem',
            alignItems: 'flex-start',
          }}
        >
          <AlertCircle size={20} />

          <div>
            <strong>
              Unable to load saved research
            </strong>

            <div
              style={{
                marginTop: '0.35rem',
                color: 'var(--text-muted)',
              }}
            >
              {error}
            </div>
          </div>
        </div>
      )}

      {/* Empty State */}
      {!loading &&
        !error &&
        savedResearch.length === 0 && (
          <div
            className="card"
            style={{
              padding: '3rem 2rem',
              textAlign: 'center',
            }}
          >
            <BookmarkCheck
              size={42}
              style={{
                marginBottom: '1rem',
              }}
            />

            <h2
              style={{
                marginBottom: '0.5rem',
              }}
            >
              No saved research yet
            </h2>

            <p
              style={{
                color: 'var(--text-muted)',
                margin: 0,
              }}
            >
              Answers saved from the Research
              Assistant will appear here.
            </p>
          </div>
        )}

      {/* Saved Research List */}
      {!loading &&
        !error &&
        savedResearch.length > 0 && (
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '1rem',
            }}
          >
            {savedResearch.map((item) => (
              <div
                key={item.id}
                className="card"
                style={{
                  padding: '1.4rem',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent:
                      'space-between',
                    alignItems: 'flex-start',
                    gap: '1rem',
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      gap: '0.9rem',
                      flex: 1,
                    }}
                  >
                    <div
                      style={{
                        marginTop: '0.15rem',
                      }}
                    >
                      <FileText size={20} />
                    </div>

                    <div style={{ flex: 1 }}>
                      <h3
                        style={{
                          margin:
                            '0 0 0.45rem 0',
                          fontSize: '1.05rem',
                        }}
                      >
                        {item.title ||
                          'Saved Research'}
                      </h3>

                      <div
                        style={{
                          fontWeight: 600,
                          marginBottom: '0.5rem',
                        }}
                      >
                        {item.question}
                      </div>

                      <p
                        style={{
                          margin: 0,
                          color:
                            'var(--text-muted)',
                          lineHeight: 1.6,
                          display:
                            '-webkit-box',
                          WebkitLineClamp: 3,
                          WebkitBoxOrient:
                            'vertical',
                          overflow: 'hidden',
                        }}
                      >
                        {item.answer}
                      </p>
                    </div>
                  </div>

                  <div
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'flex-end',
                      gap: '0.75rem',
                    }}
                  >
                    <div
                      style={{
                        fontSize: '0.8rem',
                        color:
                          'var(--text-muted)',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {formatDate(
                        item.createdAt
                      )}
                    </div>

                    <div
                      style={{
                        display: 'flex',
                        gap: '0.5rem',
                      }}
                    >
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() =>
                          handleOpen(item.id)
                        }
                        disabled={
                          openingId === item.id
                        }
                      >
                        {openingId === item.id ? (
                          <Loader2 size={16} />
                        ) : (
                          <Eye size={16} />
                        )}

                        <span>
                          {openingId === item.id
                            ? 'Opening...'
                            : 'Open'}
                        </span>
                      </button>

                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() =>
                          handleDelete(item.id)
                        }
                        disabled={
                          deletingId === item.id
                        }
                      >
                        {deletingId === item.id ? (
                          <Loader2 size={16} />
                        ) : (
                          <Trash2 size={16} />
                        )}

                        <span>
                          {deletingId === item.id
                            ? 'Deleting...'
                            : 'Delete'}
                        </span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

      {/* Open Research Modal */}
      {selectedItem && (
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
          onClick={() =>
            setSelectedItem(null)
          }
        >
          <div
            className="card"
            style={{
              width: 'min(850px, 95vw)',
              maxHeight: '85vh',
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
                alignItems: 'flex-start',
                gap: '1rem',
                marginBottom: '1.5rem',
              }}
            >
              <div>
                <h2
                  style={{
                    margin:
                      '0 0 0.35rem 0',
                  }}
                >
                  {selectedItem.title ||
                    'Saved Research'}
                </h2>

                <div
                  style={{
                    color:
                      'var(--text-muted)',
                    fontSize: '0.85rem',
                  }}
                >
                  {formatDate(
                    selectedItem.createdAt
                  )}
                </div>
              </div>

              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={() =>
                  setSelectedItem(null)
                }
              >
                <X size={16} />
              </button>
            </div>

            <div
              style={{
                marginBottom: '1.5rem',
              }}
            >
              <div
                style={{
                  fontSize: '0.8rem',
                  fontWeight: 700,
                  color:
                    'var(--text-muted)',
                  marginBottom: '0.4rem',
                  textTransform: 'uppercase',
                  letterSpacing: '0.04em',
                }}
              >
                Research Question
              </div>

              <div
                style={{
                  fontSize: '1.05rem',
                  fontWeight: 600,
                  lineHeight: 1.6,
                }}
              >
                {selectedItem.question}
              </div>
            </div>

            <div
              style={{
                marginBottom: '1.5rem',
              }}
            >
              <div
                style={{
                  fontSize: '0.8rem',
                  fontWeight: 700,
                  color:
                    'var(--text-muted)',
                  marginBottom: '0.4rem',
                  textTransform: 'uppercase',
                  letterSpacing: '0.04em',
                }}
              >
                Saved Answer
              </div>

              <div
                style={{
                  whiteSpace: 'pre-wrap',
                  lineHeight: 1.75,
                }}
              >
                {selectedItem.answer}
              </div>
            </div>

            {Array.isArray(
              selectedItem.citations
            ) &&
              selectedItem.citations
                .length > 0 && (
                <div>
                  <div
                    style={{
                      fontSize: '0.8rem',
                      fontWeight: 700,
                      color:
                        'var(--text-muted)',
                      marginBottom:
                        '0.75rem',
                      textTransform:
                        'uppercase',
                      letterSpacing:
                        '0.04em',
                    }}
                  >
                    Citations
                  </div>

                  <div
                    style={{
                      display: 'flex',
                      flexDirection:
                        'column',
                      gap: '0.75rem',
                    }}
                  >
                    {selectedItem.citations.map(
                      (citation, index) => (
                        <div
                          key={
                            citation.chunkId ||
                            index
                          }
                          className="card"
                          style={{
                            padding:
                              '0.9rem 1rem',
                          }}
                        >
                          <div
                            style={{
                              fontWeight:
                                600,
                            }}
                          >
                            {citation.title ||
                              'Source'}
                          </div>

                          {citation.author && (
                            <div
                              style={{
                                color:
                                  'var(--text-muted)',
                                fontSize:
                                  '0.85rem',
                                marginTop:
                                  '0.25rem',
                              }}
                            >
                              {
                                citation.author
                              }
                            </div>
                          )}

                          {citation.section && (
                            <div
                              style={{
                                color:
                                  'var(--text-muted)',
                                fontSize:
                                  '0.85rem',
                                marginTop:
                                  '0.25rem',
                              }}
                            >
                              Section:{' '}
                              {
                                citation.section
                              }
                            </div>
                          )}
                        </div>
                      )
                    )}
                  </div>
                </div>
              )}
          </div>
        </div>
      )}
    </div>
  );
}

export default MyResearchPage;