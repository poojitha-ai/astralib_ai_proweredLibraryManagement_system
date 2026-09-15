import React, { useState, useEffect } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { api } from '../../services/api';
import {
  Send,
  AlertCircle,
  Library,
  BookmarkPlus,
  CheckCircle2,
} from 'lucide-react';
import { Badge } from '../../components/common/Components';

export function ResearchAssistantPage() {
  const [searchParams] = useSearchParams();

  const [question, setQuestion] = useState(searchParams.get('q') || '');
  const [topK, setTopK] = useState(3);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const [conversationHistory, setConversationHistory] = useState([]);

  const [savingId, setSavingId] = useState(null);
  const [saveMessage, setSaveMessage] = useState('');
  const [saveError, setSaveError] = useState('');

  useEffect(() => {
    const initialQ = searchParams.get('q');

    if (initialQ) {
      setQuestion(initialQ);
      handleAskQuestion(initialQ);
    }
  }, [searchParams]);

  const handleAskQuestion = async (qText) => {
    const q = (qText || question).trim();

    if (!q) {
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await api.rag.ask(q, topK);

      const newEntry = {
        id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
        question: q,
        answer:
          response.answer ||
          'No grounded synthesis could be generated.',
        citations: response.citations || [],
        timestamp: new Date().toLocaleTimeString([], {
          hour: '2-digit',
          minute: '2-digit',
        }),
        saved: false,
      };

      setConversationHistory((prev) => [
        newEntry,
        ...prev,
      ]);

      setQuestion('');
    } catch (err) {
      console.error('RAG query failure:', err);

      setError(
        err.message ||
          'The AstraLib Research Assistant is temporarily unable to respond. Please ensure the backend and vector index are initialized.'
      );
    } finally {
      setLoading(false);
    }
  };

  const handleSaveAnswer = async (item) => {
    if (!item || item.saved) {
      return;
    }

    const itemKey = item.id || item.timestamp;

    setSavingId(itemKey);
    setSaveMessage('');
    setSaveError('');

    try {
      await api.savedResearch.save({
        title: item.question,
        question: item.question,
        answer: item.answer,
        citations: item.citations || [],
      });

      setConversationHistory((prev) =>
        prev.map((entry) =>
          (entry.id || entry.timestamp) === itemKey
            ? { ...entry, saved: true }
            : entry
        )
      );

      setSaveMessage('Answer saved to My Research.');
    } catch (err) {
      console.error('Save research failure:', err);

      setSaveError(
        err.message ||
          'Unable to save this answer. Please try again.'
      );
    } finally {
      setSavingId(null);
    }
  };

  const onSubmit = (e) => {
    e.preventDefault();
    handleAskQuestion(question);
  };

  return (
    <div className="page-container">

      {/* =========================================================
          PAGE HEADER
      ========================================================== */}

      <div className="page-header">
        <div className="page-title-group">
          <h1>Institutional Research Assistant</h1>

          <p>
            Synthesize grounded academic answers with verifiable
            document citations from AstraLib repository.
          </p>
        </div>
      </div>

      {/* =========================================================
          RESEARCH QUESTION FORM
      ========================================================== */}

      <div
        className="card"
        style={{
          marginBottom: '2rem',
          padding: '1.75rem',
        }}
      >
        <form onSubmit={onSubmit}>

          <div
            className="form-group"
            style={{ marginBottom: '1rem' }}
          >
            <label
              className="form-label"
              htmlFor="research-question"
            >
              Research Inquiry / Synthesis Prompt
            </label>

            <textarea
              id="research-question"
              className="form-textarea"
              rows={3}
              style={{
                fontSize: '0.95rem',
                lineHeight: '1.6',
              }}
              placeholder="e.g. How does Sherlock Holmes use observation and logical reasoning to solve problems?"
              value={question}
              onChange={(e) =>
                setQuestion(e.target.value)
              }
              disabled={loading}
              autoFocus
            />
          </div>

          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: '1rem',
            }}
          >

            {/* Citation Depth */}

            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.75rem',
                fontSize: '0.85rem',
              }}
            >
              <span
                style={{
                  color: 'var(--text-secondary)',
                  fontWeight: 600,
                }}
              >
                Citations Depth:
              </span>

              <select
                className="form-select"
                style={{
                  width: 'auto',
                  padding: '0.35rem 0.65rem',
                }}
                value={topK}
                onChange={(e) =>
                  setTopK(Number(e.target.value))
                }
                disabled={loading}
              >
                <option value={2}>
                  2 Sources (Concise)
                </option>

                <option value={3}>
                  3 Sources (Standard)
                </option>

                <option value={5}>
                  5 Sources (Comprehensive)
                </option>
              </select>
            </div>

            {/* Ask Button */}

            <button
              type="submit"
              className="btn btn-primary btn-lg"
              disabled={
                loading || !question.trim()
              }
              style={{ minWidth: '180px' }}
            >
              {loading ? (
                <>
                  <div
                    className="loading-spinner"
                    style={{
                      width: '16px',
                      height: '16px',
                      borderWidth: '2px',
                    }}
                  />

                  <span>Synthesizing...</span>
                </>
              ) : (
                <>
                  <Send size={16} />
                  <span>
                    Ask Research Assistant
                  </span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>

      {/* =========================================================
          ERROR MESSAGE
      ========================================================== */}

      {error && (
        <div
          className="banner banner-error"
          style={{ marginBottom: '2rem' }}
        >
          <AlertCircle size={18} />
          <span>{error}</span>
        </div>
      )}


      {saveMessage && (
        <div
          className="banner"
          style={{
            marginBottom: '2rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.6rem',
            border: '1px solid var(--border-default)',
            background: '#FFFFFF',
            color: 'var(--primary-ink)',
          }}
        >
          <CheckCircle2 size={18} />
          <span>{saveMessage}</span>
        </div>
      )}

      {saveError && (
        <div
          className="banner banner-error"
          style={{ marginBottom: '2rem' }}
        >
          <AlertCircle size={18} />
          <span>{saveError}</span>
        </div>
      )}

      {/* =========================================================
          EMPTY STATE
      ========================================================== */}

      {conversationHistory.length === 0 ? (

        <div
          className="card"
          style={{
            padding: '3rem 2rem',
            textAlign: 'center',
            backgroundColor: '#FFFFFF',
          }}
        >
          <Library
            size={44}
            strokeWidth={1.5}
            color="var(--primary-ink)"
            style={{
              margin: '0 auto 1rem auto',
            }}
          />

          <h3
            style={{
              fontSize: '1.25rem',
              color: 'var(--primary-ink)',
              marginBottom: '0.5rem',
            }}
          >
            Academic Grounded Synthesis
          </h3>

          <p
            style={{
              maxWidth: '600px',
              margin: '0 auto 1.5rem auto',
              color: 'var(--text-secondary)',
              fontSize: '0.92rem',
            }}
          >
            Unlike open-ended generative chat,
            the AstraLib Research Assistant
            inspects stored library monographs
            and research papers to substantiate
            answers with source references.
          </p>

          <div
            style={{
              display: 'flex',
              gap: '0.75rem',
              justifyContent: 'center',
              flexWrap: 'wrap',
            }}
          >
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() =>
                setQuestion(
                  'How does Sherlock Holmes use observation and logical reasoning to solve problems?'
                )
              }
            >
              "Sherlock Holmes and Deduction"
            </button>

            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() =>
                setQuestion(
                  'What are the foundational principles of artificial intelligence discussed in the library?'
                )
              }
            >
              "Principles of Artificial Intelligence"
            </button>
          </div>
        </div>

      ) : (

        /* =======================================================
            CONVERSATION RESULTS
        ======================================================== */

        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '2rem',
          }}
        >

          {conversationHistory.map(
            (item, idx) => (

              <div
                key={idx}
                className="card"
                style={{
                  padding: '2rem',
                  border:
                    '1px solid var(--border-default)',
                }}
              >

                {/* QUESTION */}

                <div
                  style={{
                    borderBottom:
                      '1px solid var(--border-light)',
                    paddingBottom: '1rem',
                    marginBottom: '1.25rem',
                    display: 'flex',
                    justifyContent:
                      'space-between',
                    alignItems: 'flex-start',
                  }}
                >

                  <div>
                    <div
                      style={{
                        fontSize: '0.75rem',
                        textTransform:
                          'uppercase',
                        color:
                          'var(--accent-gold)',
                        fontWeight: 700,
                        letterSpacing:
                          '0.5px',
                      }}
                    >
                      Research Inquiry
                    </div>

                    <h2
                      style={{
                        fontSize: '1.3rem',
                        color:
                          'var(--primary-ink)',
                        marginTop: '0.25rem',
                        fontFamily:
                          'var(--font-serif)',
                      }}
                    >
                      {item.question}
                    </h2>
                  </div>

                  <span
                    style={{
                      fontSize: '0.78rem',
                      color:
                        'var(--text-muted)',
                    }}
                  >
                    {item.timestamp}
                  </span>
                </div>

                {/* =================================================
                    SYNTHESIZED ANSWER
                ================================================== */}

                <div
                  style={{
                    marginBottom: '1.75rem',
                  }}
                >
                  <div
                    style={{
                      fontSize: '0.8rem',
                      textTransform:
                        'uppercase',
                      color:
                        'var(--secondary-slate)',
                      fontWeight: 600,
                      marginBottom: '0.5rem',
                    }}
                  >
                    Synthesized Answer:
                  </div>

                  <div
                    style={{
                      fontSize: '1rem',
                      lineHeight: '1.75',
                      color:
                        'var(--text-primary)',
                      background:
                        'var(--bg-app)',
                      padding:
                        '1.25rem 1.5rem',
                      borderRadius:
                        'var(--radius-sm)',
                      border:
                        '1px solid var(--border-light)',
                      whiteSpace: 'pre-line',
                    }}
                  >
                    {item.answer}
                  </div>
                </div>

                {/* =================================================
                    CITATIONS
                ================================================== */}

                <div>

                  <div
                    style={{
                      fontSize: '0.8rem',
                      textTransform:
                        'uppercase',
                      color:
                        'var(--primary-ink)',
                      fontWeight: 700,
                      marginBottom: '0.75rem',
                    }}
                  >
                    Referenced Institutional
                    Sources (
                    {item.citations?.length ||
                      0}
                    ):
                  </div>

                  {item.citations?.length ===
                  0 ? (

                    <p
                      style={{
                        fontSize: '0.85rem',
                        color:
                          'var(--text-muted)',
                      }}
                    >
                      No specific document
                      chunks met the minimum
                      relevance threshold for
                      this query.
                    </p>

                  ) : (

                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns:
                          'repeat(auto-fit, minmax(280px, 1fr))',
                        gap: '1rem',
                      }}
                    >

                      {item.citations.map(
                        (cit, cIdx) => (

                          <div
                            key={cIdx}
                            style={{
                              padding: '1rem',
                              borderRadius:
                                'var(--radius-sm)',
                              border:
                                '1px solid var(--border-default)',
                              background:
                                '#FFFFFF',
                              display: 'flex',
                              flexDirection:
                                'column',
                              justifyContent:
                                'space-between',
                            }}
                          >

                            {/* Citation Information */}

                            <div>

                              <div
                                style={{
                                  display:
                                    'flex',
                                  justifyContent:
                                    'space-between',
                                  alignItems:
                                    'center',
                                  marginBottom:
                                    '0.4rem',
                                  gap: '0.5rem',
                                }}
                              >

                                <Badge variant="primary">
                                  Citation [
                                  {cit.chunkId ||
                                    `Chunk ${
                                      cit.chunkIndex +
                                      1
                                    }`}
                                  ]
                                </Badge>

                                <span
                                  style={{
                                    fontSize:
                                      '0.75rem',
                                    fontWeight:
                                      600,
                                    color:
                                      'var(--accent-gold)',
                                    whiteSpace:
                                      'nowrap',
                                  }}
                                >
                                  Match:{' '}
                                  {Math.round(
                                    (cit.score ||
                                      0.8) *
                                      100
                                  )}
                                  %
                                </span>
                              </div>

                              <h4
                                style={{
                                  fontSize:
                                    '0.95rem',
                                  color:
                                    'var(--primary-ink)',
                                  margin:
                                    '0.35rem 0',
                                }}
                              >
                                {cit.title}
                              </h4>

                              {cit.author && (
                                <div
                                  style={{
                                    fontSize:
                                      '0.8rem',
                                    color:
                                      'var(--text-secondary)',
                                    marginBottom:
                                      '0.35rem',
                                  }}
                                >
                                  By {cit.author}
                                </div>
                              )}

                              {cit.section && (
                                <div
                                  style={{
                                    fontSize:
                                      '0.78rem',
                                    color:
                                      'var(--text-muted)',
                                  }}
                                >
                                  Section:{' '}
                                  {cit.section}
                                </div>
                              )}

                            </div>

                            {/* =========================================
                                CITATION NAVIGATION

                                IMPORTANT:
                                documentId is the vector/document identity
                                such as GUTENBERG-1661.

                                bookId is the AstraLib database Book ID
                                such as 3003.

                                /books/:id requires bookId.
                            ========================================== */}

                            <div
                              style={{
                                marginTop:
                                  '0.75rem',
                                paddingTop:
                                  '0.75rem',
                                borderTop:
                                  '1px solid var(--border-light)',
                              }}
                            >

                              {cit.bookId ? (

                                <Link
                                  to={`/reader/${cit.bookId}?chunkId=${encodeURIComponent(cit.chunkId)}`}
                                  className="btn btn-secondary btn-sm"
                                  style={{
                                    width:
                                      '100%',
                                  }}
                                >
                                  Open Cited Passage
                                </Link>

                              ) : (

                                <button
                                  type="button"
                                  className="btn btn-secondary btn-sm"
                                  style={{
                                    width:
                                      '100%',
                                  }}
                                  disabled
                                  title="This citation is not linked to an AstraLib book record."
                                >
                                  Document Record
                                  Unavailable
                                </button>

                              )}

                            </div>

                          </div>
                        )
                      )}

                    </div>
                  )}

                </div>

                {/* =================================================
                    ANSWER ACTIONS
                ================================================== */}

                <div
                  style={{
                    marginTop: '1.5rem',
                    paddingTop: '1rem',
                    borderTop: '1px solid var(--border-light)',
                    display: 'flex',
                    justifyContent: 'flex-end',
                  }}
                >
                  <button
                    type="button"
                    className="btn btn-primary btn-sm"
                    onClick={() => handleSaveAnswer(item)}
                    disabled={
                      item.saved ||
                      savingId === (item.id || item.timestamp)
                    }
                  >
                    {item.saved ? (
                      <>
                        <CheckCircle2 size={16} />
                        <span>Saved</span>
                      </>
                    ) : savingId ===
                      (item.id || item.timestamp) ? (
                      <>
                        <div
                          className="loading-spinner"
                          style={{
                            width: '14px',
                            height: '14px',
                            borderWidth: '2px',
                          }}
                        />
                        <span>Saving...</span>
                      </>
                    ) : (
                      <>
                        <BookmarkPlus size={16} />
                        <span>Save Answer</span>
                      </>
                    )}
                  </button>
                </div>

              </div>
            )
          )}

        </div>
      )}

    </div>
  );
}