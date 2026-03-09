/**
 * Sequence Diagram Canvas Content
 * 
 * UML sequence diagram for Diagram × Code level.
 * Displays interaction flows and message passing between objects.
 * 
 * @doc.type component
 * @doc.purpose Sequence diagram for interaction visualization
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  Surface as Paper,
} from '@ghatana/ui';

interface Actor {
    id: string;
    name: string;
    type: 'user' | 'system' | 'service' | 'database';
}

interface Message {
    id: string;
    from: string;
    to: string;
    message: string;
    type: 'sync' | 'async' | 'return';
    order: number;
}

// Mock sequence data
const MOCK_ACTORS: Actor[] = [
    { id: 'client', name: 'Client', type: 'user' },
    { id: 'api', name: 'API Gateway', type: 'system' },
    { id: 'auth', name: 'Auth Service', type: 'service' },
    { id: 'db', name: 'Database', type: 'database' },
];

const MOCK_MESSAGES: Message[] = [
    { id: 'm1', from: 'client', to: 'api', message: 'POST /login', type: 'sync', order: 1 },
    { id: 'm2', from: 'api', to: 'auth', message: 'authenticate(credentials)', type: 'sync', order: 2 },
    { id: 'm3', from: 'auth', to: 'db', message: 'findUser(email)', type: 'sync', order: 3 },
    { id: 'm4', from: 'db', to: 'auth', message: 'User', type: 'return', order: 4 },
    { id: 'm5', from: 'auth', to: 'auth', message: 'validatePassword()', type: 'sync', order: 5 },
    { id: 'm6', from: 'auth', to: 'auth', message: 'generateToken()', type: 'sync', order: 6 },
    { id: 'm7', from: 'auth', to: 'api', message: 'AuthToken', type: 'return', order: 7 },
    { id: 'm8', from: 'api', to: 'client', message: '200 OK { token }', type: 'return', order: 8 },
];

const getActorColor = (type: Actor['type']) => {
    switch (type) {
        case 'user':
            return '#2196F3';
        case 'system':
            return '#4CAF50';
        case 'service':
            return '#FF9800';
        case 'database':
            return '#9C27B0';
    }
};

const ActorBox = ({ actor, index, total }: { actor: Actor; index: number; total: number }) => {
    const leftPosition = ((index + 1) / (total + 1)) * 100;
    const color = getActorColor(actor.type);

    return (
        <>
            {/* Actor box */}
            <Paper
                elevation={2}
                className="absolute" style={{ left: `${leftPosition, backgroundColor: 'color' }}
            >
                <Typography variant="body2" className="font-semibold">
                    {actor.name}
                </Typography>
                <Chip
                    label={actor.type}
                    size="small"
                    className="h-[18px] mt-1 text-[0.65rem] text-white" />
            </Paper>

            {/* Lifeline */}
            <Box
                className="absolute" style={{ left: `${leftPosition }}
            />
        </>
    );
};

const MessageArrow = ({
    message,
    actors,
    yPosition,
}: {
    message: Message;
    actors: Actor[];
    yPosition: number;
}) => {
    const fromIndex = actors.findIndex(a => a.id === message.from);
    const toIndex = actors.findIndex(a => a.id === message.to);

    if (fromIndex === -1 || toIndex === -1) return null;

    const fromX = ((fromIndex + 1) / (actors.length + 1)) * 100;
    const toX = ((toIndex + 1) / (actors.length + 1)) * 100;

    const isReturn = message.type === 'return';
    const isSelfCall = message.from === message.to;

    if (isSelfCall) {
        // Self-call loop
        return (
            <Box
                className="absolute" style={{ left: `${fromX }}
            >
                <svg width="60" height="40" style={{ overflow: 'visible' }}>
                    <defs>
                        <marker
                            id={`arrow-self-${message.id}`}
                            markerWidth="8"
                            markerHeight="8"
                            refX="6"
                            refY="4"
                            orient="auto"
                        >
                            <polygon points="0 0, 8 4, 0 8" fill="#666" />
                        </marker>
                    </defs>
                    <path
                        d="M 0 0 L 40 0 L 40 30 L 0 30"
                        fill="none"
                        stroke="#666"
                        strokeWidth="1.5"
                        markerEnd={`url(#arrow-self-${message.id})`}
                    />
                </svg>
                <Typography
                    variant="caption"
                    className="absolute whitespace-nowrap left-[45px] top-[10px] text-[0.7rem] bg-white p-[2px 4px]"
                >
                    {message.message}
                </Typography>
            </Box>
        );
    }

    const direction = toX > fromX ? 1 : -1;
    const distance = Math.abs(toX - fromX);

    return (
        <Box
            className="absolute" style={{ left: `${Math.min(fromX, transform: 'translateX(-50%)' }}
        >
            <svg width="100%" height="30" style={{ overflow: 'visible' }}>
                <defs>
                    <marker
                        id={`arrow-${message.id}`}
                        markerWidth="8"
                        markerHeight="8"
                        refX={direction > 0 ? 6 : 2}
                        refY="4"
                        orient="auto"
                    >
                        <polygon points="0 0, 8 4, 0 8" fill="#666" />
                    </marker>
                </defs>
                <line
                    x1={direction > 0 ? '0%' : '100%'}
                    y1="15"
                    x2={direction > 0 ? '100%' : '0%'}
                    y2="15"
                    stroke="#666"
                    strokeWidth="1.5"
                    strokeDasharray={isReturn ? '5,3' : 'none'}
                    markerEnd={`url(#arrow-${message.id})`}
                />
            </svg>
            <Typography
                variant="caption"
                className="absolute rounded whitespace-nowrap overflow-hidden text-ellipsis left-[50%] top-[-5px] text-[0.7rem] bg-white p-[2px 6px] border border-solid border-[#eee] max-w-[90%]" >
                {message.message}
            </Typography>
        </Box>
    );
};

export const SequenceDiagramCanvas = () => {
    const [actors] = useState<Actor[]>(MOCK_ACTORS);
    const [messages] = useState<Message[]>(MOCK_MESSAGES.sort((a, b) => a.order - b.order));
    const [selectedMessage, setSelectedMessage] = useState<string | null>(null);

    const hasContent = actors.length > 0 && messages.length > 0;

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Record Sequence',
                    onClick: () => {
                        console.log('Record sequence');
                    },
                },
                secondaryAction: {
                    label: 'Import from Trace',
                    onClick: () => {
                        console.log('Import trace');
                    },
                },
            }}
        >
            <Box
                className="relative h-full w-full overflow-auto bg-[#fafafa] p-4"
            >
                <Box
                    className="relative min-h-[500px]" style={{ height: `${Math.max(500, transform: 'translateX(-50%)' }}
                >
                    {/* Title */}
                    <Typography
                        variant="h6"
                        className="absolute font-semibold top-[10px] left-[50%]" >
                        Login Flow Sequence
                    </Typography>

                    {/* Actors */}
                    {actors.map((actor, index) => (
                        <ActorBox key={actor.id} actor={actor} index={index} total={actors.length} />
                    ))}

                    {/* Messages */}
                    {messages.map((message, index) => (
                        <Box
                            key={message.id}
                            onMouseEnter={() => setSelectedMessage(message.id)}
                            onMouseLeave={() => setSelectedMessage(null)}
                            className="cursor-pointer transition-all duration-300" style={{ opacity: selectedMessage === null || selectedMessage === message.id ? 1 : 0.4, backgroundImage: 'repeating-linear-gradient(90deg' }}
                        >
                            <MessageArrow message={message} actors={actors} yPosition={140 + index * 60} />
                        </Box>
                    ))}
                </Box>

                {/* Legend */}
                <Box
                    className="fixed rounded bottom-[16px] left-[16px] bg-white p-4 shadow min-w-[200px]"
                >
                    <Typography variant="subtitle2" gutterBottom>
                        Message Types
                    </Typography>
                    <Box className="flex items-center gap-2 mt-1">
                        <Box className="w-[40px] h-[2px] bg-[#666]" />
                        <Typography variant="caption">Synchronous</Typography>
                    </Box>
                    <Box className="flex items-center gap-2 mt-1">
                        <Box
                            className="w-[40px] h-[2px] bg-[#666]" />
                        <Typography variant="caption">Return</Typography>
                    </Box>
                    <Box className="mt-2">
                        <Typography variant="caption" display="block" color="text.secondary">
                            Messages: {messages.length}
                        </Typography>
                        <Typography variant="caption" display="block" color="text.secondary">
                            Actors: {actors.length}
                        </Typography>
                    </Box>
                </Box>

                {/* Message details */}
                {selectedMessage && (
                    <Box
                        className="fixed rounded top-[16px] right-[16px] bg-white p-4 shadow min-w-[250px] max-w-[350px]"
                    >
                        {(() => {
                            const msg = messages.find(m => m.id === selectedMessage);
                            if (!msg) return null;
                            const fromActor = actors.find(a => a.id === msg.from);
                            const toActor = actors.find(a => a.id === msg.to);
                            return (
                                <>
                                    <Typography variant="subtitle2" gutterBottom className="font-semibold">
                                        Message #{msg.order}
                                    </Typography>
                                    <Typography variant="body2" className="font-mono mb-2">
                                        {msg.message}
                                    </Typography>
                                    <Chip
                                        label={msg.type.toUpperCase()}
                                        size="small"
                                        className="mb-2"
                                    />
                                    <Typography variant="caption" display="block" color="text.secondary">
                                        From: {fromActor?.name} ({fromActor?.type})
                                    </Typography>
                                    <Typography variant="caption" display="block" color="text.secondary">
                                        To: {toActor?.name} ({toActor?.type})
                                    </Typography>
                                </>
                            );
                        })()}
                    </Box>
                )}
            </Box>
        </BaseCanvasContent>
    );
};

export default SequenceDiagramCanvas;
