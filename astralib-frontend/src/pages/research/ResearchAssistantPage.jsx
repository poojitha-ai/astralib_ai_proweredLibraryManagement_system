import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertCircle,
  BookOpen,
  Menu,
  MessageSquare,
  Plus,
  Send,
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

  const scrollRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    loadConversations();
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

  const sendMessage = async (event) => {
    event?.preventDefault();

    const text = input.trim();

    if (!text || sending) {
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
      const result = await api.conversations.send(activeId, text);

      setActiveId(result.conversationId);

      setMessages((previous) => [
        ...previous,
        result.assistantMessage,
      ]);

      loadConversations();
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
            Your academic companion. Ask about a concept, work through a
            problem, or find the right book in AstraLib.
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
          <div className="chat-sidebar-title">Conversations</div>

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
                activeId
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
            {lastAssistant?.grounded
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
              From the AstraLib collection
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
