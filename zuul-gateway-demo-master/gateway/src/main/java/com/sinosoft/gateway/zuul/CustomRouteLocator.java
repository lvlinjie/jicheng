package com.sinosoft.gateway.zuul;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.cloud.netflix.zuul.filters.RefreshableRouteLocator;
import org.springframework.cloud.netflix.zuul.filters.SimpleRouteLocator;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class CustomRouteLocator extends SimpleRouteLocator implements RefreshableRouteLocator {

    public final static Logger logger = LoggerFactory.getLogger(CustomRouteLocator.class);

    private JdbcTemplate jdbcTemplate;

    private ZuulProperties properties;//配置文件中的路由信息

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CustomRouteLocator(String servletPath, ZuulProperties properties) {
        super(servletPath, properties);
        this.properties = properties;
        logger.info("servletPath:{}", servletPath);
    }
    //这个方法很重要,父实现了,子类也实现并重写了
    @Override
    public void refresh() {
        doRefresh();
    }


    /**
     * 这个方法重要,路由定位器,初始化路由信息
     * @return 路由的映射集合
     */
    @Override
    protected Map<String, ZuulProperties.ZuulRoute> locateRoutes() {
        LinkedHashMap<String, ZuulProperties.ZuulRoute> routesMap = new LinkedHashMap<>();
        routesMap.putAll(super.locateRoutes());//从application.properties中加载路由信息
        routesMap.putAll(locateRoutesFromDB());//从db中加载路由信息
        LinkedHashMap<String, ZuulProperties.ZuulRoute> values = new LinkedHashMap<>();
         //优化一下路由配置:遍历所有的路由信息,对映射信息进一步优化
        for (Map.Entry<String, ZuulProperties.ZuulRoute> entry : routesMap.entrySet()) {
            String path = entry.getKey();//key在前面也相当于一个路径
            if (!path.startsWith("/")) {//如果不是以/开头,这里要转换一下,数据库查询出来路由信息做过判断,应该不会出现这样的情况,这里是有可能出现在配置文件里面的错误
                path = "/" + path;//转成以/开头的路径
            }
            if (StringUtils.hasText(this.properties.getPrefix())) {//如果这个路由信息中间含有前缀信息()
                path = this.properties.getPrefix() + path;//那要把映射路径换成前缀+路径(全)
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
            }
            values.put(path, entry.getValue());
        }
        return values;
    }

    /**
     * 从数据库加载配置信息,这里也是动态配置持久化的地方
     * @return 配置信息的映射
     */
    private Map<String, ZuulProperties.ZuulRoute> locateRoutesFromDB() {
        Map<String, ZuulProperties.ZuulRoute> routes = new LinkedHashMap<>();
        List<ZuulRouteVO> results = jdbcTemplate.query("select * from gateway_api_define where enabled = true ", new
                BeanPropertyRowMapper<>(ZuulRouteVO.class));//去数据库查询,指定返回实体类型
        for (ZuulRouteVO result : results) {//遍历路由映射集合
            if (StringUtils.isEmpty(result.getPath()) ) {
                continue;//如果实体没有映射路径,跳出循环,继续遍历下一下
            }
            if (StringUtils.isEmpty(result.getServiceId()) && StringUtils.isEmpty(result.getUrl())) {
                continue;//如果映射路径不为空,但是映射服务名和映射的URL都为空,跳出循环,遍历下一个
            }
            ZuulProperties.ZuulRoute zuulRoute = new ZuulProperties.ZuulRoute();
            try {
                BeanUtils.copyProperties(result, zuulRoute);//把result的属性copy到zuulRoute里面
            } catch (Exception e) {
                logger.error("=============load zuul route info from db with error==============", e);
            }
            routes.put(zuulRoute.getPath(), zuulRoute);//k-v映射,路径-路由实体映射
        }
        return routes;//返回数据库查询的所有的路径-实体
    }

    /**
     * vo实体
     */
    public static class ZuulRouteVO {
        private String id;
        private String path;
        private String serviceId;
        private String url;
        private boolean stripPrefix = true;
        private Boolean retryable;
        private Boolean enabled;
        public String getId() {
            return id;
        }
        public void setId(String id) {
            this.id = id;
        }
        public String getPath() {
            return path;
        }
        public void setPath(String path) {
            this.path = path;
        }
        public String getServiceId() {
            return serviceId;
        }
        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }
        public String getUrl() {
            return url;
        }
        public void setUrl(String url) {
            this.url = url;
        }
        public boolean isStripPrefix() {
            return stripPrefix;
        }
        public void setStripPrefix(boolean stripPrefix) {
            this.stripPrefix = stripPrefix;
        }
        public Boolean getRetryable() {
            return retryable;
        }
        public void setRetryable(Boolean retryable) {
            this.retryable = retryable;
        }
        public Boolean getEnabled() {
            return enabled;
        }
        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}
