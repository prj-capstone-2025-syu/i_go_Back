import React, { useState, useRef, useEffect } from 'react';
import { sendChatMessage } from '@/api/chatApi';
import styles from './ChatInterface.module.css';

interface Message {
    id: number;
    text: string;
    isUser: boolean;
    timestamp: Date;
}

export default function ChatInterface() {
    const [messages, setMessages] = useState<Message[]>([]);
    const [inputMessage, setInputMessage] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const messagesEndRef = useRef<HTMLDivElement>(null);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!inputMessage.trim() || isLoading) return;

        const userMessage: Message = {
            id: Date.now(),
            text: inputMessage,
            isUser: true,
            timestamp: new Date()
        };

        setMessages(prev => [...prev, userMessage]);
        setInputMessage('');
        setIsLoading(true);

        try {
            const response = await sendChatMessage(inputMessage);
            
            const botMessage: Message = {
                id: Date.now() + 1,
                text: response.message,
                isUser: false,
                timestamp: new Date()
            };

            setMessages(prev => [...prev, botMessage]);
        } catch (error) {
            console.error('메시지 전송 실패:', error);
            const errorMessage: Message = {
                id: Date.now() + 1,
                text: '죄송합니다. 메시지 처리 중 오류가 발생했습니다.',
                isUser: false,
                timestamp: new Date()
            };
            setMessages(prev => [...prev, errorMessage]);
        } finally {
            setIsLoading(false);
        }
    };

    const handleSend = async (msg?: string) => {
        const message = typeof msg === "string" ? msg : inputMessage;
        if (!message.trim()) return;

        const userMessage: Message = {
            id: Date.now(),
            text: message,
            isUser: true,
            timestamp: new Date()
        };

        setMessages(prev => [...prev, userMessage]);
        setInputMessage('');
        setIsLoading(true);

        try {
            const response = await sendChatMessage(message);
            
            const botMessage: Message = {
                id: Date.now() + 1,
                text: response.message,
                isUser: false,
                timestamp: new Date()
            };

            setMessages(prev => [...prev, botMessage]);
        } catch (error) {
            console.error('메시지 전송 실패:', error);
            const errorMessage: Message = {
                id: Date.now() + 1,
                text: '죄송합니다. 메시지 처리 중 오류가 발생했습니다.',
                isUser: false,
                timestamp: new Date()
            };
            setMessages(prev => [...prev, errorMessage]);
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className={styles.chatContainer}>
            <div className={styles.messagesContainer}>
                {messages.map((message) => (
                    <div
                        key={message.id}
                        className={`${styles.message} ${message.isUser ? styles.userMessage : styles.botMessage}`}
                    >
                        <div className={styles.messageContent}>
                            {message.text}
                        </div>
                        <div className={styles.timestamp}>
                            {message.timestamp.toLocaleTimeString()}
                        </div>
                    </div>
                ))}
                <div ref={messagesEndRef} />
            </div>
            <form onSubmit={handleSubmit} className={styles.inputContainer}>
                <input
                    type="text"
                    value={inputMessage}
                    onChange={(e) => setInputMessage(e.target.value)}
                    placeholder="메시지를 입력하세요..."
                    disabled={isLoading}
                    className={styles.input}
                />
                <button
                    type="submit"
                    disabled={isLoading}
                    className={styles.sendButton}
                >
                    {isLoading ? '전송 중...' : '전송'}
                </button>
            </form>
        </div>
    );
} 