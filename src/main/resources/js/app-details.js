// set up SVG for D3
var width  = $('#workspace').width(),
    height = $('#workspace').height(),
    invalid_color = '#b66',
    valid_color = '#6b6',
    init_color = '#cc3',
    disabled_color = '#bbb',
    node_size = 40,
    editting_plugin = false;

var svg = d3.select('#workspace')
    .append('svg')
    .attr('oncontextmenu', 'return false;')
    .attr('width', width)
    .attr('height', height);

var contextMenu = [
    {
        title: 'Add New Plugin',
        action: function(data, index) {
            addnode(this);
        }
    },{
        title: 'Preview CADL',
        action: function(data, index) {
            previewCADL();
        }
    }
];

var bg = svg.append('svg:rect')
    .attr('class', 'zoom')
    .attr('x', 0)
    .attr('y', 0)
    .attr('width', 1000)
    .attr('height', 1000)
    .style('opacity', 0.0)
    .on('contextmenu', d3.contextMenu(contextMenu, {
        theme: function () {
            return 'd3-context-menu-theme';
        },
        onOpen: function (data, index) { },
        onClose: function (data, index) { },
        position: function (data, index) {
            let elm = this;
            let bounds = elm.getBoundingClientRect();
            if (data === 'green') {
                // first circle will have the menu aligned top-right
                return {
                    left: bounds.left + bounds.width + 10,
                    top: bounds.top
                }
            }
        }
    }))
    .on('mousedown', function(d) {
        if (active_menu != null)
            active_menu.hide();
        active_menu = null;
        active_menu_node = null;
        selected_node = null;
        selected_link = null;
        restart();
    });

// set up initial nodes and links
//  - nodes are known by 'id', not by index in array.
//  - reflexive edges are indicated on the node (as a bold black circle).
//  - links are always source < target; edge directions are set by 'left' and 'right'.
var nodes = [
        //{id: 0, reflexive: true, title: 'Plugin 1', params: { 'pluginname': 'cresco-container-plugin', 'jarfile': 'cresco-container-plugin-0.1.0.jar', 'version': '0.1.0.309ef0c78a20ad45fe6c4fe2d1cd9ffa8ab81289.2018-05-15T13:34:13Z', 'md5': 'ef1b94e8f44ffd0ccfbe0a6431a6b344'} },
        //{id: 1, reflexive: true, title: 'Plugin 2', params: { 'pluginname': 'cresco-container-plugin', 'jarfile': 'cresco-container-plugin-0.1.0.jar', 'version': '0.1.0.309ef0c78a20ad45fe6c4fe2d1cd9ffa8ab81289.2018-05-15T13:34:13Z', 'md5': 'ef1b94e8f44ffd0ccfbe0a6431a6b344' } },
        //{id: 2, reflexive: true, title: 'Plugin 3', params: { 'pluginname': 'cresco-container-plugin', 'jarfile': 'cresco-container-plugin-0.1.0.jar', 'version': '0.1.0.309ef0c78a20ad45fe6c4fe2d1cd9ffa8ab81289.2018-05-15T13:34:13Z', 'md5': 'ef1b94e8f44ffd0ccfbe0a6431a6b344' } }
    ],
    lastNodeId = -1,
    links = [
        //{source: nodes[0], target: nodes[1], left: true, right: true },
        //{source: nodes[1], target: nodes[2], left: true, right: true }
    ];

// init D3 force layout
var force = d3.layout.force()
    .nodes(nodes)
    .links(links)
    .size([width, height])
    .linkDistance(node_size * 4)
    .charge(-500)
    .on('tick', tick);

window.onresize = function() {
    width  = $('#workspace').width();
    height = $('#workspace').height();
    svg.attr('width', width);
    svg.attr('height', height);
    force.size([width, height]);
    restart();
};

// define arrow markers for graph links
svg.append('svg:defs').append('svg:marker')
    .attr('id', 'end-arrow')
    .attr('viewBox', '0 -5 10 10')
    .attr('refX', 6)
    .attr('markerWidth', 3)
    .attr('markerHeight', 3)
    .attr('orient', 'auto')
    .append('svg:path')
    .attr('d', 'M0,-5L10,0L0,5')
    .attr('fill', '#000');

svg.append('svg:defs').append('svg:marker')
    .attr('id', 'start-arrow')
    .attr('viewBox', '0 -5 10 10')
    .attr('refX', 4)
    .attr('markerWidth', 3)
    .attr('markerHeight', 3)
    .attr('orient', 'auto')
    .append('svg:path')
    .attr('d', 'M10,-5L0,0L10,5')
    .attr('fill', '#000');

var tooltip = d3.select('body')
    .append('div')
    .attr('id', 'node-tooltip');

// line displayed when dragging new nodes
var drag_line = svg.append('svg:path')
    .attr('class', 'link dragline hidden')
    .attr('d', 'M0,0L0,0');

// handles to link and node element groups
var path = svg.append('svg:g').selectAll('path'),
    circle = svg.append('svg:g').selectAll('g'),
    bg = svg.append('svg:rect').selectAll('rect');

// mouse event vars
var selected_node = null,
    selected_link = null,
    active_menu = null,
    active_menu_node = null,
    tooltip_show = null,
    mousedown_link = null,
    mousedown_node = null,
    mouseup_node = null;

function resetMouseVars() {
    mousedown_node = null;
    mouseup_node = null;
    mousedown_link = null;
}

// update force layout (called automatically each iteration)
function tick() {
    // draw directed edges with proper padding from node centers
    path.attr('d', function(d) {
        var deltaX = d.target.x - d.source.x,
            deltaY = d.target.y - d.source.y,
            dist = Math.sqrt(deltaX * deltaX + deltaY * deltaY),
            normX = deltaX / dist,
            normY = deltaY / dist,
            sourcePadding = d.left ? node_size + 5 : node_size,
            targetPadding = d.right ? node_size + 5 : node_size,
            sourceX = d.source.x + (sourcePadding * normX),
            sourceY = d.source.y + (sourcePadding * normY),
            targetX = d.target.x - (targetPadding * normX),
            targetY = d.target.y - (targetPadding * normY);
        return 'M' + sourceX + ',' + sourceY + 'L' + targetX + ',' + targetY;
    });

    circle.attr('transform', function(d) {
        return 'translate(' + d.x + ',' + d.y + ')';
    });
}

// update graph (called when needed)
function restart() {
    // path (link) group
    path = path.data(links);

    // update existing links
    path.classed('selected', function(d) { return d === selected_link; })
        .style('marker-start', function(d) { return d.left ? 'url(#start-arrow)' : ''; })
        .style('marker-end', function(d) { return d.right ? 'url(#end-arrow)' : ''; });


    // add new links
    path.enter().append('svg:path')
        .attr('class', 'link')
        .classed('selected', function(d) { return d === selected_link; })
        .style('marker-start', function(d) { return d.left ? 'url(#start-arrow)' : ''; })
        .style('marker-end', function(d) { return d.right ? 'url(#end-arrow)' : ''; })
        .on('mousedown', function(d) {
            if(d3.event.ctrlKey) return;

            // select link
            mousedown_link = d;
            if(mousedown_link === selected_link) selected_link = null;
            else selected_link = mousedown_link;
            selected_node = null;
            restart();
        });

    // remove old links
    path.exit().remove();

    // circle (node) group
    // NB: the function arg is crucial here! nodes are known by id, not by index!
    circle = circle.data(nodes, function(d) { return d.id; });

    // update existing nodes (reflexive & selected visual states)
    circle.selectAll('circle')
        .style('fill', function(d) { return (checknode(d)); })
        .classed('reflexive', function(d) { return d.reflexive; });

    // add new nodes
    var g = circle.enter().append('svg:g');

    g.append('svg:circle')
        .attr('class', 'node')
        .attr('r', node_size)
        .style('fill', function(d) { return (checknode(d)) ? valid_color : invalid_color })
        .style('stroke', function(d) { return '#000' })
        .classed('reflexive', function(d) { return d.reflexive; })
        .on('mousemove', function(d) {
            tooltip.style("top", (event.pageY-10)+"px").style("left",(event.pageX+10)+"px");
        })
        .on('mouseover', function(d) {
            tooltip.html(generateTooltip(d));
            tooltip_show = setTimeout(function() {
                tooltip.style("visibility", "visible");
            }, 600);
            if(!mousedown_node || d === mousedown_node) return;
            // enlarge target node
            d3.select(this).attr('transform', 'scale(1.1)');
        })
        .on('mouseout', function(d) {
            clearTimeout(tooltip_show);
            tooltip.style("visibility", "hidden");
            if(!mousedown_node || d === mousedown_node) return;
            // unenlarge target node
            d3.select(this).attr('transform', '');
        })
        .on('mousedown', function(d) {
            clearTimeout(tooltip_show);
            tooltip_show = setTimeout(function() {
                tooltip.style("visibility", "visible");
            }, 2000);
            if(d3.event.ctrlKey) return;

            if (active_menu != null)
                active_menu.hide();
            active_menu = null;
            active_menu_node = null;

            // select node
            mousedown_node = d;
            if (mousedown_node === selected_node) {
                selected_node = null;
            } else {
                selected_node = mousedown_node;
            }
            selected_link = null;

            // reposition drag line
            drag_line
                .style('marker-end', 'url(#end-arrow)')
                .classed('hidden', false)
                .attr('d', 'M' + mousedown_node.x + ',' + mousedown_node.y + 'L' + mousedown_node.x + ',' + mousedown_node.y);

            restart();
        })
        .on('mouseup', function(d) {
            if(!mousedown_node) return;

            // needed by FF
            drag_line
                .classed('hidden', true)
                .style('marker-end', '');

            // check for drag-to-self
            mouseup_node = d;
            if (mouseup_node === mousedown_node) {
                if (active_menu == null && selected_node === mouseup_node) {
                    var data = [
                        {icon: "/services/img/icons/sitemap.svg", favicon: "wrench", text: "Region Status", node: d, action: function(d) { window.location.href = '/services/regions/details/${d.data.node.params.region}'; }},
                        {icon: "/services/img/icons/microchip.svg", favicon: "wrench", text: "Agent Status", node: d, action: function(d) { window.location.href = '/services/agents/details/${d.data.node.params.region}/${d.data.node.params.agent}'; }},
                        {icon: "/services/img/icons/plug.svg", favicon: "trash-alt", text: "Plugin Status", node: d, action: function(d) { window.location.href = '/services/plugins/details/${d.data.node.params.region}/${d.data.node.params.agent}/${d.data.node.params.plugin}'; }}
                    ];

                    active_menu = new d3.radialMenu().radius(node_size + 1)
                        .thickness(30)
                        .appendTo(d3.select(this)[0][0].parentNode)
                        .show(data);
                    active_menu_node = mouseup_node;
                }
                resetMouseVars();
                return;
            }

            // unenlarge target node
            d3.select(this).attr('transform', '');

            // add link to graph (update if exists)
            // NB: links are strictly source < target; arrows separately specified by booleans
            var source, target, direction;
            if(mousedown_node.id < mouseup_node.id) {
                source = mousedown_node;
                target = mouseup_node;
                direction = 'right';
            } else {
                source = mouseup_node;
                target = mousedown_node;
                direction = 'left';
            }

            var link;
            link = links.filter(function(l) {
                return (l.source === source && l.target === target);
            })[0];

            if (link) {
                link[direction] = true;
            } else {
                link = {source: source, target: target, left: false, right: false};
                link[direction] = true;
                links.push(link);
            }

            // select new link
            selected_link = null;
            selected_node = null;
            restart();
        })
        .on('contextmenu', function(d) { d3.event.preventDefault(); });

    // show node IDs
    g.append('svg:text')
        .attr('x', 0)
        .attr('y', 4)
        .attr('class', 'id')
        .text(function(d) { return d.title; });

    // remove old nodes
    circle.exit().remove();

    // set the graph in motion
    force.start();
}

function addnode(element) {
    let point = d3.mouse(element);
    let node = {
        id: ++lastNodeId,
        title: 'Plugin ' + lastNodeId,
        reflexive: true,
        params: {
            'pluginname': '',
            'jarfile': '',
            'version': '',
            'md5': '',
            'location_agent': '',
            'location_region': ''
        }
    };
    node.x = point[0];
    node.y = point[1];
    nodes.push(node);

    restart();
}

function addExistingNode(data) {
    let node = {
        id: data.node_id,
        title: data.node_name,
        reflexive: true,
        params: data.params
    };
    node.x = 0;
    node.y = 0;
    nodes.push(node);
    restart();
    if (node.params.inode_id !== 'undefined' && node.params.resource_id !== 'undefined') {
        $.ajax({
            url: "/services/applications/nodeinfo/" + node.params.inode_id + "/" + node.params.resource_id,
            success: function (nodeData) {
                //console.log(nodeData);
                node.params.region = nodeData.isassignmentinfo.region;
                node.params.agent = nodeData.isassignmentinfo.agent;
                node.params.plugin = nodeData.isassignmentinfo.plugin;
                restart();
            },
            error: function (error) {
                console.log(error);
            }
        });
    }
}

function addExistingEdge(data) {
    let source = null;
    let target = null;
    for (id in nodes) {
        const node = nodes[id];
        if (node.id === data.node_from)
            source = node;
        if (node.id === data.node_to)
            target = node;
    }
    if (source === null || target === null)
        return;
    let link = {
        source: source,
        target: target,
        left: false,
        right: true
    };
    links.push(link);
    restart();
}

function checknode(d) {
    let color = invalid_color;
    if (d.params === 'undefined')
        color = invalid_color;
    else if (d.params.status_code === 'undefined')
        color = invalid_color;
    else if (d.params.status_code === "3")
        color = init_color;
    else if (d.params.status_code === "8")
        color = disabled_color;
    else if (d.params.status_code === "10")
        color = valid_color;
    return color;
}

function mousedown() { }

function mousemove() {
    if(!mousedown_node) return;

    // update drag line
    drag_line.attr('d', 'M' + mousedown_node.x + ',' + mousedown_node.y + 'L' + d3.mouse(this)[0] + ',' + d3.mouse(this)[1]);

    restart();
}

function mouseup() {
    if(mousedown_node) {
        // hide drag line
        drag_line
            .classed('hidden', true)
            .style('marker-end', '');
    }

    // because :active only works in WebKit?
    svg.classed('active', false);

    // clear mouse event vars
    resetMouseVars();
}

function spliceLinksForNode(node) {
    var toSplice = links.filter(function(l) {
        return (l.source === node || l.target === node);
    });
    toSplice.map(function(l) {
        links.splice(links.indexOf(l), 1);
    });
}

// only respond once per keydown
var lastKeyDown = -1;

function keydown() {
    if (editting_plugin) return;
    d3.event.preventDefault();

    if(lastKeyDown !== -1) return;
    lastKeyDown = d3.event.keyCode;

    // ctrl
    if(d3.event.keyCode === 17) {
        circle.call(force.drag);
        svg.classed('ctrl', true);
    }

    if(!selected_node && !selected_link) return;
    switch(d3.event.keyCode) {
        case 27: // esc
            if (active_menu != null)
                active_menu.hide();
            active_menu = null;
            active_menu_node = null;
            selected_link = null;
            selected_node = null;
            tooltip.style('visibility', 'hidden');
            clearTimeout(tooltip_show);
            restart();
            break;
        case 8: // backspace
        case 46: // delete
            if(selected_node) {
                nodes.splice(nodes.indexOf(selected_node), 1);
                spliceLinksForNode(selected_node);
            } else if(selected_link) {
                links.splice(links.indexOf(selected_link), 1);
            }
            selected_link = null;
            selected_node = null;
            tooltip.style('visibility', 'hidden');
            clearTimeout(tooltip_show);
            restart();
            break;
        case 66: // B
            if(selected_link) {
                // set link direction to both left and right
                selected_link.left = true;
                selected_link.right = true;
            }
            restart();
            break;
        case 76: // L
            if(selected_link) {
                // set link direction to left only
                selected_link.left = true;
                selected_link.right = false;
            }
            restart();
            break;
        case 82: // R
            if(selected_node) {
                // toggle node reflexivity
                selected_node.reflexive = !selected_node.reflexive;
            } else if(selected_link) {
                // set link direction to right only
                selected_link.left = false;
                selected_link.right = true;
            }
            restart();
            break;
    }
}

function keyup() {
    lastKeyDown = -1;

    // ctrl
    if(d3.event.keyCode === 17) {
        circle
            .on('mousedown.drag', null)
            .on('touchstart.drag', null);
        svg.classed('ctrl', false);
    }
}

function generateTooltip(d) {
    var tooltip_html = "<div id='node-tooltip-title'><h5>" + d.title + "</h5></div>";
    tooltip_html += "<div id='node-tooltip-body'>";
    for (var param in d.params) {
        tooltip_html += "<span class='key'>" + param + ":</span> <span class='string'>" + d.params[param] + "</span><br>";
    }
    tooltip_html += "</div>";
    return tooltip_html;
}

// app starts here
svg.on('mousedown', mousedown)
    .on('mousemove', mousemove)
    .on('mouseup', mouseup);
d3.select(window)
    .on('keydown', keydown)
    .on('keyup', keyup);
restart();

