'use strict';

const express = require('express'),
    mustacheExpress = require('mustache-express'),
    app = express(),
    port = process.env.PORT || 5000;

app.engine('mustache', mustacheExpress());
app.set('view engine', 'mustache');
app.set('views', __dirname + "/");
app.use('/css', express.static('css'));
app.use('/img', express.static('img'));
app.use('/js', express.static('js'));
app.use('/vendors', express.static('vendors'));

/**
 * Root Routes
 */
app.get('/', (req, res) => {
    res.render('overview', {
        page: {
            title: "Some Page"
        },
        user: {
            name: "Admin"
        }
    });
});
app.get('/login', (req, res) => {
    res.render('login', {
        errors: "Invalid username/password"
    });
});
app.post('/login', (req, res) => {
    res.redirect('/');
});
app.get('/logout', (req, res) => {
    res.redirect('/login');
});

/**
 * Agents Routes
 */
app.get('/agents', (req, res) => {
    res.render('agents', {
        page: "builder",
        section: "applicaitons",
        user: "Admin"
    })
});
app.get('/agents/details/:region/:agent', (req, res) => {
    res.render('agent-details', {
        page: "details",
        section: "agents",
        user: "Admin",
        region: req.params.region,
        agent: req.params.agent
    });
});
app.get('/agents/list', (req, res) => {
    res.send(agentsListData(null));
});
app.get('/agents/listlocal', (req, res) => {
    res.send(agentsListLocalData());
});
app.get('/agents/list/:region', (req, res) => {
    res.send(agentsListData(req.params.region));
});
app.get('/agents/resources/:region/:agent', (req, res) => {
    res.send(agentsResourceData(req.params.region, req.params.agent));
});

/**
 * Applications Routes
 */
app.get('/applications', (req, res) => {
    res.render('applications', {
        page: "index",
        section: "applications",
        user: "Admin"
    });
});
app.get('/applications/build', (req, res) => {
    res.render('application-builder', {
        page: "builder",
        section: "applicaitons",
        user: "Admin"
    })
});
app.get('/applications/details/:id', (req, res) => {
    res.render('application-details', {
        page: "builder",
        section: "applicaitons",
        user: "Admin",
        app_id: req.params.id
    })
});
app.get('/applications/list', (req, res) => {
    res.send(applicationsListData());
});
app.post('/applications/add', (req, res) => {
    res.send(applicationsAddData());
});
app.get('/applications/info/:id', (req, res) => {
    res.send(applicationsInfoData(req.params.id));
});
app.get('/applications/nodeinfo/:inode/:resource', (req, res) => {
    res.send(applicationsNodeInfoData(req.params.inode, req.params.resource));
});
app.get('/applications/export/:id', (req, res) => {
    res.send(applicationsExportData(req.params.id));
});
app.get('/applications/delete/:id', (req, res) => {
    res.redirect('/applications');
});

/**
 * Plugins Routes
 */
app.get('/plugins', (req, res) => {
    res.render('plugins', {
        page: "index",
        section: "plugins",
        user: "Admin"
    });
});
app.get('/plugins/details/:region/:agent/:plugin(*)', (req, res) => {
    res.render('plugin-details', {
        page: "details",
        section: "plugins",
        user: "Admin",
        region: req.params.region,
        agent: req.params.agent,
        plugin: req.params.plugin
    });
});
app.get('/plugins/info/:region/:agent/:plugin(*)', (req, res) => {
    res.send(pluginsInfoData(req.params.region, req.params.agent, req.params.plugin));
});
app.get('/plugins/kpi/:region/:agent/:plugin(*)', (req, res) => {
    res.send(pluginsKPIData(req.params.region, req.params.agent, req.params.plugin));
});
app.get('/plugins/list', (req, res) => {
    res.send(pluginsListData(null, null));
});
app.get('/plugins/list/:region', (req, res) => {
    res.send(pluginsListData(req.params.region, null));
});
app.get('/plugins/list/:region/:agent', (req, res) => {
    res.send(pluginsListData(req.params.region, req.params.agent));
});
app.post('/plugins/uploadplugin', (req, res) => {
    res.send(pluginsUploadPluginData());
});
app.get('/plugins/listrepo', (req, res) => {
    res.send(pluginsListRepoData());
});
app.get('/plugins/local', (req, res) => {
    // ToDo: Implement this route
    res.send("Not yet implemented!");
});

/**
 * Regions Routes
 */
app.get('/regions', (req, res) => {
    res.render('regions', {
        page: { },
        user: {
            name: "Admin"
        }
    });
});
app.get('/regions/details/:region', (req, res) => {
    res.render('region-details', {
        page: "details",
        section: "regions",
        user: "Admin",
        region: req.params.region
    });
});
app.get('/regions/list', (req, res) => {
    res.send(regionsListData());
});
app.get('/regions/resources/:region', (req, res) => {
    res.send(regionsResourcesData(req.params.region));
});

/**
 * Mock-Server Startup Section
 */
app.listen(port);
console.log(`Mock Cresco Server running at http://localhost:${port}`);

/**
 * Agents Mock-Data Generators
 */
function agentsListData(region) {
    var data = {"agents":[{"environment":"simulation","plugins":"2","name":"mock-agent","location":"work","region":"mock-region","platform":"simulation"},{"environment":"simulation","plugins":"2","name":"mock-agent-2","location":"work","region":"mock-region","platform":"simulation"}]};
    return data;
}
function agentsListLocalData() {
    var data = {"agents":[{"agent":"mock-agent","region":"mock-region"}]};
    return data;
}
function agentsResourceData(region, agent) {
    let perf = {
        "disk": [{
            "disk-writebytes": "4676810703872",
            "disk-writes": "422573934",
            "disk-size": "480103981056",
            "disk-model": "Unknown",
            "disk-readbytes": "541668420608",
            "disk-name": "/dev/sda",
            "disk-transfertime": "938590636",
            "disk-reads": "33616835"
        }],
        "os": [{
            "sys-manufacturer": "GNU/Linux",
            "process-count": "1",
            "sys-uptime": "63595868",
            "sys-family": "Alpine Linux",
            "sys-os": "3.7.0 build 4.4.0-22-generic"
        }],
        "mem": [{
            "memory-total": "67562414080",
            "swap-total": "20064497664",
            "swap-used": "4030464",
            "memory-available": "62166331392"
        }],
        "part": [{
            "part-size": "254803968",
            "part-mount": "",
            "part-name": "sda1",
            "part-id": "/dev/sda1"
        }, {
            "part-size": "459782750208",
            "part-mount": "",
            "part-name": "sda2",
            "part-id": "/dev/sda2"
        }, {
            "part-size": "20064501760",
            "part-mount": "",
            "part-name": "sda3",
            "part-id": "/dev/sda3"
        }],
        "cpu": [{
            "cpu-logical-count": "8",
            "cpu-summary": "",
            "cpu-nice-load": "0.0",
            "cpu-physical-count": "1",
            "cpu-sys-load": "0.4",
            "is64bit": "false",
            "cpu-user-load": "11.4",
            "cpu-idle-load": "88.3",
            "cpu-ident": " Family ? Model ? Stepping ?",
            "cpu-id": "0000000000000000"
        }],
        "net": [{
            "packets-sent": "4324",
            "packets-received": "4702",
            "interface-name": "eth0",
            "link-speed": "10000",
            "mac": "02:42:ac:11:00:02",
            "ipv6-addresses": ["fe80:0:0:0:42:acff:fe11:2"],
            "mtu": "1500",
            "ipv4-addresses": ["172.17.0.2"],
            "errors-out": "0",
            "errors-in": "0",
            "bytes-sent": "5817510",
            "bytes-received": "6883077",
            "timestamp": "1526935403816"
        }],
        "fs": [{
            "volume": "none",
            "name": "/",
            "description": "Mount Point",
            "available-space": "418170646528",
            "type": "aufs",
            "total-space": "452433559552",
            "mount": "/",
            "uuid": ""
        }, {
            "volume": "/dev/disk/by-uuid/b1a72de3-5325-49dd-b475-28b2ad90452c",
            "name": "/dev/disk/by-uuid/b1a72de3-5325-49dd-b475-28b2ad90452c",
            "description": "Local Disk",
            "available-space": "418170646528",
            "type": "ext4",
            "total-space": "452433559552",
            "mount": "/etc/resolv.conf",
            "uuid": ""
        }, {
            "volume": "/dev/disk/by-uuid/b1a72de3-5325-49dd-b475-28b2ad90452c",
            "name": "/dev/disk/by-uuid/b1a72de3-5325-49dd-b475-28b2ad90452c",
            "description": "Local Disk",
            "available-space": "418170646528",
            "type": "ext4",
            "total-space": "452433559552",
            "mount": "/etc/hostname",
            "uuid": ""
        }, {
            "volume": "/dev/disk/by-uuid/b1a72de3-5325-49dd-b475-28b2ad90452c",
            "name": "/dev/disk/by-uuid/b1a72de3-5325-49dd-b475-28b2ad90452c",
            "description": "Local Disk",
            "available-space": "418170646528",
            "type": "ext4",
            "total-space": "452433559552",
            "mount": "/etc/hosts",
            "uuid": ""
        }]
    };
    let data = {
        "agentresourceinfo": [{
            "perf": JSON.stringify(perf)
        }]
    };
    return data;
}

/**
 * Applications Mock-Data Generators
 */
function applicationsListData() {
    var data = {"pipelines":[{"tenant_id":"0","status_code":"50","status_desc":"Pipeline Failed Resource Assignment","pipeline_id":"mock-bad-pipeline-id","pipeline_name":"mock_bad_pipeline"},{"tenant_id":"0","status_code":"10","status_desc":"Pipeline Active","pipeline_id":"mock-pipeline-id","pipeline_name":"mock_pipeline"}]};
    return data;
}
function applicationsAddData() {
    var data = {"gpipeline_id": "mock-pipeline-id"}
    return data;
}
function applicationsInfoData(id) {
    var data = {"pipeline_id":"mock-pipeline-id","pipeline_name":"mock_pipeline","status_code":"10","status_desc":"Pipeline Active","nodes":[{"type":"dummy","node_name":"Plugin 1","node_id":"mock-node-id","isSource":false,"workloadUtil":0.0,"params":{"status_code":"3","status_desc":"iNode Active.","params":"pluginname:mock-pluginname,jarfile:mock-jar,version:mock-version,md5:mock-md5,location_agent:mock-agent,location_region:mock-region,resource_id:mock-resource-id,inode_id:mock-inode-id","inode_id":"mock-inode-id","resource_id":"mock-resource-id"}},{"type":"dummy","node_name":"Plugin 2","node_id":"mock-node-2-id","isSource":false,"workloadUtil":0.0,"params":{"status_code":"8","status_desc":"iNode Active.","params":"pluginname:mock-pluginname,jarfile:mock-jar,version:mock-version,md5:mock-md5,location_agent:mock-agent-2,location_region:mock-region,resource_id:mock-resource-2-id,inode_id:mock-inode-2-id","inode_id":"mock-inode-2-id","resource_id":"mock-resource-2-id"}}],"edges":[{"edge_id": "mock-edge-id", "node_from": "mock-node-id", "node_to": "mock-node-2-id"}]};
    return data;
}
function applicationsNodeInfoData(inode, resource) {
    var data = {
        "isassignmentinfo": {
            "agent": "mock-agent",
            "plugin": "plugin/0",
            "resource_id": "ca7768c1-e568-4bd1-b9e1-2d3468470e27",
            "inode_id": "a8dcc078-287c-45c7-9dcf-0f22dce014eb",
            "region": "mock-region"
        },
        "isassignmentresourceinfo": {}
    };
    return data;
}
function applicationsExportData(id) {
    var data = {"pipeline_id":"mock-pipeline-id","pipeline_name":"mock_pipeline","status_code":"3","status_desc":"Pipeline Nodes Created.","nodes":[{"type":"dummy","node_name":"Plugin 1","node_id":"mock-node-id","isSource":false,"workloadUtil":0.0,"params":{"pluginname":"mock-pluginname","jarfile":"mock-jar","version":"mock-version","md5":"mock-md5","location_agent":"mock-agent","location_region":"mock-region","resource_id":"mock-resource-id","inode_id":"mock-inode-id"}},{"type":"dummy","node_name":"Plugin 2","node_id":"mock-node-2-id","isSource":false,"workloadUtil":0.0,"params":{"pluginname":"mock-pluginname","jarfile":"mock-jar","version":"mock-version","md5":"mock-md5","location_agent":"mock-agent-2","location_region":"mock-region","resource_id":"mock-resource-2-id","inode_id":"mock-inode-2-id"}}],"edges":[{"edge_id": "mock-edge-id", "node_from": "mock-node-id", "node_to": "mock-node-2-id"}]};
    return data;
}

/**
 * Plugins Mock-Data Generators
 */
function pluginsInfoData(region, agent, plugin) {
    var data = {"pluginname": "mock-pluginname", "jarfile": "mock-jar"}
    return data;
}
function pluginsKPIData(region, agent, plugin) {
    let data = [
        {
            "name": "controllerinfo_inode",
            "metrics": "{\"controller\":[{\"name\":\"system.load.average.1m\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"2.8642578125\"},{\"name\":\"system.cpu.usage\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"0.14108723135271808\"},{\"name\":\"jvm.memory.committed\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"5898240.0\"},{\"name\":\"process.cpu.usage\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"0.05002657804450424\"},{\"name\":\"jvm.threads.peak\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"86.0\"},{\"name\":\"jvm.threads.live\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"85.0\"},{\"name\":\"jvm.memory.max\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"1.1010048E7\"},{\"name\":\"jvm.memory.used\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"1.3084992E7\"},{\"name\":\"system.cpu.count\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"8.0\"},{\"name\":\"jvm.classes.loaded\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"7873.0\"},{\"totaltime\":\"14708.963966\",\"max\":\"14636.039381\",\"mean\":\"1337.1785423636363\",\"name\":\"message.transaction.time\",\"count\":\"11\",\"type\":\"NODE\",\"class\":\"TIMER\"},{\"name\":\"jvm.classes.unloaded\",\"count\":\"0.0\",\"type\":\"NODE\",\"class\":\"COUNTER\"},{\"name\":\"jvm.buffer.memory.used\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"1.074557642E9\"},{\"name\":\"jvm.threads.daemon\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"38.0\"},{\"name\":\"jvm.buffer.count\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"5.0\"},{\"name\":\"jvm.buffer.total.capacity\",\"type\":\"NODE\",\"class\":\"GAUGE\",\"value\":\"1.074557642E9\"}]}"
        }
    ];
    return data;
}
function pluginsListData(region, agent) {
    var data = {"plugins":[{"agent": "mock-agent", "name": "plugin/0", "region":"mock-region"},{"agent": "mock-agent", "name": "plugin/1", "region":"mock-region"},{"agent": "mock-agent-2", "name": "plugin/0", "region":"mock-region"},{"agent": "mock-agent-2", "name": "plugin/1", "region":"mock-region"}]};
    if (agent !== null)
        data = {"plugins":[{"agent": agent, "name": "plugin/0", "region":"mock-region"},{"agent": agent, "name": "plugin/1", "region":"mock-region"}]};
    return data;
}
function pluginsUploadPluginData() {
    var data = {"pluginname": "mock-plugin", "jarfile": "mock-jar", "version": "mock-version", "md5": "mock-md5"};
    return data;
}
function pluginsListRepoData() {
    var data = {"plugins": [{"pluginname": "mock-plugin", "jarfile": "mock-jar", "version": "mock-version", "md5": "mock-md5"},{"pluginname": "mock-plugin-2", "jarfile": "mock-jar-2", "version": "mock-version-2", "md5": "mock-md5-2"},{"pluginname": "mock-plugin-3", "jarfile": "mock-jar-3", "version": "mock-version-3", "md5": "mock-md5-3"}]};
    return data;
}

/**
 Regions Mock-Data Generators
 */
function regionsListData() {
    var data = {"regions": [{"name": "mock-region", "agents": "2"}]};
    return data;
}
function regionsResourcesData(region) {
    var data = {
        "regionresourceinfo": [
            {
                "disk_available": "1672682586112",
                "disk_total": "1809734238208",
                "mem_available": "62166331392",
                "cpu_core_count": "8",
                "mem_total": "67562414080"
            }
        ]
    };
    return data;
}
