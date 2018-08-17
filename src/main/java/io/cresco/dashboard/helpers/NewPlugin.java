package io.cresco.dashboard.helpers;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class NewPlugin {
    String url;
    String config;

    public NewPlugin() {
        url = "";
        config = "";
    }

    public NewPlugin(String url, String config) {
        this.url = url;
        this.config = config;
    }

    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }

    public String getConfig() {
        return config;
    }
    public void setConfig(String config) {
        this.config = config;
    }
}
