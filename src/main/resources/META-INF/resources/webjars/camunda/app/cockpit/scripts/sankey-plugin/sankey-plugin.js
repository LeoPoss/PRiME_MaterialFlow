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

        const overlays = viewer.get("overlays");
        const canvas = viewer.get("canvas");
        const elementRegistry = viewer.get("elementRegistry");
        const rootElement = canvas.getRootElement();
        const rootBBox = canvas.getGraphics(rootElement).getBBox();

        const tasks = elementRegistry.filter(el => PLUGIN_CONFIG.TASK_TYPES.includes(el.type));
        const textAnnotations = elementRegistry.filter(el => el.type === 'bpmn:TextAnnotation' && el.id.toLowerCase().includes('resourcerequirementannotation'));
        const dataObjectRefs = elementRegistry.filter(el => el.type === 'bpmn:DataObjectReference' && el.id.toLowerCase().includes('resource'));

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

        // Add Resource Requirement Annotation Icon
        textAnnotations.forEach(annotation => {
            const iconSize = 24;
            const x = annotation.x - rootBBox.x - iconSize - 4;
            const y = annotation.y - rootBBox.y + imgHeight;

            const svgIcon = document.createElement('div');
            svgIcon.innerHTML = `
                <svg xmlns="http://www.w3.org/2000/svg" 
                     width="${iconSize}px" 
                     height="${iconSize}px" 
                     viewBox="0 0 24.78 27.53"
                     style="position: absolute; left: ${x}px; top: ${y}px; pointer-events: none; z-index: 1001;">
                    <path d="M200-120c-22 0-40.83-7.83-56.5-23.5C127.83-159.17 120-178 120-200v-560c0-22 7.83-40.83 23.5-56.5C159.17-832.17 178-840 200-840h168c8.67-24 23.17-43.33 43.5-58 20.33-14.67 43.17-22 68.5-22s48.17 7.33 68.5 22 34.83 34 43.5 58h168c22 0 40.83 7.83 56.5 23.5C832.17-800.83 840-782 840-760v560c0 22-7.83 40.83-23.5 56.5C800.83-127.83 782-120 760-120zm280-670c8.67 0 15.83-2.83 21.5-8.5s8.5-12.83 8.5-21.5-2.83-15.83-8.5-21.5-12.83-8.5-21.5-8.5-15.83 2.83-21.5 8.5-8.5 12.83-8.5 21.5 2.83 15.83 8.5 21.5 12.83 8.5 21.5 8.5z" style="fill-rule:nonzero" transform="matrix(.03441 0 0 .03441 -4.13 31.66)"/>
                    <g style="fill:#fff;fill-opacity:1">
                        <path d="M200-160v50c0 11.33-3.83 20.83-11.5 28.5C180.83-73.83 171.33-70 160-70h-40c-11.33 0-20.83-3.83-28.5-11.5C83.83-89.17 80-98.67 80-110v-90c0-11.33 3.83-20.83 11.5-28.5 7.67-7.67 17.17-11.5 28.5-11.5h720c11.33 0 20.83 3.83 28.5 11.5 7.67 7.67 11.5 17.17 11.5 28.5v90c0 11.33-3.83 20.83-11.5 28.5C860.83-73.83 851.33-70 840-70h-40c-11.33 0-20.83-3.83-28.5-11.5-7.67-7.67-11.5-17.17-11.5-28.5v-50H540v50c0 11.33-3.83 20.83-11.5 28.5C520.83-73.83 511.33-70 500-70h-40c-11.33 0-20.83-3.83-28.5-11.5-7.67-7.67-11.5-17.17-11.5-28.5v-50zm40-160c-11.33 0-20.83-3.83-28.5-11.5-7.67-7.67-11.5-17.17-11.5-28.5v-480c0-11.33 3.83-20.83 11.5-28.5 7.67-7.67 17.17-11.5 28.5-11.5h480c11.33 0 20.83 3.83 28.5 11.5 7.67 7.67 11.5 17.17 11.5 28.5v480c0 11.33-3.83 20.83-11.5 28.5-7.67 7.67-17.17 11.5-28.5 11.5zm320-320c11.33 0 20.83-3.83 28.5-11.5 7.67-7.67 11.5-17.17 11.5-28.5 0-11.33-3.83-20.83-11.5-28.5-7.67-7.67-17.17-11.5-28.5-11.5H400c-11.33 0-20.83 3.83-28.5 11.5-7.67 7.67-11.5 17.17-11.5 28.5 0 11.33 3.83 20.83 11.5 28.5 7.67 7.67 17.17 11.5 28.5 11.5z" style="fill:#fff;fill-opacity:1;fill-rule:nonzero" transform="translate(11.23 26.82) scale(.01378)"/>
                    </g>
                    <g style="fill:#fff;fill-opacity:1">
                        <path d="M714-162 537-339l84-84 177 177c11.33 11.33 17 25.33 17 42s-5.67 30.67-17 42c-11.33 11.33-25.33 17-42 17s-30.67-5.67-42-17zm-552 0c-11.33-11.33-17-25.33-17-42s5.67-30.67 17-42l234-234-68-68c-7.33 7.33-16.67 11-28 11-11.33 0-20.67-3.67-28-11l-23-23v90c0 9.33-4 15.67-12 19s-15.33 1.67-22-5L106-576c-6.67-6.67-8.33-14-5-22s9.67-12 19-12h90l-22-22c-8-8-12-17.33-12-28 0-10.67 4-20 12-28l114-114c13.33-13.33 27.67-23 43-29 15.33-6 31-9 47-9 13.33 0 25.83 2 37.5 6 11.67 4 23.17 10 34.5 18 5.33 3.33 8.17 8 8.5 14 .33 6-1.83 11.33-6.5 16l-76 76 22 22c7.33 7.33 11 16.67 11 28 0 11.33-3.67 20.67-11 28l68 68 90-90c-2.67-7.33-4.83-15-6.5-23s-2.5-16-2.5-24c0-39.33 13.5-72.5 40.5-99.5S661.67-841 701-841c5.33 0 10.33.17 15 .5 4.67.33 9.33 1.17 14 2.5 6 2 9.83 6.17 11.5 12.5s.17 11.83-4.5 16.5l-65 65c-4 4-6 8.67-6 14s2 10 6 14l44 44c4 4 8.67 6 14 6s10-2 14-6l65-65c4.67-4.67 10.17-6.33 16.5-5s10.5 5.33 12.5 12a68.66 68.66 0 0 1 2.5 14c.33 4.67.5 9.67.5 15 0 39.33-13.5 72.5-40.5 99.5S740.33-561 701-561c-8 0-16-.67-24-2a91.81 91.81 0 0 1-23-7L246-162c-11.33 11.33-25.33 17-42 17s-30.67-5.67-42-17z" style="fill:#fff;fill-opacity:1;fill-rule:nonzero" transform="translate(.14 16.5) scale(.01378)"/>
                    </g>
                </svg>
            `;

            container.appendChild(svgIcon);
            console.debug(`Added SVG icon for TextAnnotation at (${annotation.x}, ${annotation.y})`);
        });

        // Add Resource Requirement Collection Icon
        dataObjectRefs.forEach(ref => {
            const {x, y, width, height} = ref;
            const iconSize = 24;
            const iconX = x - rootBBox.x + (width - iconSize) / 2;
            const iconY = y - rootBBox.y + imgHeight - 6 + (height - iconSize) / 2;

            const svgIcon = document.createElement('div');
            if (ref.id.toLowerCase().includes("inventory")) {
                svgIcon.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="${iconSize}px" height="${iconSize}px" viewBox="0 0 24.78 27.53" style="position: absolute; left: ${iconX}px; top: ${iconY}px; pointer-events: none; z-index: 1001;"><path d="M200-160v50c0 11.333-3.833 20.833-11.5 28.5C180.833-73.833 171.333-70 160-70h-40c-11.333 0-20.833-3.833-28.5-11.5C83.833-89.167 80-98.667 80-110v-90c0-11.333 3.833-20.833 11.5-28.5 7.667-7.667 17.167-11.5 28.5-11.5h720c11.333 0 20.833 3.833 28.5 11.5 7.667 7.667 11.5 17.167 11.5 28.5v90c0 11.333-3.833 20.833-11.5 28.5C860.833-73.833 851.333-70 840-70h-40c-11.333 0-20.833-3.833-28.5-11.5-7.667-7.667-11.5-17.167-11.5-28.5v-50H540v50c0 11.333-3.833 20.833-11.5 28.5C520.833-73.833 511.333-70 500-70h-40c-11.333 0-20.833-3.833-28.5-11.5-7.667-7.667-11.5-17.167-11.5-28.5v-50zm40-160c-11.333 0-20.833-3.833-28.5-11.5-7.667-7.667-11.5-17.167-11.5-28.5v-480c0-11.333 3.833-20.833 11.5-28.5 7.667-7.667 17.167-11.5 28.5-11.5h480c11.333 0 20.833 3.833 28.5 11.5 7.667 7.667 11.5 17.167 11.5 28.5v480c0 11.333-3.833 20.833-11.5 28.5-7.667 7.667-17.167 11.5-28.5 11.5zm320-320c11.333 0 20.833-3.833 28.5-11.5 7.667-7.667 11.5-17.167 11.5-28.5 0-11.333-3.833-20.833-11.5-28.5-7.667-7.667-17.167-11.5-28.5-11.5H400c-11.333 0-20.833 3.833-28.5 11.5-7.667 7.667-11.5 17.167-11.5 28.5 0 11.333 3.833 20.833 11.5 28.5 7.667 7.667 17.167 11.5 28.5 11.5z" style="fill-rule:nonzero" transform="translate(12.204 20.705) scale(.01378)"/><path d="M714-162 537-339l84-84 177 177c11.333 11.333 17 25.333 17 42s-5.667 30.667-17 42c-11.333 11.333-25.333 17-42 17s-30.667-5.667-42-17zm-552 0c-11.333-11.333-17-25.333-17-42s5.667-30.667 17-42l234-234-68-68c-7.333 7.333-16.667 11-28 11-11.333 0-20.667-3.667-28-11l-23-23v90c0 9.333-4 15.667-12 19s-15.333 1.667-22-5L106-576c-6.667-6.667-8.333-14-5-22s9.667-12 19-12h90l-22-22c-8-8-12-17.333-12-28 0-10.667 4-20 12-28l114-114c13.333-13.333 27.667-23 43-29 15.333-6 31-9 47-9 13.333 0 25.833 2 37.5 6 11.667 4 23.167 10 34.5 18 5.333 3.333 8.167 8 8.5 14 .333 6-1.833 11.333-6.5 16l-76 76 22 22c7.333 7.333 11 16.667 11 28 0 11.333-3.667 20.667-11 28l68 68 90-90c-2.667-7.333-4.833-15-6.5-23-1.667-8-2.5-16-2.5-24 0-39.333 13.5-72.5 40.5-99.5S661.667-841 701-841c5.333 0 10.333.167 15 .5 4.667.333 9.333 1.167 14 2.5 6 2 9.833 6.167 11.5 12.5 1.667 6.333.167 11.833-4.5 16.5l-65 65c-4 4-6 8.667-6 14s2 10 6 14l44 44c4 4 8.667 6 14 6s10-2 14-6l65-65c4.667-4.667 10.167-6.333 16.5-5 6.333 1.333 10.5 5.333 12.5 12 1.333 4.667 2.167 9.333 2.5 14 .333 4.667.5 9.667.5 15 0 39.333-13.5 72.5-40.5 99.5S740.333-561 701-561c-8 0-16-.667-24-2a91.812 91.812 0 0 1-23-7L246-162c-11.333 11.333-25.333 17-42 17s-30.667-5.667-42-17z" style="fill-rule:nonzero" transform="translate(-.737 20.705) scale(.01378)"/></svg>`;
            } else {
                svgIcon.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="${iconSize}px" height="${iconSize}px" viewBox="0 0 24.78 27.53" style="position: absolute; left: ${iconX}px; top: ${iconY}px; pointer-events: none; z-index: 1001;"> <path d="M200-120c-22 0-40.83-7.83-56.5-23.5C127.83-159.17 120-178 120-200v-560c0-22 7.83-40.83 23.5-56.5C159.17-832.17 178-840 200-840h168c8.67-24 23.17-43.33 43.5-58 20.33-14.67 43.17-22 68.5-22s48.17 7.33 68.5 22 34.83 34 43.5 58h168c22 0 40.83 7.83 56.5 23.5C832.17-800.83 840-782 840-760v560c0 22-7.83 40.83-23.5 56.5C800.83-127.83 782-120 760-120zm280-670c8.67 0 15.83-2.83 21.5-8.5s8.5-12.83 8.5-21.5-2.83-15.83-8.5-21.5-12.83-8.5-21.5-8.5-15.83 2.83-21.5 8.5-8.5 12.83-8.5 21.5 2.83 15.83 8.5 21.5 12.83 8.5 21.5 8.5z" style="fill-rule:nonzero" transform="matrix(.03441 0 0 .03441 -4.13 31.66)"/> <g style="fill:#fff;fill-opacity:1"> <path d="M200-160v50c0 11.33-3.83 20.83-11.5 28.5C180.83-73.83 171.33-70 160-70h-40c-11.33 0-20.83-3.83-28.5-11.5C83.83-89.17 80-98.67 80-110v-90c0-11.33 3.83-20.83 11.5-28.5 7.67-7.67 17.17-11.5 28.5-11.5h720c11.33 0 20.83 3.83 28.5 11.5 7.67 7.67 11.5 17.17 11.5 28.5v90c0 11.33-3.83 20.83-11.5 28.5C860.83-73.83 851.33-70 840-70h-40c-11.33 0-20.83-3.83-28.5-11.5-7.67-7.67-11.5-17.17-11.5-28.5v-50H540v50c0 11.33-3.83 20.83-11.5 28.5C520.83-73.83 511.33-70 500-70h-40c-11.33 0-20.83-3.83-28.5-11.5-7.67-7.67-11.5-17.17-11.5-28.5v-50zm40-160c-11.33 0-20.83-3.83-28.5-11.5-7.67-7.67-11.5-17.17-11.5-28.5v-480c0-11.33 3.83-20.83 11.5-28.5 7.67-7.67 17.17-11.5 28.5-11.5h480c11.33 0 20.83 3.83 28.5 11.5 7.67 7.67 11.5 17.17 11.5 28.5v480c0 11.33-3.83 20.83-11.5 28.5-7.67 7.67-17.17 11.5-28.5 11.5zm320-320c11.33 0 20.83-3.83 28.5-11.5 7.67-7.67 11.5-17.17 11.5-28.5 0-11.33-3.83-20.83-11.5-28.5-7.67-7.67-17.17-11.5-28.5-11.5H400c-11.33 0-20.83 3.83-28.5 11.5-7.67 7.67-11.5 17.17-11.5 28.5 0 11.33 3.83 20.83 11.5 28.5 7.67 7.67 17.17 11.5 28.5 11.5z" style="fill:#fff;fill-opacity:1;fill-rule:nonzero" transform="translate(11.23 26.82) scale(.01378)"/> </g> <g style="fill:#fff;fill-opacity:1"> <path d="M714-162 537-339l84-84 177 177c11.33 11.33 17 25.33 17 42s-5.67 30.67-17 42c-11.33 11.33-25.33 17-42 17s-30.67-5.67-42-17zm-552 0c-11.33-11.33-17-25.33-17-42s5.67-30.67 17-42l234-234-68-68c-7.33 7.33-16.67 11-28 11-11.33 0-20.67-3.67-28-11l-23-23v90c0 9.33-4 15.67-12 19s-15.33 1.67-22-5L106-576c-6.67-6.67-8.33-14-5-22s9.67-12 19-12h90l-22-22c-8-8-12-17.33-12-28 0-10.67 4-20 12-28l114-114c13.33-13.33 27.67-23 43-29 15.33-6 31-9 47-9 13.33 0 25.83 2 37.5 6 11.67 4 23.17 10 34.5 18 5.33 3.33 8.17 8 8.5 14 .33 6-1.83 11.33-6.5 16l-76 76 22 22c7.33 7.33 11 16.67 11 28 0 11.33-3.67 20.67-11 28l68 68 90-90c-2.67-7.33-4.83-15-6.5-23s-2.5-16-2.5-24c0-39.33 13.5-72.5 40.5-99.5S661.67-841 701-841c5.33 0 10.33.17 15 .5 4.67.33 9.33 1.17 14 2.5 6 2 9.83 6.17 11.5 12.5s.17 11.83-4.5 16.5l-65 65c-4 4-6 8.67-6 14s2 10 6 14l44 44c4 4 8.67 6 14 6s10-2 14-6l65-65c4.67-4.67 10.17-6.33 16.5-5s10.5 5.33 12.5 12a68.66 68.66 0 0 1 2.5 14c.33 4.67.5 9.67.5 15 0 39.33-13.5 72.5-40.5 99.5S740.33-561 701-561c-8 0-16-.67-24-2a91.81 91.81 0 0 1-23-7L246-162c-11.33 11.33-25.33 17-42 17s-30.67-5.67-42-17z" style="fill:#fff;fill-opacity:1;fill-rule:nonzero" transform="translate(.14 16.5) scale(.01378)"/> </g> </svg>`;
            }

            // Add the SVG to the container
            container.appendChild(svgIcon);

            console.debug(`Added centered SVG icon for DataObjectReference at (${x}, ${y})`);
        });

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
                const response = await fetch(`/api/sankey/${rootElement.id}`);
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
