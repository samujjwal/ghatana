/**
 * D3 Pharmacokinetics Charts
 *
 * @doc.type module
 * @doc.purpose Render PK concentration curves and related charts using D3
 * @doc.layer product
 * @doc.pattern Chart
 */

import React, { useRef, useEffect, useMemo, useCallback, useState } from "react";
import * as d3 from "d3";

// =============================================================================
// Types
// =============================================================================

export interface PKDataPoint {
    /** Time in hours */
    time: number;
    /** Drug concentration in mg/L */
    concentration: number;
    /** Compartment (for multi-compartment models) */
    compartment?: "central" | "peripheral" | "effect";
    /** Phase of PK */
    phase?: "absorption" | "distribution" | "elimination";
}

export interface PKChartProps {
    /** Time-concentration data points */
    data: PKDataPoint[];
    /** Chart width */
    width?: number;
    /** Chart height */
    height?: number;
    /** Therapeutic range (min, max) */
    therapeuticRange?: [number, number];
    /** Toxic threshold */
    toxicThreshold?: number;
    /** Show area under curve shading */
    showAUC?: boolean;
    /** Show therapeutic window */
    showTherapeuticWindow?: boolean;
    /** Show multiple compartments */
    multiCompartment?: boolean;
    /** X-axis label */
    xLabel?: string;
    /** Y-axis label */
    yLabel?: string;
    /** Chart title */
    title?: string;
    /** Current playback time (for animation) */
    currentTime?: number;
    /** Show dose markers */
    doseMarkers?: number[];
    /** Theme colors */
    theme?: PKChartTheme;
    /** On point hover callback */
    onPointHover?: (point: PKDataPoint | null) => void;
    /** On time click callback */
    onTimeClick?: (time: number) => void;
    /** Show logarithmic Y-axis */
    logScale?: boolean;
    /** Show half-life annotations */
    showHalfLife?: boolean;
    /** Half-life value in hours */
    halfLife?: number;
    /** Custom CSS class */
    className?: string;
}

export interface PKChartTheme {
    background: string;
    foreground: string;
    primary: string;
    secondary: string;
    therapeutic: string;
    toxic: string;
    auc: string;
    grid: string;
    axis: string;
    annotation: string;
}

const DEFAULT_THEME: PKChartTheme = {
    background: "#0f172a",
    foreground: "#f8fafc",
    primary: "#3b82f6",
    secondary: "#8b5cf6",
    therapeutic: "#22c55e",
    toxic: "#ef4444",
    auc: "rgba(59, 130, 246, 0.2)",
    grid: "#334155",
    axis: "#64748b",
    annotation: "#94a3b8",
};

// Compartment colors
const COMPARTMENT_COLORS = {
    central: "#3b82f6",
    peripheral: "#8b5cf6",
    effect: "#f59e0b",
};

// =============================================================================
// Main PK Chart Component
// =============================================================================

export function PKConcentrationChart({
    data,
    width = 600,
    height = 400,
    therapeuticRange,
    toxicThreshold,
    showAUC = true,
    showTherapeuticWindow = true,
    multiCompartment = false,
    xLabel = "Time (hours)",
    yLabel = "Concentration (mg/L)",
    title,
    currentTime,
    doseMarkers = [],
    theme = DEFAULT_THEME,
    onPointHover,
    onTimeClick,
    logScale = false,
    showHalfLife = false,
    halfLife,
    className,
}: PKChartProps): React.ReactElement {
    const svgRef = useRef<SVGSVGElement>(null);
    const tooltipRef = useRef<HTMLDivElement>(null);
    const [hoveredPoint, setHoveredPoint] = useState<PKDataPoint | null>(null);

    // Margins
    const margin = { top: 40, right: 40, bottom: 50, left: 60 };
    const innerWidth = width - margin.left - margin.right;
    const innerHeight = height - margin.top - margin.bottom;

    // Scales
    const xScale = useMemo(() => {
        const extent = d3.extent(data, (d) => d.time) as [number, number];
        return d3.scaleLinear().domain([0, extent[1] * 1.05]).range([0, innerWidth]);
    }, [data, innerWidth]);

    const yScale = useMemo(() => {
        const maxConc = d3.max(data, (d) => d.concentration) ?? 0;
        const yMax = Math.max(maxConc * 1.2, toxicThreshold ?? 0, therapeuticRange?.[1] ?? 0);

        if (logScale) {
            const minConc = d3.min(data.filter((d) => d.concentration > 0), (d) => d.concentration) ?? 0.01;
            return d3.scaleLog().domain([minConc * 0.5, yMax]).range([innerHeight, 0]).clamp(true);
        }

        return d3.scaleLinear().domain([0, yMax]).range([innerHeight, 0]);
    }, [data, innerHeight, toxicThreshold, therapeuticRange, logScale]);

    // Group data by compartment
    const compartmentData = useMemo(() => {
        if (!multiCompartment) {
            return new Map([['central', data]]) as Map<string, PKDataPoint[]>;
        }

        return d3.group(data, (d) => d.compartment ?? 'central') as Map<string, PKDataPoint[]>;
    }, [data, multiCompartment]);

    // Line generator
    const lineGenerator = useMemo(
        () =>
            d3
                .line<PKDataPoint>()
                .x((d) => xScale(d.time))
                .y((d) => yScale(d.concentration))
                .curve(d3.curveMonotoneX),
        [xScale, yScale]
    );

    // Area generator for AUC
    const areaGenerator = useMemo(
        () =>
            d3
                .area<PKDataPoint>()
                .x((d) => xScale(d.time))
                .y0(innerHeight)
                .y1((d) => yScale(d.concentration))
                .curve(d3.curveMonotoneX),
        [xScale, yScale, innerHeight]
    );

    // Render chart
    useEffect(() => {
        if (!svgRef.current) return;

        const svg = d3.select(svgRef.current);
        svg.selectAll("*").remove();

        // Background
        svg
            .append("rect")
            .attr("width", width)
            .attr("height", height)
            .attr("fill", theme.background);

        // Chart group
        const g = svg
            .append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`);

        // Grid lines
        const xGrid = d3.axisBottom(xScale).tickSize(-innerHeight).tickFormat(() => "");
        const yGrid = d3.axisLeft(yScale).tickSize(-innerWidth).tickFormat(() => "");

        g.append("g")
            .attr("class", "grid x-grid")
            .attr("transform", `translate(0,${innerHeight})`)
            .call(xGrid)
            .selectAll("line")
            .attr("stroke", theme.grid)
            .attr("stroke-opacity", 0.3);

        g.append("g")
            .attr("class", "grid y-grid")
            .call(yGrid)
            .selectAll("line")
            .attr("stroke", theme.grid)
            .attr("stroke-opacity", 0.3);

        // Remove grid domain lines
        g.selectAll(".grid .domain").remove();

        // Therapeutic window
        if (showTherapeuticWindow && therapeuticRange) {
            const [minTherapeutic, maxTherapeutic] = therapeuticRange;

            g.append("rect")
                .attr("x", 0)
                .attr("y", yScale(maxTherapeutic))
                .attr("width", innerWidth)
                .attr("height", yScale(minTherapeutic) - yScale(maxTherapeutic))
                .attr("fill", theme.therapeutic)
                .attr("opacity", 0.15);

            // Min therapeutic line
            g.append("line")
                .attr("x1", 0)
                .attr("x2", innerWidth)
                .attr("y1", yScale(minTherapeutic))
                .attr("y2", yScale(minTherapeutic))
                .attr("stroke", theme.therapeutic)
                .attr("stroke-dasharray", "4,4")
                .attr("stroke-width", 1.5);

            // Max therapeutic line
            g.append("line")
                .attr("x1", 0)
                .attr("x2", innerWidth)
                .attr("y1", yScale(maxTherapeutic))
                .attr("y2", yScale(maxTherapeutic))
                .attr("stroke", theme.therapeutic)
                .attr("stroke-dasharray", "4,4")
                .attr("stroke-width", 1.5);

            // Labels
            g.append("text")
                .attr("x", innerWidth + 5)
                .attr("y", yScale(minTherapeutic))
                .attr("dy", "0.35em")
                .attr("fill", theme.therapeutic)
                .attr("font-size", 10)
                .text("MEC");

            g.append("text")
                .attr("x", innerWidth + 5)
                .attr("y", yScale(maxTherapeutic))
                .attr("dy", "0.35em")
                .attr("fill", theme.therapeutic)
                .attr("font-size", 10)
                .text("MTC");
        }

        // Toxic threshold
        if (toxicThreshold) {
            g.append("line")
                .attr("x1", 0)
                .attr("x2", innerWidth)
                .attr("y1", yScale(toxicThreshold))
                .attr("y2", yScale(toxicThreshold))
                .attr("stroke", theme.toxic)
                .attr("stroke-dasharray", "6,3")
                .attr("stroke-width", 2);

            g.append("text")
                .attr("x", innerWidth + 5)
                .attr("y", yScale(toxicThreshold))
                .attr("dy", "0.35em")
                .attr("fill", theme.toxic)
                .attr("font-size", 10)
                .text("Toxic");
        }

        // AUC shading (for primary/central compartment only)
        if (showAUC && compartmentData.has("central")) {
            const centralData = compartmentData.get("central") ?? data;
            g.append("path")
                .datum(centralData)
                .attr("fill", theme.auc)
                .attr("d", areaGenerator);
        }

        // Draw lines for each compartment
        compartmentData.forEach((points, compartment) => {
            const color = COMPARTMENT_COLORS[compartment as keyof typeof COMPARTMENT_COLORS] ?? theme.primary;

            g.append("path")
                .datum(points)
                .attr("fill", "none")
                .attr("stroke", color)
                .attr("stroke-width", 2.5)
                .attr("d", lineGenerator);
        });

        // Dose markers
        doseMarkers.forEach((doseTime) => {
            g.append("line")
                .attr("x1", xScale(doseTime))
                .attr("x2", xScale(doseTime))
                .attr("y1", 0)
                .attr("y2", innerHeight)
                .attr("stroke", theme.annotation)
                .attr("stroke-dasharray", "2,2")
                .attr("stroke-width", 1);

            g.append("text")
                .attr("x", xScale(doseTime))
                .attr("y", -5)
                .attr("text-anchor", "middle")
                .attr("fill", theme.annotation)
                .attr("font-size", 10)
                .text("💊");
        });

        // Half-life annotations
        if (showHalfLife && halfLife) {
            const maxConc = d3.max(data, (d) => d.concentration) ?? 0;
            const halfLifeConc = maxConc / 2;

            // Find the time when concentration reaches half
            const halfLifeTime = data.find((d) => d.concentration <= halfLifeConc)?.time;

            if (halfLifeTime) {
                // Half-life horizontal line
                g.append("line")
                    .attr("x1", 0)
                    .attr("x2", xScale(halfLifeTime))
                    .attr("y1", yScale(halfLifeConc))
                    .attr("y2", yScale(halfLifeConc))
                    .attr("stroke", theme.secondary)
                    .attr("stroke-dasharray", "3,3")
                    .attr("stroke-width", 1);

                // Annotation
                g.append("text")
                    .attr("x", xScale(halfLifeTime) / 2)
                    .attr("y", yScale(halfLifeConc) - 8)
                    .attr("text-anchor", "middle")
                    .attr("fill", theme.secondary)
                    .attr("font-size", 10)
                    .text(`t½ = ${halfLife.toFixed(1)}h`);
            }
        }

        // Current time indicator
        if (currentTime !== undefined) {
            const currentConc = data.find((d) => d.time >= currentTime)?.concentration ?? 0;

            g.append("line")
                .attr("x1", xScale(currentTime))
                .attr("x2", xScale(currentTime))
                .attr("y1", 0)
                .attr("y2", innerHeight)
                .attr("stroke", theme.foreground)
                .attr("stroke-width", 1.5)
                .attr("opacity", 0.8);

            g.append("circle")
                .attr("cx", xScale(currentTime))
                .attr("cy", yScale(currentConc))
                .attr("r", 6)
                .attr("fill", theme.primary)
                .attr("stroke", theme.foreground)
                .attr("stroke-width", 2);
        }

        // Interactive overlay
        const bisect = d3.bisector<PKDataPoint, number>((d) => d.time).left;

        g.append("rect")
            .attr("class", "overlay")
            .attr("width", innerWidth)
            .attr("height", innerHeight)
            .attr("fill", "transparent")
            .style("cursor", "crosshair")
            .on("mousemove", (event) => {
                const [mx] = d3.pointer(event);
                const time = xScale.invert(mx);
                const index = bisect(data, time, 1);
                const d0 = data[index - 1]!;
                const d1 = data[index];
                const point = d1 && time - d0.time > d1.time - time ? d1 : d0;

                if (point) {
                    setHoveredPoint(point);
                    onPointHover?.(point);
                }
            })
            .on("mouseleave", () => {
                setHoveredPoint(null);
                onPointHover?.(null);
            })
            .on("click", (event) => {
                const [mx] = d3.pointer(event);
                const time = xScale.invert(mx);
                onTimeClick?.(time);
            });

        // X-axis
        const xAxis = d3.axisBottom(xScale).ticks(8);
        g.append("g")
            .attr("transform", `translate(0,${innerHeight})`)
            .call(xAxis)
            .attr("color", theme.axis)
            .selectAll("text")
            .attr("fill", theme.foreground);

        // Y-axis
        const yAxis = logScale
            ? d3.axisLeft(yScale).ticks(5, ".1s")
            : d3.axisLeft(yScale).ticks(6);

        g.append("g")
            .call(yAxis)
            .attr("color", theme.axis)
            .selectAll("text")
            .attr("fill", theme.foreground);

        // X-axis label
        g.append("text")
            .attr("x", innerWidth / 2)
            .attr("y", innerHeight + 40)
            .attr("text-anchor", "middle")
            .attr("fill", theme.foreground)
            .attr("font-size", 12)
            .text(xLabel);

        // Y-axis label
        g.append("text")
            .attr("transform", "rotate(-90)")
            .attr("x", -innerHeight / 2)
            .attr("y", -45)
            .attr("text-anchor", "middle")
            .attr("fill", theme.foreground)
            .attr("font-size", 12)
            .text(yLabel);

        // Title
        if (title) {
            svg
                .append("text")
                .attr("x", width / 2)
                .attr("y", 20)
                .attr("text-anchor", "middle")
                .attr("fill", theme.foreground)
                .attr("font-size", 14)
                .attr("font-weight", "600")
                .text(title);
        }

        // Legend (for multi-compartment)
        if (multiCompartment && compartmentData.size > 1) {
            const legend = g.append("g").attr("transform", `translate(${innerWidth - 120}, 10)`);

            let legendY = 0;
            compartmentData.forEach((_: PKDataPoint[], compartment: string) => {
                const color = COMPARTMENT_COLORS[compartment as keyof typeof COMPARTMENT_COLORS] ?? theme.primary;

                legend
                    .append("line")
                    .attr("x1", 0)
                    .attr("x2", 20)
                    .attr("y1", legendY)
                    .attr("y2", legendY)
                    .attr("stroke", color)
                    .attr("stroke-width", 2);

                legend
                    .append("text")
                    .attr("x", 25)
                    .attr("y", legendY)
                    .attr("dy", "0.35em")
                    .attr("fill", theme.foreground)
                    .attr("font-size", 10)
                    .text(compartment.charAt(0).toUpperCase() + compartment.slice(1));

                legendY += 18;
            });
        }
    }, [
        data,
        width,
        height,
        margin,
        innerWidth,
        innerHeight,
        xScale,
        yScale,
        lineGenerator,
        areaGenerator,
        compartmentData,
        therapeuticRange,
        toxicThreshold,
        showAUC,
        showTherapeuticWindow,
        multiCompartment,
        doseMarkers,
        currentTime,
        showHalfLife,
        halfLife,
        xLabel,
        yLabel,
        title,
        theme,
        logScale,
        onPointHover,
        onTimeClick,
    ]);

    return (
        <div className={className} style={{ position: "relative" }}>
            <svg ref={svgRef} width={width} height={height} />

            {/* Tooltip */}
            {hoveredPoint && (
                <div
                    ref={tooltipRef}
                    style={{
                        position: "absolute",
                        top: margin.top + yScale(hoveredPoint.concentration) - 60,
                        left: margin.left + xScale(hoveredPoint.time) + 10,
                        background: "rgba(0,0,0,0.85)",
                        color: theme.foreground,
                        padding: "8px 12px",
                        borderRadius: 6,
                        fontSize: 12,
                        pointerEvents: "none",
                        whiteSpace: "nowrap",
                        boxShadow: "0 4px 12px rgba(0,0,0,0.3)",
                    }}
                >
                    <div style={{ fontWeight: 600, marginBottom: 4 }}>
                        t = {hoveredPoint.time.toFixed(2)} h
                    </div>
                    <div>C = {hoveredPoint.concentration.toFixed(2)} mg/L</div>
                    {hoveredPoint.phase && (
                        <div style={{ color: theme.annotation, marginTop: 4 }}>
                            Phase: {hoveredPoint.phase}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

// =============================================================================
// SIR Epidemic Chart
// =============================================================================

export interface SIRDataPoint {
    time: number;
    susceptible: number;
    infected: number;
    recovered: number;
    vaccinated?: number;
    deceased?: number;
}

export interface SIRChartProps {
    data: SIRDataPoint[];
    width?: number;
    height?: number;
    title?: string;
    currentTime?: number;
    interventionMarkers?: { time: number; label: string }[];
    showRt?: boolean;
    r0?: number;
    theme?: PKChartTheme;
    className?: string;
}

export function SIREpidemicChart({
    data,
    width = 600,
    height = 400,
    title = "Epidemic Progression",
    currentTime,
    interventionMarkers = [],
    showRt = true,
    r0 = 2.5,
    theme = DEFAULT_THEME,
    className,
}: SIRChartProps): React.ReactElement {
    const svgRef = useRef<SVGSVGElement>(null);

    const margin = { top: 40, right: 100, bottom: 50, left: 60 };
    const innerWidth = width - margin.left - margin.right;
    const innerHeight = height - margin.top - margin.bottom;

    // Scales
    const xScale = useMemo(() => {
        const extent = d3.extent(data, (d) => d.time) as [number, number];
        return d3.scaleLinear().domain([0, extent[1]]).range([0, innerWidth]);
    }, [data, innerWidth]);

    const yScale = useMemo(() => {
        const maxPop = d3.max(data, (d) =>
            Math.max(d.susceptible, d.infected, d.recovered)
        ) ?? 0;
        return d3.scaleLinear().domain([0, maxPop * 1.1]).range([innerHeight, 0]);
    }, [data, innerHeight]);

    // Line generators
    const createLine = (accessor: (d: SIRDataPoint) => number) =>
        d3
            .line<SIRDataPoint>()
            .x((d) => xScale(d.time))
            .y((d) => yScale(accessor(d)))
            .curve(d3.curveMonotoneX);

    const susceptibleLine = createLine((d) => d.susceptible);
    const infectedLine = createLine((d) => d.infected);
    const recoveredLine = createLine((d) => d.recovered);
    const vaccinatedLine = createLine((d) => d.vaccinated ?? 0);

    // Calculate Rt over time
    const rtData = useMemo(() => {
        return data.map((d) => ({
            time: d.time,
            rt: r0 * (d.susceptible / (d.susceptible + d.infected + d.recovered)),
        }));
    }, [data, r0]);

    useEffect(() => {
        if (!svgRef.current) return;

        const svg = d3.select(svgRef.current);
        svg.selectAll("*").remove();

        // Background
        svg
            .append("rect")
            .attr("width", width)
            .attr("height", height)
            .attr("fill", theme.background);

        const g = svg
            .append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`);

        // Grid
        const xGrid = d3.axisBottom(xScale).tickSize(-innerHeight).tickFormat(() => "");
        g.append("g")
            .attr("transform", `translate(0,${innerHeight})`)
            .call(xGrid)
            .selectAll("line")
            .attr("stroke", theme.grid)
            .attr("stroke-opacity", 0.3);
        g.selectAll(".domain").remove();

        // R=1 threshold line
        if (showRt) {
            const rtScale = d3.scaleLinear().domain([0, r0 * 1.5]).range([innerHeight, 0]);

            g.append("line")
                .attr("x1", 0)
                .attr("x2", innerWidth)
                .attr("y1", rtScale(1))
                .attr("y2", rtScale(1))
                .attr("stroke", theme.annotation)
                .attr("stroke-dasharray", "4,4")
                .attr("stroke-width", 1);

            g.append("text")
                .attr("x", innerWidth + 5)
                .attr("y", rtScale(1))
                .attr("dy", "0.35em")
                .attr("fill", theme.annotation)
                .attr("font-size", 9)
                .text("Rₜ = 1");
        }

        // Intervention markers
        interventionMarkers.forEach(({ time, label }) => {
            g.append("line")
                .attr("x1", xScale(time))
                .attr("x2", xScale(time))
                .attr("y1", 0)
                .attr("y2", innerHeight)
                .attr("stroke", theme.annotation)
                .attr("stroke-dasharray", "3,3")
                .attr("stroke-width", 1);

            g.append("text")
                .attr("x", xScale(time))
                .attr("y", -5)
                .attr("text-anchor", "middle")
                .attr("fill", theme.annotation)
                .attr("font-size", 9)
                .text(label);
        });

        // Draw compartment lines
        const colors = {
            susceptible: "#3b82f6",
            infected: "#ef4444",
            recovered: "#22c55e",
            vaccinated: "#8b5cf6",
        };

        // Susceptible
        g.append("path")
            .datum(data)
            .attr("fill", "none")
            .attr("stroke", colors.susceptible)
            .attr("stroke-width", 2)
            .attr("d", susceptibleLine);

        // Infected
        g.append("path")
            .datum(data)
            .attr("fill", "none")
            .attr("stroke", colors.infected)
            .attr("stroke-width", 2.5)
            .attr("d", infectedLine);

        // Recovered
        g.append("path")
            .datum(data)
            .attr("fill", "none")
            .attr("stroke", colors.recovered)
            .attr("stroke-width", 2)
            .attr("d", recoveredLine);

        // Vaccinated (if present)
        if (data.some((d) => d.vaccinated !== undefined && d.vaccinated > 0)) {
            g.append("path")
                .datum(data)
                .attr("fill", "none")
                .attr("stroke", colors.vaccinated)
                .attr("stroke-width", 2)
                .attr("stroke-dasharray", "4,2")
                .attr("d", vaccinatedLine);
        }

        // Current time indicator
        if (currentTime !== undefined) {
            g.append("line")
                .attr("x1", xScale(currentTime))
                .attr("x2", xScale(currentTime))
                .attr("y1", 0)
                .attr("y2", innerHeight)
                .attr("stroke", theme.foreground)
                .attr("stroke-width", 1.5);
        }

        // Axes
        g.append("g")
            .attr("transform", `translate(0,${innerHeight})`)
            .call(d3.axisBottom(xScale).ticks(8))
            .attr("color", theme.axis)
            .selectAll("text")
            .attr("fill", theme.foreground);

        g.append("g")
            .call(d3.axisLeft(yScale).ticks(6).tickFormat(d3.format(".2s")))
            .attr("color", theme.axis)
            .selectAll("text")
            .attr("fill", theme.foreground);

        // X-axis label
        g.append("text")
            .attr("x", innerWidth / 2)
            .attr("y", innerHeight + 40)
            .attr("text-anchor", "middle")
            .attr("fill", theme.foreground)
            .attr("font-size", 12)
            .text("Time (days)");

        // Y-axis label
        g.append("text")
            .attr("transform", "rotate(-90)")
            .attr("x", -innerHeight / 2)
            .attr("y", -45)
            .attr("text-anchor", "middle")
            .attr("fill", theme.foreground)
            .attr("font-size", 12)
            .text("Population");

        // Title
        if (title) {
            svg
                .append("text")
                .attr("x", width / 2)
                .attr("y", 20)
                .attr("text-anchor", "middle")
                .attr("fill", theme.foreground)
                .attr("font-size", 14)
                .attr("font-weight", "600")
                .text(title);
        }

        // Legend
        const legend = g.append("g").attr("transform", `translate(${innerWidth + 10}, 10)`);

        const legendItems = [
            { label: "Susceptible", color: colors.susceptible },
            { label: "Infected", color: colors.infected },
            { label: "Recovered", color: colors.recovered },
        ];

        if (data.some((d) => d.vaccinated !== undefined && d.vaccinated > 0)) {
            legendItems.push({ label: "Vaccinated", color: colors.vaccinated });
        }

        legendItems.forEach((item, i) => {
            legend
                .append("line")
                .attr("x1", 0)
                .attr("x2", 15)
                .attr("y1", i * 18)
                .attr("y2", i * 18)
                .attr("stroke", item.color)
                .attr("stroke-width", 2);

            legend
                .append("text")
                .attr("x", 20)
                .attr("y", i * 18)
                .attr("dy", "0.35em")
                .attr("fill", theme.foreground)
                .attr("font-size", 10)
                .text(item.label);
        });
    }, [
        data,
        width,
        height,
        margin,
        innerWidth,
        innerHeight,
        xScale,
        yScale,
        susceptibleLine,
        infectedLine,
        recoveredLine,
        vaccinatedLine,
        rtData,
        currentTime,
        interventionMarkers,
        showRt,
        r0,
        title,
        theme,
    ]);

    return (
        <div className={className}>
            <svg ref={svgRef} width={width} height={height} />
        </div>
    );
}

// =============================================================================
// PK Parameter Comparison Chart
// =============================================================================

export interface PKParameterData {
    name: string;
    value: number;
    unit: string;
    normalRange?: [number, number];
}

export interface PKParameterChartProps {
    parameters: PKParameterData[];
    width?: number;
    height?: number;
    title?: string;
    theme?: PKChartTheme;
    className?: string;
}

export function PKParameterChart({
    parameters,
    width = 400,
    height = 300,
    title = "PK Parameters",
    theme = DEFAULT_THEME,
    className,
}: PKParameterChartProps): React.ReactElement {
    const svgRef = useRef<SVGSVGElement>(null);

    const margin = { top: 40, right: 80, bottom: 30, left: 120 };
    const innerWidth = width - margin.left - margin.right;
    const innerHeight = height - margin.top - margin.bottom;

    useEffect(() => {
        if (!svgRef.current) return;

        const svg = d3.select(svgRef.current);
        svg.selectAll("*").remove();

        svg
            .append("rect")
            .attr("width", width)
            .attr("height", height)
            .attr("fill", theme.background);

        const g = svg
            .append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`);

        // Scales
        const yScale = d3
            .scaleBand()
            .domain(parameters.map((p) => p.name))
            .range([0, innerHeight])
            .padding(0.3);

        const xScale = d3
            .scaleLinear()
            .domain([0, d3.max(parameters, (p) => p.normalRange?.[1] ?? p.value * 1.5) ?? 100])
            .range([0, innerWidth]);

        // Normal range bars
        parameters.forEach((param) => {
            if (param.normalRange) {
                g.append("rect")
                    .attr("x", xScale(param.normalRange[0]))
                    .attr("y", (yScale(param.name) ?? 0) + yScale.bandwidth() * 0.2)
                    .attr("width", xScale(param.normalRange[1]) - xScale(param.normalRange[0]))
                    .attr("height", yScale.bandwidth() * 0.6)
                    .attr("fill", theme.therapeutic)
                    .attr("opacity", 0.2)
                    .attr("rx", 3);
            }
        });

        // Value markers
        parameters.forEach((param) => {
            const y = (yScale(param.name) ?? 0) + yScale.bandwidth() / 2;
            const x = xScale(param.value);

            // Determine if value is in normal range
            const inRange = param.normalRange
                ? param.value >= param.normalRange[0] && param.value <= param.normalRange[1]
                : true;

            // Marker
            g.append("circle")
                .attr("cx", x)
                .attr("cy", y)
                .attr("r", 8)
                .attr("fill", inRange ? theme.primary : theme.toxic);

            // Value label
            g.append("text")
                .attr("x", x)
                .attr("y", y - 15)
                .attr("text-anchor", "middle")
                .attr("fill", theme.foreground)
                .attr("font-size", 10)
                .attr("font-weight", "600")
                .text(`${param.value.toFixed(1)} ${param.unit}`);
        });

        // Y-axis (parameter names)
        g.append("g")
            .call(d3.axisLeft(yScale))
            .attr("color", theme.axis)
            .selectAll("text")
            .attr("fill", theme.foreground)
            .attr("font-size", 11);

        g.selectAll(".domain").remove();
        g.selectAll(".tick line").remove();

        // Title
        if (title) {
            svg
                .append("text")
                .attr("x", width / 2)
                .attr("y", 20)
                .attr("text-anchor", "middle")
                .attr("fill", theme.foreground)
                .attr("font-size", 14)
                .attr("font-weight", "600")
                .text(title);
        }
    }, [parameters, width, height, margin, innerWidth, innerHeight, title, theme]);

    return (
        <div className={className}>
            <svg ref={svgRef} width={width} height={height} />
        </div>
    );
}

// =============================================================================
// Exports
// =============================================================================

export { PKConcentrationChart as default };
