/**
 * Sticky Notes Canvas Content
 * 
 * Sticky notes grid for Brainstorm × Component level.
 * Implements drag-and-drop and grouping.
 * 
 * @doc.type component
 * @doc.purpose Sticky notes canvas for brainstorming
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Button,
  Surface as Paper,
} from '@ghatana/ui';

interface StickyNote {
    id: string;
    content: string;
    color: string;
    x: number;
    y: number;
}

const NOTE_COLORS = [
    '#fef3c7', // yellow
    '#dbeafe', // blue
    '#fce7f3', // pink
    '#d1fae5', // green
    '#e0e7ff', // indigo
];

export const StickyNotesCanvas = () => {
    const [notes, setNotes] = useState<StickyNote[]>([]);

    const addNote = () => {
        const newNote: StickyNote = {
            id: `note-${Date.now()}`,
            content: 'New idea...',
            color: NOTE_COLORS[Math.floor(Math.random() * NOTE_COLORS.length)],
            x: Math.random() * 60 + 20, // 20-80%
            y: Math.random() * 60 + 20,
        };
        setNotes([...notes, newNote]);
    };

    const deleteNote = (id: string) => {
        setNotes(notes.filter(note => note.id !== id));
    };

    const hasContent = notes.length > 0;

    return (
        <BaseCanvasContent hasContent={hasContent}>
            <Box
                className="relative h-full w-full bg-[#fafafa]" style={{ backgroundImage: 'radial-gradient(circle, backgroundSize: '20px 20px' }} >
                {/* Add Note Button */}
                <Button
                    variant="outlined"
                    size="small"
                    onClick={addNote}
                    className="absolute top-[16px] right-[16px] z-10 bg-white shadow"
                >
                    ➕ Add Note
                </Button>

                {/* Sticky Notes */}
                {notes.map((note) => (
                    <Paper
                        key={note.id}
                        elevation={3}
                        className="absolute" style={{ left: `${note.x, fontFamily: 'cursive' }}
                    >
                        <Box
                            className="flex justify-end mb-2 opacity-[0.5] hover:opacity-100"
                        >
                            <Button
                                size="small"
                                onClick={() => deleteNote(note.id)} sx: minWidth: 'auto' */
                            >
                                🗑️
                            </Button>
                        </Box>
                        <Typography
                            variant="body2"
                            className="text-sm leading-[1.4]" >
                            {note.content}
                        </Typography>
                    </Paper>
                ))}
            </Box>
        </BaseCanvasContent>
    );
};

export default StickyNotesCanvas;
