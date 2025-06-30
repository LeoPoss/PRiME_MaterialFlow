const PLUGIN_CONFIG = {
    id: "sankeyOverlay",
    pluginPoint: "cockpit.processDefinition.diagram.plugin",
    priority: 0,
    TASK_TYPES: ["bpmn:Task", "bpmn:ManualTask", "bpmn:UserTask", "bpmn:ServiceTask", "bpmn:ScriptTask", "bpmn:BusinessRuleTask"],

    CSS_VARIABLES: {
        text: {
            default: 'var(--sankey-text-default)',
            materials: [
                'var(--sankey-text-1)',
                'var(--sankey-text-2)',
                'var(--sankey-text-3)',
                'var(--sankey-text-4)',
                'var(--sankey-text-5)',
                'var(--sankey-text-6)',
                'var(--sankey-text-7)'
            ]
        },
        link: {
            default: 'var(--sankey-link-default)',
            materials: [
                'var(--sankey-material-1)',
                'var(--sankey-material-2)',
                'var(--sankey-material-3)',
                'var(--sankey-material-4)',
                'var(--sankey-material-5)',
                'var(--sankey-material-6)',
                'var(--sankey-material-7)'
            ]
        }
    },

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
        const imgHeight = (tasks.length * 60);
        const containerHeight = imgHeight + rootBBox.height + 200;
        const top = rootBBox.y - imgHeight;
        const left = rootBBox.x;

        /**
         * Extracts and maps task positions from BPMN elements
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
         */
        const createSankeyContainer = () => {
            const container = document.createElement("div");
            Object.assign(container.style, PLUGIN_CONFIG.STYLES.container, {
                width: `${imgWidth}px`, height: `${containerHeight}px`
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

                // "Validate" the data
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
         */
        const renderSankey = (container, imgWidth, imgHeight, sankeyData) => {
            d3.select(".sankey-diagram").selectAll("*").remove();

            // Set up SVG and main group with null checks
            const svg = d3.select(container).select("svg");
            if (svg.empty()) {
                console.error("SVG container not found");
                return;
            }

            let g = svg.select("g.sankey-diagram");
            if (g.empty()) {
                g = svg.append("g").attr("class", "sankey-diagram");
            }

            // Initialize Sankey layout
            const sankey = d3.sankey()
                .nodeId(d => d.id)
                .nodeWidth(15)
                .nodePadding(10)
                .size([imgWidth, imgHeight])
                .nodeSort(null);

            // Process nodes and links
            const {nodes, links} = sankey({
                nodes: sankeyData.nodes.map(node => {
                    const type = node.type || 'default';
                    return {
                        ...node,
                        type: type,
                        color: PLUGIN_CONFIG.CSS_VARIABLES.link.default,
                        textColor: type === 'task' ? "none" : PLUGIN_CONFIG.CSS_VARIABLES.text.default
                    };
                }),
                links: sankeyData.links.map(link => ({
                    ...link
                }))
            });

            // Assign material colors to material types
            const materialTypes = [...new Set(nodes
                .filter(node => !['task', 'endEvent'].includes(node.type))
                .map(node => node.type))];

            materialTypes.forEach((type, index) => {
                const colorIndex = index % PLUGIN_CONFIG.CSS_VARIABLES.link.materials.length;
                nodes.forEach(node => {
                    if (node.type === type) {
                        node.textColor = PLUGIN_CONFIG.CSS_VARIABLES.text.materials[colorIndex];
                        node.color = PLUGIN_CONFIG.CSS_VARIABLES.link.materials[colorIndex];
                    }
                });
            });

            // First, identify all start nodes
            const startNodes = nodes.filter(node =>
                links.some(link => link.source.id === node.id) &&
                !links.some(link => link.target.id === node.id)
            );

            // Sort start nodes by their original Y position
            startNodes.sort((a, b) => a.y0 - b.y0);

            // Adjust middle nodes
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
                const {source, target} = d;
                const {x1: sourceX, y0: sourceY0, y1: sourceY1} = source;
                const {x0: targetX, y0: targetY0, y1: targetY1} = target;

                // Find related links
                const outgoingLinks = links.filter(l => l.source.id === source.id);
                const incomingLinks = links.filter(l => l.target.id === target.id);

                // Calculate positions
                const sourceY = sourceY0 + ((outgoingLinks.indexOf(d) + 0.5) * (sourceY1 - sourceY0)) / outgoingLinks.length;
                const targetY = incomingLinks.slice(0, incomingLinks.indexOf(d))
                        .reduce((sum, link) => sum + (link.value / incomingLinks.reduce((s, l) => s + l.value, 0) * (targetY1 - targetY0)), 0) +
                    (d.value / (2 * incomingLinks.reduce((s, l) => s + l.value, 0)) * (targetY1 - targetY0)) + targetY0;

                // Handle different link types
                if (target.id === 'endEvent') {
                    const midX = (sourceX + targetX) / 2;
                    return `M ${sourceX},${sourceY} C ${midX},${sourceY} ${midX},${targetY} ${targetX},${targetY}`;
                }

                const isMiddleNode = links.some(l => l.target.id === source.id) && links.some(l => l.source.id === source.id);
                if (isMiddleNode) {
                    const midX = sourceX + 100;
                    return `M ${sourceX},${sourceY} C ${midX},${sourceY} ${midX},${targetY} ${targetX},${targetY}`;
                }

                // Default curve for other links
                const controlX1 = targetX - 200;
                const controlX2 = controlX1 + 30;
                return `M ${sourceX},${sourceY} C ${controlX1},${sourceY} ${controlX2},${targetY} ${targetX},${targetY}`;
            }

            // Calculate flow metrics for visualization
            const {maxWarehouseValue, totalWarehouseFlow} = (() => {
                const warehouseLinks = links.filter(link => !links.some(l => l.target.id === link.source.id));
                const maxValue = warehouseLinks.length ? Math.max(...warehouseLinks.map(l => l.value)) : 0;
                const totalFlow = warehouseLinks.reduce((sum, link) => sum + link.value, 0);
                console.debug("Flow metrics:", {maxValue, totalFlow});
                return {maxWarehouseValue: maxValue, totalWarehouseFlow: totalFlow};
            })();

            /**
             * Calculates link width based on flow value and context
             * @param {Object} link - Link data object
             * @returns {number} Calculated width in pixels
             */
            const calculateLinkWidth = (link) => {
                const maxAllowed = link.target.y1 - link.target.y0;
                const isWarehouse = !links.some(l => l.target.id === link.source.id);

                const computedWidth = isWarehouse
                    ? (link.value / maxWarehouseValue) * ((maxWarehouseValue / totalWarehouseFlow) * 100)
                    : link.value * (maxAllowed / links.filter(l => l.target.id === link.target.id)
                    .reduce((sum, l) => sum + l.value, 0));

                return Math.max(PLUGIN_CONFIG.STYLES.link.minStrokeWidth,
                    Math.min(computedWidth, maxAllowed));
            };

            // Draw link paths
            if (!g.node()) {
                console.error("SVG group not properly initialized");
                return;
            }

            // Add a class to the main SVG for better CSS targeting
            svg.attr('class', 'sankey-diagram');

            const linkGroup = g.append("g").attr('class', 'sankey-links');
            const linkPaths = linkGroup.selectAll("path")
                .data(links)
                .enter()
                .append("path")
                .attr("d", customLinkPath)
                .attr("id", (_, i) => `linkPath${i}`)
                .attr("class", "sankey-link")
                .style("stroke", d => d.source.color)
                .style("stroke-width", calculateLinkWidth);

            // Render nodes
            const nodeGroup = g.append("g").attr('class', 'sankey-nodes');
            nodeGroup.selectAll("rect")
                .data(nodes)
                .enter()
                .append("rect")
                .attr("x", d => d.x0)
                .attr("y", d => d.y0)
                .attr("height", d => d.y1 - d.y0)
                .attr("width", d => d.x1 - d.x0)
                .attr("class", d => `sankey-node ${d.type.toLowerCase()}`)
                .style("fill", d => d.textColor)
                .style("stroke", "none");

            // Add link labels
            const textGroup = g.append("g").attr('class', 'sankey-labels');
            textGroup.selectAll("text")
                .data(links)
                .enter()
                .append("text")
                .attr("class", "sankey-label")
                .style("font-size", "16px")
                .style("font-weight", "bolder")
                .each(function (d) {
                    d3.select(this)
                        .style("paint-order", "stroke")
                        .style("stroke", "white")
                        .style("stroke-width", 2)
                        .style("stroke-linecap", "round")
                        .style("stroke-linejoin", "round");
                })
                .append("textPath")
                .attr("href", (_, i) => `#linkPath${i}`)
                .attr("startOffset", "2")
                .attr("dy", "0.35em")
                .style("text-anchor", "start")
                .style("dominant-baseline", "middle")
                .style("fill", d => d.source.type === "task" ? PLUGIN_CONFIG.CSS_VARIABLES.text.default : d.source.textColor)
                .text(d => `${d.value} ${d.unit || '×'} ${d.material}`);

            // Ensure nodes are rendered above links
            d3.selectAll(".sankey-diagram rect").raise();
        }
    }
};
