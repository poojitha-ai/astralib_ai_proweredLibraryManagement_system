/**
 * AstraLib Centralized API Service
 *
 * Frontend calls /api through the Vite proxy.
 * Current Spring Boot backend: http://localhost:8082
 */

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || '/api';

/**
 * Standard HTTP request wrapper with JWT token handling.
 *
 * Use this for normal JSON/text API responses.
 * Binary files such as EPUBs are handled separately.
 */
async function request(endpoint, options = {}) {
  const url = `${API_BASE_URL}${endpoint}`;

  const headers = {
    ...options.headers,
  };

  // Don't set Content-Type for FormData.
  // The browser will automatically set the multipart boundary.
  if (
    !(options.body instanceof FormData) &&
    !headers['Content-Type']
  ) {
    headers['Content-Type'] =
      'application/json';
  }

  const token =
    localStorage.getItem(
      'astralib_token'
    );

  if (token) {
    headers['Authorization'] =
      `Bearer ${token}`;
  }

  const config = {
    ...options,
    headers,
  };

  try {
    const response =
      await fetch(
        url,
        config
      );

    // Handle expired/invalid authentication.
    if (response.status === 401) {
      if (
        endpoint !== '/auth/login' &&
        endpoint !== '/auth/register'
      ) {
        localStorage.removeItem(
          'astralib_token'
        );

        localStorage.removeItem(
          'astralib_user'
        );
      }
    }

    const contentType =
      response.headers.get(
        'content-type'
      );

    let data;

    if (
      contentType &&
      contentType.includes(
        'application/json'
      )
    ) {
      data =
        await response.json();
    } else {
      const text =
        await response.text();

      try {
        data =
          JSON.parse(text);
      } catch {
        data =
          text
            ? { message: text }
            : {};
      }
    }

    if (!response.ok) {
      const errorMessage =
        data?.error ||
        data?.message ||
        `Request failed with status ${response.status}`;

      const error =
        new Error(
          errorMessage
        );

      error.status =
        response.status;

      error.data =
        data;

      throw error;
    }

    return data;

  } catch (err) {

    if (
      err.name === 'TypeError' &&
      err.message.includes(
        'fetch'
      )
    ) {
      const networkError =
        new Error(
          'Unable to connect to AstraLib backend server. Please ensure the Spring Boot backend is running on port 8082.'
        );

      networkError.status =
        503;

      throw networkError;
    }

    throw err;
  }
}

/**
 * Special binary file request.
 *
 * EPUB/PDF files must NOT go through the normal request()
 * function because they are binary data.
 */
async function requestBinary(
  endpoint
) {
  const url =
    `${API_BASE_URL}${endpoint}`;

  const headers = {};

  const token =
    localStorage.getItem(
      'astralib_token'
    );

  if (token) {
    headers['Authorization'] =
      `Bearer ${token}`;
  }

  try {
    const response =
      await fetch(
        url,
        {
          method: 'GET',
          headers,
        }
      );

    if (
      response.status === 401
    ) {
      localStorage.removeItem(
        'astralib_token'
      );

      localStorage.removeItem(
        'astralib_user'
      );
    }

    if (!response.ok) {
      let errorMessage =
        `Unable to load digital resource. Status ${response.status}`;

      try {
        const contentType =
          response.headers.get(
            'content-type'
          );

        if (
          contentType &&
          contentType.includes(
            'application/json'
          )
        ) {
          const data =
            await response.json();

          errorMessage =
            data?.error ||
            data?.message ||
            errorMessage;
        }

      } catch {
        // Ignore response parsing errors.
      }

      const error =
        new Error(
          errorMessage
        );

      error.status =
        response.status;

      throw error;
    }

    return await response.arrayBuffer();

  } catch (err) {

    if (
      err.name === 'TypeError' &&
      err.message.includes(
        'fetch'
      )
    ) {
      const networkError =
        new Error(
          'Unable to connect to AstraLib backend server. Please ensure the Spring Boot backend is running on port 8082.'
        );

      networkError.status =
        503;

      throw networkError;
    }

    throw err;
  }
}

export const api = {

  // =========================================================
  // Authentication APIs
  // =========================================================

  auth: {

    login: (
      email,
      password
    ) =>
      request(
        '/auth/login',
        {
          method: 'POST',

          body:
            JSON.stringify({
              email,
              password,
            }),
        }
      ),

    register: (
      name,
      email,
      password
    ) =>
      request(
        '/auth/register',
        {
          method: 'POST',

          body:
            JSON.stringify({
              name,
              email,
              password,
            }),
        }
      ),

    getCurrentUser: () =>
      request(
        '/users/me'
      ),
  },

  // =========================================================
  // Catalogue & Books APIs
  // =========================================================

  books: {

    getAll: (
      filters = {}
    ) => {

      const params =
        new URLSearchParams();

      if (filters.title) {
        params.append(
          'title',
          filters.title
        );
      }

      if (filters.author) {
        params.append(
          'author',
          filters.author
        );
      }

      if (filters.category) {
        params.append(
          'category',
          filters.category
        );
      }

      if (filters.year) {
        params.append(
          'year',
          filters.year
        );
      }

      if (filters.keyword) {
        params.append(
          'keyword',
          filters.keyword
        );
      }

      const query =
        params.toString();

      return request(
        `/books${
          query
            ? `?${query}`
            : ''
        }`
      );
    },

    getById: (id) =>
      request(
        `/books/${id}`
      ),

    create: (
      bookData
    ) =>
      request(
        '/books',
        {
          method: 'POST',

          body:
            JSON.stringify(
              bookData
            ),
        }
      ),

    update: (
      id,
      bookData
    ) =>
      request(
        `/books/${id}`,
        {
          method: 'PUT',

          body:
            JSON.stringify(
              bookData
            ),
        }
      ),

    delete: (id) =>
      request(
        `/books/${id}`,
        {
          method: 'DELETE',
        }
      ),
  },

  // =========================================================
  // Authors APIs
  // =========================================================

  authors: {

    getAll: () =>
      request(
        '/authors'
      ),

    getById: (id) =>
      request(
        `/authors/${id}`
      ),

    create: (
      authorData
    ) =>
      request(
        '/authors',
        {
          method: 'POST',

          body:
            JSON.stringify(
              authorData
            ),
        }
      ),

    update: (
      id,
      authorData
    ) =>
      request(
        `/authors/${id}`,
        {
          method: 'PUT',

          body:
            JSON.stringify(
              authorData
            ),
        }
      ),

    delete: (id) =>
      request(
        `/authors/${id}`,
        {
          method: 'DELETE',
        }
      ),
  },

  // =========================================================
  // Categories APIs
  // =========================================================

  categories: {

    getAll: () =>
      request(
        '/categories'
      ),

    create: (
      categoryData
    ) =>
      request(
        '/categories',
        {
          method: 'POST',

          body:
            JSON.stringify(
              categoryData
            ),
        }
      ),

    update: (
      id,
      categoryData
    ) =>
      request(
        `/categories/${id}`,
        {
          method: 'PUT',

          body:
            JSON.stringify(
              categoryData
            ),
        }
      ),

    delete: (id) =>
      request(
        `/categories/${id}`,
        {
          method: 'DELETE',
        }
      ),
  },

  // =========================================================
  // Digital Resources APIs
  // =========================================================

  resources: {

    /**
     * Get information about one DigitalResource.
     */
    getById: (id) =>
      request(
        `/resources/${id}`
      ),

    /**
     * Get all digital resources belonging to a book.
     */
    getByBookId: (
      bookId
    ) =>
      request(
        `/books/${bookId}/resources`
      ),

    /**
     * Return URL of digital file.
     */
    getFileUrl: (id) =>
      `${API_BASE_URL}/resources/file/${id}`,

    /**
     * Download EPUB/PDF as ArrayBuffer.
     */
    getFileArrayBuffer:
      (id) =>
        requestBinary(
          `/resources/file/${id}`
        ),

    /**
     * Upload a digital resource.
     */
    upload: (
      formData
    ) =>
      request(
        '/resources',
        {
          method: 'POST',
          body: formData,
        }
      ),
  },

  // =========================================================
  // Favorites APIs
  // =========================================================

  favorites: {

    getAll: () =>
      request(
        '/favorites'
      ),

    add: (
      bookId
    ) =>
      request(
        `/favorites/${bookId}`,
        {
          method: 'POST',
        }
      ),

    remove: (
      bookId
    ) =>
      request(
        `/favorites/${bookId}`,
        {
          method: 'DELETE',
        }
      ),
  },

  // =========================================================
  // Reading History APIs
  // =========================================================

  readingHistory: {

    // Get Continue Reading / reading-history records
    getAll: () =>
      request(
        '/reading-history'
      ),

    // Get saved progress for one book
    getProgress: (
      bookId
    ) =>
      request(
        `/reading-history/progress/${bookId}`
      ),

    // Save/update EPUB position
    saveProgress: ({
      bookId,
      resourceId,
      progressPercent,
      lastLocationCfi,
    }) =>
      request(
        '/reading-history/progress',
        {
          method: 'POST',

          body:
            JSON.stringify({
              bookId,
              resourceId,
              progressPercent,
              lastLocationCfi,
            }),
        }
      ),
  },

  // =========================================================
  // Recommendations APIs
  // =========================================================

  recommendations: {

    getMy: (
      limit = 10
    ) =>
      request(
        `/recommendations?limit=${limit}`
      ),

    getUserRecommendations:
      (
        userId,
        limit = 10
      ) =>
        request(
          `/users/${userId}/recommendations?limit=${limit}`
        ),

    getUserBookScore:
      (
        userId,
        bookId
      ) =>
        request(
          `/users/${userId}/score/${bookId}`
        ),
  },

  // =========================================================
  // Search APIs
  // Keyword / Semantic / Hybrid Search
  // =========================================================

  search: {

    semantic: (
      query,
      topK = 6,
      documentType = ''
    ) => {

      const params =
        new URLSearchParams({
          query,
          topK,
        });

      if (documentType) {
        params.append(
          'documentType',
          documentType
        );
      }

      return request(
        `/search/semantic?${params.toString()}`
      );
    },

    hybrid: (
      query,
      topK = 6,
      documentType = ''
    ) => {

      const params =
        new URLSearchParams({
          query,
          topK,
        });

      if (documentType) {
        params.append(
          'documentType',
          documentType
        );
      }

      return request(
        `/search/hybrid?${params.toString()}`
      );
    },
  },
   // =========================================================
// Knowledge Graph APIs
// =========================================================

knowledgeGraph: {

  /**
   * Phase 1
   *
   * Get book metadata graph:
   *
   * BOOK
   *   ↓
   * AUTHOR
   * CATEGORY
   * SECTION
   */
  getBookGraph: (
    bookId
  ) =>
    request(
      `/knowledge-graph/books/${encodeURIComponent(
        bookId
      )}`
    ),


  /**
   * Phase 2
   *
   * Get concepts belonging to one section.
   *
   * SECTION
   *   ↓
   * CONCEPT
   *   ↓
   * RELATED_TO
   */
  getSectionConceptGraph: (
    bookId,
    section,
    limit = 12
  ) => {

    const params =
      new URLSearchParams();

    params.set(
      'section',
      section
    );

    params.set(
      'limit',
      String(limit)
    );


    return request(
      `/knowledge-graph/books/${encodeURIComponent(
        bookId
      )}/concepts?${params.toString()}`
    );
  },
},
  // =========================================================
  // Research Assistant / RAG APIs
  // =========================================================

  rag: {

    /**
     * Ask AstraLib a general RAG question.
     */
    ask: (
      question,
      topK = 3
    ) =>
      request(
        '/rag/ask',
        {
          method: 'POST',

          body:
            JSON.stringify({
              question,
              topK,
            }),
        }
      ),

    /**
     * Ask a question about a particular document.
     */
    askDocument: (
      documentId,
      question,
      topK = 3
    ) =>
      request(
        `/rag/documents/${documentId}/ask`,
        {
          method: 'POST',

          body:
            JSON.stringify({
              question,
              topK,
            }),
        }
      ),

    getCitation: (
      chunkId
    ) =>
      request(
        `/rag/citations/${encodeURIComponent(
          chunkId
        )}`
      ),
  },

  // =========================================================
  // Saved Research APIs
  // =========================================================

  savedResearch: {

    save: ({
      title,
      question,
      answer,
      citations = [],
    }) =>
      request(
        '/saved-research',
        {
          method: 'POST',

          body:
            JSON.stringify({
              title,
              question,
              answer,
              citations,
            }),
        }
      ),

    getAll: () =>
      request(
        '/saved-research'
      ),

    getById: (
      id
    ) =>
      request(
        `/saved-research/${id}`
      ),

    delete: (
      id
    ) =>
      request(
        `/saved-research/${id}`,
        {
          method: 'DELETE',
        }
      ),
  },

  // =========================================================
  // Study Hall / Study Groups APIs
  // =========================================================

  studyGroups: {

    getAll: () =>
      request(
        '/study-groups'
      ),

    getById: (
      id
    ) =>
      request(
        `/study-groups/${id}`
      ),

    create: (
      groupData
    ) =>
      request(
        '/study-groups',
        {
          method: 'POST',

          body:
            JSON.stringify(
              groupData
            ),
        }
      ),

    join: (
      id
    ) =>
      request(
        `/study-groups/${id}/join`,
        {
          method: 'POST',
        }
      ),

    leave: (
      id
    ) =>
      request(
        `/study-groups/${id}/leave`,
        {
          method: 'DELETE',
        }
      ),

    getMembers: (
      id
    ) =>
      request(
        `/study-groups/${id}/members`
      ),

    getMyGroups: () =>
      request(
        '/study-groups/my-groups'
      ),

    // Owner adds a registered AstraLib user to a group
    addMember: (
      id,
      email
    ) =>
      request(
        `/study-groups/${id}/members`,
        {
          method: 'POST',

          body:
            JSON.stringify({
              email,
            }),
        }
      ),

    // Owner removes a group member
    removeMember: (
      id,
      memberUserId
    ) =>
      request(
        `/study-groups/${id}/members/${memberUserId}`,
        {
          method: 'DELETE',
        }
      ),
  },

  // =========================================================
  // Study Hall / Study Session APIs
  // =========================================================

  studySessions: {

    /**
     * Get all sessions belonging to one study group.
     */
    getGroupSessions: (
      groupId
    ) =>
      request(
        `/study-sessions/group/${groupId}`
      ),

    /**
     * Get current room state:
     *
     * {
     *   live: true/false,
     *   liveSession: {...},
     *   nextSession: {...}
     * }
     */
    getCurrent: (
      groupId
    ) =>
      request(
        `/study-sessions/group/${groupId}/current`
      ),

    /**
     * Schedule a future session.
     *
     * sessionData:
     * {
     *   title,
     *   agenda,
     *   scheduledAt
     * }
     */
    schedule: (
      groupId,
      sessionData
    ) =>
      request(
        `/study-sessions/group/${groupId}/schedule`,
        {
          method: 'POST',

          body:
            JSON.stringify(
              sessionData
            ),
        }
      ),

    /**
     * Create and immediately start a new live session.
     *
     * sessionData:
     * {
     *   title,
     *   agenda
     * }
     */
    startNow: (
      groupId,
      sessionData = {}
    ) =>
      request(
        `/study-sessions/group/${groupId}/start-now`,
        {
          method: 'POST',

          body:
            JSON.stringify(
              sessionData
            ),
        }
      ),

    /**
     * Start an already scheduled session.
     */
    start: (
      sessionId
    ) =>
      request(
        `/study-sessions/${sessionId}/start`,
        {
          method: 'POST',
        }
      ),

    /**
     * End the currently live session.
     */
    end: (
      sessionId
    ) =>
      request(
        `/study-sessions/${sessionId}/end`,
        {
          method: 'POST',
        }
      ),
  },

  // =========================================================
  // Notifications APIs
  // =========================================================

  notifications: {

    /**
     * Get all notifications of current logged-in user.
     */
    getAll: () =>
      request(
        '/notifications'
      ),

    /**
     * Get notification badge count.
     *
     * Response:
     * {
     *   unreadCount: 2
     * }
     */
    getUnreadCount: () =>
      request(
        '/notifications/unread-count'
      ),

    /**
     * Mark one notification as read.
     */
    markAsRead: (
      notificationId
    ) =>
      request(
        `/notifications/${notificationId}/read`,
        {
          method: 'PUT',
        }
      ),

    /**
     * Mark every notification as read.
     */
    markAllAsRead: () =>
      request(
        '/notifications/read-all',
        {
          method: 'PUT',
        }
      ),
  },

  // =========================================================
  // Admin APIs
  // =========================================================

  admin: {

    getDatasetStats: () =>
      request(
        '/admin/dataset-stats'
      ),

    loadDatasets: () =>
      request(
        '/admin/load-datasets',
        {
          method: 'POST',
        }
      ),

    getVectorStats: () =>
      request(
        '/admin/vector-stats'
      ),

    // Ingest all vectors
    triggerVectorIngest: () =>
      request(
        '/admin/vector-ingest',
        {
          method: 'POST',
        }
      ),

    // Re-ingest all vectors
    reingestVectors: () =>
      request(
        '/admin/vector-reingest',
        {
          method: 'POST',
        }
      ),

    // Ingest vectors for one book
    ingestBookVectors: (
      bookId
    ) =>
      request(
        `/admin/vector-ingest/book/${bookId}`,
        {
          method: 'POST',
        }
      ),

    updateUserRole: (
      userId,
      role
    ) =>
      request(
        `/admin/update-role/${userId}?role=${role}`,
        {
          method: 'POST',
        }
      ),
  },
};

export default api;