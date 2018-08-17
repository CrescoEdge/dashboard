var elements = [];
var element_objects = [];
var connections = [];
var notifications = [];
var connectorPaintStyle = {
    lineWidth: 4,
    strokeStyle: '#888',
    joinstyle: "round",
    outlineColor: "#eaedef",
    outlineWidth: 2
};
var connectorHoverStyle = {
    lineWidth: 4,
    strokeStyle: '#dd4b39',
    outlineWidth: 2,
    outlineColor: 'white'
};
var endpointHoverStyle = {
    fillStyle: '#5c96bc'
};
var sourceEndpoint = {
    endpoint: "Dot",
    paintStyle: {
        strokeStyle: '#dd4b39',
        fillStyle: 'transparent',
        radius: 7,
        lineWidth: 2
    },
    isSource: true,
    connector: [ "Flowchart", { stub: [10, 10], gap: 10, cornerRadius: 5, alwaysRespectStubs: true } ],
    connectorStyle: connectorPaintStyle,
    hoverPaintStyle: endpointHoverStyle,
    connectorHoverStyle: connectorHoverStyle,
    maxConnections: 1,
    dragOptions: {},
    overlays:[
        [ "Label", {
            location:[0.5, 1.5],
            label:"",
            cssClass:"endpointSourceLabel"
        } ]
    ]
};
var targetEndpoint = {
    endpoint: "Dot",
    paintStyle: { fillStyle: '#dd4b39', radius: 11 },
    hoverPaintStyle: endpointHoverStyle,
    maxConnections: -1,
    dropOptions: { hoverClass: 'hover', activeClass: 'active' },
    isTarget: true,
    overlays:[
        [ "Label", { location:[0.5, -0.5], label:"", cssClass:"endpointTargetLabel" } ]
    ]
};