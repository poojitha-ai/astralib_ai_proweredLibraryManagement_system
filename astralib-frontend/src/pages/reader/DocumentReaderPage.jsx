import React, {
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  Link,
  useParams,
  useSearchParams,
} from 'react-router-dom';

import ePub from 'epubjs';

import { api } from '../../services/api';

import {
  ArrowLeft,
  BookOpen,
  FileText,
  Send,
  Bot,
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  List,
  Search,
  X,
} from 'lucide-react';

import {
  Badge,
  LoadingSpinner,
  EmptyState,
} from '../../components/common/Components';

export function DocumentReaderPage() {
  const { resourceId } = useParams();

  const [searchParams] = useSearchParams();

  const citationChunkId =
    searchParams.get('chunkId');

  const isCitationMode =
    Boolean(citationChunkId);

  // =========================================================
  // BOOK / RESOURCE
  // =========================================================

  const [book, setBook] = useState(null);

  const [
    digitalResource,
    setDigitalResource,
  ] = useState(null);

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState('');

  // =========================================================
  // EPUB READER REFS
  // =========================================================

  const viewerRef = useRef(null);

  const epubBookRef =
    useRef(null);

  const renditionRef =
    useRef(null);

  const saveTimerRef =
    useRef(null);

  const searchInputRef =
    useRef(null);

  // Prevent initialization events from
  // overwriting saved progress.
  const isRestoringRef =
    useRef(false);

  // Citation navigation must never overwrite
  // the user's genuine saved reading position.
  const isCitationModeRef =
    useRef(false);

  // =========================================================
  // READER STATE
  // =========================================================

  const [
    readerReady,
    setReaderReady,
  ] = useState(false);

  const [
    readerError,
    setReaderError,
  ] = useState('');

  const [
    currentChapter,
    setCurrentChapter,
  ] = useState('');

  const [
    progress,
    setProgress,
  ] = useState(0);

  // =========================================================
  // TABLE OF CONTENTS
  // =========================================================

  const [toc, setToc] =
    useState([]);

  const [
    tocOpen,
    setTocOpen,
  ] = useState(false);

  // =========================================================
  // BOOK SEARCH
  // =========================================================

  const [
    searchOpen,
    setSearchOpen,
  ] = useState(false);

  const [
    bookSearchQuery,
    setBookSearchQuery,
  ] = useState('');

  const [
    bookSearchResults,
    setBookSearchResults,
  ] = useState([]);

  const [
    bookSearching,
    setBookSearching,
  ] = useState(false);

  const [
    searchMessage,
    setSearchMessage,
  ] = useState('');

  // =========================================================
  // DOCUMENT Q&A
  // =========================================================

  const [
    question,
    setQuestion,
  ] = useState('');

  const [
    asking,
    setAsking,
  ] = useState(false);

  const [
    qaHistory,
    setQaHistory,
  ] = useState([]);

  const [
    qaError,
    setQaError,
  ] = useState('');

  // =========================================================
  // USER-SCOPED LOCAL STORAGE BACKUP KEY
  // =========================================================
  //
  // IMPORTANT:
  // Reading progress must never be shared between users who
  // use the same browser/device.
  //
  // Old key:
  //   astralib_reading_3003
  //
  // New key:
  //   astralib_reading_<user-id>_3003
  //
  // We first try the stored user object. If its exact shape
  // changes, we also fall back to the JWT subject (`sub`),
  // which AstraLib uses for the authenticated user ID.
  // =========================================================

  const getCurrentUserStorageId = () => {
    try {
      const rawUser =
        localStorage.getItem(
          'astralib_user'
        );

      if (rawUser) {
        const currentUser =
          JSON.parse(rawUser);

        const storedUserId =
          currentUser?.id ??
          currentUser?.userId ??
          currentUser?.email ??
          currentUser?.user?.id ??
          currentUser?.user?.userId ??
          currentUser?.user?.email;

        if (
          storedUserId !== null &&
          storedUserId !== undefined &&
          String(storedUserId).trim()
        ) {
          return String(
            storedUserId
          );
        }
      }
    } catch (userError) {
      console.warn(
        'Could not read AstraLib user from localStorage:',
        userError
      );
    }

    try {
      const token =
        localStorage.getItem(
          'astralib_token'
        );

      if (token) {
        const tokenParts =
          token.split('.');

        if (
          tokenParts.length >= 2
        ) {
          const normalizedPayload =
            tokenParts[1]
              .replace(/-/g, '+')
              .replace(/_/g, '/');

          const paddedPayload =
            normalizedPayload.padEnd(
              Math.ceil(
                normalizedPayload.length /
                  4
              ) * 4,
              '='
            );

          const payload =
            JSON.parse(
              atob(
                paddedPayload
              )
            );

          const tokenUserId =
            payload?.sub ??
            payload?.userId ??
            payload?.email;

          if (
            tokenUserId !== null &&
            tokenUserId !== undefined &&
            String(tokenUserId).trim()
          ) {
            return String(
              tokenUserId
            );
          }
        }
      }
    } catch (tokenError) {
      console.warn(
        'Could not derive AstraLib user ID from JWT:',
        tokenError
      );
    }

    // This should normally never be used because
    // the reader is an authenticated feature.
    return 'unknown-user';
  };

  const getReadingStorageKey = () => {
    const currentUserId =
      getCurrentUserStorageId();

    return `astralib_reading_${currentUserId}_${resourceId}`;
  };

  // =========================================================
  // TOC UTILITIES
  // =========================================================

  const flattenToc = (
    items = [],
    level = 0,
    result = []
  ) => {
    items.forEach((item) => {
      result.push({
        ...item,
        level,
      });

      if (
        Array.isArray(item.subitems) &&
        item.subitems.length > 0
      ) {
        flattenToc(
          item.subitems,
          level + 1,
          result
        );
      }
    });

    return result;
  };

  const flattenedToc =
    flattenToc(toc);

  const getChapterLabel = (
    href
  ) => {
    if (!href) {
      return '';
    }

    const cleanHref =
      href.split('#')[0];

    const matched =
      flattenedToc.find(
        (item) => {
          const itemHref =
            item.href
              ?.split('#')[0];

          return (
            itemHref === cleanHref ||
            cleanHref.endsWith(
              itemHref || ''
            ) ||
            itemHref?.endsWith(
              cleanHref
            )
          );
        }
      );

    return (
      matched?.label ||
      cleanHref
    );
  };

  // =========================================================
  // 1. LOAD BOOK + RESOURCE
  // =========================================================

  useEffect(() => {
    async function loadBookAndResource() {
      setLoading(true);
      setError('');

      try {
        // reader/:resourceId currently carries BOOK ID.
        // Sherlock = /reader/3003

        const bookData =
          await api.books.getById(
            resourceId
          );

        setBook(bookData);

        const resources =
          await api.resources
            .getByBookId(
              resourceId
            );

        if (
          !Array.isArray(resources) ||
          resources.length === 0
        ) {
          throw new Error(
            'No digital copy is available for this book.'
          );
        }

        // Prefer EPUB.
        const selectedResource =
          resources.find(
            (item) =>
              item.format ===
                'application/epub+zip' ||
              item.fileName
                ?.toLowerCase()
                .endsWith('.epub')
          ) ||
          resources[0];

        setDigitalResource(
          selectedResource
        );
      } catch (err) {
        console.error(
          'Reader load error:',
          err
        );

        setError(
          err.message ||
            'Unable to open this digital resource.'
        );
      } finally {
        setLoading(false);
      }
    }

    loadBookAndResource();
  }, [resourceId]);

  // =========================================================
  // 2. EPUB RENDERING
  // =========================================================

  useEffect(() => {
    async function renderEpub() {
      if (
        !digitalResource?.id
      ) {
        return;
      }

      const isEpub =
        digitalResource.format ===
          'application/epub+zip' ||
        digitalResource.fileName
          ?.toLowerCase()
          .endsWith('.epub');

      if (!isEpub) {
        setReaderError(
          'This reader currently supports EPUB resources.'
        );

        return;
      }

      try {
        setReaderError('');
        setReaderReady(false);
        setCurrentChapter('');
        setToc([]);

        // =====================================================
        // LOAD SAVED POSITION
        // =====================================================

        let savedCfi = null;
        let savedProgress = 0;

        // -----------------------------------------------------
        // MYSQL FIRST
        // -----------------------------------------------------

        try {
          const serverProgress =
            await api.readingHistory
              .getProgress(
                Number(
                  resourceId
                )
              );

          if (serverProgress) {
            savedCfi =
              serverProgress
                .lastLocationCfi ||
              null;

            if (
              typeof serverProgress
                .progressPercent ===
              'number'
            ) {
              savedProgress =
                serverProgress
                  .progressPercent;

              setProgress(
                savedProgress
              );
            }

            console.log(
              'Reading position loaded from MySQL:',
              `${savedProgress}%`
            );
          }
        } catch (progressError) {
          console.warn(
            'Could not load reading position from MySQL:',
            progressError
          );
        }

        // -----------------------------------------------------
        // LOCALSTORAGE FALLBACK
        // -----------------------------------------------------

        if (!savedCfi) {
          const savedStateRaw =
            localStorage.getItem(
              getReadingStorageKey()
            );

          if (savedStateRaw) {
            try {
              const savedState =
                JSON.parse(
                  savedStateRaw
                );

              savedCfi =
                savedState?.cfi ||
                null;

              if (
                typeof savedState
                  ?.progress ===
                'number'
              ) {
                savedProgress =
                  savedState
                    .progress;

                setProgress(
                  savedProgress
                );
              }

              console.log(
                'Reading position loaded from localStorage:',
                `${savedProgress}%`
              );
            } catch (err) {
              console.warn(
                'Could not restore local reading state:',
                err
              );
            }
          }
        }

        if (!savedCfi) {
          setProgress(
            savedProgress || 0
          );
        }

        // =====================================================
        // LOAD EPUB BINARY
        // =====================================================

        const fileData =
          await api.resources
            .getFileArrayBuffer(
              digitalResource.id
            );

        // Clean old book.
        if (
          epubBookRef.current
        ) {
          try {
            epubBookRef.current
              .destroy();
          } catch {
            // Ignore.
          }
        }

        const epubBook =
          ePub(fileData);

        epubBookRef.current =
          epubBook;

        await epubBook.ready;

        // =====================================================
        // LOAD TABLE OF CONTENTS
        // =====================================================

        try {
          const navigation =
            await epubBook.loaded
              .navigation;

          const navigationToc =
            Array.isArray(
              navigation?.toc
            )
              ? navigation.toc
              : [];

          setToc(
            navigationToc
          );

          console.log(
            'EPUB Table of Contents loaded:',
            navigationToc.length
          );
        } catch (tocError) {
          console.warn(
            'Could not load EPUB table of contents:',
            tocError
          );

          setToc([]);
        }

        // =====================================================
        // CREATE VIEWER
        // =====================================================

        if (
          !viewerRef.current
        ) {
          return;
        }

        viewerRef.current
          .innerHTML = '';

        const rendition =
          epubBook.renderTo(
            viewerRef.current,
            {
              width: '100%',
              height: '100%',
              spread: 'auto',
              flow: 'paginated',
            }
          );

        renditionRef.current =
          rendition;

        // =====================================================
        // BOOK TYPOGRAPHY
        // =====================================================

        rendition.themes.default({
          body: {
            'font-family':
              'Georgia, "Times New Roman", serif',

            'font-size':
              '18px',

            'line-height':
              '1.75',

            color:
              '#1F2933',

            padding:
              '24px 28px',

            background:
              '#FFFFFF',
          },

          p: {
            'line-height':
              '1.75',
          },

          h1: {
            color:
              '#234A6B',

            'font-family':
              'Georgia, "Times New Roman", serif',
          },

          h2: {
            color:
              '#234A6B',

            'font-family':
              'Georgia, "Times New Roman", serif',
          },

          h3: {
            color:
              '#234A6B',
          },

          a: {
            color:
              '#234A6B',
          },
        });

        // =====================================================
        // EPUB LOCATIONS
        // =====================================================

        try {
          await epubBook.locations
            .generate(1600);

          console.log(
            'EPUB locations generated:',
            epubBook.locations
              .length()
          );
        } catch (locationErr) {
          console.warn(
            'Could not generate EPUB locations:',
            locationErr
          );
        }

        // =====================================================
        // SAVE PROGRESS
        // =====================================================

        const saveProgressToBackend =
          (
            cfi,
            calculatedProgress
          ) => {
            if (
              saveTimerRef.current
            ) {
              clearTimeout(
                saveTimerRef.current
              );
            }

            saveTimerRef.current =
              setTimeout(
                async () => {
                  try {
                    await api
                      .readingHistory
                      .saveProgress({
                        bookId:
                          Number(
                            resourceId
                          ),

                        resourceId:
                          Number(
                            digitalResource.id
                          ),

                        progressPercent:
                          calculatedProgress,

                        lastLocationCfi:
                          cfi,
                      });

                    console.log(
                      'Reading progress saved:',
                      `${calculatedProgress}%`
                    );
                  } catch (
                    saveError
                  ) {
                    console.warn(
                      'Could not save reading progress:',
                      saveError
                    );
                  }
                },
                600
              );
          };

        // =====================================================
        // LOCATION / PROGRESS HANDLER
        // =====================================================

        const updateReadingProgress =
          (
            location,
            allowSave = true
          ) => {
            try {
              if (
                !location?.start
              ) {
                return;
              }

              const href =
                location.start
                  .href || '';

              const cfi =
                location.start
                  .cfi || '';

              setCurrentChapter(
                href
              );

              if (
                !cfi ||
                !epubBook
                  .locations ||
                epubBook.locations
                  .length() <= 0
              ) {
                return;
              }

              const percentage =
                epubBook.locations
                  .percentageFromCfi(
                    cfi
                  );

              if (
                typeof percentage !==
                  'number' ||
                Number.isNaN(
                  percentage
                )
              ) {
                return;
              }

              const calculatedProgress =
                Math.min(
                  100,
                  Math.max(
                    0,
                    Math.round(
                      percentage *
                        100
                    )
                  )
                );

              setProgress(
                calculatedProgress
              );

              if (
                !allowSave ||
                isRestoringRef.current ||
                isCitationModeRef.current
              ) {
                return;
              }

              // Browser backup.
              const readingState = {
                bookId:
                  String(
                    resourceId
                  ),

                resourceId:
                  digitalResource.id,

                cfi,

                chapter:
                  href,

                progress:
                  calculatedProgress,

                updatedAt:
                  new Date()
                    .toISOString(),
              };

              localStorage.setItem(
                getReadingStorageKey(),
                JSON.stringify(
                  readingState
                )
              );

              saveProgressToBackend(
                cfi,
                calculatedProgress
              );
            } catch (err) {
              console.warn(
                'Could not update reading progress:',
                err
              );
            }
          };

        // =====================================================
        // CTRL + F INSIDE EPUB IFRAME
        // =====================================================

        const registerIframeShortcuts =
          (contents) => {
            try {
              const doc =
                contents?.document;

              if (!doc) {
                return;
              }

              doc.addEventListener(
                'keydown',
                (event) => {
                  const isFind =
                    (event.ctrlKey ||
                      event.metaKey) &&
                    event.key
                      .toLowerCase() ===
                      'f';

                  if (
                    isFind
                  ) {
                    event.preventDefault();

                    setSearchOpen(
                      true
                    );

                    setTocOpen(
                      false
                    );

                    setTimeout(
                      () => {
                        searchInputRef
                          .current
                          ?.focus();
                      },
                      50
                    );
                  }
                }
              );
            } catch (err) {
              console.warn(
                'Could not register EPUB keyboard shortcut:',
                err
              );
            }
          };

        rendition.on(
          'rendered',
          (
            section,
            contents
          ) => {
            registerIframeShortcuts(
              contents
            );
          }
        );

        rendition.on(
          'relocated',
          updateReadingProgress
        );

        // =====================================================
        // CITATION PASSAGE NAVIGATION
        // =====================================================

        const findCitationInEpub =
          async (chunkContent) => {
            if (
              !chunkContent ||
              !epubBook
            ) {
              return null;
            }

            const normalizedContent =
              chunkContent
                .replace(/\s+/g, ' ')
                .trim();

            if (!normalizedContent) {
              return null;
            }

            const words =
              normalizedContent
                .split(' ')
                .filter(Boolean);

            if (words.length === 0) {
              return null;
            }

            const candidatePhrases = [];

            const addPhrase = (
              start,
              length
            ) => {
              const phrase =
                words
                  .slice(
                    start,
                    start + length
                  )
                  .join(' ')
                  .trim();

              if (
                phrase &&
                phrase.length >= 18 &&
                !candidatePhrases.includes(
                  phrase
                )
              ) {
                candidatePhrases.push(
                  phrase
                );
              }
            };

            const phraseLengths = [
              10,
              8,
              6,
              5,
            ];

            const anchorPositions = [
              0,
              Math.floor(
                words.length * 0.2
              ),
              Math.floor(
                words.length * 0.4
              ),
              Math.floor(
                words.length * 0.6
              ),
              Math.floor(
                words.length * 0.8
              ),
            ];

            phraseLengths.forEach(
              (length) => {
                anchorPositions.forEach(
                  (position) => {
                    const maxStart =
                      Math.max(
                        0,
                        words.length -
                          length
                      );

                    const safeStart =
                      Math.min(
                        Math.max(
                          0,
                          position
                        ),
                        maxStart
                      );

                    addPhrase(
                      safeStart,
                      length
                    );
                  }
                );
              }
            );

            const spineItems =
              epubBook.spine
                ?.spineItems || [];

            for (
              const phrase
              of candidatePhrases
            ) {
              for (
                const section
                of spineItems
              ) {
                let loaded = false;

                try {
                  await section.load(
                    epubBook.load.bind(
                      epubBook
                    )
                  );

                  loaded = true;

                  const matches =
                    section.find(
                      phrase
                    ) || [];

                  if (
                    matches.length > 0 &&
                    matches[0]?.cfi
                  ) {
                    return {
                      cfi:
                        matches[0].cfi,

                      href:
                        section.href,

                      excerpt:
                        matches[0]
                          .excerpt,

                      phrase,
                    };
                  }
                } catch (
                  sectionError
                ) {
                  console.warn(
                    'Citation search skipped EPUB section:',
                    section?.href,
                    sectionError
                  );
                } finally {
                  if (loaded) {
                    try {
                      section.unload();
                    } catch {
                      // Ignore unload failures.
                    }
                  }
                }
              }
            }

            return null;
          };

        // =====================================================
        // CITATION OR NORMAL READING POSITION
        // =====================================================

        let citationOpened = false;

        if (
          isCitationMode &&
          citationChunkId
        ) {
          try {
            console.log(
              'Opening citation:',
              citationChunkId
            );

            // Keep citation browsing separate from
            // the user's saved reading position.
            isCitationModeRef.current =
              true;

            isRestoringRef.current =
              true;

            const citation =
              await api.rag
                .getCitation(
                  citationChunkId
                );

            if (
              citation?.content
            ) {
              const citationMatch =
                await findCitationInEpub(
                  citation.content
                );

              if (
                citationMatch?.cfi
              ) {
                await rendition.display(
                  citationMatch.cfi
                );

                await new Promise(
                  (resolve) =>
                    setTimeout(
                      resolve,
                      350
                    )
                );

                const citationLocation =
                  rendition
                    .currentLocation();

                if (
                  citationLocation
                ) {
                  updateReadingProgress(
                    citationLocation,
                    false
                  );
                }

                citationOpened =
                  true;

                console.log(
                  'Citation passage opened:',
                  citationMatch
                );
              } else {
                console.warn(
                  'Citation text could not be located inside EPUB:',
                  citationChunkId
                );
              }
            }
          } catch (
            citationError
          ) {
            console.error(
              'Could not open citation passage:',
              citationError
            );
          } finally {
            isRestoringRef.current =
              false;
          }
        }

        // =====================================================
        // NORMAL READING POSITION
        // =====================================================
        //
        // Restore saved reading progress only when:
        // 1. There is no citation, or
        // 2. Citation lookup/search failed.
        //
        // If citation navigation succeeds, do not restore
        // savedCfi afterward because that would immediately
        // send the reader back to the user's previous position.
        // =====================================================

        if (!citationOpened) {
          // If citation lookup failed, return to
          // normal reader-saving behavior.
          isCitationModeRef.current =
            false;

          if (savedCfi) {
            try {
              isRestoringRef.current =
                true;

              await rendition.display(
                savedCfi
              );

              await new Promise(
                (resolve) =>
                  setTimeout(
                    resolve,
                    350
                  )
              );

              const restoredLocation =
                rendition
                  .currentLocation();

              if (
                restoredLocation
              ) {
                updateReadingProgress(
                  restoredLocation,
                  false
                );
              }

              isRestoringRef.current =
                false;
            } catch (
              restoreError
            ) {
              isRestoringRef.current =
                false;

              console.warn(
                'Saved EPUB location could not be restored:',
                restoreError
              );

              isRestoringRef.current =
                true;

              await rendition.display();

              await new Promise(
                (resolve) =>
                  setTimeout(
                    resolve,
                    250
                  )
              );

              isRestoringRef.current =
                false;
            }
          } else {
            isRestoringRef.current =
              true;

            await rendition.display();

            await new Promise(
              (resolve) =>
                setTimeout(
                  resolve,
                  250
                )
            );

            const initialLocation =
              rendition
                .currentLocation();

            if (
              initialLocation
            ) {
              updateReadingProgress(
                initialLocation,
                false
              );
            }

            isRestoringRef.current =
              false;
          }
        }

        setReaderReady(true);
      } catch (err) {
        isRestoringRef.current =
          false;

        console.error(
          'EPUB reader error:',
          err
        );

        setReaderError(
          err.message ||
            'Unable to render this EPUB inside AstraLib.'
        );
      }
    }

    renderEpub();

    return () => {
      if (
        saveTimerRef.current
      ) {
        clearTimeout(
          saveTimerRef.current
        );

        saveTimerRef.current =
          null;
      }

      if (
        renditionRef.current
      ) {
        try {
          renditionRef.current
            .destroy();
        } catch {
          // Ignore.
        }

        renditionRef.current =
          null;
      }

      if (
        epubBookRef.current
      ) {
        try {
          epubBookRef.current
            .destroy();
        } catch {
          // Ignore.
        }

        epubBookRef.current =
          null;
      }

      isRestoringRef.current =
        false;

      isCitationModeRef.current =
        false;
    };
  }, [
    digitalResource,
    resourceId,
    citationChunkId,
    isCitationMode,
  ]);

  // =========================================================
  // 3. GLOBAL CTRL + F
  // =========================================================

  useEffect(() => {
    const handleKeyDown = (
      event
    ) => {
      const isFind =
        (event.ctrlKey ||
          event.metaKey) &&
        event.key
          .toLowerCase() ===
          'f';

      if (isFind) {
        event.preventDefault();

        setSearchOpen(true);
        setTocOpen(false);

        setTimeout(
          () => {
            searchInputRef
              .current
              ?.focus();
          },
          50
        );
      }

      if (
        event.key ===
        'Escape'
      ) {
        setSearchOpen(false);
        setTocOpen(false);
      }
    };

    window.addEventListener(
      'keydown',
      handleKeyDown
    );

    return () => {
      window.removeEventListener(
        'keydown',
        handleKeyDown
      );
    };
  }, []);

  // =========================================================
  // 4. READER CONTROLS
  // =========================================================

  const handlePreviousPage =
    () => {
      renditionRef.current
        ?.prev();
    };

  const handleNextPage =
    () => {
      renditionRef.current
        ?.next();
    };

  // =========================================================
  // 5. CHAPTER NAVIGATION
  // =========================================================

  const handleChapterSelect =
    async (href) => {
      if (
        !href ||
        !renditionRef.current
      ) {
        return;
      }

      try {
        await renditionRef.current
          .display(href);

        setTocOpen(false);
      } catch (err) {
        console.error(
          'Could not open chapter:',
          err
        );
      }
    };

  // =========================================================
  // 6. FULL BOOK SEARCH
  // =========================================================

  const handleBookSearch =
    async () => {
      const query =
        bookSearchQuery.trim();

      if (
        !query ||
        !epubBookRef.current
      ) {
        return;
      }

      setBookSearching(true);

      setBookSearchResults(
        []
      );

      setSearchMessage('');

      try {
        const epubBook =
          epubBookRef.current;

        await epubBook.ready;

        const spineItems =
          epubBook.spine
            ?.spineItems || [];

        const results = [];

        for (
          const section
          of spineItems
        ) {
          if (
            results.length >=
            100
          ) {
            break;
          }

          try {
            await section.load(
              epubBook.load.bind(
                epubBook
              )
            );

            const matches =
              section.find(
                query
              ) || [];

            matches.forEach(
              (match) => {
                if (
                  results.length <
                  100
                ) {
                  results.push({
                    cfi:
                      match.cfi,

                    excerpt:
                      match.excerpt,

                    href:
                      section.href,

                    chapter:
                      getChapterLabel(
                        section.href
                      ),
                  });
                }
              }
            );

            section.unload();
          } catch (
            sectionError
          ) {
            console.warn(
              'Search skipped EPUB section:',
              section?.href,
              sectionError
            );
          }
        }

        setBookSearchResults(
          results
        );

        if (
          results.length === 0
        ) {
          setSearchMessage(
            `No matches found for "${query}".`
          );
        } else {
          setSearchMessage(
            `${results.length} match${
              results.length ===
              1
                ? ''
                : 'es'
            } found.`
          );
        }
      } catch (err) {
        console.error(
          'Book search failed:',
          err
        );

        setSearchMessage(
          'Search could not be completed.'
        );
      } finally {
        setBookSearching(false);
      }
    };

  // =========================================================
  // OPEN SEARCH RESULT
  // =========================================================

  const handleSearchResultSelect =
    async (result) => {
      if (
        !result?.cfi ||
        !renditionRef.current
      ) {
        return;
      }

      try {
        await renditionRef.current
          .display(
            result.cfi
          );

        setSearchOpen(false);
      } catch (err) {
        console.error(
          'Could not open search result:',
          err
        );
      }
    };

  // =========================================================
  // 7. ASK THIS DOCUMENT
  // =========================================================

  const handleAskDocument =
    async (e) => {
      e.preventDefault();

      if (
        !question.trim()
      ) {
        return;
      }

      const currentQuestion =
        question.trim();

      setAsking(true);
      setQaError('');

      try {
        const documentId =
          book?.id
            ? String(
                book.id
              )
            : String(
                resourceId
              );

        const response =
          await api.rag
            .askDocument(
              documentId,
              currentQuestion,
              3
            );

        setQaHistory(
          (previous) => [
            {
              question:
                currentQuestion,

              answer:
                response.answer ||
                'No grounded answer could be generated.',

              citations:
                response.citations ||
                [],

              timestamp:
                new Date()
                  .toLocaleTimeString(
                    [],
                    {
                      hour:
                        '2-digit',

                      minute:
                        '2-digit',
                    }
                  ),
            },

            ...previous,
          ]
        );

        setQuestion('');
      } catch (err) {
        console.error(
          'Document Q&A error:',
          err
        );

        setQaError(
          err.message ||
            'Document assistant is currently unavailable.'
        );
      } finally {
        setAsking(false);
      }
    };

  // =========================================================
  // LOADING
  // =========================================================

  if (loading) {
    return (
      <LoadingSpinner
        message="Opening digital library resource..."
      />
    );
  }

  // =========================================================
  // ERROR
  // =========================================================

  if (error) {
    return (
      <div className="page-container">
        <EmptyState
          icon={FileText}
          title="Digital Resource Unavailable"
          description={
            error
          }
          action={
            <Link
              to="/catalogue"
              className="btn btn-primary btn-sm"
            >
              Return to Catalogue
            </Link>
          }
        />
      </div>
    );
  }

  // =========================================================
  // MAIN UI
  // =========================================================

  return (
    <div
      style={{
        display: 'flex',

        flexDirection:
          'column',

        height:
          'calc(100vh - var(--topbar-height))',

        overflow:
          'hidden',

        background:
          'var(--bg-app)',

        position:
          'relative',
      }}
    >
      {/* =====================================================
          HEADER
      ====================================================== */}

      <div
        style={{
          minHeight:
            '64px',

          padding:
            '0.75rem 1.25rem',

          background:
            '#FFFFFF',

          borderBottom:
            '1px solid var(--border-default)',

          display:
            'flex',

          justifyContent:
            'space-between',

          alignItems:
            'center',

          gap:
            '1rem',
        }}
      >
        <div
          style={{
            display:
              'flex',

            alignItems:
              'center',

            gap:
              '1rem',

            minWidth:
              0,
          }}
        >
          <Link
            to={`/books/${
              book?.id ||
              resourceId
            }`}
            className="btn btn-secondary btn-sm"
          >
            <ArrowLeft
              size={16}
            />

            <span>
              Record Details
            </span>
          </Link>

          <div
            style={{
              minWidth:
                0,
            }}
          >
            <h2
              style={{
                fontFamily:
                  'var(--font-serif)',

                fontSize:
                  '1.05rem',

                color:
                  'var(--primary-ink)',

                margin:
                  0,

                lineHeight:
                  1.3,

                whiteSpace:
                  'nowrap',

                overflow:
                  'hidden',

                textOverflow:
                  'ellipsis',
              }}
            >
              {book?.title ||
                'AstraLib Digital Resource'}
            </h2>

            <div
              style={{
                fontSize:
                  '0.78rem',

                color:
                  'var(--text-secondary)',

                marginTop:
                  '0.15rem',
              }}
            >
              {book?.author
                ?.name
                ? `By ${book.author.name}`
                : 'Unknown author'}

              {book
                ?.publicationYear
                ? ` · ${book.publicationYear}`
                : ''}
            </div>
          </div>
        </div>

        {/* READER TOOLS */}

        <div
          style={{
            display:
              'flex',

            alignItems:
              'center',

            gap:
              '0.5rem',
          }}
        >
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => {
              setTocOpen(
                (value) =>
                  !value
              );

              setSearchOpen(
                false
              );
            }}
          >
            <List
              size={15}
            />

            Contents
          </button>

          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => {
              setSearchOpen(
                true
              );

              setTocOpen(
                false
              );

              setTimeout(
                () =>
                  searchInputRef
                    .current
                    ?.focus(),
                50
              );
            }}
          >
            <Search
              size={15}
            />

            Search Book
          </button>

          {digitalResource && (
            <Badge variant="primary">
              {digitalResource
                .fileName
                ?.split('.')
                .pop()
                ?.toUpperCase() ||
                'DIGITAL'}
            </Badge>
          )}

          <Badge variant="gold">
            Full Text Available
          </Badge>
        </div>
      </div>

      {/* =====================================================
          TABLE OF CONTENTS PANEL
      ====================================================== */}

      {tocOpen && (
        <div
          style={{
            position:
              'absolute',

            top:
              '68px',

            left:
              '1rem',

            width:
              '340px',

            maxHeight:
              'calc(100vh - 110px)',

            overflowY:
              'auto',

            background:
              '#FFFFFF',

            border:
              '1px solid var(--border-default)',

            boxShadow:
              '0 8px 28px rgba(0,0,0,0.14)',

            zIndex:
              50,
          }}
        >
          <div
            style={{
              padding:
                '1rem',

              borderBottom:
                '1px solid var(--border-default)',

              display:
                'flex',

              justifyContent:
                'space-between',

              alignItems:
                'center',

              position:
                'sticky',

              top:
                0,

              background:
                '#FFFFFF',

              zIndex:
                2,
            }}
          >
            <div>
              <h3
                style={{
                  margin:
                    0,

                  fontSize:
                    '1rem',

                  color:
                    'var(--primary-ink)',
                }}
              >
                Table of Contents
              </h3>

              <div
                style={{
                  marginTop:
                    '0.2rem',

                  fontSize:
                    '0.72rem',

                  color:
                    'var(--text-muted)',
                }}
              >
                Select a chapter or section
              </div>
            </div>

            <button
              type="button"
              onClick={() =>
                setTocOpen(
                  false
                )
              }
              style={{
                border:
                  'none',

                background:
                  'transparent',

                cursor:
                  'pointer',
              }}
            >
              <X
                size={18}
              />
            </button>
          </div>

          <div
            style={{
              padding:
                '0.5rem 1rem 1rem',
            }}
          >
            {flattenedToc
              .length === 0 ? (
              <div
                style={{
                  padding:
                    '1rem 0',

                  fontSize:
                    '0.85rem',

                  color:
                    'var(--text-muted)',
                }}
              >
                This EPUB does not provide a table of contents.
              </div>
            ) : (
              flattenedToc.map(
                (
                  item,
                  index
                ) => (
                  <button
                    key={`${item.href}-${index}`}
                    type="button"
                    onClick={() =>
                      handleChapterSelect(
                        item.href
                      )
                    }
                    style={{
                      width:
                        '100%',

                      display:
                        'block',

                      textAlign:
                        'left',

                      border:
                        'none',

                      borderBottom:
                        '1px solid var(--border-light)',

                      background:
                        currentChapter &&
                        item.href &&
                        currentChapter.includes(
                          item.href
                            .split(
                              '#'
                            )[0]
                        )
                          ? 'var(--bg-subtle)'
                          : 'transparent',

                      padding:
                        '0.7rem 0.4rem',

                      paddingLeft:
                        `${0.4 +
                        item.level *
                          1.1}rem`,

                      color:
                        'var(--primary-ink)',

                      fontSize:
                        item.level ===
                        0
                          ? '0.86rem'
                          : '0.8rem',

                      fontWeight:
                        item.level ===
                        0
                          ? 600
                          : 400,

                      cursor:
                        'pointer',
                    }}
                  >
                    {item.label ||
                      'Untitled Section'}
                  </button>
                )
              )
            )}
          </div>
        </div>
      )}

      {/* =====================================================
          SEARCH PANEL
      ====================================================== */}

      {searchOpen && (
        <div
          style={{
            position:
              'absolute',

            top:
              '68px',

            right:
              '1rem',

            width:
              '410px',

            maxHeight:
              'calc(100vh - 110px)',

            background:
              '#FFFFFF',

            border:
              '1px solid var(--border-default)',

            boxShadow:
              '0 8px 28px rgba(0,0,0,0.14)',

            zIndex:
              60,

            display:
              'flex',

            flexDirection:
              'column',
          }}
        >
          <div
            style={{
              padding:
                '1rem',

              borderBottom:
                '1px solid var(--border-default)',
            }}
          >
            <div
              style={{
                display:
                  'flex',

                justifyContent:
                  'space-between',

                alignItems:
                  'center',

                marginBottom:
                  '0.75rem',
              }}
            >
              <div>
                <h3
                  style={{
                    margin:
                      0,

                    fontSize:
                      '1rem',

                    color:
                      'var(--primary-ink)',
                  }}
                >
                  Search Inside Book
                </h3>

                <div
                  style={{
                    marginTop:
                      '0.2rem',

                    fontSize:
                      '0.72rem',

                    color:
                      'var(--text-muted)',
                  }}
                >
                  Shortcut: Ctrl + F
                </div>
              </div>

              <button
                type="button"
                onClick={() =>
                  setSearchOpen(
                    false
                  )
                }
                style={{
                  border:
                    'none',

                  background:
                    'transparent',

                  cursor:
                    'pointer',
                }}
              >
                <X
                  size={18}
                />
              </button>
            </div>

            <div
              style={{
                display:
                  'flex',

                gap:
                  '0.5rem',
              }}
            >
              <input
                ref={
                  searchInputRef
                }
                id="astralib-book-search"
                type="text"
                className="form-input"
                placeholder="Search a word or phrase..."
                value={
                  bookSearchQuery
                }
                onChange={(
                  e
                ) =>
                  setBookSearchQuery(
                    e.target.value
                  )
                }
                onKeyDown={(
                  e
                ) => {
                  if (
                    e.key ===
                    'Enter'
                  ) {
                    e.preventDefault();

                    handleBookSearch();
                  }
                }}
              />

              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={
                  handleBookSearch
                }
                disabled={
                  bookSearching ||
                  !bookSearchQuery.trim()
                }
              >
                <Search
                  size={15}
                />

                Search
              </button>
            </div>

            {searchMessage && (
              <div
                style={{
                  fontSize:
                    '0.76rem',

                  color:
                    'var(--text-secondary)',

                  marginTop:
                    '0.6rem',
                }}
              >
                {
                  searchMessage
                }
              </div>
            )}
          </div>

          <div
            style={{
              overflowY:
                'auto',

              padding:
                '0.75rem',

              flex:
                1,
            }}
          >
            {bookSearching ? (
              <div
                style={{
                  padding:
                    '1.5rem',

                  textAlign:
                    'center',

                  color:
                    'var(--text-secondary)',
                }}
              >
                Searching the complete book...
              </div>
            ) : bookSearchResults
                .length ===
              0 ? (
              <div
                style={{
                  padding:
                    '1.25rem',

                  fontSize:
                    '0.85rem',

                  color:
                    'var(--text-muted)',

                  textAlign:
                    'center',
                }}
              >
                Enter a word such as{' '}
                <strong>
                  Holmes
                </strong>
                ,{' '}
                <strong>
                  Watson
                </strong>{' '}
                or{' '}
                <strong>
                  Irene
                </strong>
                .
              </div>
            ) : (
              bookSearchResults.map(
                (
                  result,
                  index
                ) => (
                  <button
                    key={`${result.cfi}-${index}`}
                    type="button"
                    onClick={() =>
                      handleSearchResultSelect(
                        result
                      )
                    }
                    style={{
                      display:
                        'block',

                      width:
                        '100%',

                      textAlign:
                        'left',

                      padding:
                        '0.75rem',

                      marginBottom:
                        '0.55rem',

                      border:
                        '1px solid var(--border-light)',

                      background:
                        'var(--bg-app)',

                      cursor:
                        'pointer',
                    }}
                  >
                    <div
                      style={{
                        fontSize:
                          '0.72rem',

                        fontWeight:
                          600,

                        color:
                          'var(--primary-ink)',

                        marginBottom:
                          '0.35rem',
                      }}
                    >
                      {result.chapter ||
                        'Book Section'}
                    </div>

                    <div
                      style={{
                        fontSize:
                          '0.8rem',

                        color:
                          'var(--text-secondary)',

                        lineHeight:
                          1.5,
                      }}
                    >
                      {result.excerpt ||
                        'Matching text'}
                    </div>
                  </button>
                )
              )
            )}
          </div>
        </div>
      )}

      {/* =====================================================
          READER + ASSISTANT
      ====================================================== */}

      <div
        style={{
          display:
            'flex',

          flex:
            1,

          minHeight:
            0,

          overflow:
            'hidden',
        }}
      >
        {/* ===================================================
            LEFT - EPUB READER
        ==================================================== */}

        <div
          style={{
            flex:
              1,

            minWidth:
              0,

            display:
              'flex',

            flexDirection:
              'column',

            background:
              '#F8F8F5',

            borderRight:
              '1px solid var(--border-default)',
          }}
        >
          {/* Reader status */}

          <div
            style={{
              padding:
                '0.65rem 1rem',

              background:
                '#FFFFFF',

              borderBottom:
                '1px solid var(--border-default)',

              display:
                'flex',

              justifyContent:
                'space-between',

              alignItems:
                'center',

              gap:
                '1rem',
            }}
          >
            <div
              style={{
                display:
                  'flex',

                alignItems:
                  'center',

                gap:
                  '0.75rem',
              }}
            >
              <span
                style={{
                  fontSize:
                    '0.8rem',

                  color:
                    'var(--text-secondary)',
                }}
              >
                Digital Library Reader
              </span>

              {toc.length >
                0 && (
                <span
                  style={{
                    fontSize:
                      '0.7rem',

                    color:
                      'var(--text-muted)',
                  }}
                >
                  {flattenedToc.length}{' '}
                  sections
                </span>
              )}
            </div>

            <div
              style={{
                display:
                  'flex',

                gap:
                  '1rem',

                alignItems:
                  'center',

                fontSize:
                  '0.75rem',

                color:
                  'var(--text-secondary)',
              }}
            >
              {currentChapter && (
                <span
                  title={
                    currentChapter
                  }
                  style={{
                    maxWidth:
                      '260px',

                    overflow:
                      'hidden',

                    textOverflow:
                      'ellipsis',

                    whiteSpace:
                      'nowrap',
                  }}
                >
                  {
                    getChapterLabel(
                      currentChapter
                    )
                  }
                </span>
              )}

              <strong>
                {progress}% read
              </strong>
            </div>
          </div>

          {/* EPUB viewer */}

          <div
            style={{
              flex:
                1,

              minHeight:
                0,

              padding:
                '1.25rem',

              overflow:
                'hidden',
            }}
          >
            {readerError ? (
              <div
                className="banner banner-error"
                style={{
                  maxWidth:
                    '800px',

                  margin:
                    '2rem auto',
                }}
              >
                <AlertCircle
                  size={16}
                />

                <span>
                  {readerError}
                </span>
              </div>
            ) : (
              <div
                style={{
                  width:
                    '100%',

                  height:
                    '100%',

                  maxWidth:
                    '920px',

                  margin:
                    '0 auto',

                  background:
                    '#FFFFFF',

                  border:
                    '1px solid var(--border-default)',

                  boxShadow:
                    '0 2px 10px rgba(0,0,0,0.06)',

                  position:
                    'relative',
                }}
              >
                {!readerReady && (
                  <div
                    style={{
                      position:
                        'absolute',

                      inset:
                        0,

                      display:
                        'flex',

                      alignItems:
                        'center',

                      justifyContent:
                        'center',

                      flexDirection:
                        'column',

                      gap:
                        '0.75rem',

                      background:
                        '#FFFFFF',

                      zIndex:
                        2,

                      color:
                        'var(--text-secondary)',
                    }}
                  >
                    <BookOpen
                      size={34}
                      strokeWidth={
                        1.5
                      }
                    />

                    <span>
                      Opening digital book...
                    </span>
                  </div>
                )}

                <div
                  ref={
                    viewerRef
                  }
                  style={{
                    width:
                      '100%',

                    height:
                      '100%',
                  }}
                />
              </div>
            )}
          </div>

          {/* Page navigation */}

          <div
            style={{
              padding:
                '0.7rem 1rem',

              background:
                '#FFFFFF',

              borderTop:
                '1px solid var(--border-default)',

              display:
                'flex',

              justifyContent:
                'center',

              alignItems:
                'center',

              gap:
                '1rem',
            }}
          >
            <button
              type="button"
              className="btn btn-secondary"
              onClick={
                handlePreviousPage
              }
              disabled={
                !readerReady
              }
            >
              <ChevronLeft
                size={16}
              />

              Previous
            </button>

            <div
              style={{
                minWidth:
                  '110px',

                textAlign:
                  'center',

                fontSize:
                  '0.8rem',

                color:
                  'var(--text-secondary)',
              }}
            >
              {readerReady
                ? `${progress}%`
                : 'Loading...'}
            </div>

            <button
              type="button"
              className="btn btn-primary"
              onClick={
                handleNextPage
              }
              disabled={
                !readerReady
              }
            >
              Next

              <ChevronRight
                size={16}
              />
            </button>
          </div>
        </div>

        {/* ===================================================
            RIGHT - ASK THIS DOCUMENT
        ==================================================== */}

        <div
          style={{
            width:
              '380px',

            maxWidth:
              '40vw',

            background:
              '#FFFFFF',

            display:
              'flex',

            flexDirection:
              'column',

            minHeight:
              0,
          }}
        >
          <div
            style={{
              padding:
                '1rem 1.25rem',

              borderBottom:
                '1px solid var(--border-default)',

              background:
                'var(--bg-subtle)',
            }}
          >
            <div
              style={{
                display:
                  'flex',

                alignItems:
                  'center',

                gap:
                  '0.5rem',
              }}
            >
              <Bot
                size={18}
                color="var(--primary-ink)"
              />

              <h3
                style={{
                  fontSize:
                    '0.95rem',

                  color:
                    'var(--primary-ink)',

                  margin:
                    0,
                }}
              >
                Ask this Document
              </h3>
            </div>

            <div
              style={{
                fontSize:
                  '0.75rem',

                color:
                  'var(--text-secondary)',

                marginTop:
                  '0.25rem',
              }}
            >
              Ask questions grounded in this resource.
            </div>
          </div>

          {/* Q&A messages */}

          <div
            style={{
              flex:
                1,

              minHeight:
                0,

              overflowY:
                'auto',

              padding:
                '1rem',

              display:
                'flex',

              flexDirection:
                'column',

              gap:
                '1rem',
            }}
          >
            {qaHistory.length ===
            0 ? (
              <div
                style={{
                  textAlign:
                    'center',

                  padding:
                    '2rem 1rem',

                  color:
                    'var(--text-muted)',
                }}
              >
                <Bot
                  size={32}
                  strokeWidth={
                    1.5
                  }
                  style={{
                    marginBottom:
                      '0.6rem',

                    opacity:
                      0.55,
                  }}
                />

                <div
                  style={{
                    fontWeight:
                      600,

                    fontSize:
                      '0.88rem',

                    color:
                      'var(--text-secondary)',
                  }}
                >
                  Ask questions about this book
                </div>

                <p
                  style={{
                    fontSize:
                      '0.78rem',

                    marginTop:
                      '0.3rem',

                    lineHeight:
                      1.5,
                  }}
                >
                  Answers will use relevant AstraLib document
                  sections when available.
                </p>
              </div>
            ) : (
              qaHistory.map(
                (
                  item,
                  index
                ) => (
                  <div
                    key={
                      index
                    }
                    style={{
                      padding:
                        '0.85rem',

                      border:
                        '1px solid var(--border-default)',

                      background:
                        'var(--bg-app)',
                    }}
                  >
                    <div
                      style={{
                        fontWeight:
                          600,

                        fontSize:
                          '0.85rem',

                        color:
                          'var(--primary-ink)',

                        marginBottom:
                          '0.4rem',
                      }}
                    >
                      Q:{' '}
                      {
                        item.question
                      }
                    </div>

                    <div
                      style={{
                        fontSize:
                          '0.85rem',

                        color:
                          'var(--text-primary)',

                        lineHeight:
                          1.6,
                      }}
                    >
                      {
                        item.answer
                      }
                    </div>

                    {item.citations
                      ?.length >
                      0 && (
                      <div
                        style={{
                          marginTop:
                            '0.7rem',

                          paddingTop:
                            '0.6rem',

                          borderTop:
                            '1px solid var(--border-light)',
                        }}
                      >
                        <div
                          style={{
                            fontSize:
                              '0.7rem',

                            fontWeight:
                              600,

                            color:
                              'var(--text-secondary)',

                            marginBottom:
                              '0.25rem',
                          }}
                        >
                          Sources
                        </div>

                        {item.citations.map(
                          (
                            citation,
                            citationIndex
                          ) => (
                            <div
                              key={
                                citationIndex
                              }
                              style={{
                                fontSize:
                                  '0.72rem',

                                color:
                                  'var(--text-muted)',

                                marginBottom:
                                  '0.2rem',
                              }}
                            >
                              •{' '}

                              {citation.title ||
                                citation.section ||
                                citation.chunkId ||
                                `Chunk ${
                                  citation.chunkIndex ??
                                  citationIndex +
                                    1
                                }`}
                            </div>
                          )
                        )}
                      </div>
                    )}

                    <div
                      style={{
                        marginTop:
                          '0.5rem',

                        fontSize:
                          '0.68rem',

                        color:
                          'var(--text-muted)',

                        textAlign:
                          'right',
                      }}
                    >
                      {
                        item.timestamp
                      }
                    </div>
                  </div>
                )
              )
            )}

            {qaError && (
              <div
                className="banner banner-error"
                style={{
                  padding:
                    '0.5rem 0.75rem',

                  fontSize:
                    '0.8rem',
                }}
              >
                <AlertCircle
                  size={14}
                />

                <span>
                  {qaError}
                </span>
              </div>
            )}

            {asking && (
              <div
                style={{
                  display:
                    'flex',

                  alignItems:
                    'center',

                  gap:
                    '0.5rem',

                  padding:
                    '0.5rem',

                  color:
                    'var(--text-secondary)',

                  fontSize:
                    '0.8rem',
                }}
              >
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
                  Searching this resource...
                </span>
              </div>
            )}
          </div>

          {/* Q&A input */}

          <form
            onSubmit={
              handleAskDocument
            }
            style={{
              padding:
                '0.75rem',

              borderTop:
                '1px solid var(--border-default)',

              background:
                '#FFFFFF',
            }}
          >
            <div
              style={{
                display:
                  'flex',

                gap:
                  '0.4rem',
              }}
            >
              <input
                type="text"
                className="form-input"
                style={{
                  fontSize:
                    '0.85rem',

                  padding:
                    '0.45rem 0.65rem',
                }}
                placeholder="Ask about this book..."
                value={
                  question
                }
                onChange={(
                  e
                ) =>
                  setQuestion(
                    e.target.value
                  )
                }
                disabled={
                  asking
                }
              />

              <button
                type="submit"
                className="btn btn-primary btn-sm"
                disabled={
                  asking ||
                  !question.trim()
                }
              >
                <Send
                  size={14}
                />
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

export default DocumentReaderPage;