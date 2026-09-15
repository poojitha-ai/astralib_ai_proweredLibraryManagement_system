import React, { useEffect, useState } from 'react';
import { api } from '../../services/api';

import {
  Database,
  RefreshCw,
  CheckCircle2,
  AlertCircle,
  Play,
  Brain,
  BookOpen,
} from 'lucide-react';

import {
  Badge,
  LoadingSpinner,
} from '../../components/common/Components';

export function DatasetLoaderPage() {
  const [stats, setStats] = useState(null);
  const [vectorStats, setVectorStats] = useState(null);

  const [loading, setLoading] = useState(true);

  const [
    runningLoader,
    setRunningLoader,
  ] = useState(false);

  const [
    ingestingSherlock,
    setIngestingSherlock,
  ] = useState(false);

  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [
    sherlockResult,
    setSherlockResult,
  ] = useState(null);

  // =========================================================
  // LOAD DATABASE STATS
  // =========================================================

  const loadStats = async () => {
    setLoading(true);
    setError('');

    try {
      const results =
        await Promise.allSettled([
          api.admin.getDatasetStats(),
          api.admin.getVectorStats(),
        ]);

      if (
        results[0].status ===
        'fulfilled'
      ) {
        setStats(
          results[0].value
        );
      }

      if (
        results[1].status ===
        'fulfilled'
      ) {
        setVectorStats(
          results[1].value
        );
      }

      if (
        results[0].status ===
          'rejected' &&
        results[1].status ===
          'rejected'
      ) {
        throw new Error(
          'Unable to retrieve dataset and vector statistics.'
        );
      }
    } catch (err) {
      console.error(
        'Failed to load admin stats:',
        err
      );

      setError(
        err.message ||
          'Unable to retrieve repository statistics.'
      );
    } finally {
      setLoading(false);
    }
  };

  // =========================================================
  // LOAD STATS ON PAGE OPEN
  // =========================================================

  useEffect(() => {
    loadStats();
  }, []);

  // =========================================================
  // RUN NORMAL DATASET LOADER
  // =========================================================

  const handleRunLoader = async () => {
    setRunningLoader(true);

    setError('');
    setSuccess('');

    try {
      const res =
        await api.admin.loadDatasets();

      setSuccess(
        'Datasets loaded successfully into MySQL database.'
      );

      if (res?.stats) {
        setStats(
          res.stats
        );
      } else {
        await loadStats();
      }
    } catch (err) {
      console.error(
        'Dataset loading failure:',
        err
      );

      setError(
        err.message ||
          'Dataset loading operation failed.'
      );
    } finally {
      setRunningLoader(false);
    }
  };

  // =========================================================
  // INGEST ONLY SHERLOCK HOLMES
  //
  // Book ID = 3003
  // Digital Resource ID = 1
  // Document ID = GUTENBERG-1661
  // =========================================================

  const handleIngestSherlock =
    async () => {
      setIngestingSherlock(true);

      setError('');
      setSuccess('');
      setSherlockResult(null);

      try {
        const result =
          await api.admin
            .ingestBookVectors(3003);

        setSherlockResult(
          result
        );

        setSuccess(
          'Sherlock Holmes full-text vectors were generated successfully.'
        );

        // Reload vector counts
        try {
          const updatedVectorStats =
            await api.admin
              .getVectorStats();

          setVectorStats(
            updatedVectorStats
          );
        } catch (statsError) {
          console.warn(
            'Could not refresh vector stats:',
            statsError
          );
        }
      } catch (err) {
        console.error(
          'Sherlock vector ingestion failed:',
          err
        );

        setError(
          err.message ||
            'Sherlock Holmes vector ingestion failed.'
        );
      } finally {
        setIngestingSherlock(
          false
        );
      }
    };

  // =========================================================
  // PAGE
  // =========================================================

  return (
    <div className="page-container">

      {/* =====================================================
          HEADER
      ====================================================== */}

      <div className="page-header">
        <div className="page-title-group">
          <h1>
            Institutional Dataset Loader
          </h1>

          <p>
            Manage relational datasets and
            semantic-search vector ingestion
            pipelines used by AstraLib.
          </p>
        </div>

        <div className="page-actions">
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={loadStats}
            disabled={
              loading ||
              runningLoader ||
              ingestingSherlock
            }
          >
            <RefreshCw
              size={14}
              className={
                loading
                  ? 'loading-spinner'
                  : ''
              }
            />

            Refresh Counts
          </button>
        </div>
      </div>

      {/* =====================================================
          ERROR
      ====================================================== */}

      {error && (
        <div className="banner banner-error">
          <AlertCircle size={16} />
          <span>{error}</span>
        </div>
      )}

      {/* =====================================================
          SUCCESS
      ====================================================== */}

      {success && (
        <div
          className="banner banner-info"
          style={{
            backgroundColor:
              'var(--status-success-bg)',

            color:
              'var(--status-success-text)',

            borderColor:
              'var(--status-success-border)',
          }}
        >
          <CheckCircle2 size={16} />

          <span>{success}</span>
        </div>
      )}

      {/* =====================================================
          MAIN GRID
      ====================================================== */}

      <div
        style={{
          display: 'grid',

          gridTemplateColumns:
            'repeat(12, 1fr)',

          gap: '1.75rem',
        }}
      >

        {/* ===================================================
            LEFT SIDE
        ==================================================== */}

        <div
          style={{
            gridColumn: 'span 8',
          }}
        >

          {/* =================================================
              MYSQL DATASETS
          ================================================== */}

          <div
            className="card"
            style={{
              padding: '2rem',

              marginBottom:
                '1.75rem',
            }}
          >
            <div className="card-header">
              <div
                style={{
                  display: 'flex',

                  alignItems:
                    'center',

                  gap: '0.5rem',
                }}
              >
                <Database
                  size={18}
                  color="var(--primary-ink)"
                />

                <h2 className="card-title">
                  Seeded Database Counts
                </h2>
              </div>

              <Badge variant="primary">
                MySQL Repository
              </Badge>
            </div>

            {loading ? (
              <LoadingSpinner
                message="Checking database table record counts..."
              />
            ) : (
              <div
                className="table-container"
                style={{
                  marginBottom:
                    '1.5rem',
                }}
              >
                <table className="formal-table">
                  <thead>
                    <tr>
                      <th>
                        Entity Dataset
                      </th>

                      <th>
                        Record Count
                      </th>

                      <th>
                        Status
                      </th>
                    </tr>
                  </thead>

                  <tbody>
                    <tr>
                      <td>
                        <strong>
                          Books & Monographs
                        </strong>
                      </td>

                      <td>
                        <code>
                          {stats
                            ?.booksCount ??
                            0}
                        </code>
                      </td>

                      <td>
                        <Badge variant="success">
                          Loaded
                        </Badge>
                      </td>
                    </tr>

                    <tr>
                      <td>
                        <strong>
                          Scholarly Papers
                        </strong>
                      </td>

                      <td>
                        <code>
                          {stats
                            ?.scholarlyPapersCount ??
                            0}
                        </code>
                      </td>

                      <td>
                        <Badge variant="success">
                          Loaded
                        </Badge>
                      </td>
                    </tr>

                    <tr>
                      <td>
                        <strong>
                          Authors
                        </strong>
                      </td>

                      <td>
                        <code>
                          {stats
                            ?.authorsCount ??
                            0}
                        </code>
                      </td>

                      <td>
                        <Badge variant="success">
                          Loaded
                        </Badge>
                      </td>
                    </tr>

                    <tr>
                      <td>
                        <strong>
                          Categories & Disciplines
                        </strong>
                      </td>

                      <td>
                        <code>
                          {stats
                            ?.categoriesCount ??
                            0}
                        </code>
                      </td>

                      <td>
                        <Badge variant="success">
                          Loaded
                        </Badge>
                      </td>
                    </tr>

                    <tr>
                      <td>
                        <strong>
                          Academic Users
                        </strong>
                      </td>

                      <td>
                        <code>
                          {stats
                            ?.usersCount ??
                            0}
                        </code>
                      </td>

                      <td>
                        <Badge variant="success">
                          Loaded
                        </Badge>
                      </td>
                    </tr>

                    <tr>
                      <td>
                        <strong>
                          Interaction Activities
                        </strong>
                      </td>

                      <td>
                        <code>
                          {stats
                            ?.activitiesProcessedCount ??
                            0}
                        </code>
                      </td>

                      <td>
                        <Badge variant="default">
                          Processed
                        </Badge>
                      </td>
                    </tr>

                    <tr>
                      <td>
                        <strong>
                          Member Favorites
                        </strong>
                      </td>

                      <td>
                        <code>
                          {stats
                            ?.favoritesCount ??
                            0}
                        </code>
                      </td>

                      <td>
                        <Badge variant="default">
                          Recorded
                        </Badge>
                      </td>
                    </tr>

                    <tr>
                      <td>
                        <strong>
                          Reading History Records
                        </strong>
                      </td>

                      <td>
                        <code>
                          {stats
                            ?.readingHistoryCount ??
                            0}
                        </code>
                      </td>

                      <td>
                        <Badge variant="default">
                          Recorded
                        </Badge>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            )}

            <button
              type="button"
              className="btn btn-primary btn-lg"
              onClick={
                handleRunLoader
              }
              disabled={
                runningLoader ||
                ingestingSherlock
              }
            >
              {runningLoader ? (
                <>
                  <div
                    className="loading-spinner"
                    style={{
                      width: '16px',
                      height: '16px',
                      borderWidth:
                        '2px',
                    }}
                  />

                  <span>
                    Loading All Datasets...
                  </span>
                </>
              ) : (
                <>
                  <Play size={16} />

                  <span>
                    Execute Dataset Seeder
                  </span>
                </>
              )}
            </button>
          </div>

          {/* =================================================
              SEMANTIC VECTOR INGESTION
          ================================================== */}

          <div
            className="card"
            style={{
              padding: '2rem',
            }}
          >
            <div className="card-header">
              <div
                style={{
                  display: 'flex',

                  alignItems:
                    'center',

                  gap: '0.5rem',
                }}
              >
                <Brain
                  size={18}
                  color="var(--primary-ink)"
                />

                <h2 className="card-title">
                  Semantic Search Vector Pipeline
                </h2>
              </div>

              <Badge variant="gold">
                Gemini Embeddings
              </Badge>
            </div>

            <p
              style={{
                fontSize:
                  '0.88rem',

                color:
                  'var(--text-secondary)',

                lineHeight:
                  1.6,

                marginBottom:
                  '1.25rem',
              }}
            >
              Process the real full text of a
              digital book into semantic chunks
              and create embedding vectors used
              by AstraLib Smart Search.
            </p>

            {/* Sherlock Book */}

            <div
              style={{
                border:
                  '1px solid var(--border-default)',

                padding:
                  '1.25rem',

                background:
                  'var(--bg-app)',

                marginBottom:
                  '1.25rem',
              }}
            >
              <div
                style={{
                  display: 'flex',

                  justifyContent:
                    'space-between',

                  alignItems:
                    'center',

                  gap: '1rem',
                }}
              >
                <div>
                  <div
                    style={{
                      display:
                        'flex',

                      alignItems:
                        'center',

                      gap:
                        '0.5rem',

                      marginBottom:
                        '0.35rem',
                    }}
                  >
                    <BookOpen
                      size={17}
                      color="var(--primary-ink)"
                    />

                    <strong
                      style={{
                        color:
                          'var(--primary-ink)',
                      }}
                    >
                      The Adventures of Sherlock Holmes
                    </strong>
                  </div>

                  <div
                    style={{
                      fontSize:
                        '0.78rem',

                      color:
                        'var(--text-muted)',
                    }}
                  >
                    Book ID: 3003
                    {' · '}
                    Gutenberg ID:
                    GUTENBERG-1661
                    {' · '}
                    EPUB full text
                  </div>
                </div>

                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={
                    handleIngestSherlock
                  }
                  disabled={
                    ingestingSherlock ||
                    runningLoader
                  }
                >
                  {ingestingSherlock ? (
                    <>
                      <div
                        className="loading-spinner"
                        style={{
                          width:
                            '16px',

                          height:
                            '16px',

                          borderWidth:
                            '2px',
                        }}
                      />

                      <span>
                        Generating Vectors...
                      </span>
                    </>
                  ) : (
                    <>
                      <Brain
                        size={16}
                      />

                      <span>
                        Ingest Sherlock Vectors
                      </span>
                    </>
                  )}
                </button>
              </div>
            </div>

            {/* =================================================
                VECTOR STATS
            ================================================== */}

            <div
              style={{
                display: 'grid',

                gridTemplateColumns:
                  'repeat(4, 1fr)',

                gap:
                  '0.75rem',
              }}
            >
              <VectorStat
                label="Total Vectors"
                value={
                  vectorStats
                    ?.totalVectorRecords ??
                  0
                }
              />

              <VectorStat
                label="Book Vectors"
                value={
                  vectorStats
                    ?.bookVectorsCount ??
                  0
                }
              />

              <VectorStat
                label="Paper Vectors"
                value={
                  vectorStats
                    ?.paperVectorsCount ??
                  0
                }
              />

              <VectorStat
                label="Document Vectors"
                value={
                  vectorStats
                    ?.documentVectorsCount ??
                  0
                }
              />
            </div>

            {/* =================================================
                SHERLOCK INGEST RESULT
            ================================================== */}

            {sherlockResult && (
              <div
                style={{
                  marginTop:
                    '1.5rem',

                  padding:
                    '1rem',

                  border:
                    '1px solid var(--status-success-border)',

                  background:
                    'var(--status-success-bg)',
                }}
              >
                <div
                  style={{
                    fontWeight:
                      600,

                    color:
                      'var(--status-success-text)',

                    marginBottom:
                      '0.6rem',
                  }}
                >
                  Sherlock Vector Ingestion Result
                </div>

                <div
                  style={{
                    display: 'grid',

                    gridTemplateColumns:
                      'repeat(2, minmax(0, 1fr))',

                    gap:
                      '0.5rem',

                    fontSize:
                      '0.8rem',
                  }}
                >
                  <div>
                    Document ID:{' '}
                    <strong>
                      {sherlockResult.documentId ||
                        'GUTENBERG-1661'}
                    </strong>
                  </div>

                  <div>
                    Documents Processed:{' '}
                    <strong>
                      {sherlockResult
                        .documentsProcessed ??
                        0}
                    </strong>
                  </div>

                  <div>
                    Chunks Created:{' '}
                    <strong>
                      {sherlockResult
                        .chunksCreated ??
                        0}
                    </strong>
                  </div>

                  <div>
                    Embeddings Generated:{' '}
                    <strong>
                      {sherlockResult
                        .embeddingsGenerated ??
                        0}
                    </strong>
                  </div>

                  <div>
                    Vectors Stored:{' '}
                    <strong>
                      {sherlockResult
                        .vectorsStored ??
                        0}
                    </strong>
                  </div>

                  <div>
                    Sherlock Vector Records:{' '}
                    <strong>
                      {sherlockResult
                        .vectorRecordsForBook ??
                        0}
                    </strong>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* ===================================================
            RIGHT SIDE
        ==================================================== */}

        <div
          style={{
            gridColumn: 'span 4',
          }}
        >
          <div
            className="card"
            style={{
              padding: '1.5rem',

              backgroundColor:
                'var(--bg-app)',

              border:
                '1px solid var(--border-default)',
            }}
          >
            <h3
              style={{
                fontSize:
                  '1.05rem',

                color:
                  'var(--primary-ink)',

                marginBottom:
                  '0.75rem',
              }}
            >
              Dataset Pipeline Information
            </h3>

            <p
              style={{
                fontSize:
                  '0.85rem',

                color:
                  'var(--text-secondary)',

                lineHeight:
                  '1.6',

                marginBottom:
                  '0.75rem',
              }}
            >
              The normal dataset seeder reads
              preprocessed JSON datasets from
              the <code>data/</code> folder and
              populates relational MySQL tables.
            </p>

            <p
              style={{
                fontSize:
                  '0.85rem',

                color:
                  'var(--text-secondary)',

                lineHeight:
                  '1.6',

                marginBottom:
                  '0.75rem',
              }}
            >
              The semantic pipeline is different.
              It reads actual full-text digital
              resources such as EPUB books,
              separates them into searchable
              chunks, creates Gemini embeddings,
              and stores those vectors in{' '}
              <code>vector_records</code>.
            </p>

            <div
              style={{
                borderTop:
                  '1px solid var(--border-default)',

                paddingTop:
                  '0.75rem',

                marginTop:
                  '0.75rem',
              }}
            >
              <div
                style={{
                  fontSize:
                    '0.78rem',

                  color:
                    'var(--text-muted)',

                  lineHeight:
                    1.6,
                }}
              >
                For initial testing, only
                Sherlock Holmes is ingested.
                This prevents accidentally
                processing thousands of
                catalogue records while the
                semantic-search pipeline is
                being validated.
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// =========================================================
// VECTOR STAT COMPONENT
// =========================================================

function VectorStat({
  label,
  value,
}) {
  return (
    <div
      style={{
        padding:
          '0.85rem',

        border:
          '1px solid var(--border-light)',

        background:
          '#FFFFFF',
      }}
    >
      <div
        style={{
          fontSize:
            '0.72rem',

          color:
            'var(--text-muted)',

          marginBottom:
            '0.2rem',
        }}
      >
        {label}
      </div>

      <div
        style={{
          fontSize:
            '1.2rem',

          fontWeight:
            700,

          color:
            'var(--primary-ink)',
        }}
      >
        {value}
      </div>
    </div>
  );
}

export default DatasetLoaderPage;