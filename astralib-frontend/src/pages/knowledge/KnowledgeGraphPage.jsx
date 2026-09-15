import React, {
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  useNavigate,
  useSearchParams,
} from 'react-router-dom';

import {
  ArrowLeft,
  BookOpen,
  Network,
  RotateCcw,
  Search,
  ZoomIn,
  ZoomOut,
} from 'lucide-react';

import {
  api,
} from '../../services/api';


// =========================================================
// GRAPH CONFIGURATION
// =========================================================

const GRAPH_WIDTH = 1050;
const GRAPH_HEIGHT = 720;


// =========================================================
// NODE STYLES
// =========================================================

const NODE_STYLES = {

  BOOK: {
    width: 200,
    height: 72,
    fill: '#172554',
    stroke: '#1e3a8a',
    text: '#ffffff',
  },

  AUTHOR: {
    width: 155,
    height: 54,
    fill: '#eff6ff',
    stroke: '#3b82f6',
    text: '#1e3a8a',
  },

  CATEGORY: {
    width: 155,
    height: 54,
    fill: '#f5f3ff',
    stroke: '#8b5cf6',
    text: '#5b21b6',
  },

  SECTION: {
    width: 180,
    height: 62,
    fill: '#ffffff',
    stroke: '#64748b',
    text: '#334155',
  },

  CONCEPT: {
    width: 165,
    height: 54,
    fill: '#ecfdf5',
    stroke: '#10b981',
    text: '#065f46',
  },

  DEFAULT: {
    width: 150,
    height: 52,
    fill: '#ffffff',
    stroke: '#94a3b8',
    text: '#334155',
  },
};


// =========================================================
// HELPERS
// =========================================================

function getNodeStyle(type) {

  return (
    NODE_STYLES[type]
    ||
    NODE_STYLES.DEFAULT
  );
}


function shortenLabel(
  text,
  maxLength = 25
) {

  if (!text) {
    return '';
  }

  if (
    text.length <= maxLength
  ) {
    return text;
  }

  return (
    text.slice(
      0,
      maxLength - 1
    )
    + '…'
  );
}


// =========================================================
// BOOK OVERVIEW LAYOUT
// =========================================================

function createBookOverviewPositions(
  nodes
) {

  const positions = {};

  if (
    !Array.isArray(nodes)
  ) {
    return positions;
  }


  const centerX =
    GRAPH_WIDTH / 2;

  const centerY =
    300;


  const book =
    nodes.find(
      node =>
        node.type === 'BOOK'
    );


  if (book) {

    positions[
      book.id
    ] = {
      x: centerX,
      y: centerY,
    };
  }


  const authors =
    nodes.filter(
      node =>
        node.type === 'AUTHOR'
    );


  const categories =
    nodes.filter(
      node =>
        node.type === 'CATEGORY'
    );


  const sections =
    nodes.filter(
      node =>
        node.type === 'SECTION'
    );


  // =====================================================
  // AUTHOR
  // =====================================================

  authors.forEach(
    (
      node,
      index
    ) => {

      positions[
        node.id
      ] = {

        x:
          centerX
          - 260
          - index * 20,

        y:
          110
          + index * 70,
      };
    }
  );


  // =====================================================
  // CATEGORY
  // =====================================================

  categories.forEach(
    (
      node,
      index
    ) => {

      positions[
        node.id
      ] = {

        x:
          centerX
          + 260
          + index * 20,

        y:
          110
          + index * 70,
      };
    }
  );


  // =====================================================
  // SECTIONS
  // =====================================================

  if (
    sections.length > 0
  ) {

    const radiusX =
      400;

    const radiusY =
      270;


    sections.forEach(
      (
        node,
        index
      ) => {

        const angle =
          Math.PI
          * (
            0.05
            +
            (
              index
              /
              Math.max(
                sections.length - 1,
                1
              )
            )
            * 0.9
          );


        positions[
          node.id
        ] = {

          x:
            centerX
            +
            Math.cos(
              angle
            )
            * radiusX,

          y:
            centerY
            +
            70
            +
            Math.sin(
              angle
            )
            * radiusY,
        };
      }
    );
  }


  return positions;
}


// =========================================================
// FOCUS MODE LAYOUT
// =========================================================

function createFocusPositions(
  conceptGraph,
  sectionNodeId
) {

  const positions = {};


  const nodes =
    conceptGraph?.nodes
    || [];


  const book =
    nodes.find(
      node =>
        node.type === 'BOOK'
    );


  const section =
    nodes.find(
      node =>
        node.type === 'SECTION'
    );


  const concepts =
    nodes.filter(
      node =>
        node.type === 'CONCEPT'
    );


  const centerX =
    GRAPH_WIDTH / 2;


  // =====================================================
  // BOOK AT TOP
  // =====================================================

  if (book) {

    positions[
      book.id
    ] = {

      x:
        centerX,

      y:
        75,
    };
  }


  // =====================================================
  // SELECTED SECTION IN CENTER
  // =====================================================

  if (section) {

    positions[
      sectionNodeId
      ||
      section.id
    ] = {

      x:
        centerX,

      y:
        365,
    };


    positions[
      section.id
    ] = {

      x:
        centerX,

      y:
        365,
    };
  }


  // =====================================================
  // CONCEPTS IN TWO RINGS
  // =====================================================

  const innerConcepts =
    concepts.slice(
      0,
      6
    );


  const outerConcepts =
    concepts.slice(
      6
    );


  // -----------------------------------------------------
  // INNER RING
  // -----------------------------------------------------

  innerConcepts.forEach(
    (
      concept,
      index
    ) => {

      const angle =
        -Math.PI / 2
        +
        (
          Math.PI * 2 * index
        )
        /
        Math.max(
          innerConcepts.length,
          1
        );


      positions[
        concept.id
      ] = {

        x:
          centerX
          +
          Math.cos(
            angle
          )
          * 225,

        y:
          365
          +
          Math.sin(
            angle
          )
          * 145,
      };
    }
  );


  // -----------------------------------------------------
  // OUTER RING
  // -----------------------------------------------------

  outerConcepts.forEach(
    (
      concept,
      index
    ) => {

      const angle =
        -Math.PI / 2
        +
        Math.PI / 6
        +
        (
          Math.PI * 2 * index
        )
        /
        Math.max(
          outerConcepts.length,
          1
        );


      positions[
        concept.id
      ] = {

        x:
          centerX
          +
          Math.cos(
            angle
          )
          * 365,

        y:
          385
          +
          Math.sin(
            angle
          )
          * 250,
      };
    }
  );


  return positions;
}


// =========================================================
// MAIN PAGE
// =========================================================

export function KnowledgeGraphPage() {

  const navigate =
    useNavigate();


  const [
    searchParams,
    setSearchParams,
  ] =
    useSearchParams();


  const initialBookId =
    searchParams.get(
      'bookId'
    )
    ||
    '3003';


  // =====================================================
  // BOOK
  // =====================================================

  const [
    bookIdInput,
    setBookIdInput,
  ] =
    useState(
      initialBookId
    );


  const [
    activeBookId,
    setActiveBookId,
  ] =
    useState(
      initialBookId
    );


  // =====================================================
  // GRAPH DATA
  // =====================================================

  const [
    baseGraph,
    setBaseGraph,
  ] =
    useState(
      null
    );


  const [
    conceptGraph,
    setConceptGraph,
  ] =
    useState(
      null
    );


  const [
    expandedSectionId,
    setExpandedSectionId,
  ] =
    useState(
      null
    );


  const [
    expandedSectionLabel,
    setExpandedSectionLabel,
  ] =
    useState(
      ''
    );


  // =====================================================
  // SELECTION
  // =====================================================

  const [
    selectedNode,
    setSelectedNode,
  ] =
    useState(
      null
    );


  // =====================================================
  // STATUS
  // =====================================================

  const [
    loading,
    setLoading,
  ] =
    useState(
      false
    );


  const [
    conceptLoading,
    setConceptLoading,
  ] =
    useState(
      false
    );


  const [
    error,
    setError,
  ] =
    useState(
      ''
    );


  // =====================================================
  // GRAPH TRANSFORM
  // =====================================================

  const [
    zoom,
    setZoom,
  ] =
    useState(
      1
    );


  const [
    pan,
    setPan,
  ] =
    useState({
      x: 0,
      y: 0,
    });


  const [
    positions,
    setPositions,
  ] =
    useState({});


  // =====================================================
  // DRAGGING
  // =====================================================

  const [
    draggingNodeId,
    setDraggingNodeId,
  ] =
    useState(
      null
    );


  const [
    dragOffset,
    setDragOffset,
  ] =
    useState({
      x: 0,
      y: 0,
    });


  const [
    isPanning,
    setIsPanning,
  ] =
    useState(
      false
    );


  const [
    lastMouse,
    setLastMouse,
  ] =
    useState({
      x: 0,
      y: 0,
    });


  // =====================================================
  // ARE WE IN SECTION FOCUS MODE?
  // =====================================================

  const focusMode =
    Boolean(
      conceptGraph
      &&
      expandedSectionId
    );


  // =====================================================
  // RELATED CONCEPTS FOR SELECTED CONCEPT
  // =====================================================

  const relatedConceptIds =
    useMemo(
      () => {

        const ids =
          new Set();


        if (
          !focusMode
          ||
          selectedNode?.type
          !==
          'CONCEPT'
        ) {

          return ids;
        }


        conceptGraph
          ?.edges
          ?.filter(
            edge =>
              edge.relation
              ===
              'RELATED_TO'
          )
          .forEach(
            edge => {

              if (
                edge.source
                ===
                selectedNode.id
              ) {

                ids.add(
                  edge.target
                );
              }


              if (
                edge.target
                ===
                selectedNode.id
              ) {

                ids.add(
                  edge.source
                );
              }
            }
          );


        return ids;
      },
      [
        focusMode,
        conceptGraph,
        selectedNode,
      ]
    );


  // =====================================================
  // WHICH NODES TO DISPLAY?
  // =====================================================

  const displayedNodes =
    useMemo(
      () => {

        if (
          !focusMode
        ) {

          return (
            baseGraph?.nodes
            ||
            []
          );
        }


        return (
          conceptGraph?.nodes
          ||
          []
        );
      },
      [
        focusMode,
        baseGraph,
        conceptGraph,
      ]
    );


  // =====================================================
  // WHICH EDGES TO DISPLAY?
  // =====================================================

  const displayedEdges =
    useMemo(
      () => {

        if (
          !focusMode
        ) {

          return (
            baseGraph?.edges
            ||
            []
          );
        }


        const edges =
          conceptGraph?.edges
          ||
          [];


        // -------------------------------------------------
        // When no concept is selected:
        //
        // BOOK -> SECTION
        // SECTION -> CONCEPT
        //
        // RELATED_TO is hidden.
        // -------------------------------------------------

        if (
          selectedNode?.type
          !==
          'CONCEPT'
        ) {

          return edges.filter(
            edge =>
              edge.relation
              !==
              'RELATED_TO'
          );
        }


        // -------------------------------------------------
        // When concept is selected:
        //
        // show only RELATED_TO edges touching it.
        // -------------------------------------------------

        return edges.filter(
          edge => {

            if (
              edge.relation
              !==
              'RELATED_TO'
            ) {
              return true;
            }


            return (
              edge.source
              ===
              selectedNode.id
              ||
              edge.target
              ===
              selectedNode.id
            );
          }
        );
      },
      [
        focusMode,
        baseGraph,
        conceptGraph,
        selectedNode,
      ]
    );


  // =====================================================
  // LOAD BASE GRAPH
  // =====================================================

  const loadBookGraph =
    async (
      bookId
    ) => {

      try {

        setLoading(
          true
        );


        setError(
          ''
        );


        setConceptGraph(
          null
        );


        setExpandedSectionId(
          null
        );


        setExpandedSectionLabel(
          ''
        );


        setSelectedNode(
          null
        );


        const data =
          await api
            .knowledgeGraph
            .getBookGraph(
              bookId
            );


        setBaseGraph(
          data
        );


        setPositions(
          createBookOverviewPositions(
            data?.nodes
            ||
            []
          )
        );


        setZoom(
          1
        );


        setPan({
          x: 0,
          y: 0,
        });


      } catch (err) {

        console.error(
          'Knowledge Graph error:',
          err
        );


        setError(
          err.message
          ||
          'Could not load knowledge graph.'
        );


        setBaseGraph(
          null
        );


      } finally {

        setLoading(
          false
        );
      }
    };


  // =====================================================
  // INITIAL LOAD
  // =====================================================

  useEffect(
    () => {

      loadBookGraph(
        activeBookId
      );

    },
    [
      activeBookId,
    ]
  );


  // =====================================================
  // ENTER SECTION FOCUS MODE
  // =====================================================

  const expandSection =
    async (
      node
    ) => {

      if (
        !node
        ||
        node.type
        !==
        'SECTION'
      ) {

        return;
      }


      try {

        setConceptLoading(
          true
        );


        setError(
          ''
        );


        const data =
          await api
            .knowledgeGraph
            .getSectionConceptGraph(
              activeBookId,
              node.label,
              12
            );


        setConceptGraph(
          data
        );


        setExpandedSectionId(
          node.id
        );


        setExpandedSectionLabel(
          node.label
        );


        const sectionFromResponse =
          data?.nodes?.find(
            item =>
              item.type
              ===
              'SECTION'
          );


        setSelectedNode(
          sectionFromResponse
          ||
          node
        );


        setPositions(
          createFocusPositions(
            data,
            node.id
          )
        );


        setZoom(
          1
        );


        setPan({
          x: 0,
          y: 0,
        });


      } catch (err) {

        console.error(
          'Concept expansion error:',
          err
        );


        setError(
          err.message
          ||
          'Could not load concepts for this section.'
        );


      } finally {

        setConceptLoading(
          false
        );
      }
    };


  // =====================================================
  // RETURN TO BOOK OVERVIEW
  // =====================================================

  const returnToBookGraph =
    () => {

      setConceptGraph(
        null
      );


      setExpandedSectionId(
        null
      );


      setExpandedSectionLabel(
        ''
      );


      setSelectedNode(
        null
      );


      setPositions(
        createBookOverviewPositions(
          baseGraph?.nodes
          ||
          []
        )
      );


      setZoom(
        1
      );


      setPan({
        x: 0,
        y: 0,
      });
    };


  // =====================================================
  // NODE CLICK
  // =====================================================

  const handleNodeClick =
    (
      event,
      node
    ) => {

      event.stopPropagation();


      setSelectedNode(
        node
      );


      // -------------------------------------------------
      // In overview:
      // clicking section enters focus mode.
      // -------------------------------------------------

      if (
        !focusMode
        &&
        node.type
        ===
        'SECTION'
      ) {

        expandSection(
          node
        );
      }
    };


  // =====================================================
  // BOOK LOAD
  // =====================================================

  const handleBookSubmit =
    event => {

      event.preventDefault();


      const value =
        bookIdInput.trim();


      if (!value) {
        return;
      }


      setSearchParams({
        bookId: value,
      });


      setActiveBookId(
        value
      );
    };


  // =====================================================
  // NODE DRAG
  // =====================================================

  const handleNodeMouseDown =
    (
      event,
      node
    ) => {

      event.stopPropagation();


      const position =
        positions[
          node.id
        ];


      if (!position) {
        return;
      }


      setDraggingNodeId(
        node.id
      );


      setDragOffset({

        x:
          event.clientX
          -
          position.x
          * zoom,

        y:
          event.clientY
          -
          position.y
          * zoom,
      });
    };


  // =====================================================
  // BACKGROUND PAN
  // =====================================================

  const handleBackgroundMouseDown =
    event => {

      if (
        event.target
        !==
        event.currentTarget
      ) {

        return;
      }


      setIsPanning(
        true
      );


      setLastMouse({

        x:
          event.clientX,

        y:
          event.clientY,
      });
    };


  const handleMouseMove =
    event => {

      // =================================================
      // NODE DRAG
      // =================================================

      if (
        draggingNodeId
      ) {

        const x =
          (
            event.clientX
            -
            dragOffset.x
          )
          /
          zoom;


        const y =
          (
            event.clientY
            -
            dragOffset.y
          )
          /
          zoom;


        setPositions(
          previous => ({

            ...previous,

            [
              draggingNodeId
            ]: {
              x,
              y,
            },
          })
        );


        return;
      }


      // =================================================
      // PAN
      // =================================================

      if (
        isPanning
      ) {

        const dx =
          event.clientX
          -
          lastMouse.x;


        const dy =
          event.clientY
          -
          lastMouse.y;


        setPan(
          previous => ({

            x:
              previous.x
              +
              dx,

            y:
              previous.y
              +
              dy,
          })
        );


        setLastMouse({

          x:
            event.clientX,

          y:
            event.clientY,
        });
      }
    };


  const handleMouseUp =
    () => {

      setDraggingNodeId(
        null
      );


      setIsPanning(
        false
      );
    };


  // =====================================================
  // ZOOM
  // =====================================================

  const handleWheel =
    event => {

      const amount =
        event.deltaY < 0
          ? 0.08
          : -0.08;


      setZoom(
        previous =>
          Math.min(
            1.8,
            Math.max(
              0.45,
              previous
              +
              amount
            )
          )
      );
    };


  // =====================================================
  // RESET VIEW
  // =====================================================

  const resetView =
    () => {

      setZoom(
        1
      );


      setPan({
        x: 0,
        y: 0,
      });


      if (
        focusMode
      ) {

        setPositions(
          createFocusPositions(
            conceptGraph,
            expandedSectionId
          )
        );

      } else {

        setPositions(
          createBookOverviewPositions(
            baseGraph?.nodes
            ||
            []
          )
        );
      }
    };


  // =====================================================
  // OPEN / SEARCH SELECTED NODE
  // =====================================================

  const handleOpenSelected =
    () => {

      if (
        !selectedNode
      ) {

        return;
      }


      if (
        selectedNode.type
        ===
        'BOOK'
        ||
        selectedNode.type
        ===
        'SECTION'
      ) {

        navigate(
          `/reader/${
            selectedNode.bookId
            ||
            activeBookId
          }`
        );

        return;
      }


      if (
        selectedNode.type
        ===
        'CONCEPT'
        ||
        selectedNode.type
        ===
        'AUTHOR'
        ||
        selectedNode.type
        ===
        'CATEGORY'
      ) {

        navigate(
          `/search?q=${encodeURIComponent(
            selectedNode.label
          )}`
        );
      }
    };


  // =====================================================
  // EDGE
  // =====================================================

  const renderEdge =
    edge => {

      const source =
        positions[
          edge.source
        ];


      const target =
        positions[
          edge.target
        ];


      if (
        !source
        ||
        !target
      ) {

        return null;
      }


      const related =
        edge.relation
        ===
        'RELATED_TO';


      const conceptEdge =
        edge.relation
        ===
        'HAS_CONCEPT';


      let opacity =
        1;


      // =================================================
      // FOCUS MODE DIMMING
      // =================================================

      if (
        focusMode
        &&
        selectedNode?.type
        ===
        'CONCEPT'
        &&
        conceptEdge
      ) {

        const important =
          edge.target
          ===
          selectedNode.id
          ||
          relatedConceptIds.has(
            edge.target
          );


        opacity =
          important
            ? 0.75
            : 0.12;
      }


      const middleX =
        (
          source.x
          +
          target.x
        )
        /
        2;


      const middleY =
        (
          source.y
          +
          target.y
        )
        /
        2;


      return (

        <g
          key={
            edge.id
          }
          opacity={
            opacity
          }
        >

          <line
            x1={
              source.x
            }
            y1={
              source.y
            }
            x2={
              target.x
            }
            y2={
              target.y
            }
            stroke={
              related
                ? '#059669'
                :
                conceptEdge
                  ? '#86efac'
                  : '#cbd5e1'
            }
            strokeWidth={
              related
                ? 2.3
                : 1.5
            }
            strokeDasharray={
              related
                ? '7 5'
                : undefined
            }
          />


          {/* =============================================
              Hide concept relationship labels.
              They were causing most of the visual clutter.
          ============================================== */}

          {
            !focusMode
            &&
            (
              <>

                <rect
                  x={
                    middleX - 38
                  }
                  y={
                    middleY - 9
                  }
                  width="76"
                  height="17"
                  rx="8"
                  fill="#ffffff"
                  opacity="0.94"
                />


                <text
                  x={
                    middleX
                  }
                  y={
                    middleY + 3
                  }
                  textAnchor="middle"
                  fontSize="8"
                  fontWeight="600"
                  fill="#64748b"
                >
                  {
                    edge.relation
                  }
                </text>

              </>
            )
          }

        </g>
      );
    };


  // =====================================================
  // NODE OPACITY
  // =====================================================

  const getNodeOpacity =
    node => {

      if (
        !focusMode
        ||
        selectedNode?.type
        !==
        'CONCEPT'
      ) {

        return 1;
      }


      if (
        node.type
        ===
        'BOOK'
        ||
        node.type
        ===
        'SECTION'
      ) {

        return 1;
      }


      if (
        node.id
        ===
        selectedNode.id
      ) {

        return 1;
      }


      if (
        relatedConceptIds.has(
          node.id
        )
      ) {

        return 1;
      }


      return 0.28;
    };


  // =====================================================
  // NODE
  // =====================================================

  const renderNode =
    node => {

      const position =
        positions[
          node.id
        ];


      if (!position) {
        return null;
      }


      const style =
        getNodeStyle(
          node.type
        );


      const selected =
        selectedNode?.id
        ===
        node.id;


      const opacity =
        getNodeOpacity(
          node
        );


      return (

        <g
          key={
            node.id
          }
          transform={
            `translate(${
              position.x
            }, ${
              position.y
            })`
          }
          opacity={
            opacity
          }
          onMouseDown={
            event =>
              handleNodeMouseDown(
                event,
                node
              )
          }
          onClick={
            event =>
              handleNodeClick(
                event,
                node
              )
          }
          style={{
            cursor:
              node.type
              ===
              'SECTION'
              ||
              node.type
              ===
              'CONCEPT'
                ? 'pointer'
                : 'grab',
          }}
        >

          {
            selected
            &&
            (
              <rect
                x={
                  -style.width / 2 - 7
                }
                y={
                  -style.height / 2 - 7
                }
                width={
                  style.width + 14
                }
                height={
                  style.height + 14
                }
                rx="15"
                fill="none"
                stroke={
                  node.type
                  ===
                  'CONCEPT'
                    ? '#059669'
                    : '#0f172a'
                }
                strokeWidth="3"
                opacity="0.35"
              />
            )
          }


          <rect
            x={
              -style.width / 2
            }
            y={
              -style.height / 2
            }
            width={
              style.width
            }
            height={
              style.height
            }
            rx="11"
            fill={
              style.fill
            }
            stroke={
              style.stroke
            }
            strokeWidth={
              selected
                ? 2.8
                : 1.8
            }
          />


          <text
            x="0"
            y="-4"
            textAnchor="middle"
            fontSize={
              node.type
              ===
              'BOOK'
                ? 12
                : 10.5
            }
            fontWeight="700"
            fill={
              style.text
            }
          >

            {
              shortenLabel(
                node.label,
                node.type
                ===
                'BOOK'
                  ? 30
                  : 24
              )
            }

          </text>


          <text
            x="0"
            y="14"
            textAnchor="middle"
            fontSize="7.5"
            fontWeight="600"
            fill={
              style.text
            }
            opacity="0.65"
          >

            {
              node.type
            }

          </text>

        </g>
      );
    };


  // =====================================================
  // RELATED COUNT
  // =====================================================

  const relatedCount =
    selectedNode?.type
    ===
    'CONCEPT'
      ?
      relatedConceptIds.size
      :
      0;


  // =====================================================
  // PAGE
  // =====================================================

  return (

    <div
      style={{
        padding: '1.5rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '1rem',
        minHeight:
          'calc(100vh - 64px)',
      }}
    >

      {/* =================================================
          HEADER
      ================================================== */}

      <div>

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.65rem',
          }}
        >

          <Network
            size={26}
          />


          <h1
            style={{
              margin: 0,
              fontSize: '1.55rem',
            }}
          >

            Knowledge Graph

          </h1>

        </div>


        <p
          style={{
            margin:
              '0.4rem 0 0',
            color:
              'var(--text-muted)',
          }}
        >

          {
            focusMode
              ?
              'Section Focus Mode — select a concept to reveal its relationships.'
              :
              'Explore the book structure. Click a section to explore its concepts.'
          }

        </p>

      </div>


      {/* =================================================
          TOOLBAR
      ================================================== */}

      <div
        className="card"
        style={{
          padding:
            '0.85rem 1rem',
          display: 'flex',
          alignItems: 'center',
          gap: '0.75rem',
          flexWrap: 'wrap',
        }}
      >

        {
          !focusMode
          &&
          (
            <form
              onSubmit={
                handleBookSubmit
              }
              style={{
                display: 'flex',
                gap: '0.5rem',
              }}
            >

              <input
                value={
                  bookIdInput
                }
                onChange={
                  event =>
                    setBookIdInput(
                      event.target.value
                    )
                }
                placeholder="Book ID"
                style={{
                  width: '120px',
                  padding:
                    '0.55rem 0.7rem',
                  border:
                    '1px solid var(--border-color)',
                  borderRadius:
                    '7px',
                }}
              />


              <button
                type="submit"
                className="btn btn-primary btn-sm"
              >

                <Search
                  size={15}
                />

                Load Graph

              </button>

            </form>
          )
        }


        {
          focusMode
          &&
          (
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={
                returnToBookGraph
              }
            >

              <ArrowLeft
                size={16}
              />

              Back to Book Graph

            </button>
          )
        }


        <div
          style={{
            marginLeft: 'auto',
            display: 'flex',
            gap: '0.4rem',
          }}
        >

          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={
              () =>
                setZoom(
                  previous =>
                    Math.min(
                      1.8,
                      previous + 0.1
                    )
                )
            }
          >

            <ZoomIn
              size={16}
            />

          </button>


          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={
              () =>
                setZoom(
                  previous =>
                    Math.max(
                      0.45,
                      previous - 0.1
                    )
                )
            }
          >

            <ZoomOut
              size={16}
            />

          </button>


          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={
              resetView
            }
          >

            <RotateCcw
              size={16}
            />

          </button>

        </div>

      </div>


      {/* =================================================
          ERROR
      ================================================== */}

      {
        error
        &&
        (
          <div
            className="card"
            style={{
              padding: '0.85rem',
              color: '#991b1b',
              background: '#fef2f2',
              border:
                '1px solid #fecaca',
            }}
          >

            {
              error
            }

          </div>
        )
      }


      {/* =================================================
          CONTENT
      ================================================== */}

      <div
        style={{
          display: 'grid',
          gridTemplateColumns:
            'minmax(0, 1fr) 290px',
          gap: '1rem',
          flex: 1,
        }}
      >

        {/* ===============================================
            GRAPH
        ================================================ */}

        <div
          className="card"
          style={{
            minHeight: '650px',
            position: 'relative',
            overflow: 'hidden',
          }}
        >

          {
            loading
            &&
            (
              <div
                style={{
                  position: 'absolute',
                  inset: 0,
                  zIndex: 20,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  background:
                    'rgba(255,255,255,0.84)',
                  fontWeight: 600,
                }}
              >

                Loading Knowledge Graph...

              </div>
            )
          }


          {
            conceptLoading
            &&
            (
              <div
                style={{
                  position: 'absolute',
                  top: '1rem',
                  left: '50%',
                  transform:
                    'translateX(-50%)',
                  zIndex: 30,
                  padding:
                    '0.55rem 0.9rem',
                  borderRadius: '8px',
                  background: '#ecfdf5',
                  color: '#065f46',
                  border:
                    '1px solid #a7f3d0',
                  fontSize: '0.82rem',
                  fontWeight: 600,
                }}
              >

                Loading section concepts...

              </div>
            )
          }


          {
            focusMode
            &&
            (
              <div
                style={{
                  position: 'absolute',
                  left: '1rem',
                  top: '1rem',
                  zIndex: 10,
                  maxWidth: '330px',
                  padding:
                    '0.6rem 0.8rem',
                  borderRadius: '8px',
                  background:
                    'rgba(255,255,255,0.92)',
                  border:
                    '1px solid #e2e8f0',
                  fontSize: '0.76rem',
                  color: '#64748b',
                }}
              >

                <strong
                  style={{
                    color: '#334155',
                  }}
                >
                  Focus Mode
                </strong>

                <br />

                Click a concept to reveal only
                its related concepts.

              </div>
            )
          }


          <svg
            width="100%"
            height="650"
            viewBox={
              `0 0 ${
                GRAPH_WIDTH
              } ${
                GRAPH_HEIGHT
              }`
            }
            onMouseDown={
              handleBackgroundMouseDown
            }
            onMouseMove={
              handleMouseMove
            }
            onMouseUp={
              handleMouseUp
            }
            onMouseLeave={
              handleMouseUp
            }
            onWheel={
              handleWheel
            }
            style={{
              userSelect: 'none',
              cursor:
                isPanning
                  ? 'grabbing'
                  : 'default',
              background: '#fbfdff',
            }}
          >

            <g
              transform={
                `translate(${
                  pan.x
                } ${
                  pan.y
                }) scale(${
                  zoom
                })`
              }
            >

              {
                displayedEdges.map(
                  renderEdge
                )
              }


              {
                displayedNodes.map(
                  renderNode
                )
              }

            </g>

          </svg>

        </div>


        {/* ===============================================
            NODE DETAILS
        ================================================ */}

        <aside
          className="card"
          style={{
            padding: '1rem',
            alignSelf: 'start',
          }}
        >

          <h3
            style={{
              margin:
                '0 0 1rem',
            }}
          >

            Node Details

          </h3>


          {
            !selectedNode
            &&
            (
              <div
                style={{
                  color:
                    'var(--text-muted)',
                  fontSize: '0.85rem',
                  lineHeight: 1.6,
                }}
              >

                Select a node to view details.

                <br />
                <br />

                Click a section to enter
                concept focus mode.

              </div>
            )
          }


          {
            selectedNode
            &&
            (
              <>

                <div
                  style={{
                    marginBottom: '1rem',
                  }}
                >

                  <div
                    style={{
                      fontSize: '0.72rem',
                      color:
                        'var(--text-muted)',
                    }}
                  >

                    TYPE

                  </div>


                  <strong>

                    {
                      selectedNode.type
                    }

                  </strong>

                </div>


                <div
                  style={{
                    marginBottom: '1rem',
                  }}
                >

                  <div
                    style={{
                      fontSize: '0.72rem',
                      color:
                        'var(--text-muted)',
                    }}
                  >

                    LABEL

                  </div>


                  <div
                    style={{
                      marginTop: '0.25rem',
                      fontWeight: 600,
                      lineHeight: 1.45,
                    }}
                  >

                    {
                      selectedNode.label
                    }

                  </div>

                </div>


                {
                  selectedNode
                    .documentId
                  &&
                  (
                    <div
                      style={{
                        marginBottom: '1rem',
                      }}
                    >

                      <div
                        style={{
                          fontSize:
                            '0.72rem',
                          color:
                            'var(--text-muted)',
                        }}
                      >

                        DOCUMENT

                      </div>


                      <code>

                        {
                          selectedNode
                            .documentId
                        }

                      </code>

                    </div>
                  )
                }


                {
                  selectedNode.type
                  ===
                  'CONCEPT'
                  &&
                  (
                    <div
                      style={{
                        marginBottom: '1rem',
                        padding:
                          '0.7rem',
                        borderRadius:
                          '8px',
                        background:
                          '#ecfdf5',
                        border:
                          '1px solid #a7f3d0',
                      }}
                    >

                      <div
                        style={{
                          fontSize:
                            '0.72rem',
                          color:
                            '#047857',
                        }}
                      >

                        RELATED CONCEPTS

                      </div>


                      <strong
                        style={{
                          fontSize:
                            '1.15rem',
                        }}
                      >

                        {
                          relatedCount
                        }

                      </strong>

                    </div>
                  )
                }


                <button
                  type="button"
                  className="btn btn-primary"
                  style={{
                    width: '100%',
                    marginBottom:
                      '0.65rem',
                  }}
                  onClick={
                    handleOpenSelected
                  }
                >

                  {
                    selectedNode.type
                    ===
                    'CONCEPT'
                    ||
                    selectedNode.type
                    ===
                    'AUTHOR'
                    ||
                    selectedNode.type
                    ===
                    'CATEGORY'
                      ?
                      <Search size={16} />
                      :
                      <BookOpen size={16} />
                  }


                  {
                    selectedNode.type
                    ===
                    'CONCEPT'
                    ||
                    selectedNode.type
                    ===
                    'AUTHOR'
                    ||
                    selectedNode.type
                    ===
                    'CATEGORY'
                      ?
                      'Search This'
                      :
                      'Open Reader'
                  }

                </button>


                {
                  focusMode
                  &&
                  (
                    <button
                      type="button"
                      className="btn btn-secondary"
                      style={{
                        width: '100%',
                      }}
                      onClick={
                        returnToBookGraph
                      }
                    >

                      <ArrowLeft
                        size={16}
                      />

                      Back to Book Graph

                    </button>
                  )
                }

              </>
            )
          }


          {
            focusMode
            &&
            (
              <div
                style={{
                  marginTop: '1rem',
                  paddingTop: '1rem',
                  borderTop:
                    '1px solid var(--border-color)',
                }}
              >

                <div
                  style={{
                    fontSize: '0.72rem',
                    color:
                      'var(--text-muted)',
                    marginBottom:
                      '0.35rem',
                  }}
                >

                  SECTION IN FOCUS

                </div>


                <div
                  style={{
                    fontWeight: 600,
                    fontSize: '0.84rem',
                    lineHeight: 1.4,
                  }}
                >

                  {
                    expandedSectionLabel
                  }

                </div>


                <div
                  style={{
                    marginTop: '0.5rem',
                    color:
                      'var(--text-muted)',
                    fontSize: '0.78rem',
                  }}
                >

                  {
                    conceptGraph
                      ?.nodes
                      ?.filter(
                        node =>
                          node.type
                          ===
                          'CONCEPT'
                      )
                      .length
                    ||
                    0
                  }

                  {' '}
                  concepts discovered

                </div>

              </div>
            )
          }

        </aside>

      </div>

    </div>
  );
}


export default KnowledgeGraphPage;