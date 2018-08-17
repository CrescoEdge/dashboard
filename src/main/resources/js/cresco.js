/* eslint-disable object-shorthand */
/* eslint-disable no-unused-vars */

const CHECK_GC_STATUS_VISIBLE_PERIOD = 5000
const CHECK_GC_STATUS_NOT_VISIBLE_PERIOD = 60000
const CHECK_GC_TIMER_PERIOD = 1000
const endpoint = 'http://128.163.202.58:9999'

let table = null
let checkGCTimer = null
let timer = 0

/**
 * @returns {null}    No return
 */
function submitNewGC() {
  const pName = $('#addGCModalName').val().replace(' ', '_')
  if (pName === null || pName === '') {
    $('#createNewGCError').html('You must enter a pipeline name!')
    return null
  }
  $('#addGCModal').modal('hide')
  /**
   *
   * @param {object} data                 Blah
   * @param {string} data.dashboardLive   Whether the global controller dashboard is live or not
   * @param {string} data.pipeline_id     Pipeline identifier
   */
  $.get(`${endpoint}/API/creategc?tenant=0&projectname=${pName}`, (data) => {
    updateGCList()
    updateGCDetailsPane(data)
    $('#detailsGCModal').modal('show')
    if (data.dashboardLive) {
      checkGCTimer = null
      $('#detailsGCModalWaiting').css('display', 'none')
      $('#detailsGCModalActive').css('display', 'block')
    } else {
      $('#detailsGCModalWaiting').css('display', 'block')
      $('#detailsGCModalActive').css('display', 'none')
      checkGCTimer = setTimeout(() => {
        checkGC(data.pipeline_id)
      }, CHECK_GC_TIMER_PERIOD)
    }
  })
  return null
}

function updateGCDetailsPane(data) {
  const dockerCmd = formatDockerCommand(data)
  const dashboardURL = formatDashboardURL(data)
  $('#dockerGCCommand').html(dockerCmd)
  $('#detailsGCModalLink').attr('href', dashboardURL)
}

/**
 *
 * @param {string} pID    Pipeline identifier
 * @returns {null}        No return
 */
function checkGC(pID) {
  /**
   * @param {object} data                 Global controller information
   * @param {string} data.dashboardLive   Whether the global controller dashboard is live or not
   */
  $.get(`${endpoint}/API/getgc?tenant=0&pipeline=${pID}`, (data) => {
    if (data.dashboardLive) {
      checkGCTimer = null
      $('#detailsGCModalWaiting').css('display', 'none')
      $('#detailsGCModalActive').css('display', 'block')
    } else {
      $('#detailsGCModalWaiting').css('display', 'block')
      $('#detailsGCModalActive').css('display', 'none')
      checkGCTimer = setTimeout(() => {
        checkGC(pID)
      }, CHECK_GC_TIMER_PERIOD)
    }
  })
}

/**
 *
 * @param {object} data                           Required container information
 * @param {string} data.brokerPort                Port to use for Cresco Broker in container
 * @param {string} data.discoveryPort             Port to use for discovery in container
 * @param {string} data.discovery_secret_global   Global secret
 * @param {string} data.discovery_secret_region   Regional secret
 * @param {string} data.discovery_secret_agent    Agent secret
 * @param {string} data.ip                        IP address for dashboard
 * @returns {string}                              Formatted command to run Cresco Docker container
 */
function formatDockerCommand(data) {
  return 'docker run -it -e CRESCO_is_agent="true" ' +
    `-e CRESCO_discovery_port="${data.brokerPort}" ` +
    `-e CRESCO_netdiscoveryport="${data.discoveryPort}" ` +
    `-e CRESCO_discovery_secret_global="${data.discovery_secret_global}" ` +
    `-e CRESCO_discovery_secret_region="${data.discovery_secret_region}" ` +
    `-e CRESCO_discovery_secret_agent="${data.discovery_secret_agent}" ` +
    `-e CRESCO_regional_controller_host="${data.ip}" ` +
    'crescoedgecomputing/quickstart'
}

/**
 *
 * @param {object} data           Required dashboard information
 * @param {string} data.ip        IP address for dashboard
 * @param {string} data.webPort   Port for dashboard
 * @returns {string}              Formatted address of running GC dashboard
 */
function formatDashboardURL(data) {
  return `http://${data.ip}:${data.webPort}`
}

function updateGCList() {
  $.get(`${endpoint}/API/listgc?tenant=0`, (raw) => {
    rebuildGCList(raw)
  })
}

/**
 *
 * @param {object} data               Blah
 * @param {array} data.pipelines      Active running pipelines
 * @returns {null}        No return
 */
function rebuildGCList(data) {
  if (table !== null) {
    table.destroy()
  }
  let body = ''
  /**
   * @param {number} i                Index of pipeline
   * @param {object} v                Pipeline
   * @param {string} v.pipeline_id    Pipeline identifier
   * @param {string} v.pipeline_name  Pipeline human-readable name
   * @param {string} v.status_code    Pipeline status code
   * @param {string} v.status_desc    Human-readable pipeline status
   * @param {string} v.tenant_id      Tenant identifier
   */
  $.each(data.pipelines, (i, v) => {
    body += '<tr>' +
      `<td><a href="javascript:void(0)" onclick="getGC('${v.pipeline_id}')">${v.pipeline_name}</a></td>` +
      `<td>${v.tenant_id}</td>` +
      `<td>[${v.status_code}] - ${v.status_desc}</td>` +
      `<td><button class='btn btn-danger' onclick="deleteGC('${v.pipeline_id}')"><i class='fa fa-trash'></i></button></td>` +
      '</tr>'
  })
  $('#gcTableBody').html(body)
  table = $('#example').DataTable({
    responsive: true,
    columns: [
      {
        responsivePriority: 4
      },
      {
        responsivePriority: 1
      },
      {
        responsivePriority: 2
      },
      {
        orderable: false,
        responsivePriority: 3,
        width: '40px'
      }
    ]
  })
}

/**
 *
 * @param {string} pID    Pipeline identifier
 * @returns {null}        No return
 */
function getGC(pID) {
  $.get(`${endpoint}/API/getgc?tenant=0&pipeline=${pID}`, (data) => {
    updateGCDetailsPane(data)
    $('#detailsGCModal').modal('show')
    if (data.dashboardLive) {
      checkGCTimer = null
      $('#detailsGCModalWaiting').css('display', 'none')
      $('#detailsGCModalActive').css('display', 'block')
    } else {
      $('#detailsGCModalWaiting').css('display', 'block')
      $('#detailsGCModalActive').css('display', 'none')
      checkGCTimer = setTimeout(() => {
        checkGC(pID)
      }, CHECK_GC_TIMER_PERIOD)
    }
  })
}

/**
 *
 * @param {string} pID    Pipeline identifier
 * @returns {null}        No return
 */
function deleteGC(pID) {
  $.get(`${endpoint}/API/deletegc?tenant=0&pipeline=${pID}`, () => {
    updateGCList()
  })
}

$(() => {
  updateGCList()
  $('#addGCModal').on('hidden.bs.modal', () => {
    $('#createNewGCError').html('')
  })
  $('#detailsGCModal').on('hidden.bs.modal', () => {
    if (checkGCTimer !== null) {
      clearTimeout(checkGCTimer)
    }
  })
  timer = setInterval(updateGCList, document.hidden ? CHECK_GC_STATUS_NOT_VISIBLE_PERIOD : CHECK_GC_STATUS_VISIBLE_PERIOD)
  if (document.addEventListener) {
    document.addEventListener('visibilitychange', visibilityChanged)
  }
  function visibilityChanged() {
    clearTimeout(timer)
    timer = setInterval(updateGCList, document.hidden ? CHECK_GC_STATUS_NOT_VISIBLE_PERIOD : CHECK_GC_STATUS_VISIBLE_PERIOD)
  }
})
