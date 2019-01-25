package io.cresco.dashboard.metrics;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.cresco.library.data.TopicType;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.util.concurrent.TimeUnit;

public class MetricCollector {

    private PluginBuilder plugin;
    private CLogger logger;

    private Cache<String, String> kpiCache;
    private Cache<String, String> kpiCacheType;
    private Cache<String, String> sysInfoCache;


    public MetricCollector(PluginBuilder plugin) {
        this.plugin = plugin;
        logger = plugin.getLogger(MetricCollector.class.getName(), CLogger.Level.Info);

        kpiCache = CacheBuilder.newBuilder()
                .concurrencyLevel(4)
                .softValues()
                //.maximumSize(10)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .build();

        kpiCacheType = CacheBuilder.newBuilder()
                .concurrencyLevel(4)
                .softValues()
                //.maximumSize(10)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .build();

        sysInfoCache = CacheBuilder.newBuilder()
                .concurrencyLevel(4)
                .softValues()
                //.maximumSize(10)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .build();

    }


    public void setKpiListener() {

        MessageListener ml = new MessageListener() {
            public void onMessage(Message msg) {
                try {


                    if (msg instanceof MapMessage) {

                        MapMessage mapMessage = (MapMessage)msg;

                        if (mapMessage.getString("perf") != null) {
                            String key = mapMessage.getStringProperty("region_id") + "." + mapMessage.getStringProperty("agent_id");
                            String messageType = mapMessage.getStringProperty("pluginname");
                            if(messageType.equals("io.cresco.sysinfo")) {
                                sysInfoCache.put(key, mapMessage.getString("perf"));

                            } else if(mapMessage.getStringProperty("plugin_id") != null) {
                                //add plugin Id
                                kpiCache.put(key + "." + mapMessage.getStringProperty("plugin_id"), mapMessage.getString("perf"));
                                kpiCacheType.put(key, messageType);
                            }
                            logger.error("insert " + mapMessage.getStringProperty("pluginname") + " metric for " + key);
                        }

                    }
                } catch(Exception ex) {

                    ex.printStackTrace();
                }
            }
        };

        //plugin.getAgentService().getDataPlaneService().addMessageListener(TopicType.AGENT,ml,"region_id IS NOT NULL AND agent_id IS NOT NULL and plugin_id IS NOT NULL AND pluginname LIKE 'io.cresco.%'");
        plugin.getAgentService().getDataPlaneService().addMessageListener(TopicType.AGENT,ml,"region_id IS NOT NULL AND agent_id IS NOT NULL AND pluginname LIKE 'io.cresco.%'");

    }






}
