import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertCircle,
  BookOpen,
  FileText,
  Menu,
  MessageSquare,
  Plus,
  Send,
  Upload,
  Sparkles,
  Trash2,
  X,
} from 'lucide-react';

import { api } from '../../services/api';

/**
 * Conversational academic companion.
 *
 * This is the single chat interface in AstraLib. It keeps the whole
 * conversation on the server, so reopening a chat restores its turns,
 * its citations and the books it offered.
 */
export function ResearchAssistantPage() {
  const [conversations, setConversations] = useState([]);
  const [activeId, setActiveId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [loadingChat, setLoadingChat] = useState(false);
  const [error, setError] = useState('');
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // Private student documents are kept separate from saved conversations.
  const [studentDocuments, setStudentDocuments] = useState([]);
  const [selectedDocumentId, setSelectedDocumentId] = useState(null);
  const [loadingDocuments, setLoadingDocuments] = useState(false);
  const [uploadingDocument, setUploadingDocument] = useState(false);
  const [documentError, setDocumentError] = useState('');
  const fileInputRef = useRef(null);

  const scrollRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    loadConversations();
    loadStudentDocuments();
  }, []);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, sending]);

  const loadConversations = async () => {
    try {
      const data = await api.conversations.list();
      setConversations(Array.isArray(data) ? data : []);
    } catch (err) {
      // A missing history should not block a fresh chat.
      console.error('Conversation list failed:', err);
    }
  };

  const openConversation = async (id) => {
    if (id === activeId) {
      setSidebarOpen(false);
      return;
    }

    setLoadingChat(true);
    setError('');
    setDocumentError('');
    setSelectedDocumentId(null);
    setSidebarOpen(false);

    try {
      const data = await api.conversations.get(id);

      setActiveId(data.id);
      setMessages(Array.isArray(data.messages) ? data.messages : []);
    } catch (err) {
      console.error('Conversation load failed:', err);
      setError(
        err.message || 'That conversation could not be opened.'
      );
    } finally {
      setLoadingChat(false);
    }
  };

  const startNewChat = () => {
    setActiveId(null);
    setSelectedDocumentId(null);
    setMessages([]);
    setInput('');
    setError('');
    setSidebarOpen(false);
    inputRef.current?.focus();
  };

  const deleteConversation = async (event, id) => {
    event.stopPropagation();

    if (
      !window.confirm(
        'Delete this conversation? This removes its messages.'
      )
    ) {
      return;
    }

    try {
      await api.conversations.delete(id);

      setConversations((previous) =>
        previous.filter((item) => item.id !== id)
      );

      if (id === activeId) {
        startNewChat();
      }
    } catch (err) {
      setError(err.message || 'That conversation could not be deleted.');
    }
  };


  const loadStudentDocuments = async () => {
    setLoadingDocuments(true);
    setDocumentError('');

    try {
      const data = await api.studentDocuments.list();
      const documents = Array.isArray(data) ? data : [];
      setStudentDocuments(documents);
      setSelectedDocumentId((current) =>
        current != null && documents.some(
          (document) => String(document.id) === String(current)
        )
          ? current
          : null
      );
    } catch (err) {
      console.error('Student document list failed:', err);
      setDocumentError(err.message || 'Could not load your documents.');
    } finally {
      setLoadingDocuments(false);
    }
  };

  const uploadStudentDocument = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = '';

    if (!file) return;

    const allowedExtensions = /\.(pdf|docx|txt)$/i;
    if (!allowedExtensions.test(file.name)) {
      setDocumentError('Choose a PDF, DOCX, or TXT file.');
      return;
    }

    setUploadingDocument(true);
    setDocumentError('');

    try {
      const uploaded = await api.studentDocuments.upload(file);
      await loadStudentDocuments();

      if (uploaded?.id != null) {
        setSelectedDocumentId(uploaded.id);
        setActiveId(null);
        setMessages([]);
        setInput('');
      }
    } catch (err) {
      console.error('Student document upload failed:', err);
      setDocumentError(err.message || 'Document upload failed.');
    } finally {
      setUploadingDocument(false);
    }
  };

  const selectStudentDocument = (document) => {
    setSelectedDocumentId(document.id);
    setActiveId(null);
    setMessages([]);
    setInput('');
    setError('');
    setDocumentError('');
    setSidebarOpen(false);
  };

  const deleteStudentDocument = async (event, id) => {
    event.stopPropagation();

    if (!window.confirm('Delete this uploaded document? This cannot be undone.')) {
      return;
    }

    try {
      await api.studentDocuments.delete(id);
      setStudentDocuments((previous) =>
        previous.filter((document) => document.id !== id)
      );

      if (String(selectedDocumentId) === String(id)) {
        setSelectedDocumentId(null);
        setMessages([]);
      }
    } catch (err) {
      setDocumentError(err.message || 'Could not delete this document.');
    }
  };

  const selectedDocument = studentDocuments.find(
    (document) => String(document.id) === String(selectedDocumentId)
  );

  const sendMessage = async (event) => {
    event?.preventDefault();

    const text = input.trim();

    if (!text || sending) {
      return;
    }

    if (
      selectedDocumentId != null &&
      selectedDocument &&
      String(selectedDocument.status || '').toUpperCase() !== 'READY'
    ) {
      setError(
        String(selectedDocument.status || '').toUpperCase() === 'FAILED'
          ? (selectedDocument.errorMessage || 'This document could not be processed. Please upload it again.')
          : 'This document is still being processed. Try again when its status is READY.'
      );
      return;
    }

    setInput('');
    setError('');
    setSending(true);

    setMessages((previous) => [
      ...previous,
      {
        id: `pending-${Date.now()}`,
        role: 'USER',
        content: text,
        citations: [],
        recommendations: [],
        createdAt: '',
      },
    ]);
        try {
      if (selectedDocumentId != null) {
        const result = await api.studentDocuments.ask(
          selectedDocumentId,
          text,
          5
        );

        const answerText =
          result?.answer ??
          result?.response ??
          result?.content ??
          result?.message ??
          'I could not find an answer in this document.';

        setMessages((previous) => [
          ...previous,
          {
            id: `document-answer-${Date.now()}`,
            role: 'ASSISTANT',
            content: answerText,
            citations: result?.citations || [],
            recommendations: [],
            grounded: result?.grounded ?? true,
            documentGrounded: true,
            createdAt: new Date().toISOString(),
          },
        ]);
      } else {
        const result = await api.conversations.send(activeId, text);

        setActiveId(result.conversationId);

        setMessages((previous) => [
          ...previous,
          result.assistantMessage,
        ]);

        loadConversations();
      }
    } catch (err) {
      console.error('Chat failure:', err);

      setError(
        err.message ||
          'The assistant is temporarily unavailable. Please try again.'
      );
    } finally {
      setSending(false);
      inputRef.current?.focus();
    }
  };

  const lastAssistant = [...messages]
    .reverse()
    .find((message) => message.role === 'ASSISTANT');

  return (
    <div className="chat-page">
      <div className="chat-header">
        <div className="chat-header-text">
          <h1>Research Assistant</h1>
          <p>
            {selectedDocument
              ? `Asking questions about: ${selectedDocument.originalFilename || selectedDocument.filename || 'your document'}`
              : 'Your academic companion. Ask about a concept, work through a problem, or find the right book in AstraLib.'}
          </p>
        </div>

        <div className="chat-header-actions">
          <button
            type="button"
            className="btn btn-secondary btn-sm chat-sidebar-toggle"
            onClick={() => setSidebarOpen((open) => !open)}
          >
            {sidebarOpen ? <X size={16} /> : <Menu size={16} />}
            <span>Chats</span>
          </button>

          <button
            type="button"
            className="btn btn-primary btn-sm"
            onClick={startNewChat}
          >
            <Plus size={16} />
            <span>New Chat</span>
          </button>
        </div>
      </div>

      <div className="chat-shell">
        <aside
          className={`chat-sidebar ${sidebarOpen ? 'is-open' : ''}`}
        >
          <div className="chat-sidebar-title">My Documents</div>

          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf,.docx,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
            onChange={uploadStudentDocument}
            style={{ display: 'none' }}
          />

          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploadingDocument}
            style={{ width: '100%', marginBottom: '10px' }}
          >
            <Upload size={15} />
            <span>{uploadingDocument ? 'Uploading…' : 'Upload document'}</span>
          </button>

          {documentError && (
            <div className="banner banner-error" style={{ marginBottom: '10px' }}>
              {documentError}
            </div>
          )}

          {loadingDocuments ? (
            <p className="chat-sidebar-empty">Loading documents…</p>
          ) : studentDocuments.length === 0 ? (
            <p className="chat-sidebar-empty">
              Upload a PDF, DOCX, or TXT file to ask questions about it.
            </p>
          ) : (
            <ul className="chat-conversation-list">
              {studentDocuments.map((document) => (
                <li key={document.id}>
                  <button
                    type="button"
                    className={`chat-conversation ${
                      String(document.id) === String(selectedDocumentId) ? 'is-active' : ''
                    }`}
                    onClick={() => selectStudentDocument(document)}
                  >
                    <FileText size={15} className="chat-conversation-icon" />
                    <span className="chat-conversation-body">
                      <span className="chat-conversation-title">
                        {document.originalFilename || document.filename || `Document ${document.id}`}
                      </span>
                      <span className="chat-conversation-meta">
                        {document.status || 'Uploaded'}
                        {document.chunkCount != null ? ` · ${document.chunkCount} chunks` : ''}
                      </span>
                    </span>
                    <span
                      role="button"
                      tabIndex={0}
                      aria-label="Delete uploaded document"
                      className="chat-conversation-delete"
                      onClick={(event) => deleteStudentDocument(event, document.id)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          deleteStudentDocument(event, document.id);
                        }
                      }}
                    >
                      <Trash2 size={14} />
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}

          <div className="chat-sidebar-title" style={{ marginTop: '22px' }}>
            Conversations
          </div>

          {conversations.length === 0 ? (
            <p className="chat-sidebar-empty">
              No saved conversations yet.
            </p>
          ) : (
            <ul className="chat-conversation-list">
              {conversations.map((conversation) => (
                <li key={conversation.id}>
                  <button
                    type="button"
                    className={`chat-conversation ${
                      conversation.id === activeId ? 'is-active' : ''
                    }`}
                    onClick={() => openConversation(conversation.id)}
                  >
                    <MessageSquare size={15} className="chat-conversation-icon" />

                    <span className="chat-conversation-body">
                      <span className="chat-conversation-title">
                        {conversation.title || 'Untitled chat'}
                      </span>

                      <span className="chat-conversation-meta">
                        {(conversation.messageCount || 0)} messages
                        {conversation.updatedAt
                          ? ` · ${conversation.updatedAt.slice(0, 10)}`
                          : ''}
                      </span>
                    </span>

                    <span
                      role="button"
                      tabIndex={0}
                      aria-label="Delete conversation"
                      className="chat-conversation-delete"
                      onClick={(event) =>
                        deleteConversation(event, conversation.id)
                      }
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          deleteConversation(event, conversation.id);
                        }
                      }}
                    >
                      <Trash2 size={14} />
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </aside>

        <section className="chat-panel">
          <div className="chat-scroll" ref={scrollRef}>
            {loadingChat ? (
              <div className="chat-loading">Opening conversation…</div>
            ) : messages.length === 0 ? (
              <div className="chat-empty">
                <Sparkles
                  size={26}
                  strokeWidth={1.6}
                  className="chat-empty-icon"
                />

                <h2>Hey! Good to have you here.</h2>

                <p>
                  Tell me what you are studying, where you are stuck, or
                  what you would like to understand. I can explain a
                  concept, suggest a book from the catalogue, or look up
                  what our collection actually says.
                </p>

                <div className="chat-starters">
                  {[
                    'Hello',
                    "I'm struggling with programming",
                    'Which Java book should I start with?',
                    'Explain recursion in simple language',
                  ].map((starter) => (
                    <button
                      key={starter}
                      type="button"
                      className="chat-starter"
                      onClick={() => {
                        setInput(starter);
                        inputRef.current?.focus();
                      }}
                    >
                      {starter}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              messages.map((message, index) => (
                <ChatBubble
                  key={message.id || `message-${index}`}
                  message={message}
                />
              ))
            )}

            {sending && (
              <div className="chat-row chat-row-assistant">
                <div className="chat-avatar">A</div>

                <div className="chat-bubble chat-bubble-assistant chat-typing">
                  <span />
                  <span />
                  <span />
                </div>
              </div>
            )}
          </div>

          {error && (
            <div className="banner banner-error chat-error">
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
          )}

          <form className="chat-composer" onSubmit={sendMessage}>
            <textarea
              ref={inputRef}
              className="chat-input"
              rows={1}
              placeholder={
                selectedDocument
                  ? 'Ask a question about this document…'
                  : activeId
                    ? 'Continue the conversation…'
                    : 'Ask a question, or tell me what you are working on…'
              }
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  sendMessage(event);
                }
              }}
              disabled={sending}
            />

            <button
              type="submit"
              className="btn btn-primary chat-send"
              disabled={sending || !input.trim()}
              aria-label="Send message"
            >
              <Send size={16} />
            </button>
          </form>

          <div className="chat-footnote">
            {selectedDocument
              ? 'Document mode: answers are generated from the selected private upload.'
              : lastAssistant?.grounded
                ? 'Answers marked with sources come from indexed AstraLib books.'
                : 'Astra uses your library where it can, and says when it cannot.'}
          </div>
        </section>
      </div>
    </div>
  );
}
/**
 * Renders the assistant's light markdown: fenced code blocks, bold, italics
 * and inline code. Everything else keeps its line breaks as written.
 */
function FormattedText({ text }) {
  const source = String(text ?? '');
  const blocks = source.split('```');

  return blocks.map((block, index) => {
    if (index % 2 === 1) {
      const body = block.replace(/^[a-zA-Z0-9+#]+\n/, '');

      return (
        <pre key={index} className="chat-code">
          <code>{body.replace(/\n$/, '')}</code>
        </pre>
      );
    }

    if (!block) {
      return null;
    }

    return (
      <div key={index} className="chat-text">
        {block.split(/(\*\*[^*]+\*\*|\*[^*\n]+\*|`[^`\n]+`)/g).map((part, partIndex) => {
          if (part.startsWith('**') && part.endsWith('**')) {
            return <strong key={partIndex}>{part.slice(2, -2)}</strong>;
          }

          if (part.startsWith('*') && part.endsWith('*') && part.length > 2) {
            return <em key={partIndex}>{part.slice(1, -1)}</em>;
          }

          if (part.startsWith('`') && part.endsWith('`') && part.length > 2) {
            return <code key={partIndex}>{part.slice(1, -1)}</code>;
          }

          return part;
        })}
      </div>
    );
  });
}

function ChatBubble({ message }) {
  const isUser = message.role === 'USER';

  const citations = message.citations || [];
  const recommendations = message.recommendations || [];

  return (
    <div
      className={`chat-row ${
        isUser ? 'chat-row-user' : 'chat-row-assistant'
      }`}
    >
      {!isUser && <div className="chat-avatar">A</div>}

      <div className="chat-message-group">
        <div
          className={`chat-bubble ${
            isUser ? 'chat-bubble-user' : 'chat-bubble-assistant'
          }`}
        >
          <FormattedText text={message.content} />
        </div>

        {!isUser && message.intent === 'EXPLANATION' && (
          <div className="chat-tag">General knowledge</div>
        )}

        {recommendations.length > 0 && (
          <div className="chat-recommendations">
            {recommendations.map((book) => (
              <div key={book.bookId} className="chat-recommendation">
                <div className="chat-recommendation-head">
                  <BookOpen size={15} />
                  <Link to={`/books/${book.bookId}`}>
                    {book.title}
                  </Link>
                </div>

                <div className="chat-recommendation-meta">
                  {[book.author, book.language, book.level]
                    .filter(Boolean)
                    .join(' · ')}
                </div>

                <div className="chat-recommendation-foot">
                  <span
                    className={`badge ${
                      book.readable ? 'badge-success' : 'badge-warning'
                    }`}
                  >
                    {book.readable
                      ? `Available${book.format ? ` · ${book.format}` : ''}`
                      : 'Not available to read yet'}
                  </span>

                  <Link
                    to={`/books/${book.bookId}`}
                    className="chat-recommendation-link"
                  >
                    View details
                  </Link>
                </div>
              </div>
            ))}
          </div>
        )}

        {citations.length > 0 && (
          <div className="chat-citations">
            <div className="chat-citations-title">
              {message.documentGrounded
                ? 'From your uploaded document'
                : 'From the AstraLib collection'}
            </div>

            <ol>
              {citations.map((citation, index) => (
                <li key={citation.chunkId || index}>
                  {citation.bookId ? (
                    <Link to={`/books/${citation.bookId}`}>
                      {citation.title || 'AstraLib book'}
                    </Link>
                  ) : (
                    <span>{citation.title || 'AstraLib book'}</span>
                  )}

                  {citation.section ? ` — ${citation.section}` : ''}
                </li>
              ))}
            </ol>
          </div>
        )}
      </div>
    </div>
  );
}