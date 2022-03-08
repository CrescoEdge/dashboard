package io.cresco.dashboard.websockets;

public class ShellInfo {

    private String sessionId;
    private String identKey;
    private String identId;
    private String stream_query;
    private String stdoutlistenerId;
    private String stderrlistenerId;
    private String ioTypeKey;
    private String outputId;
    private String inputId;
    private String regionId;
    private String agentId;
    private String pluginId;


    public void setIdentKey(String identKey) {
        this.identKey = identKey;
    }

    public String getIoTypeKey() {
        return ioTypeKey;
    }

    public void setIoTypeKey(String ioTypeKey) {
        this.ioTypeKey = ioTypeKey;
    }

    public String getOutputId() {
        return outputId;
    }

    public void setOutputId(String outputId) {
        this.outputId = outputId;
    }

    public String getInputId() {
        return inputId;
    }

    public void setInputId(String inputId) {
        this.inputId = inputId;
    }

    public ShellInfo(String sessionId, String identKy, String identId, String regionId, String agentId) {
        this.sessionId = sessionId;
        this.identKey = identKy;
        this.identId = identId;
        this.regionId = regionId;
        this.agentId = agentId;
    }

    public ShellInfo(String sessionId, String stream_query, String regionId, String agentId) {
        this.sessionId = sessionId;
        this.stream_query = stream_query;
        this.regionId = regionId;
        this.agentId = agentId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getIdentKey() {
        return identKey;
    }

    public void setIdentId(String identKey) {
        this.identKey = identKey;
    }

    public String getIdentId() {
        return identId;
    }

    public String getStream_query() {
        return stream_query;
    }

    public void setStream_query(String stream_query) {
        this.stream_query = stream_query;
    }

    public String getStdoutListenerId() {
        return stdoutlistenerId;
    }

    public void setStdoutListenerId(String listenerId) {
        this.stdoutlistenerId = listenerId;
    }

    public String getStderrListenerId() {
        return stderrlistenerId;
    }

    public void setStderrListenerId(String listenerId) {
        this.stderrlistenerId = listenerId;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }
}
