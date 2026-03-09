/**
 * PageTransition Storybook Stories
 */

import { useState } from 'react';

import { AnimationProvider } from './AnimationProvider';
import {
    PageTransition,
    FadeTransition,
    SlideLeftTransition,
    SlideRightTransition,
    SlideUpTransition,
    SlideDownTransition,
    ScaleTransition,
    ScaleFadeTransition,
    SlideFadeLeftTransition,
    SlideFadeRightTransition,
} from './PageTransition';

import type { Meta, StoryObj } from '@storybook/react';
import type { TransitionType } from './PageTransition';

const meta = {
    title: 'Animations/PageTransition',
    component: PageTransition,
    decorators: [
        (Story) => (
            <AnimationProvider>
                <div style={{ minHeight: '400px', padding: '2rem' }}>
                    <Story />
                </div>
            </AnimationProvider>
        ),
    ],
    parameters: {
        layout: 'centered',
    },
    tags: ['autodocs'],
} satisfies Meta<typeof PageTransition>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

// ============================================================================
// Sample Page Content
// ============================================================================

const PageContent = ({ title, color }: { title: string; color: string }) => (
    <div
        style={{
            padding: '3rem',
            backgroundColor: color,
            borderRadius: '8px',
            color: 'white',
            textAlign: 'center',
            minHeight: '300px',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            alignItems: 'center',
        }}
    >
        <h2 style={{ margin: 0, fontSize: '2rem' }}>{title}</h2>
        <p style={{ margin: '1rem 0 0', opacity: 0.9 }}>
            This is sample page content
        </p>
    </div>
);

// ============================================================================
// Interactive Demo
// ============================================================================

const InteractiveDemo = ({ type }: { type: TransitionType }) => {
    const [key, setKey] = useState(0);
    const [color, setColor] = useState('#3b82f6');

    const colors = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'];

    const handleTransition = () => {
        setKey((prev) => prev + 1);
        setColor(colors[Math.floor(Math.random() * colors.length)]);
    };

    return (
        <div>
            <button
                onClick={handleTransition}
                style={{
                    marginBottom: '2rem',
                    padding: '0.75rem 1.5rem',
                    backgroundColor: '#3b82f6',
                    color: 'white',
                    border: 'none',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    fontSize: '1rem',
                    fontWeight: '500',
                }}
            >
                Trigger Transition
            </button>

            <PageTransition type={type} animationKey={key}>
                <PageContent title={`Page ${key}`} color={color} />
            </PageTransition>
        </div>
    );
};

// ============================================================================
// Basic Stories
// ============================================================================

export const Default: Story = {
    args: {
        children: <PageContent title="Default Page" color="#3b82f6" />,
    },
};

export const Fade: Story = {
    render: () => <InteractiveDemo type="fade" />,
};

export const SlideLeft: Story = {
    render: () => <InteractiveDemo type="slide-left" />,
};

export const SlideRight: Story = {
    render: () => <InteractiveDemo type="slide-right" />,
};

export const SlideUp: Story = {
    render: () => <InteractiveDemo type="slide-up" />,
};

export const SlideDown: Story = {
    render: () => <InteractiveDemo type="slide-down" />,
};

export const Scale: Story = {
    render: () => <InteractiveDemo type="scale" />,
};

export const ScaleFade: Story = {
    render: () => <InteractiveDemo type="scale-fade" />,
};

export const Rotate: Story = {
    render: () => <InteractiveDemo type="rotate" />,
};

export const Flip: Story = {
    render: () => <InteractiveDemo type="flip" />,
};

export const Zoom: Story = {
    render: () => <InteractiveDemo type="zoom" />,
};

export const SlideFadeLeft: Story = {
    render: () => <InteractiveDemo type="slide-fade-left" />,
};

export const SlideFadeRight: Story = {
    render: () => <InteractiveDemo type="slide-fade-right" />,
};

export const SlideFadeUp: Story = {
    render: () => <InteractiveDemo type="slide-fade-up" />,
};

export const SlideFadeDown: Story = {
    render: () => <InteractiveDemo type="slide-fade-down" />,
};

// ============================================================================
// Custom Duration
// ============================================================================

export const CustomDuration: Story = {
    render: () => {
        const [key, setKey] = useState(0);

        return (
            <div>
                <button
                    onClick={() => setKey((prev) => prev + 1)}
                    style={{
                        marginBottom: '2rem',
                        padding: '0.75rem 1.5rem',
                        backgroundColor: '#3b82f6',
                        color: 'white',
                        border: 'none',
                        borderRadius: '6px',
                        cursor: 'pointer',
                    }}
                >
                    Trigger Slow Transition (1000ms)
                </button>

                <PageTransition type="fade" duration={1000} animationKey={key}>
                    <PageContent title="Slow Fade" color="#10b981" />
                </PageTransition>
            </div>
        );
    },
};

export const CustomDelay: Story = {
    render: () => {
        const [key, setKey] = useState(0);

        return (
            <div>
                <button
                    onClick={() => setKey((prev) => prev + 1)}
                    style={{
                        marginBottom: '2rem',
                        padding: '0.75rem 1.5rem',
                        backgroundColor: '#3b82f6',
                        color: 'white',
                        border: 'none',
                        borderRadius: '6px',
                        cursor: 'pointer',
                    }}
                >
                    Trigger Delayed Transition (500ms delay)
                </button>

                <PageTransition type="scale-fade" delay={500} animationKey={key}>
                    <PageContent title="Delayed Transition" color="#f59e0b" />
                </PageTransition>
            </div>
        );
    },
};

// ============================================================================
// Preset Components
// ============================================================================

export const FadePreset: Story = {
    render: () => {
        const [key, setKey] = useState(0);

        return (
            <div>
                <button
                    onClick={() => setKey((prev) => prev + 1)}
                    style={{
                        marginBottom: '2rem',
                        padding: '0.75rem 1.5rem',
                        backgroundColor: '#3b82f6',
                        color: 'white',
                        border: 'none',
                        borderRadius: '6px',
                        cursor: 'pointer',
                    }}
                >
                    Trigger Fade
                </button>

                <FadeTransition animationKey={key}>
                    <PageContent title="Fade Preset" color="#8b5cf6" />
                </FadeTransition>
            </div>
        );
    },
};

export const SlideLeftPreset: Story = {
    render: () => {
        const [key, setKey] = useState(0);

        return (
            <div>
                <button
                    onClick={() => setKey((prev) => prev + 1)}
                    style={{
                        marginBottom: '2rem',
                        padding: '0.75rem 1.5rem',
                        backgroundColor: '#3b82f6',
                        color: 'white',
                        border: 'none',
                        borderRadius: '6px',
                        cursor: 'pointer',
                    }}
                >
                    Trigger Slide Left
                </button>

                <SlideLeftTransition animationKey={key}>
                    <PageContent title="Slide Left Preset" color="#ec4899" />
                </SlideLeftTransition>
            </div>
        );
    },
};

// ============================================================================
// Multiple Pages Simulation
// ============================================================================

export const MultiplePagesSimulation: Story = {
    render: () => {
        const [currentPage, setCurrentPage] = useState(0);

        const pages = [
            { title: 'Home', color: '#3b82f6' },
            { title: 'About', color: '#10b981' },
            { title: 'Services', color: '#f59e0b' },
            { title: 'Contact', color: '#ef4444' },
        ];

        return (
            <div>
                <div style={{ marginBottom: '2rem', display: 'flex', gap: '0.5rem' }}>
                    {pages.map((page, index) => (
                        <button
                            key={index}
                            onClick={() => setCurrentPage(index)}
                            style={{
                                padding: '0.5rem 1rem',
                                backgroundColor:
                                    currentPage === index ? '#3b82f6' : '#e5e7eb',
                                color: currentPage === index ? 'white' : '#1f2937',
                                border: 'none',
                                borderRadius: '6px',
                                cursor: 'pointer',
                                fontWeight: '500',
                            }}
                        >
                            {page.title}
                        </button>
                    ))}
                </div>

                <PageTransition type="slide-fade-right" animationKey={currentPage}>
                    <PageContent
                        title={pages[currentPage].title}
                        color={pages[currentPage].color}
                    />
                </PageTransition>
            </div>
        );
    },
};

// ============================================================================
// All Transitions Comparison
// ============================================================================

export const AllTransitionsComparison: Story = {
    render: () => {
        const [activeTransition, setActiveTransition] = useState<string | null>(
            null
        );

        const transitions = [
            'fade',
            'slide-left',
            'slide-right',
            'slide-up',
            'slide-down',
            'scale',
            'scale-fade',
            'rotate',
            'flip',
            'zoom',
            'slide-fade-left',
            'slide-fade-right',
            'slide-fade-up',
            'slide-fade-down',
        ];

        return (
            <div>
                <div
                    style={{
                        marginBottom: '2rem',
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
                        gap: '0.5rem',
                    }}
                >
                    {transitions.map((transition) => (
                        <button
                            key={transition}
                            onClick={() => setActiveTransition(transition)}
                            style={{
                                padding: '0.5rem',
                                backgroundColor:
                                    activeTransition === transition ? '#3b82f6' : '#e5e7eb',
                                color: activeTransition === transition ? 'white' : '#1f2937',
                                border: 'none',
                                borderRadius: '6px',
                                cursor: 'pointer',
                                fontSize: '0.875rem',
                            }}
                        >
                            {transition}
                        </button>
                    ))}
                </div>

                {activeTransition && (
                    <PageTransition type={activeTransition as unknown} animationKey={activeTransition}>
                        <PageContent title={activeTransition} color="#3b82f6" />
                    </PageTransition>
                )}
            </div>
        );
    },
};
