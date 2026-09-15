import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../services/api';
import { History, Clock } from 'lucide-react';
import {
  LoadingSpinner,
  EmptyState,
} from '../../components/common/Components';

export function ReadingHistoryPage() {
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // =========================================================
  // LOAD READING HISTORY
  // =========================================================

  useEffect(() => {
    async function loadHistory() {
      setLoading(true);
      setError('');

      try {
        const data = await api.readingHistory.getAll();

        setHistory(
          Array.isArray(data) ? data : []
        );
      } catch (err) {
        console.error(
          'Failed to load history:',
          err
        );

        setError(
          err.message ||
            'Unable to retrieve reading activity logs.'
        );
      } finally {
        setLoading(false);
      }
    }

    loadHistory();
  }, []);

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
          <h1>Reading Activity & History</h1>

          <p>
            Continue reading your digital books
            from your previously saved reading
            position.
          </p>
        </div>
      </div>

      {/* =====================================================
          ERROR
      ====================================================== */}

      {error && (
        <div className="banner banner-error">
          <span>{error}</span>
        </div>
      )}

      {/* =====================================================
          CONTENT
      ====================================================== */}

      {loading ? (
        <LoadingSpinner message="Loading reading history..." />
      ) : history.length === 0 ? (
        <EmptyState
          icon={History}
          title="No Reading History Recorded"
          description="Your reading timeline is empty. Open a digital book from the catalogue to begin reading."
          action={
            <Link
              to="/catalogue"
              className="btn btn-primary btn-sm"
            >
              Discover Books
            </Link>
          }
        />
      ) : (
        <div className="table-container">

          <table className="formal-table">

            {/* ===============================================
                TABLE HEADER
            ================================================ */}

            <thead>
              <tr>
                <th>Resource / Document</th>

                <th>
                  File Name / Format
                </th>

                <th>
                  Reading Progress
                </th>

                <th>
                  Last Accessed
                </th>

                <th
                  style={{
                    textAlign: 'right',
                  }}
                >
                  Resume Action
                </th>
              </tr>
            </thead>

            {/* ===============================================
                TABLE BODY
            ================================================ */}

            <tbody>

              {history.map((item) => {

                const progress =
                  typeof item.progressPercent ===
                  'number'
                    ? item.progressPercent
                    : 0;

                return (
                  <tr key={item.id}>

                    {/* =======================================
                        BOOK
                    ======================================== */}

                    <td>

                      <div
                        style={{
                          fontWeight: 600,
                          color:
                            'var(--primary-ink)',
                        }}
                      >
                        {item.bookTitle ||
                          (item.bookId
                            ? `Book #${item.bookId}`
                            : 'Digital Document')}
                      </div>

                      {item.bookId && (
                        <div
                          style={{
                            fontSize:
                              '0.75rem',
                            color:
                              'var(--text-muted)',
                            marginTop:
                              '0.2rem',
                          }}
                        >
                          Catalogue ID:{' '}
                          {item.bookId}
                        </div>
                      )}

                    </td>

                    {/* =======================================
                        FILE
                    ======================================== */}

                    <td>

                      <span
                        style={{
                          fontSize:
                            '0.85rem',
                          color:
                            'var(--text-secondary)',
                        }}
                      >
                        {item.resourceFile ||
                          'Digital Resource'}
                      </span>

                    </td>

                    {/* =======================================
                        PROGRESS
                    ======================================== */}

                    <td>

                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '0.6rem',
                          minWidth: '150px',
                        }}
                      >

                        {/* Progress Bar */}

                        <div
                          style={{
                            width: '100px',
                            height: '6px',
                            background:
                              '#E5E7EB',
                            borderRadius:
                              '5px',
                            overflow:
                              'hidden',
                          }}
                        >

                          <div
                            style={{
                              height: '100%',

                              width: `${Math.min(
                                100,
                                Math.max(
                                  0,
                                  progress
                                )
                              )}%`,

                              background:
                                'var(--primary-ink)',

                              transition:
                                'width 0.25s ease',
                            }}
                          />

                        </div>

                        <span
                          style={{
                            fontSize:
                              '0.78rem',

                            fontWeight: 600,

                            color:
                              'var(--text-secondary)',
                          }}
                        >
                          {progress}%
                        </span>

                      </div>

                    </td>

                    {/* =======================================
                        ACCESS TIME
                    ======================================== */}

                    <td>

                      <div
                        style={{
                          display: 'flex',

                          alignItems:
                            'center',

                          gap: '0.4rem',

                          fontSize:
                            '0.85rem',

                          color:
                            'var(--text-secondary)',
                        }}
                      >

                        <Clock
                          size={14}
                          color="var(--text-muted)"
                        />

                        <span>

                          {item.accessTime
                            ? new Date(
                                item.accessTime
                              ).toLocaleString()
                            : 'Recent'}

                        </span>

                      </div>

                    </td>

                    {/* =======================================
                        RESUME BUTTON
                    ======================================== */}

                    <td
                      style={{
                        textAlign: 'right',
                      }}
                    >

                      {/*
                        IMPORTANT:

                        DocumentReaderPage expects
                        BOOK ID.

                        Sherlock:
                        bookId = 3003
                        resourceId = 1

                        Therefore:

                        CORRECT:
                        /reader/3003

                        WRONG:
                        /reader/1
                      */}

                      {item.bookId ? (

                        <Link
                          to={`/reader/${item.bookId}`}
                          className="btn btn-primary btn-sm"
                        >
                          {progress > 0
                            ? 'Resume Reading'
                            : 'Open Reader'}
                        </Link>

                      ) : null}

                    </td>

                  </tr>
                );
              })}

            </tbody>

          </table>

        </div>
      )}

    </div>
  );
}

export default ReadingHistoryPage;