import { useEffect, useRef, useState } from "react";
import { getErrorMessage } from "../services/apiClient";
import { sendChatMessage } from "../services/chatApi";
import "./ChatWidget.css";

const STARTER_MESSAGE = {
  role: "assistant",
  text: "Hi, I am your ARA assistant. Ask me about booking rooms, approvals, allocations, or dashboard usage.",
};

export default function ChatWidget() {
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const [messages, setMessages] = useState([STARTER_MESSAGE]);
  const messagesRef = useRef(null);

  useEffect(() => {
    if (messagesRef.current) {
      messagesRef.current.scrollTop = messagesRef.current.scrollHeight;
    }
  }, [messages, isOpen]);

  async function onSubmit(event) {
    event.preventDefault();
    const message = input.trim();
    if (!message || isLoading) {
      return;
    }

    setError("");
    setInput("");
    setMessages((prev) => [...prev, { role: "user", text: message }]);
    setIsLoading(true);

    try {
      const reply = await sendChatMessage(message);
      setMessages((prev) => [...prev, { role: "assistant", text: reply }]);
    } catch (err) {
      setError(getErrorMessage(err, "Unable to contact chat service right now."));
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className="chat-widget">
      {isOpen ? (
        <div className="chat-widget__panel">
          <div className="chat-widget__header">
            <h3>ARA Assistant</h3>
            <button type="button" className="chat-widget__close" onClick={() => setIsOpen(false)} aria-label="Close Chat">
              ✕
            </button>
          </div>

          <div className="chat-widget__messages" ref={messagesRef}>
            {messages.map((msg, index) => (
              <div key={`${msg.role}-${index}`} className={`chat-widget__message chat-widget__message--${msg.role}`}>
                {msg.text}
              </div>
            ))}
            {isLoading ? <div className="chat-widget__typing">Thinking...</div> : null}
          </div>

          {error ? <div className="message message--error chat-widget__error">{error}</div> : null}

          <form className="chat-widget__form" onSubmit={onSubmit}>
            <input
              className="input"
              placeholder="Ask about ARA..."
              value={input}
              onChange={(event) => setInput(event.target.value)}
              disabled={isLoading}
            />
            <button type="submit" className="btn btn--primary" disabled={isLoading || !input.trim()}>
              Send
            </button>
          </form>
        </div>
      ) : null}

      <button type="button" className="chat-widget__toggle" onClick={() => setIsOpen((prev) => !prev)}>
        {isOpen ? "✕" : "💬"}
      </button>
    </div>
  );
}