import React from 'react';

/**
 * Difficulty labels.
 *
 * Learning pathways use educational levels. Leisure pathways use
 * reading-complexity labels, which describe the text and are presented
 * with that explanation rather than as a judgement of the reader.
 */

export const LEVEL_ORDER = [
  'BEGINNER',
  'INTERMEDIATE',
  'ADVANCED',
  'ACCESSIBLE',
  'STANDARD',
  'COMPLEX',
];

export const LEVEL_LABEL = {
  BEGINNER: 'Beginner',
  INTERMEDIATE: 'Intermediate',
  ADVANCED: 'Advanced',
  ACCESSIBLE: 'Accessible read',
  STANDARD: 'Standard read',
  COMPLEX: 'Complex read',
};

const LEISURE_LEVELS = ['ACCESSIBLE', 'STANDARD', 'COMPLEX'];

export function levelTooltip(level) {
  return LEISURE_LEVELS.includes(level)
    ? "A curated assessment of the text's length, style and structure — not of the reader."
    : 'Curated learning level for this subject.';
}

export function LevelChip({ level }) {
  if (!level) return null;

  return (
    <span className={`level-chip level-${level.toLowerCase()}`} title={levelTooltip(level)}>
      {LEVEL_LABEL[level] || level}
    </span>
  );
}
