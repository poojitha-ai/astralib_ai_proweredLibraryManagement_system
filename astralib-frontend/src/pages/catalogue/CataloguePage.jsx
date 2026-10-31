import React, { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../../services/api';
import {
  Search,
  BookOpen,
  Bookmark,
  BookmarkCheck,
  ArrowUpDown,
  SlidersHorizontal,
  X,
} from 'lucide-react';
import { Badge, LoadingSpinner, EmptyState } from '../../components/common/Components';
import { BookCover } from '../../components/library/BookCover';
import { LEVEL_LABEL, LEVEL_ORDER, LevelChip } from '../../components/library/LevelChip';

// =========================================================
// CATEGORY TILES
//
// Tiles are views over stored metadata (genres, subjects, the single
// catalogue category). A tile with no matching books is not rendered,
// so the page never advertises an empty shelf.
// =========================================================

const genreIs = (book, ...names) =>
  (book.genres || []).some((genre) =>
    names.some((name) => genre && genre.toLowerCase() === name.toLowerCase())
  );

const subjectHas = (book, ...fragments) =>
  (book.subjects || []).some((subject) =>
    fragments.some((fragment) => subject && subject.toLowerCase().includes(fragment.toLowerCase()))
  );

/* Whole-word subject match: "java" must not select "JavaScript". */
const subjectWord = (book, word) => {
  const pattern = new RegExp(`\\b${word}\\b`, 'i');

  return (book.subjects || []).some((subject) => pattern.test(subject || ''));
};

const categoryIs = (book, name) => book.category?.name === name;

const TILE_GROUPS = [
  {
    title: 'Programming languages',
    tiles: [
      {
        id: 'python',
        label: 'Python',
        match: (b) => genreIs(b, 'Python') || subjectHas(b, 'python'),
      },
      {
        id: 'java',
        label: 'Java',
        match: (b) => genreIs(b, 'Java') || subjectWord(b, 'java'),
      },
      {
        id: 'c',
        label: 'C',
        match: (b) => genreIs(b, 'C') || subjectHas(b, 'c (computer program'),
      },
      {
        id: 'cpp',
        label: 'C++',
        match: (b) => genreIs(b, 'C++') || subjectHas(b, 'c++'),
      },
      {
        id: 'javascript',
        label: 'JavaScript',
        match: (b) => genreIs(b, 'JavaScript') || subjectHas(b, 'javascript'),
      },
      {
        id: 'csharp',
        label: 'C#',
        match: (b) => genreIs(b, 'C#') || subjectHas(b, 'c#'),
      },
      {
        id: 'go',
        label: 'Go',
        match: (b) => genreIs(b, 'Go') || subjectHas(b, 'go (computer program'),
      },
      {
        id: 'rust',
        label: 'Rust',
        match: (b) => genreIs(b, 'Rust') || subjectHas(b, 'rust (computer program'),
      },
    ],
  },
  {
    title: 'Technical',
    tiles: [
      { id: 'programming', label: 'Programming', match: (b) => genreIs(b, 'Programming') },
      {
        id: 'computer-science',
        label: 'Computer Science',
        match: (b) => genreIs(b, 'Computer Science') || categoryIs(b, 'Computer Science'),
      },
      {
        id: 'algorithms',
        label: 'Data Structures and Algorithms',
        match: (b) => subjectHas(b, 'data structures', 'algorithms'),
      },
      { id: 'databases', label: 'Databases', match: (b) => subjectHas(b, 'database') },
      {
        id: 'operating-systems',
        label: 'Operating Systems',
        match: (b) =>
          categoryIs(b, 'Operating Systems') ||
          subjectHas(b, 'operating systems', 'process management', 'virtual memory'),
      },
      { id: 'networks', label: 'Computer Networks', match: (b) => subjectHas(b, 'network') },
      {
        id: 'web',
        label: 'Web Development',
        match: (b) => subjectHas(b, 'javascript', 'web', 'html', 'css'),
      },
      {
        id: 'ai',
        label: 'AI and Machine Learning',
        match: (b) => subjectHas(b, 'machine learning', 'artificial intelligence'),
      },
      {
        id: 'software-engineering',
        label: 'Software Engineering',
        match: (b) => genreIs(b, 'Software Engineering') || subjectHas(b, 'version control', 'git'),
      },
    ],
  },
  {
    title: 'Leisure and literature',
    tiles: [
      {
        id: 'romance',
        label: 'Romance',
        match: (b) => genreIs(b, 'Romance') || subjectHas(b, 'courtship', 'marriage'),
      },
      {
        id: 'mystery',
        label: 'Mystery',
        match: (b) => genreIs(b, 'Detective Fiction') || subjectHas(b, 'detective', 'mystery'),
      },
      {
        id: 'crime',
        label: 'Crime',
        match: (b) => subjectHas(b, 'theft', 'crime', 'murder'),
      },
      { id: 'horror', label: 'Horror', match: (b) => genreIs(b, 'Gothic Fiction') },
      { id: 'fantasy', label: 'Fantasy', match: (b) => genreIs(b, 'Fantasy') },
      {
        id: 'science-fiction',
        label: 'Science Fiction',
        match: (b) => genreIs(b, 'Science Fiction', 'Early Science Fiction'),
      },
      { id: 'adventure', label: 'Adventure', match: (b) => genreIs(b, 'Adventure Fiction') },
      { id: 'motivation', label: 'Motivation', match: (b) => genreIs(b, 'Motivation') },
      {
        id: 'self-development',
        label: 'Self-development',
        match: (b) => genreIs(b, 'Self-Development'),
      },
      {
        id: 'classics',
        label: 'Classics',
        match: (b) => genreIs(b, 'Classic Literature', 'Victorian Novel'),
      },
      { id: 'coming-of-age', label: 'Coming of Age', match: (b) => genreIs(b, 'Bildungsroman') },
      {
        id: 'short-fiction',
        label: 'Novellas and Short Fiction',
        match: (b) => genreIs(b, 'Novella', 'Short Stories'),
      },
      {
        id: 'philosophy',
        label: 'Philosophy and Ideas',
        match: (b) => genreIs(b, 'Philosophy', 'Essays', 'Strategy', 'Poetry', 'Nonfiction'),
      },
    ],
  },
];

const ALL_TILES = TILE_GROUPS.flatMap((group) => group.tiles);

const TECHNICAL_GENRES = [
  'computer science',
  'programming',
  'software engineering',
  'operating systems',
  'textbook',
];

const isTechnical = (book) =>
  (book.genres || []).some((genre) => TECHNICAL_GENRES.includes((genre || '').toLowerCase()));

// =========================================================
// CARD
// =========================================================

/*
 * A book with no stored file is catalogued, not readable. The card and
 * the detail page both say so rather than pretending otherwise.
 */
const isReadable = (book) => (book.digitalResources || []).length > 0;

function BookTile({ book, level, isFavorite, onToggleFavorite }) {
  const subjectLine = (book.subjects || []).slice(0, 2).join(' · ');
  const readable = isReadable(book);
  const meta = [readable ? book.resourceType : 'Catalogued record', book.publicationYear]
    .filter(Boolean)
    .join(' · ');

  return (
    <article className="book-tile">
      <Link to={`/books/${book.id}`} className="book-tile-cover" aria-label={book.title}>
        <BookCover book={book} />
      </Link>

      <div className="book-tile-body">
        <div className="book-tile-badges">
          {book.category?.name && <Badge variant="default">{book.category.name}</Badge>}
          <LevelChip level={level} />
          {!readable && <Badge variant="warning">Not available yet</Badge>}
        </div>

        <h3 className="book-tile-title">
          <Link to={`/books/${book.id}`}>{book.title}</Link>
        </h3>

        <div className="book-tile-author">{book.author?.name || 'Unknown author'}</div>

        {subjectLine && <div className="book-tile-subject">{subjectLine}</div>}

        <p className="book-tile-desc">
          {book.description || 'No description has been recorded for this title yet.'}
        </p>

        <div className="book-tile-foot">
          <span className="book-tile-meta">{meta || 'Digital edition'}</span>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <button
              type="button"
              className={`icon-button ${isFavorite ? 'is-active' : ''}`}
              onClick={() => onToggleFavorite(book.id)}
              title={isFavorite ? 'Remove from favorites' : 'Save to favorites'}
              aria-label={isFavorite ? 'Remove from favorites' : 'Save to favorites'}
            >
              {isFavorite ? <BookmarkCheck size={15} /> : <Bookmark size={15} />}
            </button>

            <Link to={`/books/${book.id}`} className="btn btn-outline-primary btn-sm">
              {readable ? 'Open' : 'Details'}
            </Link>
          </div>
        </div>
      </div>
    </article>
  );
}

// =========================================================
// SHELF
// =========================================================

function Shelf({ books, memberships, favorites, onToggleFavorite }) {
  return (
    <div className="library-shelf">
      {books.map((book) => (
        <BookTile
          key={book.id}
          book={book}
          level={memberships[book.id]?.[0]?.level}
          isFavorite={favorites.has(book.id)}
          onToggleFavorite={onToggleFavorite}
        />
      ))}
    </div>
  );
}

// =========================================================
// PAGE
// =========================================================

export function CataloguePage() {
  const [searchParams] = useSearchParams();

  const [books, setBooks] = useState([]);
  const [memberships, setMemberships] = useState({});
  const [favorites, setFavorites] = useState(new Set());
  const [history, setHistory] = useState([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [keyword, setKeyword] = useState(searchParams.get('keyword') || '');
  const [genre, setGenre] = useState(searchParams.get('genre') || '');
  const [subject, setSubject] = useState(searchParams.get('subject') || '');
  const [author, setAuthor] = useState(searchParams.get('author') || '');
  const [level, setLevel] = useState(searchParams.get('level') || '');
  const [format, setFormat] = useState(searchParams.get('format') || '');
  const [year, setYear] = useState(searchParams.get('year') || '');
  const [tileId, setTileId] = useState(searchParams.get('collection') || '');
  const [collection, setCollection] = useState(''); // 'technical' | 'leisure'
  const [sort, setSort] = useState('title');

  // ---------------------------------------------------
  // LOAD
  // ---------------------------------------------------

  useEffect(() => {
    async function load() {
      setLoading(true);
      setError('');

      try {
        // catalogueOnly returns the published collection: books with a
        // readable file plus curated entries that are described and
        // levelled but not yet available to read, so they are never
        // mistaken for holdings we can serve.
        const catalogue = await api.books.getAll({ catalogueOnly: true });
        setBooks(Array.isArray(catalogue) ? catalogue : []);
      } catch (err) {
        setError(err.message || 'Unable to load the library catalogue.');
        setLoading(false);
        return;
      }

      const [links, favs, historyRecords] = await Promise.allSettled([
        api.pathways.getMemberships(),
        api.favorites.getAll(),
        api.readingHistory.getAll(),
      ]);

      if (links.status === 'fulfilled' && Array.isArray(links.value)) {
        const byBook = {};

        links.value.forEach((link) => {
          if (!byBook[link.bookId]) byBook[link.bookId] = [];

          byBook[link.bookId].push(link);
        });

        Object.values(byBook).forEach((entries) => {
          entries.sort(
            (left, right) =>
              LEVEL_ORDER.indexOf(left.level) - LEVEL_ORDER.indexOf(right.level)
          );
        });

        setMemberships(byBook);
      }

      if (favs.status === 'fulfilled' && Array.isArray(favs.value)) {
        setFavorites(new Set(favs.value.map((favorite) => favorite.bookId)));
      }

      if (historyRecords.status === 'fulfilled' && Array.isArray(historyRecords.value)) {
        setHistory(historyRecords.value.filter((record) => record.bookId));
      }

      setLoading(false);
    }

    load();
  }, []);

  // ---------------------------------------------------
  // DERIVED METADATA (from stored records only)
  // ---------------------------------------------------

  const genres = useMemo(
    () => Array.from(new Set(books.flatMap((book) => book.genres || []))).sort(),
    [books]
  );

  const subjects = useMemo(
    () => Array.from(new Set(books.flatMap((book) => book.subjects || []))).sort(),
    [books]
  );

  const authors = useMemo(
    () =>
      Array.from(new Set(books.map((book) => book.author?.name).filter(Boolean))).sort(
        (left, right) => left.localeCompare(right)
      ),
    [books]
  );

  const formats = useMemo(
    () => Array.from(new Set(books.map((book) => book.resourceType).filter(Boolean))).sort(),
    [books]
  );

  const years = useMemo(
    () =>
      Array.from(new Set(books.map((book) => book.publicationYear).filter(Boolean))).sort(
        (left, right) => right - left
      ),
    [books]
  );

  const tileCounts = useMemo(() => {
    const counts = {};

    ALL_TILES.forEach((tile) => {
      counts[tile.id] = books.filter(tile.match).length;
    });

    return counts;
  }, [books]);

  // ---------------------------------------------------
  // FILTER PIPELINE
  // ---------------------------------------------------

  const activeTile = ALL_TILES.find((tile) => tile.id === tileId) || null;

  const filtersActive = Boolean(
    keyword.trim() || genre || subject || author || level || format || year || activeTile || collection
  );

  const filtered = useMemo(() => {
    const query = keyword.trim().toLowerCase();

    const result = books.filter((book) => {
      if (activeTile && !activeTile.match(book)) return false;

      if (collection === 'technical' && !isTechnical(book)) return false;
      if (collection === 'leisure' && isTechnical(book)) return false;

      if (genre && !(book.genres || []).some((value) => value === genre)) return false;

      if (
        subject &&
        !(book.subjects || []).some((value) =>
          value.toLowerCase().includes(subject.toLowerCase())
        )
      ) {
        return false;
      }

      if (author && book.author?.name !== author) return false;

      if (level && !(memberships[book.id] || []).some((entry) => entry.level === level)) {
        return false;
      }

      if (format && (book.resourceType || '').toLowerCase() !== format.toLowerCase()) {
        return false;
      }

      if (year && String(book.publicationYear) !== String(year)) return false;

      if (query) {
        const haystack = [
          book.title,
          book.author?.name,
          book.category?.name,
          book.description,
          ...(book.subjects || []),
          ...(book.genres || []),
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();

        if (!haystack.includes(query)) return false;
      }

      return true;
    });

    const sorted = [...result];

    if (sort === 'author') {
      sorted.sort((left, right) =>
        (left.author?.name || '').localeCompare(right.author?.name || '')
      );
    } else if (sort === 'newest') {
      sorted.sort((left, right) => {
        const leftDate = left.createdAt ? new Date(left.createdAt).getTime() : 0;
        const rightDate = right.createdAt ? new Date(right.createdAt).getTime() : 0;

        if (rightDate !== leftDate) return rightDate - leftDate;

        return right.id - left.id;
      });
    } else {
      sorted.sort((left, right) => left.title.localeCompare(right.title));
    }

    return sorted;
  }, [
    books,
    activeTile,
    collection,
    genre,
    subject,
    author,
    level,
    format,
    year,
    keyword,
    sort,
    memberships,
  ]);

  // ---------------------------------------------------
  // SECTIONS (only shown when nothing is filtered)
  // ---------------------------------------------------

  const recent = useMemo(() => [...books].sort((a, b) => b.id - a.id).slice(0, 5), [books]);
  const technical = useMemo(() => books.filter(isTechnical).slice(0, 6), [books]);
  const leisure = useMemo(() => books.filter((book) => !isTechnical(book)).slice(0, 6), [books]);

  const continueReading = useMemo(() => {
    const seen = new Set();

    return history
      .filter((record) => {
        if (seen.has(record.bookId)) return false;

        seen.add(record.bookId);

        return true;
      })
      .slice(0, 4);
  }, [history]);

  // ---------------------------------------------------
  // ACTIONS
  // ---------------------------------------------------

  const resetFilters = () => {
    setKeyword('');
    setGenre('');
    setSubject('');
    setAuthor('');
    setLevel('');
    setFormat('');
    setYear('');
    setTileId('');
    setCollection('');
  };

  const toggleTile = (id) => {
    setTileId((current) => (current === id ? '' : id));
    setCollection('');
  };

  const handleFavoriteToggle = async (bookId) => {
    try {
      if (favorites.has(bookId)) {
        await api.favorites.remove(bookId);
        setFavorites((previous) => {
          const next = new Set(previous);
          next.delete(bookId);
          return next;
        });
      } else {
        await api.favorites.add(bookId);
        setFavorites((previous) => new Set(previous).add(bookId));
      }
    } catch (err) {
      console.error('Favorite action failed:', err);
    }
  };

  const bookById = useMemo(() => {
    const index = {};

    books.forEach((book) => {
      index[book.id] = book;
    });

    return index;
  }, [books]);

  // ---------------------------------------------------
  // RENDER
  // ---------------------------------------------------

  return (
    <div className="page-container library-page">
      {/* ---------- Discovery hero ---------- */}
      <section className="library-hero">
        <div className="library-eyebrow">Digital Library</div>
        <h1 className="library-title">Books, ready to read and to search</h1>
        <p className="library-lede">
          Browse by programming language, subject, genre and difficulty. Most cards open a full
          digital edition that Smart Search can quote; titles marked{' '}
          <em>Not available yet</em> are catalogued and levelled, but no licensed digital edition
          is held for them.
        </p>

        <form
          className="library-search-form"
          onSubmit={(event) => event.preventDefault()}
          role="search"
        >
          <Search size={18} className="library-search-icon" />
          <input
            type="search"
            className="library-search-input"
            placeholder="Search by title, author, subject or keyword"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            aria-label="Search the library catalogue"
          />
          {keyword && (
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() => setKeyword('')}
            >
              Clear
            </button>
          )}
        </form>

        <div className="library-hero-stats">
          <div className="library-stat">
            <strong>{books.length}</strong>
            <span>Titles</span>
          </div>
          <div className="library-stat">
            <strong>{authors.length}</strong>
            <span>Authors</span>
          </div>
          <div className="library-stat">
            <strong>{subjects.length}</strong>
            <span>Subjects</span>
          </div>
          <div className="library-stat">
            <strong>{ALL_TILES.filter((tile) => tileCounts[tile.id] > 0).length}</strong>
            <span>Collections</span>
          </div>
        </div>
      </section>

      {error && (
        <div className="banner banner-error">
          <span>{error}</span>
        </div>
      )}

      {/* ---------- Refine ---------- */}
      <section className="library-toolbar" aria-label="Filter the catalogue">
        <div className="library-toolbar-grid">
          <select
            className="form-select"
            value={tileId}
            onChange={(event) => {
              setTileId(event.target.value);
              setCollection('');
            }}
            aria-label="Collection"
          >
            <option value="">All collections</option>
            {ALL_TILES.filter((tile) => tileCounts[tile.id] > 0).map((tile) => (
              <option key={tile.id} value={tile.id}>
                {tile.label} ({tileCounts[tile.id]})
              </option>
            ))}
          </select>

          <select
            className="form-select"
            value={genre}
            onChange={(event) => setGenre(event.target.value)}
            aria-label="Genre"
          >
            <option value="">All genres</option>
            {genres.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>

          <select
            className="form-select"
            value={subject}
            onChange={(event) => setSubject(event.target.value)}
            aria-label="Subject"
          >
            <option value="">All subjects</option>
            {subjects.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>

          <select
            className="form-select"
            value={author}
            onChange={(event) => setAuthor(event.target.value)}
            aria-label="Author"
          >
            <option value="">All authors</option>
            {authors.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>

          <select
            className="form-select"
            value={level}
            onChange={(event) => setLevel(event.target.value)}
            aria-label="Difficulty and reading level"
          >
            <option value="">All levels</option>
            {LEVEL_ORDER.map((value) => (
              <option key={value} value={value}>
                {LEVEL_LABEL[value]}
              </option>
            ))}
          </select>

          <select
            className="form-select"
            value={format}
            onChange={(event) => setFormat(event.target.value)}
            aria-label="Format"
          >
            <option value="">All formats</option>
            {formats.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>

          <select
            className="form-select"
            value={year}
            onChange={(event) => setYear(event.target.value)}
            aria-label="Publication year"
          >
            <option value="">All years</option>
            {years.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>

          <select
            className="form-select"
            value={sort}
            onChange={(event) => setSort(event.target.value)}
            aria-label="Sort"
          >
            <option value="title">Sort: title A–Z</option>
            <option value="author">Sort: author A–Z</option>
            <option value="newest">Sort: newest additions</option>
          </select>
        </div>

        <div className="library-toolbar-foot">
          <span className="library-result-count">
            {filtersActive ? (
              <>
                <strong>{filtered.length}</strong> of {books.length} titles match
              </>
            ) : (
              <>
                <strong>{books.length}</strong> titles across {ALL_TILES.filter((t) => tileCounts[t.id] > 0).length}{' '}
                collections
              </>
            )}
          </span>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <SlidersHorizontal size={15} style={{ color: 'var(--text-muted)' }} />
            <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
              Filters use stored catalogue metadata
            </span>
            {filtersActive && (
              <button type="button" className="btn btn-secondary btn-sm" onClick={resetFilters}>
                <X size={14} /> Reset
              </button>
            )}
          </div>
        </div>
      </section>

      {loading ? (
        <LoadingSpinner message="Opening the library catalogue…" />
      ) : books.length === 0 ? (
        <EmptyState
          icon={BookOpen}
          title="The library has no holdings yet"
          description="Books appear here once an administrator imports an EPUB and indexing finishes."
        />
      ) : filtersActive ? (
        /* ---------- Filtered results ---------- */
        <section className="library-section">
          <div className="library-section-head">
            <div>
              <h2 className="library-section-title">
                {activeTile ? activeTile.label : collection === 'technical' ? 'Technical books' : collection === 'leisure' ? 'Fiction and leisure' : 'Results'}
              </h2>
              <p className="library-section-note">
                {filtered.length} {filtered.length === 1 ? 'title' : 'titles'} · sorted by{' '}
                {sort === 'newest' ? 'newest addition' : sort === 'author' ? 'author' : 'title'}
              </p>
            </div>
            <button type="button" className="library-section-link" onClick={resetFilters}>
              Show the whole library
            </button>
          </div>

          {filtered.length === 0 ? (
            <EmptyState
              icon={BookOpen}
              title="No titles match these filters"
              description="Try a broader subject, or clear the filters to browse the whole collection."
              action={
                <button type="button" className="btn btn-secondary btn-sm" onClick={resetFilters}>
                  Reset filters
                </button>
              }
            />
          ) : (
            <Shelf
              books={filtered}
              memberships={memberships}
              favorites={favorites}
              onToggleFavorite={handleFavoriteToggle}
            />
          )}
        </section>
      ) : (
        <>
          {/* ---------- Continue reading (real history only) ---------- */}
          {continueReading.length > 0 && (
            <section className="library-section">
              <div className="library-section-head">
                <div>
                  <h2 className="library-section-title">Continue reading</h2>
                  <p className="library-section-note">
                    From your own reading history on this account.
                  </p>
                </div>
                <Link to="/history" className="library-section-link">
                  Full history
                </Link>
              </div>

              <div className="continue-strip">
                {continueReading.map((record) => {
                  const book = bookById[record.bookId];
                  const progress = Math.round(Number(record.progressPercent) || 0);

                  return (
                    <Link
                      key={record.bookId}
                      to={`/reader/${record.bookId}`}
                      className="continue-card"
                    >
                      <div className="continue-thumb">
                        <BookCover book={book || { title: record.bookTitle }} monogram />
                      </div>

                      <div className="continue-body">
                        <div className="continue-title">
                          {record.bookTitle || book?.title || `Book #${record.bookId}`}
                        </div>
                        <div className="continue-sub">
                          {book?.author?.name || 'Saved reading position'}
                        </div>
                        <div className="progress-track">
                          <div className="progress-fill" style={{ width: `${Math.min(progress, 100)}%` }} />
                        </div>
                        <div className="progress-label">{progress}% read</div>
                      </div>
                    </Link>
                  );
                })}
              </div>
            </section>
          )}

          {/* ---------- Explore categories ---------- */}
          <section className="library-section">
            <div className="library-section-head">
              <div>
                <h2 className="library-section-title">Explore the collection</h2>
                <p className="library-section-note">
                  Only subjects with titles on the shelf appear here.
                </p>
              </div>
            </div>

            <div className="category-groups">
              {TILE_GROUPS.map((group) => {
                const visible = group.tiles.filter((tile) => tileCounts[tile.id] > 0);

                if (visible.length === 0) return null;

                return (
                  <div key={group.title}>
                    <div className="category-group-title">{group.title}</div>
                    <div className="category-tiles">
                      {visible.map((tile) => (
                        <button
                          key={tile.id}
                          type="button"
                          className={`category-tile ${tileId === tile.id ? 'active' : ''}`}
                          onClick={() => toggleTile(tile.id)}
                        >
                          {tile.label}
                          <span className="category-tile-count">{tileCounts[tile.id]}</span>
                        </button>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          {/* ---------- Recently added ---------- */}
          {recent.length > 0 && (
            <section className="library-section">
              <div className="library-section-head">
                <div>
                  <h2 className="library-section-title">Recently added</h2>
                  <p className="library-section-note">The latest records in the catalogue.</p>
                </div>
                <button
                  type="button"
                  className="library-section-link"
                  onClick={() => setSort('newest')}
                >
                  Sort the library by newest
                </button>
              </div>

              <Shelf
                books={recent}
                memberships={memberships}
                favorites={favorites}
                onToggleFavorite={handleFavoriteToggle}
              />
            </section>
          )}

          {/* ---------- Technical ---------- */}
          {technical.length > 0 && (
            <section className="library-section">
              <div className="library-section-head">
                <div>
                  <h2 className="library-section-title">Programming and computer science</h2>
                  <p className="library-section-note">
                    Textbooks and working guides, from first steps to systems internals.
                  </p>
                </div>
                <button
                  type="button"
                  className="library-section-link"
                  onClick={() => setCollection('technical')}
                >
                  Browse all technical books
                </button>
              </div>

              <Shelf
                books={technical}
                memberships={memberships}
                favorites={favorites}
                onToggleFavorite={handleFavoriteToggle}
              />
            </section>
          )}

          {/* ---------- Leisure ---------- */}
          {leisure.length > 0 && (
            <section className="library-section">
              <div className="library-section-head">
                <div>
                  <h2 className="library-section-title">Fiction and leisure reading</h2>
                  <p className="library-section-note">
                    Novels, detective stories and essays, with reading-complexity labels
                    describing the text itself.
                  </p>
                </div>
                <button
                  type="button"
                  className="library-section-link"
                  onClick={() => setCollection('leisure')}
                >
                  Browse all fiction
                </button>
              </div>

              <Shelf
                books={leisure}
                memberships={memberships}
                favorites={favorites}
                onToggleFavorite={handleFavoriteToggle}
              />
            </section>
          )}

          {/* ---------- All titles ---------- */}
          <section className="library-section">
            <div className="library-section-head">
              <div>
                <h2 className="library-section-title">All titles</h2>
                <p className="library-section-note">
                  <ArrowUpDown size={12} style={{ verticalAlign: 'middle' }} /> Use the filters
                  above to narrow the shelf.
                </p>
              </div>
            </div>

            <Shelf
              books={filtered}
              memberships={memberships}
              favorites={favorites}
              onToggleFavorite={handleFavoriteToggle}
            />
          </section>
        </>
      )}
    </div>
  );
}
