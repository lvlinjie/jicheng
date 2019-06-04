package com.sinosoft.gateway.config;

import com.sinosoft.gateway.zuul.CustomRouteLocator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Created by lvlj
 * desc:路由配置
 */
@Configuration
public class CustomZuulConfig {

    @Autowired
    ZuulProperties zuulProperties; //zuul的配置文件,对应了application.properties中的配置信息
    @Autowired(required = false)
    ServerProperties server;
    @Autowired
    JdbcTemplate jdbcTemplate;

    /**
     * 显示声明一个自定义的路由定位器,加载到环境中去
     * @return
     */
    @Bean
    public CustomRouteLocator routeLocator() {
        CustomRouteLocator routeLocator = new CustomRouteLocator(this.server.getServletPrefix(), this.zuulProperties);
        routeLocator.setJdbcTemplate(jdbcTemplate);
        return routeLocator;
    }

}
