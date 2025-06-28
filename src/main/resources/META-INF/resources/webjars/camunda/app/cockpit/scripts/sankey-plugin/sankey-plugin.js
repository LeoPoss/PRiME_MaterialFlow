/**
 * Configuration for the Sankey Diagram Plugin
 */
const PLUGIN_CONFIG = {
    id: "sankeyOverlay",
    pluginPoint: "cockpit.processDefinition.diagram.plugin",
    priority: 0,

    // Constants
    TASK_TYPES: [
        "bpmn:Task",
        "bpmn:ManualTask",
        "bpmn:UserTask",
        "bpmn:ServiceTask",
        "bpmn:ScriptTask",
        "bpmn:BusinessRuleTask"
    ],

    // Styling
    STYLES: {
        container: {
            position: "absolute",
            zIndex: "1000"
        },
        link: {
            fill: "none",
            strokeOpacity: 0.7,
            minStrokeWidth: 3
        },
        label: {
            fontSize: "16px",
            fontWeight: "bold",
            fill: "black"
        },
        nodeLabel: {
            fontSize: "10px",
            textAnchor: "end"
        }
    }
};

export default {
    ...PLUGIN_CONFIG,
    /**
     * Renders the Sankey diagram overlay on the BPMN diagram
     * @param {Object} viewer - The Camunda viewer instance
     */
    render: (viewer) => {
        window.camundaViewer = viewer;
        console.log("Sankey Overlay Plugin loaded!");

        // Initialize Camunda services
        const overlays = viewer.get("overlays");
        const canvas = viewer.get("canvas");
        const elementRegistry = viewer.get("elementRegistry");
        const rootElement = canvas.getRootElement();
        const rootBBox = canvas.getGraphics(rootElement).getBBox();

        // Find all BPMN tasks
        const tasks = elementRegistry.filter(el => PLUGIN_CONFIG.TASK_TYPES.includes(el.type));

        if (!tasks.length) {
            console.error("Error: No BPMN tasks found in the diagram!");
            return;
        }

        // Calculate dimensions and positions
        const imgWidth = rootBBox.width;
        const imgHeight = (tasks.length * 40);
        const containerHeight = imgHeight + rootBBox.height;
        const top = rootBBox.y - imgHeight;
        const left = rootBBox.x;

        /**
         * Extracts and maps task positions from BPMN elements
         * @returns {Array} Array of task position objects
         */
        const getTaskPositions = () => {
            return tasks.map(task => ({
                id: task.id,
                name: task.businessObject?.name || task.id,
                x: task.x - rootBBox.x,
                y: (task.y - rootBBox.y) + imgHeight,
                width: task.width,
                height: task.height
            }));
        };

        const taskPositions = getTaskPositions();
        console.debug("Mapped BPMN task positions:", taskPositions);

        /**
         * Creates the SVG container for the Sankey diagram
         * @returns {HTMLElement} The created container element
         */
        const createSankeyContainer = () => {
            const container = document.createElement("div");
            Object.assign(container.style, PLUGIN_CONFIG.STYLES.container, {
                width: `${imgWidth}px`,
                height: `${containerHeight}px`
            });

            container.innerHTML = `
                <svg width="${imgWidth}" height="${containerHeight}" 
                     viewBox="0 0 ${imgWidth} ${containerHeight}" 
                     preserveAspectRatio="xMidYMid meet">
                    <g class="sankey-diagram"></g>
                </svg>
            `;
            return container;
        };

        const container = createSankeyContainer();

        // Add overlay to the diagram
        overlays.add(rootElement.id, {position: {top, left}, html: container});
        console.debug("Sankey overlay positioned at:", {top, left, imgWidth, containerHeight});

        /**
         * Loads required D3 and Sankey libraries
         */
        const loadDependencies = () => {
            return new Promise((resolve) => {
                const loadD3 = () => {
                    if (typeof d3 !== "undefined") return Promise.resolve();

                    return new Promise(resolveD3 => {
                        const script = document.createElement("script");
                        script.src = "https://d3js.org/d3.v7.min.js";
                        script.onload = resolveD3;
                        document.head.appendChild(script);
                    });
                };

                loadD3().then(() => {
                    if (typeof d3.sankey === "function") return resolve();

                    const script = document.createElement("script");
                    script.src = "https://cdn.jsdelivr.net/npm/d3-sankey@0.12.3";
                    script.onload = resolve;
                    document.head.appendChild(script);
                });
            });
        };

        // Initialize the Sankey diagram after loading dependencies
        loadDependencies().then(async () => {
            try {
                console.debug("Fetching Sankey data...");
                const response = await fetch("/api/sankey");
                if (!response.ok) {
                    throw new Error(`Failed to fetch Sankey data: ${response.status}`);
                }
                const sankeyData = await response.json();

                // Validate the data
                if (!sankeyData?.nodes?.length || !sankeyData?.links?.length) {
                    console.error("Invalid Sankey data: missing nodes or links");
                    return;
                }

                renderSankey(container, imgWidth, imgHeight, sankeyData);
            } catch (error) {
                console.error("Error loading Sankey data:", error);
            }
        });


        /**
         * Renders the Sankey diagram with the provided data
         * @param {HTMLElement} container - The container element
         * @param {number} imgWidth - Width of the diagram
         * @param {number} imgHeight - Height of the diagram
         * @param {Object} sankeyData - Data for the Sankey diagram
         */
        const renderSankey = (container, imgWidth, imgHeight, sankeyData) => {
            d3.select(".sankey-diagram").selectAll("*").remove();

            // Set up SVG and main group
            const svg = d3.select(container).select("svg");
            const g = svg.select("g.sankey-diagram");

            // Initialize Sankey layout
            const sankey = d3.sankey()
                .nodeId(d => d.id)
                .linkSort()
                .nodeWidth(15)
                .nodePadding(10)
                .size([imgWidth, imgHeight]);

            // Process nodes and links
            const {nodes, links} = sankey({
                nodes: sankeyData.nodes.map(node => ({
                    ...node,
                    // Add any node transformations here
                })),
                links: sankeyData.links.map(link => ({
                    source: link.source,
                    target: link.target,
                    value: link.value,
                    material: link.material,
                    unit: link.unit  // Remove default fallback to handle null/undefined properly
                }))
            });

            // Adjust node heights for better visualization
            nodes.forEach(node => {
                const isMiddleNode = links.some(link => link.source.id === node.id) &&
                    links.some(link => link.target.id === node.id);
                if (isMiddleNode) {
                    node.height = 80; // Fixed height for middle nodes
                }
            });

            console.debug("BPMN task IDs:", taskPositions.map(t => t.id));
            console.debug("Sankey node IDs:", nodes.map(n => n.id));

            const mapTaskToNode = (taskId, taskPositions, nodes) => {
                const taskPos = taskPositions.find(t => t.id === taskId);
                const node = nodes.find(n => n.id === taskId);

                if (taskPos && node) {
                    // Update node position to match BPMN task
                    node.x0 = taskPos.x;
                    node.x1 = taskPos.x + taskPos.width;
                    node.y0 = taskPos.y;
                    node.y1 = taskPos.y + taskPos.height;
                } else {
                    console.warn(`Mapping failed for Task_ID '${taskId}': Task or Node not found`);
                }
            };

            // Map all BPMN tasks to their corresponding nodes
            taskPositions.forEach(task => mapTaskToNode(task.id, taskPositions, nodes));

            /**
             * Generates a custom SVG path for Sankey links
             * @param {Object} d - Link data object
             * @returns {string} SVG path string
             */
            const customLinkPath = (d) => {
                // Source coordinates
                const sourceX = d.source.x1;
                const sourceYStart = d.source.y0;
                const sourceHeight = d.source.y1 - d.source.y0;

                // Target coordinates
                const targetX = d.target.x0;
                const targetYStart = d.target.y0;
                const targetHeight = d.target.y1 - d.target.y0;

                // Find related links
                const outgoingLinks = links.filter(link => link.source.id === d.source.id);
                const incomingLinks = links.filter(link => link.target.id === d.target.id);

                // Calculate total flow values
                const totalIncomingValue = incomingLinks.reduce((sum, link) => sum + link.value, 0);
                const totalOutgoingValue = outgoingLinks.reduce((sum, link) => sum + link.value, 0);

                // Scale link heights to fit within node height
                const scalingFactor = totalIncomingValue > 0 ? targetHeight / totalIncomingValue : 1;

                // Calculate scaled heights for incoming links
                incomingLinks.forEach(link => {
                    link.scaledHeight = link.value * scalingFactor;
                });

                // Calculate link positions
                const sourceIndex = outgoingLinks.indexOf(d);
                const targetIndex = incomingLinks.indexOf(d);
                const sourceSpacing = sourceHeight / Math.max(1, outgoingLinks.length);
                const targetYOffset = incomingLinks
                    .slice(0, targetIndex)
                    .reduce((sum, link) => sum + (link.scaledHeight || 0), 0);

                // Calculate source and target Y positions
                const sourceY = sourceYStart + (sourceSpacing * sourceIndex) + (sourceSpacing / 2);
                const targetY = targetYStart + targetYOffset + ((d.scaledHeight || 0) / 2);

                // Calculate curve control points for smooth arcs
                const curveStartX = targetX - 200; // Start curve 200px before target
                const curveTightness = 0.3; // Adjust curve tightness
                const controlX1 = curveStartX;
                const controlX2 = curveStartX + (200 * curveTightness);

                // Generate SVG path with Bezier curves
                return `M ${sourceX},${sourceY}
                        C ${controlX1},${sourceY}
                          ${controlX2},${targetY}
                          ${targetX},${targetY}`;
            }

            // Calculate flow metrics for visualization
            const calculateFlowMetrics = () => {
                const warehouseLinks = links.filter(link => !links.some(l => l.target.id === link.source.id));
                const maxWarehouseValue = warehouseLinks.length ? Math.max(...warehouseLinks.map(link => link.value)) : 0;
                const totalWarehouseFlow = warehouseLinks.reduce((sum, link) => sum + link.value, 0);

                // Debug logging
                console.debug("Flow metrics:", {maxWarehouseValue, totalWarehouseFlow});

                return {maxWarehouseValue, totalWarehouseFlow};
            };

            const {maxWarehouseValue, totalWarehouseFlow} = calculateFlowMetrics();


            /**
             * Calculates the width of a link based on its value and context
             * @param {Object} link - The link data object
             * @returns {number} The calculated width in pixels
             */
            const calculateLinkWidth = (link) => {
                const maxAllowed = link.target.y1 - link.target.y0;
                const isWarehouse = !links.some(l => l.target.id === link.source.id);

                let computedWidth;

                if (isWarehouse) {
                    // Scale warehouse links based on their proportion of total flow
                    const proportion = maxWarehouseValue > 0 ? (link.value / maxWarehouseValue) : 0;
                    computedWidth = proportion * ((maxWarehouseValue / totalWarehouseFlow) * 100);
                } else {
                    // Scale process links based on their proportion of incoming flow
                    const incomingLinks = links.filter(l => l.target.id === link.target.id);
                    const totalIncomingValue = incomingLinks.reduce((sum, l) => sum + l.value, 0);
                    const scalingFactor = totalIncomingValue > 0 ? maxAllowed / totalIncomingValue : 1;
                    computedWidth = link.value * scalingFactor;
                }

                // Apply minimum and maximum width constraints
                return Math.max(PLUGIN_CONFIG.STYLES.link.minStrokeWidth,
                    Math.min(computedWidth, maxAllowed));
            };

            // Draw the link paths
            const linkPaths = g.append("g")
                .selectAll("path")
                .data(links)
                .enter()
                .append("path")
                .attr("d", customLinkPath)
                .attr("id", (_, i) => `linkPath${i}`)
                .style("fill", "none")
                .style("stroke", d => d3.color(d.source.color).darker(1))
                .style("stroke-opacity", PLUGIN_CONFIG.STYLES.link.strokeOpacity)
                .style("stroke-width", calculateLinkWidth);

            nodes.forEach(node => {
                const isMiddleNode = links.some(link => link.source.id === node.id) && links.some(link => link.target.id === node.id);
                console.info(`Node "${node.id}" is a task:`, isMiddleNode);
            });

q
            /**
             * Renders the nodes of the Sankey diagram
             */
            const renderNodes = () => {
                g.append("g")
                    .selectAll("rect")
                    .data(nodes)
                    .enter()
                    .append("rect")
                    .attr("x", d => d.x0)
                    .attr("y", d => d.y0)
                    .attr("height", d => d.y1 - d.y0)
                    .attr("width", d => d.x1 - d.x0)
                    .style("fill", d => {
                        const isMiddleNode = taskPositions.some(task => task.id === d.id);
                        return isMiddleNode ? "none" : d.color; // Hide middle nodes
                    })
                    .style("stroke", d => {
                        const isMiddleNode = taskPositions.some(task => task.id === d.id);
                        return isMiddleNode ? "none" : "black";
                    });
            };

            // Render all nodes
            renderNodes();

            // Add link labels with matching colors and units
            linkPaths.append("title")
                .text(d => {
                    `${d.source.name} → ${d.target.name}\n${d.material}: ${d.value} ${d.unit || 'unit'}${d.value !== 1 ? 's' : ''}`
                });

            // Add text paths at the beginning of each link
            const linkTexts = g.append("g")
                .selectAll("text")
                .data(links)
                .enter()
                .append("text")
                .style("font-size", "16px")
                .style("font-weight", "bolder")
                .style("fill", d => d.source.color) // Match source node color
                .append("textPath")
                .attr("href", (d, i) => `#linkPath${i}`)
                .attr("startOffset", "2")  // Position at the start
                .style("text-anchor", "start")  // Align text to start
                .style("dominant-baseline", "middle")
                .style("paint-order", "stroke")
                .style("stroke", "white")
                .style("stroke-width", 2)
                .style("stroke-linejoin", "round")
                .attr("dy", "0.35em")  // Vertical alignment
                .text(d => {
                    return `${d.material}: ${d.value} ${d.unit || 'unit'}${d.value !== 1 ? 's' : ''}`;
                });

            // Add node labels (only for middle nodes)
            const nodeLabels = g.append("g")
                .selectAll("text")
                .data(nodes)
                .enter()
                .append("text")
                .filter(d => {
                    // Only show labels for nodes that are neither start nor end nodes
                    const isStartNode = !links.some(link => link.target.id === d.id);
                    const isEndNode = !links.some(link => link.source.id === d.id);
                    return !(isStartNode || isEndNode);
                })
                .attr("x", d => d.x0 - 5) // Position to the left of the node
                .attr("y", d => (d.y0 + d.y1) / 2)
                .attr("dy", "0.35em")
                .attr("text-anchor", "end")
                .text(d => d.name)
                .style("font-size", PLUGIN_CONFIG.STYLES.nodeLabel.fontSize);

            // Bring all rectangles to front
            d3.selectAll(".sankey-diagram rect").raise();

            console.debug("Sankey diagram rendered successfully!");

        }
    }
};
