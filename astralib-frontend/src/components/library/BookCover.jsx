import React from 'react';

/**
 * A book cover.
 *
 * When the catalogue holds a real cover image it is used. Otherwise the
 * title is set typographically on a restrained institutional colour, so
 * every card on a shelf has a cover of the same shape without inventing
 * artwork we do not have.
 */

const COVER_BASES = [
  '#234A6B', // ink blue
  '#2F4A3C', // library green
  '#5A3733', // oxblood
  '#3B3F5C', // slate violet
  '#4A3B2A', // bronze brown
  '#2C4750', // deep teal
];

function baseFor(title) {
  const text = title || '';
  let hash = 0;
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) % 100000;
  }
  return COVER_BASES[hash % COVER_BASES.length];
}

function monogramFor(title) {
  const words = (title || '').split(/\s+/).filter((word) => /[A-Za-z0-9]/.test(word));

  if (words.length === 0) return 'A';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();

  return (words[0][0] + words[1][0]).toUpperCase();
}

export function BookCover({ book, monogram = false }) {
  const title = book?.title || 'Untitled';

  if (monogram) {
    return (
      <div
        className="cover-art cover-art--monogram"
        style={{ '--cover-base': baseFor(title) }}
        aria-hidden="true"
      >
        <span className="cover-art-monogram">{monogramFor(title)}</span>
      </div>
    );
  }

  if (book?.coverImage) {
    return (
      <img
        src={book.coverImage}
        alt={`Cover of ${title}`}
        onError={(e) => {
          e.currentTarget.style.display = 'none';
        }}
      />
    );
  }

  return (
    <div className="cover-art" style={{ '--cover-base': baseFor(title) }}>
      <span className="cover-art-mark">AstraLib</span>
      <span className="cover-art-rule" />
      <span className="cover-art-title">{title}</span>
      <span className="cover-art-author">{book?.author?.name || 'Unknown author'}</span>
    </div>
  );
}
